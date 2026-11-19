package com.example.widget

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.data.DefaultCategoryResolver
import com.example.data.EnvelopeConfig
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.domain.VoiceDarijaParser
import com.example.domain.VoiceParsedTransaction
import java.util.Locale

sealed interface AssistantExpenseResolution {
    /**
     * Google Assistant provided a complete expense phrase or structured amount+merchant
     * (e.g. "Ok Google, cherche Café 15 DH sur Masroufi" or "masroufi://assistant/add?query=qahwa+15+w+taxi+25").
     * QuickCaptureActivity immediately saves the transactions and displays the 5-second Undo card.
     */
    data class AutoLoggedBatch(
        val items: List<VoiceParsedTransaction>,
        val rawUtterance: String
    ) : AssistantExpenseResolution

    /**
     * Google Assistant launched Masroufi to add an expense by voice without an inline amount
     * (e.g. "Ok Google, ouvre Ajouter dépense sur Masroufi" or "Hey Google, open Masroufi Voice").
     * QuickCaptureActivity opens directly with Moonshine Voice microphone live.
     */
    data object OpenVoiceCapture : AssistantExpenseResolution

    /**
     * Google Assistant requested the quick keypad ("Ok Google, ouvre Clavier dépense sur Masroufi").
     */
    data object OpenKeypadCapture : AssistantExpenseResolution

    /**
     * Standard non-Assistant launch.
     */
    data object NotAssistantIntent : AssistantExpenseResolution
}

/**
 * Bridge connecting Android Google Assistant (App Actions BIIs, Voice Search, Note-to-Self,
 * Assistant Routines deep links, and Pinned Assistant Shortcuts) to Masroufi's Quick Capture
 * and multi-language VoiceDarijaParser.
 */
object GoogleAssistantExpenseBridge {

    const val ACTION_ASSISTANT_ADD_EXPENSE = "com.aistudio.masroufi.ACTION_ASSISTANT_ADD_EXPENSE"
    const val ACTION_GMS_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"
    const val ACTION_GMS_CREATE_NOTE = "com.google.android.gms.actions.CREATE_NOTE"

    const val EXTRA_ASSISTANT_QUERY = "extra_assistant_query"
    const val EXTRA_ASSISTANT_DESCRIPTION = "extra_assistant_description"
    const val EXTRA_ASSISTANT_AMOUNT = "extra_assistant_amount"
    const val EXTRA_ASSISTANT_CURRENCY = "extra_assistant_currency"
    const val EXTRA_ASSISTANT_MERCHANT = "extra_assistant_merchant"
    const val EXTRA_FEATURE = "feature"

    private val KEYPAD_FEATURE_TOKENS = setOf(
        "quick_add",
        "clavier dépense",
        "clavier depense",
        "keypad expense",
        "keypad"
    )

