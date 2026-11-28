package com.masroufi.credit.data

import com.masroufi.credit.domain.CreditInput
import com.masroufi.credit.domain.CreditResult
import kotlinx.coroutines.flow.Flow

interface CreditDataSource {
    fun currentUid(): String?
    suspend fun saveCredit(
        input: CreditInput,
        result: CreditResult,
        linkToMonthlyExpenses: Boolean
    ): Result<String>
    fun observeSavedCredits(): Flow<List<SavedCreditEntity>>
    suspend fun deleteCredit(creditId: String): Result<Unit>
}
