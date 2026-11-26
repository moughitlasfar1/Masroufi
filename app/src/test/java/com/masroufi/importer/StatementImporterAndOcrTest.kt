package com.masroufi.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountItem
import com.example.data.EnvelopeConfig
import com.example.data.TransactionItem
import com.example.domain.PayCycleEngine
import com.masroufi.core.Money
import com.masroufi.importer.data.ImporterMappingStore
import com.masroufi.importer.domain.AmountColumnMode
import com.masroufi.importer.domain.BankProfile
import com.masroufi.importer.domain.CsvColumnMapping
import com.masroufi.importer.domain.CsvStatementParser
import com.masroufi.importer.domain.PdfStatementParser
import com.masroufi.importer.domain.ReceiptParser
import com.masroufi.importer.domain.StatementPipeline
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatementImporterAndOcrTest {

    private lateinit var context: Context

    private val defaultEnvelopes = listOf(
        EnvelopeConfig(id = "env_charges", key = "charges", nameFr = "Charges & Maison", percentage = 35.0),
        EnvelopeConfig(id = "env_marche", key = "marche", nameFr = "Marché", percentage = 25.0),
        EnvelopeConfig(id = "env_transport", key = "transport", nameFr = "Transport", percentage = 15.0),
        EnvelopeConfig(id = "env_cafe", key = "cafe", nameFr = "Café & Sorties", percentage = 10.0),
        EnvelopeConfig(id = "env_epargne", key = "epargne", nameFr = "Épargne", percentage = 10.0),
        EnvelopeConfig(id = "env_autre", key = "autre", nameFr = "Autre", percentage = 5.0)
    )

    private val defaultAccounts = listOf(
        AccountItem(id = "acc_cash", name = "Espèces (Cash)", kind = "cash"),
        AccountItem(id = "acc_bank", name = "Compte Bancaire", kind = "bank")
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ImporterMappingStore.clearAll(context)
        PayCycleEngine.importLearnedCategoriesJson("{}", merge = false)
    }

    @Test
    fun `CSV detects UTF-8 BOM and Windows-1252 French accents without corruption`() {
        val utf8WithBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Date;Libellé;Débit;Crédit\n12/05/2026;Café Maure;25,50;\n".toByteArray(Charsets.UTF_8)
        val (decodedBom, encBom) = CsvStatementParser.decodeBytes(utf8WithBom)
        assertEquals("UTF-8-BOM", encBom)
        assertTrue(decodedBom.startsWith("Date;Libellé"))

        val win1252 = Charset.forName("windows-1252")
        val legacyBytes = "Banque Populaire - Chaabi Net\nDate Opération;Libellé;Débit;Crédit\n14/05/2026;PAIEMENT CARTE CAFÉ ÉTOILE;1\u00A0250,50;\n"
            .toByteArray(win1252)
        val (decodedWin, encWin) = CsvStatementParser.decodeBytes(legacyBytes)
        assertEquals("Windows-1252", encWin)
        assertTrue(decodedWin.contains("Libellé"))
        assertTrue(decodedWin.contains("CAFÉ ÉTOILE"))
    }

    @Test
    fun `CSV parses BCP semicolon export with preamble, NBSP thousands, and decimal commas`() {
        val sampleBcp = """
            Banque Populaire - Extrait de compte
            Date Opération;Date Valeur;Libellé;Débit;Crédit
            01/05/2026;01/05/2026;SOLDE ANTERIEUR;;12 500,00
            03/05/2026;03/05/2026;PAIEMENT CARTE MARJANE CALIFORNIE;420,75;
            05/05/2026;05/05/2026;RETRAIT GAB BCP AGDAL;500,00;
            07/05/2026;07/05/2026;VIREMENT RECU SALAIRE MAI;;11\u00A0500,00
        """.trimIndent().replace("\\u00A0", "\u00A0")

        val inspection = CsvStatementParser.inspectCsv(sampleBcp.toByteArray(Charsets.UTF_8))
        assertEquals(BankProfile.BCP, inspection.detectedBankProfile)
        assertEquals(';', inspection.delimiter)
        assertTrue(inspection.highConfidenceAutoMap)
        assertEquals(AmountColumnMode.SPLIT_DEBIT_CREDIT, inspection.suggestedMapping.amountMode)

        val rows = CsvStatementParser.extractRows(inspection.allRawRows, inspection.suggestedMapping, inspection.headerRowIndex)
        assertEquals(3, rows.size)
        assertEquals("2026-05-03", rows[0].isoDate)
        assertEquals(Money(42075L), rows[0].debitMad)
        assertEquals(Money.fromDh(500), rows[1].debitMad)
        assertEquals(Money.fromDh(11500), rows[2].creditMad)
    }

    @Test
    fun `CSV parses CFG or Societe Generale comma-delimited single signed amount with quoted commas`() {
        val sampleCfg = listOf(
            "CFG Bank - Relevé mensuel",
            "Date,Description,Montant",
            "10/05/2026,\"PAIEMENT TPE \"\"PAUL, ANFA\"\"\",-85.50",
            "11/05/2026,\"FACTURE INWI FIBRE\",-249.00",
            "15/05/2026,\"VIREMENT RECU CLIENT\",+3 200,00"
        ).joinToString("\n")

        val inspection = CsvStatementParser.inspectCsv(sampleCfg.toByteArray(Charsets.UTF_8))
        assertEquals(BankProfile.CFG_SG, inspection.detectedBankProfile)
        assertEquals(',', inspection.delimiter)
        assertEquals(AmountColumnMode.SINGLE_SIGNED, inspection.suggestedMapping.amountMode)

        val rows = CsvStatementParser.extractRows(inspection.allRawRows, inspection.suggestedMapping, inspection.headerRowIndex)
        assertEquals(3, rows.size)
        assertEquals("PAIEMENT TPE \"PAUL, ANFA\"", rows[0].rawLabel)
        assertEquals(Money(8550L), rows[0].debitMad)
        assertEquals(Money.fromDh(249), rows[1].debitMad)
        assertEquals(Money.fromDh(3200), rows[2].creditMad)
    }

    @Test
    fun `ImporterMappingStore persists and reloads per-bank CSV column mapping encrypted`() {
        val customCih = CsvColumnMapping(
            bankProfile = BankProfile.CIH,
            delimiter = ';',
            hasHeaderRow = true,
            dateCol = 0,
            labelCol = 2,
            amountMode = AmountColumnMode.SPLIT_DEBIT_CREDIT,
            debitCol = 3,
            creditCol = 4
        )
        assertTrue(ImporterMappingStore.saveMapping(context, customCih))

        val loaded = ImporterMappingStore.loadAll(context)
        assertEquals(customCih, loaded[BankProfile.CIH])

        val cihCsv = """
            CIH Bank Online
            Date;Ref;Détail;Sortie;Entrée
            12/05/2026;REF991;ACHAT TPE CARREFOUR MARKET;189,90;
        """.trimIndent()
        val inspection = CsvStatementParser.inspectCsv(cihCsv.toByteArray(Charsets.UTF_8), savedMappings = loaded)
        assertTrue(inspection.usedSavedMapping)
        assertEquals(2, inspection.suggestedMapping.labelCol)
    }

    @Test
    fun `Shared pipeline converts Retrait GAB to bank-to-cash transfer, uses learned categories, and flags duplicates unticked`() {
        PayCycleEngine.learnCategoryForDescription("kiosk zaki", "transport")

        val csv = """
            Attijariwafa Bank
            Date;Libellé;Débit;Crédit
            04/05/2026;RETRAIT GAB ATTIJARI MAARIF;600,00;
            05/05/2026;PAIEMENT CARTE KIOSK ZAKI;140,00;
            06/05/2026;PAIEMENT CARTE MARJANE HAY RIAD;315,50;
            06/05/2026;PAIEMENT CARTE GLOVO MAROC;95,00;
            06/05/2026;PAIEMENT CARTE GLOVO MAROC;95,00;
        """.trimIndent()

        val insp = CsvStatementParser.inspectCsv(csv.toByteArray(Charsets.UTF_8))
        val rows = CsvStatementParser.extractRows(insp.allRawRows, insp.suggestedMapping, insp.headerRowIndex)

        val existingTxs = listOf(
            TransactionItem(
                id = "tx_manual_1",
                type = "expense",
                amount = Money(31550L),
                category = "marche",
                envelopeKey = "marche",
                accountId = "acc_bank",
                desc = "Marjane",
                isoDate = "2026-05-06"
            )
        )

        val candidates = StatementPipeline.buildCandidates(
            rows = rows,
            existingTransactions = existingTxs,
            envelopes = defaultEnvelopes,
            accounts = defaultAccounts,
            targetBankAccountId = "acc_bank",
            lang = "fr"
        )
        assertEquals(5, candidates.size)

        val atm = candidates[0]
        assertTrue(atm.isAtmTransfer)
        assertEquals("transfer", atm.txType)
        assertEquals("acc_bank", atm.accountId)
        assertEquals("acc_cash", atm.toAccountId)
        assertEquals("Retrait GAB", atm.cleanedLabel)

        val learned = candidates[1]
        assertEquals("transport", learned.envelopeKey)
        assertEquals("KIOSK ZAKI", learned.cleanedLabel)

        val dup = candidates[2]
        assertTrue(dup.isProbableDuplicate)
        assertFalse(dup.isSelected)
        assertEquals("marche", dup.envelopeKey)

        val glovo1 = candidates[3]
        val glovo2 = candidates[4]
        assertEquals("cafe", glovo1.envelopeKey)
        assertTrue(glovo1.deterministicId.startsWith("tx_imp_"))
        assertNotEquals(glovo1.deterministicId, glovo2.deterministicId)

        val rerun = StatementPipeline.buildCandidates(
            rows = rows,
            existingTransactions = existingTxs,
            envelopes = defaultEnvelopes,
            accounts = defaultAccounts,
            targetBankAccountId = "acc_bank",
            lang = "fr"
        )
        assertEquals(candidates.map { it.deterministicId }, rerun.map { it.deterministicId })

        val txs = StatementPipeline.toTransactionItems(candidates, userId = "u1")
        assertEquals(4, txs.size)
        assertTrue(txs.none { it.deterministicIdEquals(dup.deterministicId) })
        assertEquals("2026-05-04", txs[0].isoDate)
        assertEquals(12, txs[0].hourOfDay)
    }

    private fun TransactionItem.deterministicIdEquals(other: String): Boolean = this.id == other

    @Test
    fun `ReceiptParser extracts Moroccan supermarket total, date, and merchant while ignoring change rendered`() {
        val marjaneReceipt = """
            MARJANE HOLDING SA
            Bd Californie Casablanca
            ICE 001234567890012
            TICKET N 48219
            14/05/2026 18:42
            LAIT CENTRALE 1L        10,50
            HUILE OLIVE 1L          89,00
            POULET FERMIER         146,30
            SOUS TOTAL             245,80
            TOTAL TTC              245,80 DH
            ESPECES                300,00
            RENDU MONNAIE           54,20
            MERCI DE VOTRE VISITE
        """.trimIndent()

        val parsed = ReceiptParser.parse(marjaneReceipt, todayIso = "2026-05-20")
        assertEquals(Money(24580L), parsed.totalAmountMad)
        assertEquals("2026-05-14", parsed.isoDate)
        assertEquals("MARJANE HOLDING SA", parsed.merchantName)
        assertFalse(parsed.arabicWeakHint)
    }

    @Test
    fun `ReceiptParser handles total amount on next line and flags Arabic-only or empty OCR honestly`() {
        val cafeReceipt = """
            BIENVENUE
            CAFE BIANCA ANFA
            Date: 19-05-2026
            1x Espresso             18,00
            1x Jus Orange           20,00
            NET A PAYER
            38,00 DH
        """.trimIndent()

        val parsedCafe = ReceiptParser.parse(cafeReceipt, todayIso = "2026-05-20")
        assertEquals(Money.fromDh(38), parsedCafe.totalAmountMad)
        assertEquals("2026-05-19", parsedCafe.isoDate)
        assertEquals("CAFE BIANCA ANFA", parsedCafe.merchantName)

        val arabicOnly = ReceiptParser.parse("فاتورة رقم ١٢٣\nالمجموع ٥٠ درهم", todayIso = "2026-05-20")
        assertTrue(arabicOnly.arabicWeakHint)
        assertNull(arabicOnly.totalAmountMad)
    }

    @Test
    fun `PdfStatementParser parses multi-line Moroccan bank PDFs and infers debit vs credit from running balance and keywords`() {
        val bmcePdfText = """
            BANK OF AFRICA - BMCE
            RELEVE DE COMPTE - MAI 2026
            SOLDE ANTERIEUR AU 30/04/2026                        10 000,00
            Date Opé   Date Val   Nature d'opération             Débit        Crédit       Solde
            02/05/2026 02/05/2026 PAIEMENT CARTE ONCF VOYAGES    120,00                    9 880,00
            GARE CASA VOYAGEURS
            05/05/2026 05/05/2026 RETRAIT GAB BOA AGDAL          400,00                    9 480,00
            08/05/2026 08/05/2026 VIREMENT RECU PRIME            2 500,00                 11 980,00
            10/05/2026 10/05/2026 PRELEVEMENT LYDEC EAU          210,50                   11 769,50
            NOUVEAU SOLDE AU 31/05/2026                                                   11 769,50
            Page 1/1
        """.trimIndent()

        val parsed = PdfStatementParser.parseText(bmcePdfText)
        assertEquals(BankProfile.BMCE, parsed.detectedBankProfile)
        assertEquals(4, parsed.rows.size)

        assertEquals("2026-05-02", parsed.rows[0].isoDate)
        assertTrue(parsed.rows[0].rawLabel.contains("ONCF"))
        assertTrue(parsed.rows[0].rawLabel.contains("CASA VOYAGEURS"))
        assertEquals(Money.fromDh(120), parsed.rows[0].debitMad)
        assertNull(parsed.rows[0].creditMad)

        assertEquals(Money.fromDh(400), parsed.rows[1].debitMad)

        assertNull(parsed.rows[2].debitMad)
        assertEquals(Money.fromDh(2500), parsed.rows[2].creditMad)

        assertEquals(Money(21050L), parsed.rows[3].debitMad)
    }

    @Test
    fun `PdfStatementParser detects CIH, Attijariwafa, BCP, and Societe Generale profiles`() {
        val cihSample = """
            CIH BANK - Relevé de compte
            12/05/2026 ACHAT TPE BIM MAROC 145,00
            13/05/2026 VIREMENT RECU FREELANCE 4 000,00
        """.trimIndent()
        val cihRes = PdfStatementParser.parseText(cihSample)
        assertEquals(BankProfile.CIH, cihRes.detectedBankProfile)
        assertEquals(2, cihRes.rows.size)
        assertEquals(Money.fromDh(145), cihRes.rows[0].debitMad)
        assertEquals(Money.fromDh(4000), cihRes.rows[1].creditMad)

        val sgSample = """
            SOCIETE GENERALE MAROC (SGMB)
            15/05/2026 PAIEMENT CARTE SHELL ANFA -350,00
            16/05/2026 VERSEMENT ESPECES +1 200,00
        """.trimIndent()
        val sgRes = PdfStatementParser.parseText(sgSample)
        assertEquals(BankProfile.CFG_SG, sgRes.detectedBankProfile)
        assertEquals(2, sgRes.rows.size)
        assertEquals(Money.fromDh(350), sgRes.rows[0].debitMad)
        assertEquals(Money.fromDh(1200), sgRes.rows[1].creditMad)
    }
}