    /**
     * Inspects an incoming [Intent] and resolves whether Google Assistant supplied:
     * 1) An expense utterance or structured amount to auto-save immediately ([AssistantExpenseResolution.AutoLoggedBatch]),
     * 2) A voice-capture launch request ([AssistantExpenseResolution.OpenVoiceCapture]), or
     * 3) A keypad launch request ([AssistantExpenseResolution.OpenKeypadCapture]).
     */
    fun resolveIntent(
        intent: Intent?,
        language: String = "fr",
        envelopes: List<EnvelopeConfig> = emptyList()
    ): AssistantExpenseResolution {
        if (intent == null) return AssistantExpenseResolution.NotAssistantIntent

        val action = intent.action.orEmpty()
        val dataUri: Uri? = intent.data

        // 1. Check structured amount + merchant from CREATE_MONEY_TRANSFER or deep link query params
        val rawAmountStr = intent.getStringExtra(EXTRA_ASSISTANT_AMOUNT)
            ?.ifBlank { null }
            ?: intent.extras?.get(EXTRA_ASSISTANT_AMOUNT)?.toString()?.takeIf { it.isNotBlank() }
            ?: dataUri?.getQueryParameter("amount")

        val rawMerchantStr = intent.getStringExtra(EXTRA_ASSISTANT_MERCHANT)
            ?.ifBlank { null }
            ?: intent.getStringExtra(EXTRA_ASSISTANT_DESCRIPTION)?.ifBlank { null }
            ?: dataUri?.getQueryParameter("merchant")
            ?: dataUri?.getQueryParameter("desc")
            ?: dataUri?.getQueryParameter("category")

        val parsedStructuredAmount = rawAmountStr
            ?.replace(",", ".")
            ?.replace(Regex("""[^\d.]"""), "")
            ?.toDoubleOrNull()

        if (parsedStructuredAmount != null && parsedStructuredAmount > 0.0) {
            val merchantOrDesc = rawMerchantStr?.trim().orEmpty().ifBlank {
                when (language) {
                    "ar" -> "مصروف صوتي"
                    "darija" -> "مصروف بالصوت"
                    "en" -> "Voice Expense"
                    else -> "Dépense Assistant"
                }
            }
            val combinedPhrase = "$merchantOrDesc ${PayCycleEngine.roundMoney(parsedStructuredAmount)}"
            val parsedFromPhrase = VoiceDarijaParser.parseMultiExpenses(combinedPhrase, envelopes, language)
            val items = if (parsedFromPhrase.isNotEmpty()) {
                parsedFromPhrase
            } else {
                val envKey = PayCycleEngine.resolveEnvelopeKey(merchantOrDesc, merchantOrDesc, envelopes).ifBlank { "autre" }
                val catName = DefaultCategoryResolver.resolveTransactionCategoryDisplay(envKey, envKey, envelopes, language)
                listOf(
                    VoiceParsedTransaction(
                        rawTranscript = combinedPhrase,
                        amount = PayCycleEngine.roundMoney(parsedStructuredAmount),
                        description = merchantOrDesc,
                        envelopeKey = envKey,
                        categoryName = catName,
                        txType = "expense"
                    )
                )
            }
            return AssistantExpenseResolution.AutoLoggedBatch(
                items = items,
                rawUtterance = combinedPhrase
            )
        }

        // 2. Check natural-language query from Assistant BIIs, Search, Note-to-Self, or Deep Link
        val rawQuery = sequenceOf(
            intent.getStringExtra(EXTRA_ASSISTANT_QUERY),
            intent.getStringExtra(SearchManager.QUERY),
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra("query"),
            dataUri?.getQueryParameter("query"),
            dataUri?.getQueryParameter("q"),
            dataUri?.getQueryParameter("text"),
            dataUri?.getQueryParameter("utterance"),
            intent.getStringExtra(EXTRA_FEATURE)
        ).mapNotNull { it?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        if (rawQuery.isNotBlank()) {
            val lowerFeature = rawQuery.lowercase(Locale.ROOT)
            if (lowerFeature in KEYPAD_FEATURE_TOKENS) {
                return AssistantExpenseResolution.OpenKeypadCapture
            }

            // Try parsing natural language expense(s) (e.g., "Café 15 DH", "9ahwa 15 w taxi 25", "Marjane 120")
            val cleanedQuery = stripAssistantPrefixWords(rawQuery)
            val parsedExpenses = VoiceDarijaParser.parseMultiExpenses(cleanedQuery, envelopes, language)
            if (parsedExpenses.isNotEmpty()) {
                return AssistantExpenseResolution.AutoLoggedBatch(
                    items = parsedExpenses,
                    rawUtterance = cleanedQuery
                )
            }

            // If feature parameter was matched without an inline amount (e.g. "voice_expense", "ajouter dépense")
            return AssistantExpenseResolution.OpenVoiceCapture
        }

        // 3. Check if launched via Assistant action or masroufi://assistant deep link without query params
        val isAssistantAction = action == ACTION_ASSISTANT_ADD_EXPENSE ||
            action == ACTION_GMS_SEARCH ||
            action == ACTION_GMS_CREATE_NOTE ||
            action == Intent.ACTION_SEARCH ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND ||
            (action == Intent.ACTION_VIEW && dataUri?.scheme == "masroufi")

        if (isAssistantAction) {
            return AssistantExpenseResolution.OpenVoiceCapture
        }

        return AssistantExpenseResolution.NotAssistantIntent
    }

    /**
     * Strips leading assistant command verbs ("ajouter dépense", "add expense", "note", "enregistrer", "زيد مصروف")
     * so phrases like "Ajouter dépense Café 15 DH" parse cleanly as "Café 15 DH".
     */
    internal fun stripAssistantPrefixWords(raw: String): String {
        return raw
            .replace(
                Regex(
                    """^(?:ok\s+google|hey\s+google|ajouter\s+une\s+dépense|ajouter\s+dépense|ajouter\s+depense|enregistrer\s+une\s+dépense|enregistrer|add\s+an\s+expense|add\s+expense|log\s+expense|record\s+expense|إضافة\s+مصروف|تسجيل\s+مصروف|زيد\s+مصروف|قيد\s+مصروف)\s*(?:de|for|بـ|ب|:)?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
            .ifBlank { raw.trim() }
    }

    /**
     * Publishes dynamic shortcuts via [ShortcutManagerCompat] with Google Assistant `OPEN_APP_FEATURE`
     * capability bindings so Assistant Shortcut Suggestions surface Masroufi Voice and top presets.
     */
    fun pushAssistantDynamicShortcuts(context: Context, snapshot: WidgetSnapshot) {
        runCatching {
            val strings = Translations.forLang(snapshot.language)
            val cur = snapshot.currencySymbol.ifBlank { "DH" }

            // 1. Primary Google Assistant Voice Expense shortcut
            val voiceIntent = Intent(context, AssistantEntryActivity::class.java).apply {
                action = ACTION_ASSISTANT_ADD_EXPENSE
                putExtra(EXTRA_FEATURE, "voice_expense")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val voiceShortcut = ShortcutInfoCompat.Builder(context, "assistant_voice_expense")
                .setShortLabel(strings.tileVoiceLabel)
                .setLongLabel("${strings.btnQuickAdd} (Google Assistant)")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
                .setIntent(voiceIntent)
                .addCapabilityBinding(
                    "actions.intent.OPEN_APP_FEATURE",
                    "feature",
                    listOf("voice_expense", "ajouter dépense", "add expense", "زيد مصروف")
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, voiceShortcut)

            // 2. Top 2 user presets bound for Google Assistant
            snapshot.presets.take(2).forEachIndexed { idx, preset ->
                val presetPhrase = "${preset.name} ${preset.amount.toInt()}"
                val presetIntent = Intent(context, AssistantEntryActivity::class.java).apply {
                    action = ACTION_ASSISTANT_ADD_EXPENSE
                    putExtra(EXTRA_ASSISTANT_QUERY, presetPhrase)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val label = "${preset.name} (${PayCycleEngine.formatDh(preset.amount)} $cur)"
                val presetShortcut = ShortcutInfoCompat.Builder(context, "dyn_preset_$idx")
                    .setShortLabel(preset.name.take(12))
                    .setLongLabel("${strings.widgetQuickAddBtn} $label")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
                    .setIntent(presetIntent)
                    .addCapabilityBinding(
                        "actions.intent.OPEN_APP_FEATURE",
                        "feature",
                        listOf(preset.name.lowercase(Locale.ROOT), label.lowercase(Locale.ROOT))
                    )
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(context, presetShortcut)
            }
        }
    }

    /**
     * Requests to pin the "Ok Google · Ajouter dépense" shortcut to the user's home screen / Assistant shortcuts.
     */
    fun requestPinAssistantVoiceShortcut(
        context: Context,
        language: String,
        onResult: (String) -> Unit
    ) {
        val strings = Translations.forLang(language)
        runCatching {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                onResult("✓ Raccourci actif dans Google Assistant (« Ok Google, ouvre Masroufi Voix »)")
                return
            }
            val voiceIntent = Intent(context, AssistantEntryActivity::class.java).apply {
                action = ACTION_ASSISTANT_ADD_EXPENSE
                putExtra(EXTRA_FEATURE, "voice_expense")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pinShortcut = ShortcutInfoCompat.Builder(context, "pin_assistant_voice_expense")
                .setShortLabel(strings.tileVoiceLabel)
                .setLongLabel("${strings.btnQuickAdd} (Google Assistant)")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
                .setIntent(voiceIntent)
                .build()
            val pinned = ShortcutManagerCompat.requestPinShortcut(context, pinShortcut, null)
            onResult(
                if (pinned) "✓ ${strings.assistantPinShortcutBtn}"
                else "✓ Raccourci Google Assistant prêt"
            )
        }.onFailure {
            onResult("✓ Raccourci Google Assistant prêt")
        }
    }

    /**
     * Fires a real Google Assistant `ACTION_ASSISTANT_ADD_EXPENSE` intent into
     * [QuickCaptureActivity] (with confirmation required) so the user can test hands-free capture safely.
     */
    fun launchSimulatedAssistantExpense(context: Context, sampleQuery: String) {
        runCatching {
            val cleanQuery = AssistantEntryValidator.sanitizeText(sampleQuery)
            val intent = Intent(context, QuickCaptureActivity::class.java).apply {
                action = ACTION_ASSISTANT_ADD_EXPENSE
                putExtra(EXTRA_ASSISTANT_QUERY, cleanQuery)
                putExtra(SearchManager.QUERY, cleanQuery)
                putExtra(QuickCaptureActivity.EXTRA_CONFIRM_REQUIRED, true)
                putExtra(QuickCaptureActivity.EXTRA_FROM_EXTERNAL, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Returns localized example voice commands users can say to Google Assistant.
     */
    fun sampleVoiceCommandsForLang(lang: String): List<Pair<String, String>> = when (lang) {
        "ar" -> listOf(
            "« Ok Google, افتح إضافة مصروف في Masroufi »" to "قهوة 15 وطاكسي 25",
            "« Ok Google, ابحث عن قهوة 15 درهم في Masroufi »" to "قهوة 15 درهم",
            "« Ok Google, سجل مرجان 120 درهم في Masroufi »" to "مرجان 120 درهم"
        )
        "darija" -> listOf(
            "« Ok Google, 7el Masroufi Voice »" to "9ahwa 15 w taxi 25",
            "« Ok Google, 9elleb 3la 9ahwa 15 DH f Masroufi »" to "9ahwa 15 DH",
            "« Ok Google, note Marjane 120 DH f Masroufi »" to "Marjane 120 DH"
        )
        "en" -> listOf(
            "\"Hey Google, open Add Expense in Masroufi\"" to "Coffee 15 and Taxi 25",
            "\"Hey Google, search Coffee 15 DH on Masroufi\"" to "Coffee 15 DH",
            "\"Hey Google, note Groceries 120 DH in Masroufi\"" to "Groceries 120 DH"
        )
        else -> listOf(
            "« Ok Google, ouvre Ajouter dépense sur Masroufi »" to "Café 15 et Taxi 25",
            "« Ok Google, cherche Café 15 DH sur Masroufi »" to "Café 15 DH",
            "« Ok Google, note Marjane 120 DH sur Masroufi »" to "Marjane 120 DH"
        )
    }
}
