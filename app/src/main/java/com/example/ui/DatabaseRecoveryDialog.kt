package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class DbRecoveryStrings(
    val title: String,
    val body: String,
    val primaryLocalCopyWarning: String,
    val restoreBackupBtn: String,
    val resetDbBtn: String,
    val confirmResetTitle: String,
    val confirmResetBody: String,
    val confirmCheckboxLabel: String,
    val confirmPermanentResetBtn: String,
    val cancelResetBtn: String
)

internal fun recoveryStringsFor(language: String): DbRecoveryStrings = when (language.lowercase()) {
    "ar" -> DbRecoveryStrings(
        title = "تعذّر فتح قاعدة البيانات المشفّرة",
        body = "مفتاح التشفير المحلي في Android Keystore مفقود أو تالف. لم يتم حذف بياناتك. يمكنك استعادة نسخة احتياطية مشفّرة أو إعادة تهيئة قاعدة البيانات المحلية.",
        primaryLocalCopyWarning = "تنبيه مهم: أصبحت قاعدة البيانات المحلية المشفّرة (Room) الآن النسخة المحلية الوحيدة لبياناتك. إعادة تهيئتها ستحذف بياناتك المحلية نهائياً إذا لم تكن لديك نسخة احتياطية.",
        restoreBackupBtn = "استعادة نسخة احتياطية",
        resetDbBtn = "إعادة تهيئة القاعدة المحلية",
        confirmResetTitle = "تأكيد الحذف النهائي للقاعدة المحلية",
        confirmResetBody = "بعد إتمام الاستيراد المحلي، تُعد قاعدة البيانات المشفّرة على هذا الجهاز النسخة المحلية الوحيدة. سيؤدي هذا الإجراء إلى مسحها بالكامل وتوليد مفتاح جديد.",
        confirmCheckboxLabel = "أفهم أن قاعدة البيانات المحلية المشفّرة هي النسخة المحلية الوحيدة وسيتم مسحها نهائياً.",
        confirmPermanentResetBtn = "تأكيد إعادة التهيئة النهائية",
        cancelResetBtn = "رجوع"
    )
    "darija" -> DbRecoveryStrings(
        title = "ما قدّيناش نحلّو قاعدة البيانات المشفّرة",
        body = "الساروت ديال التشفير فـ Android Keystore ضاع ولا تالف. المعلومات ديالك ما تمسحاتش. تقدر ترجّع نسخة احتياطية مشفّرة ولا تعاود تهيئ القاعدة المحلية.",
        primaryLocalCopyWarning = "تحذير مهم: قاعدة البيانات المحلية المشفّرة (Room) ولّات دابا هي النسخة المحلية الوحيدة ديال المعلومات ديالك. إلا مسحتيها وما عندكش نسخة احتياطية، كلشي غادي يضيع.",
        restoreBackupBtn = "استرجاع نسخة احتياطية",
        resetDbBtn = "إعادة تهيئة القاعدة المحلية",
        confirmResetTitle = "تأكيد المسح النهائي للقاعدة المحلية",
        confirmResetBody = "حيت الاستيراد المحلي كمل، القاعدة المشفّرة فالتيليفون هي النسخة المحلية الوحيدة. هاد العملية غادي تمسح كلشي وتكريي ساروت جديد.",
        confirmCheckboxLabel = "فاهم بلي القاعدة المحلية هي النسخة الوحيدة وغادي يتمسح كلشي نهائياً.",
        confirmPermanentResetBtn = "أكّد المسح وإعادة التهيئة",
        cancelResetBtn = "رجوع"
    )
    "en" -> DbRecoveryStrings(
        title = "Unable to Unlock Encrypted Database",
        body = "The local Android Keystore encryption key is missing or invalidated. Your data has not been deleted. You can restore an encrypted backup or reset the local database.",
        primaryLocalCopyWarning = "Important: Your encrypted Room database is now the only local copy of your financial records. Resetting it will permanently erase local data unless you have a backup.",
        restoreBackupBtn = "Restore Backup",
        resetDbBtn = "Reset Local Database",
        confirmResetTitle = "Confirm Permanent Local Reset",
        confirmResetBody = "Because local import has completed, the encrypted database on this device is your only local copy. Proceeding will permanently delete it and generate a new encryption key.",
        confirmCheckboxLabel = "I understand that Room is now my only local copy and resetting will permanently erase it.",
        confirmPermanentResetBtn = "Confirm Permanent Reset",
        cancelResetBtn = "Back"
    )
    else -> DbRecoveryStrings(
        title = "Base de données chiffrée inaccessible",
        body = "La clé locale Android Keystore est manquante ou invalidée. Vos données n'ont pas été supprimées. Vous pouvez restaurer une sauvegarde chiffrée ou réinitialiser la base locale.",
        primaryLocalCopyWarning = "Attention : la base chiffrée Room est désormais l'unique copie locale de vos données. La réinitialiser effacera définitivement vos données locales si vous n'avez pas de sauvegarde.",
        restoreBackupBtn = "Restaurer une sauvegarde",
        resetDbBtn = "Réinitialiser la base locale",
        confirmResetTitle = "Confirmer la réinitialisation définitive",
        confirmResetBody = "L'import local étant terminé, cette base chiffrée est l'unique copie locale sur cet appareil. Cette action l'effacera définitivement et générera une nouvelle clé.",
        confirmCheckboxLabel = "Je comprends que Room est désormais mon unique copie locale et qu'elle sera effacée définitivement.",
        confirmPermanentResetBtn = "Confirmer la réinitialisation",
        cancelResetBtn = "Retour"
    )
}

