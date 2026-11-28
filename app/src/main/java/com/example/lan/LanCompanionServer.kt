package com.example.lan

import com.example.data.CustomizationPrefs
import com.example.data.MasroufiDataSource
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.domain.CashFlowForecastEngine
import com.example.domain.FinancialInsightsEngine
import com.example.domain.ForecastHorizon
import com.example.domain.InternalTransferResult
import com.example.domain.LoanRepaymentResult
import com.example.domain.NetWorthCalculator
import com.example.domain.PayCycleEngine
import com.example.domain.RecurrenceScheduleEngine
import com.example.domain.TransactionSearchCriteria
import com.masroufi.core.Money
import com.masroufi.core.sumMoney
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.ServerSocket
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provider for zero-CDN embedded LAN web assets (`assets/lan_web/`).
 */
fun interface LanAssetProvider {
    fun readUtf8(assetName: String): String?
}

/**
 * Embedded local-only Ktor CIO HTTP server for Masroufi's LAN Companion.
 *
 * Strict guarantees:
 * - Binds strictly to an RFC1918 private Wi-Fi IPv4 address (or `127.0.0.1` in unit tests ONLY).
 * - Never binds to `0.0.0.0`.
 * - Enforces Host header validation (anti-DNS-rebinding), Origin/Sec-Fetch-Site validation on POST,
 *   ephemeral 6-digit PIN auth with 5-strike lockout, memory-only 256-bit Bearer token auth,
 *   and strict CSP / `Cache-Control: no-store` / `X-Content-Type-Options: nosniff` headers.
 * - Uses manual reflection-free `JSONObject`/`JSONArray` and exact `Long` centimes throughout.
 * - Routes all writes through Room `@Transaction` repository methods.
 */
