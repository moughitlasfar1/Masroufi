package com.example.widget

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.example.security.AppSecurityManager
import com.masroufi.core.Money
import com.masroufi.core.MoneyParser

sealed interface AssistantAmountValidation {
    data object Missing : AssistantAmountValidation
    data class Valid(val amount: Money) : AssistantAmountValidation
    data object Invalid : AssistantAmountValidation
}

/**
 * Pure validation and rate-limiting rules for external Google Assistant / deep-link requests.
 * Separated into a testable object so unit tests can verify bounds, control-char stripping,
 * and 10-second rate limiting without an emulator.
 */
object AssistantEntryValidator {
    val MAX_AMOUNT: Money = Money.fromDh(100_000)
    const val MAX_TEXT_LENGTH: Int = 60
    const val RATE_LIMIT_WINDOW_MS: Long = 10_000L

    private val lock = Any()
    private var lastAcceptedElapsedMs: Long = Long.MIN_VALUE

    /**
     * Strips ASCII & Unicode control characters, collapses whitespace, and caps at [MAX_TEXT_LENGTH].
     */
    fun sanitizeText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = buildString(raw.length.coerceAtMost(MAX_TEXT_LENGTH * 2)) {
            for (ch in raw) {
                if (!Character.isISOControl(ch) && ch != '\u202E' && ch != '\u202D' && ch != '\u200E' && ch != '\u200F') {
                    append(ch)
                } else {
                    append(' ')
                }
            }
        }
        return cleaned
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(MAX_TEXT_LENGTH)
    }

    /**
     * Validates an optional amount string strictly via [MoneyParser] (zero Double parsing).
     * Returns [AssistantAmountValidation.Missing] if null/blank, [AssistantAmountValidation.Valid]
     * if strictly within (0, MAX_AMOUNT], or [AssistantAmountValidation.Invalid] otherwise.
     */
    fun validateAmount(rawAmount: String?): AssistantAmountValidation {
        if (rawAmount.isNullOrBlank()) return AssistantAmountValidation.Missing
        val parsedCentimes = MoneyParser.parseMoroccanAmountToCentimes(rawAmount)
            ?: return AssistantAmountValidation.Invalid
        val parsed = Money.fromCentimes(parsedCentimes)
        if (!parsed.isPositive || parsed > MAX_AMOUNT) {
            return AssistantAmountValidation.Invalid
        }
        return AssistantAmountValidation.Valid(parsed)
    }

    /**
     * Enforces at most 1 external request per 10 seconds using a monotonic clock.
     */
    fun tryAcquireRateLimit(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(lock) {
            if (lastAcceptedElapsedMs != Long.MIN_VALUE &&
                nowElapsedMs >= lastAcceptedElapsedMs &&
                (nowElapsedMs - lastAcceptedElapsedMs) < RATE_LIMIT_WINDOW_MS
            ) {
                return false
            }
            lastAcceptedElapsedMs = nowElapsedMs
            return true
        }
    }

    internal fun resetRateLimitForTest() {
        synchronized(lock) {
            lastAcceptedElapsedMs = Long.MIN_VALUE
        }
    }
}

/**
 * S2: Thin exported trampoline activity that receives ONLY external Google Assistant
 * and non-browsable `masroufi://` intents, validates/sanitizes every parameter, enforces a
 * 10-second rate limit, and forwards to internal (unexported) [QuickCaptureActivity]
 * with [QuickCaptureActivity.EXTRA_CONFIRM_REQUIRED] = true so no external caller can ever
 * silently auto-save an expense.
 */
class AssistantEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSecurityManager.applyWindowFlagSecure(this, AppSecurityManager.isFlagSecureEnabled(this))

        val incoming = intent
        if (incoming == null) {
            finish()
            return
        }

        // Reject rapid external intent flooding (1 request / 10 seconds)
        if (!AssistantEntryValidator.tryAcquireRateLimit()) {
            finish()
            return
        }

        val forward = buildValidatedForwardIntent(incoming)
        if (forward != null) {
            startActivity(forward)
        }
        finish()
    }

    internal fun buildValidatedForwardIntent(incoming: Intent): Intent? {
        val action = incoming.action.orEmpty()
        val dataUri: Uri? = incoming.data

        val allowedAction = action == GoogleAssistantExpenseBridge.ACTION_ASSISTANT_ADD_EXPENSE ||
            action == MasroufiAppWidget.ACTION_QUICK_VOICE ||
            action == MasroufiAppWidget.ACTION_QUICK_ADD ||
            action == GoogleAssistantExpenseBridge.ACTION_GMS_SEARCH ||
            action == GoogleAssistantExpenseBridge.ACTION_GMS_CREATE_NOTE ||
            action == Intent.ACTION_SEARCH ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND ||
            (action == Intent.ACTION_VIEW &&
                dataUri?.scheme == "masroufi" &&
                (dataUri.host == "assistant" || dataUri.host == "expense"))

        if (!allowedAction) return null

        val rawAmountStr = incoming.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT)
            ?.ifBlank { null }
            ?: incoming.extras?.get(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT)?.toString()?.takeIf { it.isNotBlank() }
            ?: dataUri?.getQueryParameter("amount")

        val validatedAmount: Money? = when (val validation = AssistantEntryValidator.validateAmount(rawAmountStr)) {
            AssistantAmountValidation.Missing -> null
            is AssistantAmountValidation.Valid -> validation.amount
            AssistantAmountValidation.Invalid -> {
                // Invalid or out-of-range amount (> 100,000 or <= 0) — reject safely
                return null
            }
        }

        val rawMerchant = incoming.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_MERCHANT)
            ?: incoming.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_DESCRIPTION)
            ?: dataUri?.getQueryParameter("merchant")
            ?: dataUri?.getQueryParameter("desc")
            ?: dataUri?.getQueryParameter("category")

        val rawQuery = sequenceOf(
            incoming.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_QUERY),
            incoming.getStringExtra(SearchManager.QUERY),
            incoming.getStringExtra(Intent.EXTRA_TEXT),
            incoming.getStringExtra("query"),
            dataUri?.getQueryParameter("query"),
            dataUri?.getQueryParameter("q"),
            dataUri?.getQueryParameter("text"),
            dataUri?.getQueryParameter("utterance")
        ).firstOrNull { !it.isNullOrBlank() }

        val rawFeature = incoming.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_FEATURE)

        val cleanMerchant = AssistantEntryValidator.sanitizeText(rawMerchant)
        val cleanQuery = AssistantEntryValidator.sanitizeText(rawQuery)
        val cleanFeature = AssistantEntryValidator.sanitizeText(rawFeature)

        return Intent(this, QuickCaptureActivity::class.java).apply {
            this.action = when (action) {
                MasroufiAppWidget.ACTION_QUICK_VOICE -> MasroufiAppWidget.ACTION_QUICK_VOICE
                MasroufiAppWidget.ACTION_QUICK_ADD -> MasroufiAppWidget.ACTION_QUICK_ADD
                else -> GoogleAssistantExpenseBridge.ACTION_ASSISTANT_ADD_EXPENSE
            }
            if (validatedAmount != null) {
                putExtra(
                    GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT,
                    validatedAmount.toCanonicalDecimalString()
                )
            }
            if (cleanMerchant.isNotBlank()) {
                putExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_MERCHANT, cleanMerchant)
            }
            if (cleanQuery.isNotBlank()) {
                putExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_QUERY, cleanQuery)
            }
            if (cleanFeature.isNotBlank()) {
                putExtra(GoogleAssistantExpenseBridge.EXTRA_FEATURE, cleanFeature)
            }
            putExtra(QuickCaptureActivity.EXTRA_CONFIRM_REQUIRED, true)
            putExtra(QuickCaptureActivity.EXTRA_FROM_EXTERNAL, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
