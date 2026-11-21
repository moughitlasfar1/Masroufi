package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DbRecoveryStrings(
    val title: String,
    val body: String,
    val restoreBackupBtn: String,
    val resetDbBtn: String
)

private fun recoveryStringsFor(language: String): DbRecoveryStrings = when (language.lowercase()) {
    "ar" -> DbRecoveryStrings(
        title = "تعذّر فتح قاعدة البيانات المشفّرة",
        body = "مفتاح التشفير المحلي في Android Keystore مفقود أو تالف. لم يتم حذف بياناتك. يمكنك استعادة نسخة احتياطية مشفّرة أو إعادة تهيئة قاعدة البيانات المحلية.",
        restoreBackupBtn = "استعادة نسخة احتياطية",
        resetDbBtn = "إعادة تهيئة القاعدة المحلية"
    )
    "darija" -> DbRecoveryStrings(
        title = "ما قدّيناش نحلّو قاعدة البيانات المشفّرة",
        body = "الساروت ديال التشفير فـ Android Keystore ضاع ولا تالف. المعلومات ديالك ما تمسحاتش. تقدر ترجّع نسخة احتياطية مشفّرة ولا تعاود تهيئ القاعدة المحلية.",
        restoreBackupBtn = "استرجاع نسخة احتياطية",
        resetDbBtn = "إعادة تهيئة القاعدة المحلية"
    )
    "en" -> DbRecoveryStrings(
        title = "Unable to Unlock Encrypted Database",
        body = "The local Android Keystore encryption key is missing or invalidated. Your data has not been deleted. You can restore an encrypted backup or reset the local database.",
        restoreBackupBtn = "Restore Backup",
        resetDbBtn = "Reset Local Database"
    )
    else -> DbRecoveryStrings(
        title = "Base de données chiffrée inaccessible",
        body = "La clé locale Android Keystore est manquante ou invalidée. Vos données n'ont pas été supprimées. Vous pouvez restaurer une sauvegarde chiffrée ou réinitialiser la base locale.",
        restoreBackupBtn = "Restaurer une sauvegarde",
        resetDbBtn = "Réinitialiser la base locale"
    )
}

/**
 * Non-crashing recovery dialog shown if [com.example.data.local.DatabaseHealthState.RecoveryRequired]
 * occurs (e.g., corrupted/invalidated Keystore key while an encrypted DB file exists on disk).
 */
@Composable
fun DatabaseRecoveryDialog(
    language: String,
    onRestoreBackup: () -> Unit,
    onResetLocalDatabase: () -> Unit
) {
    val copy = recoveryStringsFor(language)
    AlertDialog(
        onDismissRequest = { /* Mandatory user choice for corrupted encrypted DB */ },
        icon = {
            Icon(
                imageVector = Icons.Default.LockReset,
                contentDescription = copy.title,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = copy.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = copy.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRestoreBackup,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("db_recovery_restore_button")
            ) {
                Text(copy.restoreBackupBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onResetLocalDatabase,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("db_recovery_reset_button")
            ) {
                Text(copy.resetDbBtn, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