class LanCompanionServer(
    private val dataSource: MasroufiDataSource,
    private val userId: String,
    val authManager: LanAuthSessionManager,
    private val assetProvider: LanAssetProvider,
    private val todayIsoProvider: () -> String = { PayCycleEngine.todayIso() },
    private val onStateChanged: () -> Unit = {}
) {
    companion object {
        const val DEFAULT_PORT = 8765
        const val MAX_BODY_BYTES = 64 * 1024
        private val ALLOWED_TX_TYPES = setOf("expense", "income")
        private const val STRICT_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    var bindHost: String = ""
        private set
    var boundPort: Int = 0
        private set

    val baseUrl: String
        get() = if (bindHost.isNotEmpty() && boundPort > 0) "http://$bindHost:$boundPort" else ""

    fun start(
        host: String,
        preferredPort: Int = DEFAULT_PORT,
        allowLoopbackForTests: Boolean = false
    ): String {
        require(LanNetworkUtils.isValidLanBindHost(host, allowLoopbackForTests)) {
            "Refusing to bind LAN Companion to non-LAN host"
        }
        stop()

        val portToUse = resolveAvailablePort(host, preferredPort)
        bindHost = host
        boundPort = portToUse

        val engine = embeddedServer(CIO, host = host, port = portToUse) {
            intercept(ApplicationCallPipeline.Plugins) {
                applySecurityHeaders(call)

                // 1. Strict Host header validation against DNS rebinding
                val incomingHost = call.request.headers[HttpHeaders.Host]
                if (!authManager.isAllowedHostHeader(incomingHost, bindHost, boundPort)) {
                    call.respondJson(
                        HttpStatusCode.Forbidden,
                        LanJsonCodec.errorJson("invalid_host", "Forbidden Host header")
                    )
                    finish()
                    return@intercept
                }

                // 2. Strict Origin / Sec-Fetch-Site validation on mutating requests
                if (call.request.httpMethod != HttpMethod.Get && call.request.httpMethod != HttpMethod.Head) {
                    val origin = call.request.headers[HttpHeaders.Origin]
                    val fetchSite = call.request.headers["Sec-Fetch-Site"]
                    if (!authManager.isAllowedOriginForMutation(origin, fetchSite, bindHost, boundPort)) {
                        call.respondJson(
                            HttpStatusCode.Forbidden,
                            LanJsonCodec.errorJson("invalid_origin", "Cross-origin mutation rejected")
                        )
                        finish()
                        return@intercept
                    }
                }
            }

            routing {
                // Static zero-CDN embedded Web UI
                get("/") {
                    val html = assetProvider.readUtf8("index.html")
                    if (html == null) {
                        call.respondText("LAN UI assets unavailable", ContentType.Text.Plain, HttpStatusCode.NotFound)
                    } else {
                        call.respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"))
                    }
                }
                get("/app.css") {
                    val css = assetProvider.readUtf8("app.css")
                    if (css == null) {
                        call.respondText("", ContentType.Text.CSS, HttpStatusCode.NotFound)
                    } else {
                        call.respondText(css, ContentType.Text.CSS.withParameter("charset", "utf-8"))
                    }
                }
                get("/app.js") {
                    val js = assetProvider.readUtf8("app.js")
                    if (js == null) {
                        call.respondText("", ContentType.Application.JavaScript, HttpStatusCode.NotFound)
                    } else {
                        call.respondText(js, ContentType.Application.JavaScript.withParameter("charset", "utf-8"))
                    }
                }

                // Auth: Ephemeral 6-digit PIN -> memory-only Bearer token
                post("/api/auth/login") {
                    val bodyObj = call.readJsonObjectOrRespondError() ?: return@post
                    val candidatePin = bodyObj.optString("pin", "")
                    when (val res = authManager.authenticatePin(candidatePin)) {
                        is LanPinAuthResult.Success -> {
                            val profile = dataSource.observeProfile(userId).first() ?: UserProfile(userId = userId)
                            val items = dataSource.observeFinancialItems(userId).first()
                            val prefs = items.firstOrNull { it.itemType == "prefs" }
                                ?.let { CustomizationPrefs.fromFinancialItem(it) }
                                ?: CustomizationPrefs()
                            onStateChanged()
                            val payload = JSONObject().apply {
                                put("token", res.token)
                                put("idleTimeoutMinutes", res.idleTimeoutMinutes)
                                put("language", profile.language)
                                put("currency", prefs.currencySymbol.ifBlank { "DH" })
                            }
                            call.respondJson(HttpStatusCode.OK, payload.toString())
                        }
                        is LanPinAuthResult.InvalidPin -> {
                            call.respondJson(
                                HttpStatusCode.Unauthorized,
                                LanJsonCodec.errorJson(
                                    code = "invalid_pin",
                                    message = "Invalid session PIN",
                                    extra = mapOf("remainingAttempts" to res.remainingAttempts)
                                )
                            )
                        }
                        is LanPinAuthResult.LockedOut -> {
                            call.response.header(HttpHeaders.RetryAfter, res.retryAfterSeconds.toString())
                            call.respondJson(
                                HttpStatusCode.TooManyRequests,
                                LanJsonCodec.errorJson(
                                    code = "locked_out",
                                    message = "Too many failed attempts. Try again later.",
                                    extra = mapOf("retryAfterSeconds" to res.retryAfterSeconds)
                                )
                            )
                        }
                    }
                }

                post("/api/auth/logout") {
                    val token = call.requireBearerTokenOrRespond401() ?: return@post
                    authManager.revokeToken(token)
                    onStateChanged()
                    call.respondJson(HttpStatusCode.OK, JSONObject().put("ok", true).toString())
                }

                // Read endpoints (Bearer-protected)
                get("/api/dashboard") {
                    call.requireBearerTokenOrRespond401() ?: return@get
                    val todayIso = todayIsoProvider()
                    val profile = dataSource.observeProfile(userId).first() ?: UserProfile(userId = userId)
                    val accounts = dataSource.observeAccounts(userId).first()
                    val envelopes = dataSource.observeEnvelopes(userId).first()
                    val items = dataSource.observeFinancialItems(userId).first()
                    val transactions = dataSource.observeTransactions(userId).first()
                    val loans = dataSource.getLoans(userId)
                    val rules = dataSource.getRecurringRules(userId)

                    val prefs = items.firstOrNull { it.itemType == "prefs" }
                        ?.let { CustomizationPrefs.fromFinancialItem(it) }
                        ?: CustomizationPrefs()
                    val activeWindow = PayCycleEngine.computeCycleWindow(
                        payCycleDay = profile.payCycleDay,
                        monthOffset = 0,
                        nowIso = todayIso
                    )
                    val cycleTxs = transactions.filter {
                        PayCycleEngine.isInWindow(it.isoDate, activeWindow) && it.isoDate <= todayIso
                    }
                    val cycleExpenses = cycleTxs.filter { it.type == "expense" }.sumMoney { it.amount }
                    val cycleIncomes = cycleTxs.filter { it.type == "income" }.sumMoney { it.amount }
                    val totalAvailable = profile.baseBudget + cycleIncomes
                    val remainingBudget = totalAvailable - cycleExpenses
                    val daysLeft = activeWindow.daysRemaining.coerceAtLeast(1)
                    val safeToday = if (remainingBudget.isPositive) {
                        remainingBudget.divideBy(daysLeft.toLong())
                    } else {
                        Money.ZERO
                    }

                    val netWorth = NetWorthCalculator.compute(
                        accounts = accounts,
                        transactions = transactions,
                        loans = loans,
                        legacyFinancialItems = items
                    )
                    val forecast = CashFlowForecastEngine.compute(
                        netWorthSnapshot = netWorth,
                        payCycleDay = profile.payCycleDay,
                        todayIso = todayIso,
                        horizon = ForecastHorizon.ACTIVE_PAY_CYCLE,
                        recurringRules = rules,
                        legacyFinancialItems = items,
                        transactions = transactions,
                        loans = loans
                    )
                    val insights = FinancialInsightsEngine.compute(
                        transactions = transactions,
                        envelopes = envelopes,
                        payCycleDay = profile.payCycleDay,
                        todayIso = todayIso,
                        activeWindowOverride = activeWindow
                    )
                    val envSpent = LinkedHashMap<String, Long>()
                    cycleTxs.filter { it.type == "expense" }.forEach { tx ->
                        val k = tx.envelopeKey.ifBlank { "autre" }
                        envSpent[k] = (envSpent[k] ?: 0L) + tx.amount.centimes
                    }

                    val payload = LanJsonCodec.encodeDashboardPayload(
                        profile = profile,
                        currencySymbol = prefs.currencySymbol,
                        activeWindow = activeWindow,
                        cycleExpenses = cycleExpenses,
                        cycleIncomes = cycleIncomes,
                        remainingBudget = remainingBudget,
                        safeToSpendToday = safeToday,
                        netWorth = netWorth,
                        forecast = forecast,
                        insights = insights,
                        envelopes = envelopes,
                        envelopeSpentCentimes = envSpent,
                        accounts = accounts,
                        recentTransactions = transactions.sortedByDescending { it.isoDate }
                    )
                    call.respondJson(HttpStatusCode.OK, payload.toString())
                }

                get("/api/accounts") {
                    call.requireBearerTokenOrRespond401() ?: return@get
                    val accounts = dataSource.observeAccounts(userId).first()
                    val transactions = dataSource.observeTransactions(userId).first()
                    val loans = dataSource.getLoans(userId)
                    val items = dataSource.observeFinancialItems(userId).first()
                    val netWorth = NetWorthCalculator.compute(accounts, transactions, loans, items)
                    val payload = JSONObject().apply {
                        put("netWorthCentimes", netWorth.netWorth.centimes)
                        put("totalLiquidAssetsCentimes", netWorth.totalLiquidAssets.centimes)
                        put("accounts", JSONArray().apply {
                            netWorth.accountBalances.forEach { put(LanJsonCodec.encodeAccountWithBalance(it)) }
                        })
                    }
                    call.respondJson(HttpStatusCode.OK, payload.toString())
                }

                get("/api/loans") {
                    call.requireBearerTokenOrRespond401() ?: return@get
                    val loans = dataSource.getLoans(userId)
                    val repayments = dataSource.getLoanRepayments(userId)
                    val payload = JSONObject().apply {
                        put("loans", JSONArray().apply {
                            loans.forEach { put(LanJsonCodec.encodeLoan(it)) }
                        })
                        put("repayments", JSONArray().apply {
                            repayments.take(50).forEach { put(LanJsonCodec.encodeLoanRepayment(it)) }
                        })
                    }
                    call.respondJson(HttpStatusCode.OK, payload.toString())
                }

                get("/api/recurring") {
                    call.requireBearerTokenOrRespond401() ?: return@get
                    val todayIso = todayIsoProvider()
                    val rules = dataSource.getRecurringRules(userId)
                    val due = RecurrenceScheduleEngine.findDueOccurrences(
                        rules = rules,
                        upToIso = todayIso,
                        todayIso = todayIso
                    )
                    val payload = JSONObject().apply {
                        put("rules", JSONArray().apply {
                            rules.forEach { put(LanJsonCodec.encodeRecurringRule(it)) }
                        })
                        put("dueOccurrences", JSONArray().apply {
                            due.forEach { put(LanJsonCodec.encodeRecurringOccurrence(it)) }
                        })
                    }
                    call.respondJson(HttpStatusCode.OK, payload.toString())
                }

                get("/api/search") {
                    call.requireBearerTokenOrRespond401() ?: return@get
                    val params = call.request.queryParameters
                    val criteria = TransactionSearchCriteria(
                        query = params["q"].orEmpty().take(120),
                        startIsoDate = params["startIso"]?.takeIf { it.isNotBlank() },
                        endIsoDate = params["endIso"]?.takeIf { it.isNotBlank() },
                        minAmountCentimes = params["minCentimes"]?.toLongOrNull(),
                        maxAmountCentimes = params["maxCentimes"]?.toLongOrNull(),
                        accountId = params["accountId"]?.takeIf { it.isNotBlank() },
                        envelopeKey = params["envelopeKey"]?.takeIf { it.isNotBlank() },
                        type = params["type"]?.takeIf { it.isNotBlank() },
                        loanId = params["loanId"]?.takeIf { it.isNotBlank() },
                        loanLinkedOnly = params["loanLinkedOnly"].equals("true", ignoreCase = true)
                    )
                    val hits = dataSource.searchTransactions(criteria, userId)
                    val payload = JSONObject().apply {
                        put("count", hits.size)
                        put("transactions", JSONArray().apply {
                            hits.take(200).forEach { put(LanJsonCodec.encodeTransaction(it)) }
                        })
                    }
                    call.respondJson(HttpStatusCode.OK, payload.toString())
                }

                // Write endpoints (Bearer-protected, exact Long centimes, Room @Transaction repository)
                post("/api/transactions") {
                    call.requireBearerTokenOrRespond401() ?: return@post
                    val body = call.readJsonObjectOrRespondError() ?: return@post
                    val type = body.optString("type", "expense").trim().lowercase(Locale.ROOT)
                    if (type !in ALLOWED_TX_TYPES) {
                        call.respondJson(
                            HttpStatusCode.BadRequest,
                            LanJsonCodec.errorJson("invalid_type", "Type must be expense or income")
                        )
                        return@post
                    }
                    val amount = LanJsonCodec.parseExactMoney(body)
                    if (amount == null) {
                        call.respondJson(
                            HttpStatusCode.BadRequest,
                            LanJsonCodec.errorJson("invalid_amount", "Valid positive integer amountCentimes required")
                        )
                        return@post
                    }
                    val accounts = dataSource.observeAccounts(userId).first()
                    val requestedAccId = body.optString("accountId", "").trim()
                    val accountId = if (requestedAccId.isNotEmpty() && accounts.any { it.id == requestedAccId }) {
                        requestedAccId
                    } else {
                        accounts.firstOrNull()?.id ?: "acc_cash"
                    }
                    val rawIso = body.optString("isoDate", "").trim()
                    val isoDate = if (PayCycleEngine.isValidIsoDate(rawIso)) rawIso else todayIsoProvider()
                    val envelopeKey = body.optString("envelopeKey", "autre").trim().ifBlank { "autre" }.take(60)
                    val category = body.optString("category", envelopeKey).trim().ifBlank { envelopeKey }.take(80)
                    val desc = body.optString("desc", "").trim().take(120)

                    val tx = TransactionItem(
                        id = "tx_lan_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        userId = userId,
                        type = type,
                        amount = amount,
                        category = category,
                        envelopeKey = envelopeKey,
                        accountId = accountId,
                        desc = desc,
                        isoDate = isoDate
                    )
                    val savedId = dataSource.upsertTransaction(tx, userId)
                    val saved = tx.copy(id = savedId)
                    val payload = JSONObject().apply {
                        put("ok", true)
                        put("transaction", LanJsonCodec.encodeTransaction(saved))
                    }
                    call.respondJson(HttpStatusCode.Created, payload.toString())
                }

                post("/api/transfers") {
                    call.requireBearerTokenOrRespond401() ?: return@post
                    val body = call.readJsonObjectOrRespondError() ?: return@post
                    val fromAccountId = body.optString("fromAccountId", "").trim()
                    val toAccountId = body.optString("toAccountId", "").trim()
                    val amount = LanJsonCodec.parseExactMoney(body)
                    if (amount == null) {
                        call.respondJson(
                            HttpStatusCode.BadRequest,
                            LanJsonCodec.errorJson("invalid_amount", "Valid positive integer amountCentimes required")
                        )
                        return@post
                    }
                    val rawIso = body.optString("isoDate", "").trim()
                    val isoDate = if (PayCycleEngine.isValidIsoDate(rawIso)) rawIso else todayIsoProvider()
                    val desc = body.optString("desc", "").trim().take(120)

                    when (val result = dataSource.executeInternalTransfer(
                        fromAccountId = fromAccountId,
                        toAccountId = toAccountId,
                        amount = amount,
                        isoDate = isoDate,
                        desc = desc,
                        userId = userId
                    )) {
                        is InternalTransferResult.Success -> {
                            val payload = JSONObject().apply {
                                put("ok", true)
                                put("transferGroupId", result.transferGroupId)
                                put("outgoingTransaction", LanJsonCodec.encodeTransaction(result.outgoingTx))
                                put("incomingTransaction", LanJsonCodec.encodeTransaction(result.incomingTx))
                            }
                            call.respondJson(HttpStatusCode.Created, payload.toString())
                        }
                        InternalTransferResult.InvalidAccounts -> {
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                LanJsonCodec.errorJson("invalid_accounts", "Distinct valid source and destination accounts required")
                            )
                        }
                        InternalTransferResult.InvalidAmount -> {
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                LanJsonCodec.errorJson("invalid_amount", "Transfer amount must be positive")
                            )
                        }
                    }
                }

                post("/api/loans/{id}/repay") {
                    call.requireBearerTokenOrRespond401() ?: return@post
                    val loanId = call.parameters["id"]?.trim().orEmpty()
                    if (loanId.isEmpty() || loanId.length > 120) {
                        call.respondJson(
                            HttpStatusCode.BadRequest,
                            LanJsonCodec.errorJson("invalid_loan_id", "Missing loan id")
                        )
                        return@post
                    }
                    val body = call.readJsonObjectOrRespondError() ?: return@post
                    val amount = LanJsonCodec.parseExactMoney(body)
                    if (amount == null) {
                        call.respondJson(
                            HttpStatusCode.BadRequest,
                            LanJsonCodec.errorJson("invalid_amount", "Valid positive integer amountCentimes required")
                        )
                        return@post
                    }
                    val linkedAccountId = body.optString("linkedAccountId", "").trim().takeIf { it.isNotEmpty() }
                    val rawIso = body.optString("isoDate", "").trim()
                    val isoDate = if (PayCycleEngine.isValidIsoDate(rawIso)) rawIso else todayIsoProvider()
                    val note = body.optString("note", "").trim().take(160)

                    when (val result = dataSource.recordLoanRepayment(
                        loanId = loanId,
                        amount = amount,
                        linkedAccountId = linkedAccountId,
                        isoDate = isoDate,
                        note = note,
                        userId = userId
                    )) {
                        is LoanRepaymentResult.Success -> {
                            val payload = JSONObject().apply {
                                put("ok", true)
                                put("loan", LanJsonCodec.encodeLoan(result.updatedLoan))
                                put("repayment", LanJsonCodec.encodeLoanRepayment(result.repayment))
                                put(
                                    "linkedTransaction",
                                    result.linkedTransaction?.let { LanJsonCodec.encodeTransaction(it) } ?: JSONObject.NULL
                                )
                            }
                            call.respondJson(HttpStatusCode.Created, payload.toString())
                        }
                        is LoanRepaymentResult.OverpaymentBlocked -> {
                            call.respondJson(
                                HttpStatusCode.Conflict,
                                LanJsonCodec.errorJson(
                                    code = "overpayment_blocked",
                                    message = "Repayment exceeds remaining loan balance",
                                    extra = mapOf(
                                        "remainingCentimes" to result.remaining.centimes,
                                        "attemptedCentimes" to result.attempted.centimes
                                    )
                                )
                            )
                        }
                        is LoanRepaymentResult.AlreadyPaidOff -> {
                            call.respondJson(
                                HttpStatusCode.Conflict,
                                LanJsonCodec.errorJson("loan_already_paid_off", "Loan is already paid off")
                            )
                        }
                        LoanRepaymentResult.InvalidAmount -> {
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                LanJsonCodec.errorJson("invalid_amount", "Repayment amount must be positive")
                            )
                        }
                        is LoanRepaymentResult.LoanNotFound -> {
                            call.respondJson(
                                HttpStatusCode.NotFound,
                                LanJsonCodec.errorJson("loan_not_found", "Loan not found")
                            )
                        }
                    }
                }
            }
        }

        engine.start(wait = false)
        server = engine
        return baseUrl
    }

    fun stop() {
        runCatching { server?.stop(gracePeriodMillis = 250L, timeoutMillis = 1000L) }
        server = null
        bindHost = ""
        boundPort = 0
    }

    private fun applySecurityHeaders(call: ApplicationCall) {
        call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
        call.response.header(HttpHeaders.Pragma, "no-cache")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("X-Frame-Options", "DENY")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header("Content-Security-Policy", STRICT_CSP)
    }

    private suspend fun ApplicationCall.respondJson(status: HttpStatusCode, json: String) {
        respondText(
            text = json,
            contentType = ContentType.Application.Json.withParameter("charset", "utf-8"),
            status = status
        )
    }

    private suspend fun ApplicationCall.requireBearerTokenOrRespond401(): String? {
        val token = authManager.validateBearerHeader(request.headers[HttpHeaders.Authorization])
        if (token == null) {
            respondJson(
                HttpStatusCode.Unauthorized,
                LanJsonCodec.errorJson("unauthorized", "Valid Bearer session token required")
            )
            return null
        }
        return token
    }

    private suspend fun ApplicationCall.readJsonObjectOrRespondError(): JSONObject? {
        val raw = runCatching { receiveText() }.getOrElse {
            respondJson(
                HttpStatusCode.BadRequest,
                LanJsonCodec.errorJson("invalid_body", "Unable to read request body")
            )
            return null
        }
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            respondJson(
                HttpStatusCode.PayloadTooLarge,
                LanJsonCodec.errorJson("payload_too_large", "Request body exceeds 64 KB limit")
            )
            return null
        }
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            respondJson(
                HttpStatusCode.BadRequest,
                LanJsonCodec.errorJson("invalid_json", "Malformed JSON payload")
            )
            null
        }
    }

    private fun resolveAvailablePort(host: String, preferredPort: Int): Int {
        if (preferredPort > 0) {
            val preferredFree = runCatching {
                ServerSocket(preferredPort, 1, java.net.InetAddress.getByName(host)).use { true }
            }.getOrDefault(false)
            if (preferredFree) return preferredPort
        }
        return ServerSocket(0, 1, java.net.InetAddress.getByName(host)).use { it.localPort }
    }
}