/**
 * Non-crashing recovery dialog shown if [com.example.data.local.DatabaseHealthState.RecoveryRequired]
 * occurs (e.g., corrupted/invalidated Keystore key while an encrypted DB file exists on disk).
 *
 * Once the Firestore -> Room import has completed ([isRoomPrimaryAfterImport] = true),
 * warns explicitly that Room is now the only local copy and requires a second-step
 * checkbox confirmation before resetting the local database.
 */
@Composable
fun DatabaseRecoveryDialog(
    language: String,
    isRoomPrimaryAfterImport: Boolean = false,
    onRestoreBackup: () -> Unit,
    onResetLocalDatabase: () -> Unit
) {
    val copy = recoveryStringsFor(language)
    var awaitingExplicitConfirm by remember(isRoomPrimaryAfterImport) { mutableStateOf(false) }
    var acknowledgedDataLoss by remember(isRoomPrimaryAfterImport) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* Mandatory user choice for corrupted encrypted DB */ },
        icon = {
            Icon(
                imageVector = if (awaitingExplicitConfirm) Icons.Default.WarningAmber else Icons.Default.LockReset,
                contentDescription = if (awaitingExplicitConfirm) copy.confirmResetTitle else copy.title,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (awaitingExplicitConfirm) copy.confirmResetTitle else copy.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!awaitingExplicitConfirm) {
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isRoomPrimaryAfterImport) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("db_recovery_primary_warning")
                        ) {
                            Text(
                                text = copy.primaryLocalCopyWarning,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = copy.confirmResetBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledgedDataLoss = !acknowledgedDataLoss }
                            .padding(vertical = 4.dp)
                            .testTag("db_recovery_confirm_checkbox_row")
                    ) {
                        Checkbox(
                            checked = acknowledgedDataLoss,
                            onCheckedChange = { acknowledgedDataLoss = it },
                            modifier = Modifier.testTag("db_recovery_confirm_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = copy.confirmCheckboxLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!awaitingExplicitConfirm) {
                Button(
                    onClick = onRestoreBackup,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("db_recovery_restore_button")
                ) {
                    Text(copy.restoreBackupBtn, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onResetLocalDatabase,
                    enabled = acknowledgedDataLoss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("db_recovery_confirm_reset_button")
                ) {
                    Text(copy.confirmPermanentResetBtn, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!awaitingExplicitConfirm) {
                OutlinedButton(
                    onClick = {
                        if (isRoomPrimaryAfterImport) {
                            acknowledgedDataLoss = false
                            awaitingExplicitConfirm = true
                        } else {
                            onResetLocalDatabase()
                        }
                    },
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
            } else {
                OutlinedButton(
                    onClick = {
                        awaitingExplicitConfirm = false
                        acknowledgedDataLoss = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("db_recovery_cancel_reset_button")
                ) {
                    Text(copy.cancelResetBtn, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
