package com.example

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiDatabase
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.example.domain.LoanDirection
import com.example.domain.LoanItem
import com.example.domain.LoanStatus
import com.example.domain.NetWorthCalculator
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrencePostingMode
import com.example.domain.RecurringRuleItem
import com.example.lan.LanAssetProvider
import com.example.lan.LanAuthSessionManager
import com.example.lan.LanCompanionServer
import com.example.lan.LanCompanionService
import com.example.lan.LanJsonCodec
import com.example.lan.LanNetworkUtils
import com.example.ui.lanCompanionStringsFor
import com.masroufi.core.Money
import java.io.ByteArrayOutputStream
import java.net.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Phase 7B — Local LAN Companion (Embedded Ktor CIO Server) Security & API Test Suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7BLanCompanionSecurityTest {

    private lateinit var context: Context
    private lateinit var db: MasroufiDatabase
    private lateinit var repo: RoomMasroufiRepository
    private var server: LanCompanionServer? = null
    private var nowMs: Long = 1_764_320_000_000L // Deterministic clock
    private val userId = "u_phase7b_lan"
    private val fixedTodayIso = "2026-11-28"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MasroufiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomMasroufiRepository(
            dao = db.masroufiDao(),
            remoteMirror = NoOpRemoteMirror,
            userIdProvider = { userId }
        )
        runBlocking {
            repo.upsertProfile(
                UserProfile(
                    userId = userId,
                    baseBudget = Money.fromDh(6000),
                    payCycleDay = 1,
                    language = "fr"
                ),
                userId
            )
            repo.upsertAccount(
                AccountItem(
                    id = "acc_cash",
                    userId = userId,
                    name = "Espèces",
                    kind = "cash",
                    initialBalance = Money.fromDh(1000)
                ),
                userId
            )
            repo.upsertAccount(
                AccountItem(
                    id = "acc_bank",
                    userId = userId,
                    name = "Banque",
                    kind = "bank",
                    initialBalance = Money.fromDh(5000)
                ),
                userId
            )
        }
    }

    @After
    fun tearDown() {
        runCatching { server?.stop() }
        server = null
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }

    private fun startTestServer(): Pair<LanCompanionServer, LanAuthSessionManager> {
        val auth = LanAuthSessionManager(
            initialIdleTimeoutMinutes = 15,
            clockMs = { nowMs }
        )
        val assets = LanAssetProvider { name ->
            val safeName = name.substringAfterLast('/')
            runCatching {
                context.assets.open("lan_web/$safeName").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
        }
        val srv = LanCompanionServer(
            dataSource = repo,
            userId = userId,
            authManager = auth,
            assetProvider = assets,
            todayIsoProvider = { fixedTodayIso }
        )
        srv.start(host = "127.0.0.1", preferredPort = 0, allowLoopbackForTests = true)
        server = srv
        return srv to auth
    }

    private data class RawHttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: String
    )

    /**
     * Minimal HTTP/1.1 raw socket client so spoofed `Host`, `Origin`, and `Sec-Fetch-Site` headers
     * are transmitted verbatim without JDK `HttpURLConnection` header stripping.
     */
    private fun sendRawHttp(
        port: Int,
        method: String,
        path: String,
        hostHeader: String = "127.0.0.1:$port",
        extraHeaders: Map<String, String> = emptyMap(),
        body: String? = null
    ): RawHttpResponse {
        val bodyBytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            val out = socket.getOutputStream()
            val req = StringBuilder().apply {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                append("Connection: close\r\n")
                if (body != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }
            out.write(req.toString().toByteArray(Charsets.ISO_8859_1))
            if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
            out.flush()

            val raw = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val inp = socket.getInputStream()
            while (true) {
                val read = inp.read(buf)
                if (read <= 0) break
                raw.write(buf, 0, read)
            }
            val fullText = raw.toString(Charsets.UTF_8.name())
            val splitIdx = fullText.indexOf("\r\n\r\n")
            val headText = if (splitIdx >= 0) fullText.substring(0, splitIdx) else fullText
            val rawBody = if (splitIdx >= 0) fullText.substring(splitIdx + 4) else ""
            val lines = headText.split("\r\n")
            val statusCode = lines.firstOrNull()?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0
            val headers = LinkedHashMap<String, String>()
            lines.drop(1).forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
            }
            val decodedBody = if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
                decodeChunkedBody(rawBody)
            } else {
                rawBody
            }
            return RawHttpResponse(statusCode, headers, decodedBody)
        }
    }

    private fun decodeChunkedBody(chunked: String): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < chunked.length) {
            val crlf = chunked.indexOf("\r\n", idx)
            if (crlf < 0) break
            val sizeHex = chunked.substring(idx, crlf).substringBefore(';').trim()
            val size = sizeHex.toIntOrNull(16) ?: break
            if (size == 0) break
            val start = crlf + 2
            val end = (start + size).coerceAtMost(chunked.length)
            sb.append(chunked.substring(start, end))
            idx = end + 2
        }
        return sb.toString()
    }

    private fun loginAndGetToken(srv: LanCompanionServer, pin: String): String {
        val res = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/auth/login",
            extraHeaders = mapOf("Origin" to "http://127.0.0.1:${srv.boundPort}"),
            body = JSONObject().put("pin", pin).toString()
        )
        assertEquals("Expected 200 login, body=${res.body}", 200, res.statusCode)
        val token = JSONObject(res.body).getString("token")
        assertEquals("Token must be 256-bit (64 hex chars)", 64, token.length)
        return token
    }

    @Test
    fun `LanNetworkUtils strictly allows only RFC1918 private IPv4 addresses and rejects wildcard or public hosts`() {
        assertTrue(LanNetworkUtils.isPrivateRfc1918Ipv4("10.0.0.5"))
        assertTrue(LanNetworkUtils.isPrivateRfc1918Ipv4("172.16.10.2"))
        assertTrue(LanNetworkUtils.isPrivateRfc1918Ipv4("172.31.255.254"))
        assertTrue(LanNetworkUtils.isPrivateRfc1918Ipv4("192.168.1.42"))

        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("0.0.0.0"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("::"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("127.0.0.1"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("8.8.8.8"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("172.15.0.1"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("172.32.0.1"))
        assertFalse(LanNetworkUtils.isPrivateRfc1918Ipv4("169.254.1.10"))

        assertFalse(LanNetworkUtils.isValidLanBindHost("0.0.0.0", allowLoopbackForTests = false))
        assertFalse(LanNetworkUtils.isValidLanBindHost("127.0.0.1", allowLoopbackForTests = false))
        assertTrue(LanNetworkUtils.isValidLanBindHost("127.0.0.1", allowLoopbackForTests = true))

        // Starting server on 0.0.0.0 must throw IllegalArgumentException
        val srv = LanCompanionServer(
            dataSource = repo,
            userId = userId,
            authManager = LanAuthSessionManager(clockMs = { nowMs }),
            assetProvider = LanAssetProvider { "" }
        )
        var threw = false
        try {
            srv.start(host = "0.0.0.0", preferredPort = 0, allowLoopbackForTests = false)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Server must refuse binding to 0.0.0.0", threw)
    }

    @Test
    fun `ephemeral PIN auth enforces 5-strike 5-minute lockout, clock unlock, Bearer auth, logout, and PIN regeneration`() {
        val (srv, auth) = startTestServer()
        val pin = auth.currentPin()
        assertEquals(6, pin.length)
        assertTrue(pin.all { it in '0'..'9' })

        // Unauthenticated read -> 401
        val unauth = sendRawHttp(srv.boundPort, "GET", "/api/dashboard")
        assertEquals(401, unauth.statusCode)

        // 4 wrong attempts -> 401 with decreasing remainingAttempts
        val wrongPin = if (pin == "000000") "999999" else "000000"
        for (attempt in 1..4) {
            val r = sendRawHttp(
                port = srv.boundPort,
                method = "POST",
                path = "/api/auth/login",
                body = JSONObject().put("pin", wrongPin).toString()
            )
            assertEquals(401, r.statusCode)
            assertEquals(5 - attempt, JSONObject(r.body).getInt("remainingAttempts"))
        }

        // 5th wrong attempt -> 429 Too Many Requests + Retry-After header (300s)
        val fifth = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/auth/login",
            body = JSONObject().put("pin", wrongPin).toString()
        )
        assertEquals(429, fifth.statusCode)
        assertEquals("300", fifth.headers["retry-after"])

        // Even correct PIN is blocked during active lockout
        val duringLockout = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/auth/login",
            body = JSONObject().put("pin", pin).toString()
        )
        assertEquals(429, duringLockout.statusCode)

        // Advance injectable clock past 5 minutes -> login succeeds
        nowMs += LanAuthSessionManager.LOCKOUT_DURATION_MS + 1_000L
        val token1 = loginAndGetToken(srv, pin)
        assertEquals(1, auth.activeSessionsCount())

        // Valid Bearer succeeds
        val okDash = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/dashboard",
            extraHeaders = mapOf("Authorization" to "Bearer $token1")
        )
        assertEquals(200, okDash.statusCode)

        // Regenerate PIN immediately revokes existing Bearer token
        val pin2 = auth.regeneratePin()
        assertEquals(0, auth.activeSessionsCount())
        val revokedAfterRegen = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/dashboard",
            extraHeaders = mapOf("Authorization" to "Bearer $token1")
        )
        assertEquals(401, revokedAfterRegen.statusCode)

        // Login with new PIN works, then logout revokes immediately
        val token2 = loginAndGetToken(srv, pin2)
        val logout = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/auth/logout",
            extraHeaders = mapOf("Authorization" to "Bearer $token2")
        )
        assertEquals(200, logout.statusCode)
        val afterLogout = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/dashboard",
            extraHeaders = mapOf("Authorization" to "Bearer $token2")
        )
        assertEquals(401, afterLogout.statusCode)

        // Idle timeout expiry invalidates session cleanly
        val token3 = loginAndGetToken(srv, pin2)
        assertFalse(auth.isIdleExpired())
        nowMs += 15L * 60L * 1000L + 1_000L
        assertTrue(auth.isIdleExpired())
        val afterIdle = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/dashboard",
            extraHeaders = mapOf("Authorization" to "Bearer $token3")
        )
        assertEquals(401, afterIdle.statusCode)
    }

    @Test
    fun `server enforces Host anti-DNS-rebinding, Origin and Sec-Fetch-Site CSRF guards, security headers, and 64KB body cap`() {
        val (srv, auth) = startTestServer()
        val token = loginAndGetToken(srv, auth.currentPin())

        // 1. Spoofed Host header -> 403 Forbidden
        val badHost = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/dashboard",
            hostHeader = "evil.attacker:${srv.boundPort}",
            extraHeaders = mapOf("Authorization" to "Bearer $token")
        )
        assertEquals(403, badHost.statusCode)
        assertEquals("invalid_host", JSONObject(badHost.body).getString("error"))

        // 2. Spoofed Origin header on POST -> 403 Forbidden
        val badOrigin = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transactions",
            extraHeaders = mapOf(
                "Authorization" to "Bearer $token",
                "Origin" to "http://evil.attacker"
            ),
            body = JSONObject().put("type", "expense").put("amountCentimes", 1500L).toString()
        )
        assertEquals(403, badOrigin.statusCode)
        assertEquals("invalid_origin", JSONObject(badOrigin.body).getString("error"))

        // 3. Cross-site Sec-Fetch-Site on POST -> 403 Forbidden
        val crossSite = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transactions",
            extraHeaders = mapOf(
                "Authorization" to "Bearer $token",
                "Sec-Fetch-Site" to "cross-site"
            ),
            body = JSONObject().put("type", "expense").put("amountCentimes", 1500L).toString()
        )
        assertEquals(403, crossSite.statusCode)
        assertEquals("invalid_origin", JSONObject(crossSite.body).getString("error"))

        // 4. Security headers present on HTML and API responses
        val htmlRes = sendRawHttp(srv.boundPort, "GET", "/")
        assertEquals(200, htmlRes.statusCode)
        assertTrue(htmlRes.headers["cache-control"].orEmpty().contains("no-store"))
        assertEquals("nosniff", htmlRes.headers["x-content-type-options"])
        assertEquals("DENY", htmlRes.headers["x-frame-options"])
        assertEquals("no-referrer", htmlRes.headers["referrer-policy"])
        assertTrue(htmlRes.headers["content-security-policy"].orEmpty().contains("default-src 'self'"))

        // 5. Body larger than 64KB rejected with 413 Payload Too Large
        val hugeDesc = "A".repeat(70 * 1024)
        val tooLarge = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transactions",
            extraHeaders = mapOf("Authorization" to "Bearer $token"),
            body = JSONObject().put("type", "expense").put("amountCentimes", 100L).put("desc", hugeDesc).toString()
        )
        assertEquals(413, tooLarge.statusCode)
    }

    @Test
    fun `LAN read and write APIs enforce exact Long centimes, atomic transfers with zero net worth delta, FTS4 search, and loan overpayment guards`() = runBlocking {
        // Seed initial loan & recurring rule
        val loan = LoanItem(
            id = "loan_lan_1",
            userId = userId,
            title = "Prêt Ordinateur",
            counterparty = "Youssef",
            direction = LoanDirection.LIABILITY,
            principal = Money.fromDh(1000),
            remaining = Money.fromDh(1000),
            linkedAccountId = "acc_bank",
            status = LoanStatus.ACTIVE
        )
        repo.upsertLoan(loan, userId)
        repo.upsertRecurringRule(
            RecurringRuleItem(
                id = "rule_lan_1",
                userId = userId,
                title = "Fibre Optique",
                amount = Money.fromDh(249),
                frequency = RecurrenceFrequency.MONTHLY,
                anchorDayOfMonth = 28,
                startIsoDate = "2026-11-28",
                nextDueIsoDate = "2026-11-28",
                postingMode = RecurrencePostingMode.CONFIRM
            ),
            userId
        )

        val (srv, auth) = startTestServer()
        val token = loginAndGetToken(srv, auth.currentPin())
        val authHeaders = mapOf(
            "Authorization" to "Bearer $token",
            "Origin" to "http://127.0.0.1:${srv.boundPort}"
        )

        // 1. Float JSON amount MUST be rejected (strict zero Double/Float invariant)
        assertNull(LanJsonCodec.parseExactMoney(JSONObject("""{"amountCentimes": 12.34}""")))
        val floatReject = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transactions",
            extraHeaders = authHeaders,
            body = """{"type":"expense","amountCentimes":12.34,"desc":"Float test"}"""
        )
        assertEquals(400, floatReject.statusCode)

        // 2. POST /api/transactions with exact integer centimes (123.45 DH = 12345 centimes)
        val createTx = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transactions",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("type", "expense")
                .put("amountCentimes", 12_345L)
                .put("category", "Café & Sorties")
                .put("envelopeKey", "cafe")
                .put("accountId", "acc_cash")
                .put("desc", "Espresso Marjane")
                .put("isoDate", fixedTodayIso)
                .toString()
        )
        assertEquals(201, createTx.statusCode)
        val createdTxJson = JSONObject(createTx.body).getJSONObject("transaction")
        assertEquals(12_345L, createdTxJson.getLong("amountCentimes"))

        // 3. FTS4 search via GET /api/search?q=Marjane
        val searchRes = sendRawHttp(
            port = srv.boundPort,
            method = "GET",
            path = "/api/search?q=Marjane&minCentimes=10000&maxCentimes=20000",
            extraHeaders = mapOf("Authorization" to "Bearer $token")
        )
        assertEquals(200, searchRes.statusCode)
        val searchJson = JSONObject(searchRes.body)
        assertEquals(1, searchJson.getInt("count"))
        assertEquals(12_345L, searchJson.getJSONArray("transactions").getJSONObject(0).getLong("amountCentimes"))

        // 4. Atomic transfer via POST /api/transfers: Net Worth delta MUST equal 0
        val beforeAccounts = repo.observeAccounts(userId).first()
        val beforeTxs = repo.observeTransactions(userId).first()
        val beforeLoans = repo.getLoans(userId)
        val netWorthBefore = NetWorthCalculator.compute(beforeAccounts, beforeTxs, beforeLoans).netWorth.centimes

        val transferRes = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/transfers",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("fromAccountId", "acc_bank")
                .put("toAccountId", "acc_cash")
                .put("amountCentimes", 50_000L) // 500.00 DH
                .put("desc", "Retrait ATM")
                .put("isoDate", fixedTodayIso)
                .toString()
        )
        assertEquals(201, transferRes.statusCode)
        val transferJson = JSONObject(transferRes.body)
        val groupId = transferJson.getString("transferGroupId")
        assertTrue(groupId.isNotBlank())
        assertEquals("transfer_out", transferJson.getJSONObject("outgoingTransaction").getString("type"))
        assertEquals("transfer_in", transferJson.getJSONObject("incomingTransaction").getString("type"))
        assertEquals(groupId, transferJson.getJSONObject("outgoingTransaction").getString("transferGroupId"))
        assertEquals(groupId, transferJson.getJSONObject("incomingTransaction").getString("transferGroupId"))

        val afterTxs = repo.observeTransactions(userId).first()
        val netWorthAfter = NetWorthCalculator.compute(beforeAccounts, afterTxs, beforeLoans).netWorth.centimes
        assertEquals("Atomic internal transfer must leave Net Worth unchanged", netWorthBefore, netWorthAfter)

        // 5. Loan repayment: overpayment blocked (409), partial repayment (201), final repayment -> PAID_OFF, further blocked (409)
        val overpay = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/loans/loan_lan_1/repay",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("amountCentimes", 150_000L) // 1500 DH > 1000 DH remaining
                .put("linkedAccountId", "acc_bank")
                .put("isoDate", fixedTodayIso)
                .toString()
        )
        assertEquals(409, overpay.statusCode)
        assertEquals("overpayment_blocked", JSONObject(overpay.body).getString("error"))

        val partialRepay = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/loans/loan_lan_1/repay",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("amountCentimes", 40_000L) // 400 DH
                .put("linkedAccountId", "acc_bank")
                .put("isoDate", fixedTodayIso)
                .put("note", "Tranche 1")
                .toString()
        )
        assertEquals(201, partialRepay.statusCode)
        assertEquals(60_000L, JSONObject(partialRepay.body).getJSONObject("loan").getLong("remainingCentimes"))

        val finalRepay = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/loans/loan_lan_1/repay",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("amountCentimes", 60_000L) // Exact 600 DH remaining
                .put("linkedAccountId", "acc_bank")
                .put("isoDate", fixedTodayIso)
                .toString()
        )
        assertEquals(201, finalRepay.statusCode)
        val paidLoanJson = JSONObject(finalRepay.body).getJSONObject("loan")
        assertEquals(0L, paidLoanJson.getLong("remainingCentimes"))
        assertEquals(LoanStatus.PAID_OFF.wire, paidLoanJson.getString("status"))

        val afterPaidOff = sendRawHttp(
            port = srv.boundPort,
            method = "POST",
            path = "/api/loans/loan_lan_1/repay",
            extraHeaders = authHeaders,
            body = JSONObject()
                .put("amountCentimes", 100L)
                .put("linkedAccountId", "acc_bank")
                .toString()
        )
        assertEquals(409, afterPaidOff.statusCode)
        assertEquals("loan_already_paid_off", JSONObject(afterPaidOff.body).getString("error"))

        // 6. Verify read endpoints (/api/dashboard, /api/accounts, /api/loans, /api/recurring)
        val dashRes = sendRawHttp(srv.boundPort, "GET", "/api/dashboard", extraHeaders = mapOf("Authorization" to "Bearer $token"))
        assertEquals(200, dashRes.statusCode)
        val dashJson = JSONObject(dashRes.body)
        assertEquals(600_000L, dashJson.getLong("baseBudgetCentimes"))

        val accRes = sendRawHttp(srv.boundPort, "GET", "/api/accounts", extraHeaders = mapOf("Authorization" to "Bearer $token"))
        assertEquals(200, accRes.statusCode)
        assertEquals(2, JSONObject(accRes.body).getJSONArray("accounts").length())

        val loanRes = sendRawHttp(srv.boundPort, "GET", "/api/loans", extraHeaders = mapOf("Authorization" to "Bearer $token"))
        assertEquals(200, loanRes.statusCode)
        assertEquals(1, JSONObject(loanRes.body).getJSONArray("loans").length())
        assertEquals(2, JSONObject(loanRes.body).getJSONArray("repayments").length())

        val recRes = sendRawHttp(srv.boundPort, "GET", "/api/recurring", extraHeaders = mapOf("Authorization" to "Bearer $token"))
        assertEquals(200, recRes.statusCode)
        assertEquals(1, JSONObject(recRes.body).getJSONArray("rules").length())
        assertEquals(1, JSONObject(recRes.body).getJSONArray("dueOccurrences").length())
    }

    @Test
    fun `embedded web assets are strictly zero-CDN, XSS-safe without innerHTML, and never persist tokens`() {
        val html = context.assets.open("lan_web/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val css = context.assets.open("lan_web/app.css").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val js = context.assets.open("lan_web/app.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

        val combined = "$html\n$css\n$js"
        assertFalse("Zero external http:// CDN references allowed", combined.contains("http://"))
        assertFalse("Zero external https:// CDN references allowed", combined.contains("https://"))
        assertFalse("Zero innerHTML usage allowed (strict textContent XSS hardening)", js.contains("innerHTML"))
        assertFalse("Zero outerHTML usage allowed", js.contains("outerHTML"))
        assertFalse("Zero document.write usage allowed", js.contains("document.write"))
        assertFalse("Zero localStorage token persistence allowed", js.contains("localStorage"))
        assertFalse("Zero sessionStorage token persistence allowed", js.contains("sessionStorage"))
        assertFalse("Zero document.cookie usage allowed", js.contains("document.cookie"))
        assertTrue("HTML must reference local /app.css", html.contains("/app.css"))
        assertTrue("HTML must reference local /app.js", html.contains("/app.js"))
    }

    @Test
    fun `manifest keeps allowBackup=false, usesCleartextTraffic=false, LanCompanionService non-exported dataSync, and PIN-free private notification`() {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "android:allowBackup MUST remain false",
            0,
            appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        )
        assertEquals(
            "android:usesCleartextTraffic MUST remain false",
            0,
            appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
        )

        val serviceInfo = pm.getServiceInfo(
            ComponentName(context, LanCompanionService::class.java),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertFalse("LanCompanionService MUST be android:exported=false", serviceInfo.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(
                "LanCompanionService MUST declare foregroundServiceType=dataSync",
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                serviceInfo.foregroundServiceType
            )
        }

        // Foreground notification invariant: URL-only + Stop action + VISIBILITY_PRIVATE + NEVER includes PIN
        val secretPin = "739154"
        val url = "http://192.168.1.42:8765"
        val notif = LanCompanionService.buildForegroundNotification(context, url)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notif.visibility)
        assertNotNull("Lock-screen publicVersion must be attached", notif.publicVersion)

        val contentText = notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val contentTitle = notif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue("Notification should show local URL", contentText.contains(url))
        assertFalse("Notification MUST NEVER contain PIN", contentText.contains(secretPin))
        assertFalse("Notification title MUST NEVER contain PIN", contentTitle.contains(secretPin))

        val publicText = notif.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertFalse("Public lock-screen notification must omit local URL", publicText.contains(url))
        assertFalse("Public lock-screen notification must omit PIN", publicText.contains(secretPin))

        assertTrue("Notification must expose a one-tap Stop action", (notif.actions?.size ?: 0) >= 1)
        assertEquals("Stop", notif.actions[0].title.toString())
    }

    @Test
    fun `4-language LanCompanion UI strings are complete across fr, ar, darija, and en`() {
        val langs = listOf("fr", "ar", "darija", "en")
        val titles = langs.map { lang ->
            val s = lanCompanionStringsFor(lang)
            assertTrue(s.title.isNotBlank())
            assertTrue(s.startServerBtn.isNotBlank())
            assertTrue(s.stopServerBtn.isNotBlank())
            assertTrue(s.copyUrlBtn.isNotBlank())
            assertTrue(s.regeneratePinBtn.isNotBlank())
            assertTrue(s.wifiRequiredError.isNotBlank())
            assertTrue(s.securityBulletWifi.isNotBlank())
            s.title
        }
        assertEquals("Each language should provide distinct localized title", 4, titles.distinct().size)
        assertNotEquals(lanCompanionStringsFor("fr").statusOn, lanCompanionStringsFor("ar").statusOn)
    }
}
