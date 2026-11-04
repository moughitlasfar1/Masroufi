package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasroufiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndObserveTransaction_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MasroufiRepository(firestore)
        val uniqueId = "tx_${UUID.randomUUID().toString().replace("-", "")}"

        val savedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertTransaction(
                TransactionItem(
                    id = uniqueId,
                    userId = aliceUid,
                    type = "expense",
                    amount = 35.0,
                    category = "Café & Sorties",
                    envelopeKey = "cafe",
                    accountId = "acc_cash",
                    toAccountId = "",
                    desc = "Café matinal",
                    isoDate = "2026-10-31"
                )
            )
        }
        assertEquals(uniqueId, savedId)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTransactions(aliceUid).first { list -> list.any { it.id == uniqueId } }
        }
        assertTrue(emitted.any { it.id == uniqueId && it.amount == 35.0 })
    }

    @Test
    fun getTransactionById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MasroufiRepository(firestore)
        val uniqueId = "tx_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertTransaction(
                TransactionItem(
                    id = uniqueId,
                    userId = aliceUid,
                    type = "expense",
                    amount = 120.0,
                    category = "Transport & Carburant",
                    envelopeKey = "transport",
                    accountId = "acc_bank",
                    toAccountId = "",
                    desc = "Taxi",
                    isoDate = "2026-10-31"
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MasroufiRepository(firestore)
        var caughtException: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getTransactionById(aliceUid, uniqueId)
            }
        } catch (e: Exception) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val fsEx = caughtException?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = MasroufiRepository(firestore)

        var caughtException: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTransactions(aliceUid).first()
            }
        } catch (e: Exception) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val fsEx = caughtException?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun upsertTransferAndUpdatePreservingCreatedAt_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MasroufiRepository(firestore)
        val uniqueId = "tx_tr_${UUID.randomUUID().toString().replace("-", "")}"

        val savedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertTransaction(
                TransactionItem(
                    id = uniqueId,
                    userId = aliceUid,
                    type = "transfer",
                    amount = 250.0,
                    category = "Transfert",
                    envelopeKey = "autre",
                    accountId = "acc_bank",
                    toAccountId = "acc_cash",
                    desc = "Retrait GAB",
                    isoDate = "2026-10-31"
                )
            )
        }
        assertEquals(uniqueId, savedId)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getTransactionById(aliceUid, uniqueId)
        }
        assertNotNull(fetched)
        assertEquals("acc_cash", fetched?.toAccountId)

        // Update preserving createdAt succeeds under SEC-08 rules
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertTransaction(
                fetched!!.copy(amount = 300.0, desc = "Retrait GAB Mis à jour")
            )
        }
        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getTransactionById(aliceUid, uniqueId)
        }
        assertEquals(300.0, updated?.amount ?: 0.0, 0.01)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@masroufi.test"
        const val BOB_EMAIL = "bob@masroufi.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
