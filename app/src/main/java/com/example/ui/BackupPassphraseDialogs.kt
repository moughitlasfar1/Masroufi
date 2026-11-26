package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.BackupEnvelopeInfo
import com.example.security.PortableBackupCrypto

/**
 * Material 3 dialog prompting for a passphrase + confirmation before exporting a portable
 * Backup v3 (PBKDF2-HMAC-SHA256 600,000 iterations + AES-256-GCM) file.
 */
@Composable
fun ExportBackupPassphraseDialog(
    lang: String,
    isEncrypting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmPassphrase: (CharArray) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    val minLen = PortableBackupCrypto.MIN_PASSPHRASE_LENGTH
    val trimmed = passphrase.trim()
    val trimmedConfirm = confirmPassphrase.trim()
    val lengthOk = trimmed.length >= minLen
    val matches = trimmed.isNotEmpty() && trimmed == trimmedConfirm
    val canSubmit = !isEncrypting && lengthOk && matches

    val title = when (lang) {
        "ar" -> "تصدير نسخة احتياطية مشفّرة (v3)"
        "darija" -> "تصدير نسخة مشفّرة (v3)"
        "en" -> "Export Encrypted Backup (v3)"
        else -> "Exporter une sauvegarde chiffrée (v3)"
    }
    val noticeText = when (lang) {
        "ar" -> "هذا الملف مشفّر بـ AES-256-GCM ويعمل على أي جهاز. لا يمكن استرجاع كلمة المرور إذا نُسيت."
        "darija" -> "هاد الملف مشفّر بـ AES-256-GCM وكايخدم على أي تيليفون. إلا نسيتي كلمة السر مايمكنش ترجعها."
        "en" -> "This AES-256-GCM backup works on any device. Forgotten passphrases cannot be recovered."
        else -> "Cette sauvegarde AES-256-GCM fonctionne sur n'importe quel appareil. Un mot de passe oublié ne peut pas être récupéré."
    }
    val passLabel = when (lang) {
        "ar" -> "كلمة المرور ($minLen أحرف على الأقل)"
        "darija" -> "كلمة السر ($minLen حروف على الأقل)"
        "en" -> "Passphrase (min $minLen characters)"
        else -> "Mot de passe (min $minLen caractères)"
    }
    val confirmLabel = when (lang) {
        "ar" -> "تأكيد كلمة المرور"
        "darija" -> "تأكيد كلمة السر"
        "en" -> "Confirm passphrase"
        else -> "Confirmer le mot de passe"
    }
    val mismatchLabel = when (lang) {
        "ar" -> "كلمتا المرور غير متطابقتين"
        "darija" -> "كلمات السر ما متطابقينش"
        "en" -> "Passphrases do not match"
        else -> "Les mots de passe ne correspondent pas"
    }
    val exportLabel = when (lang) {
        "ar" -> "تشفير وحفظ"
        "darija" -> "تشفير وحفظ"
        "en" -> "Encrypt & Save"
        else -> "Chiffrer et enregistrer"
    }
    val cancelLabel = when (lang) {
        "ar" -> "إلغاء"
        "darija" -> "إلغاء"
        "en" -> "Cancel"
        else -> "Annuler"
    }

    AlertDialog(
        onDismissRequest = { if (!isEncrypting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_backup_passphrase_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    enabled = !isEncrypting,
                    label = { Text(passLabel, fontSize = 12.sp) },
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassphrase = !showPassphrase },
                            modifier = Modifier.testTag("export_passphrase_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            text = "${trimmed.length} / $minLen",
                            color = if (lengthOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_passphrase_input")
                )

                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = { confirmPassphrase = it },
                    enabled = !isEncrypting,
                    label = { Text(confirmLabel, fontSize = 12.sp) },
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPassphrase.isNotEmpty() && !matches,
                    supportingText = if (confirmPassphrase.isNotEmpty() && !matches) {
                        { Text(mismatchLabel, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_passphrase_confirm_input")
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("export_passphrase_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chars = trimmed.toCharArray()
                    onConfirmPassphrase(chars)
                },
                enabled = canSubmit,
                modifier = Modifier.testTag("export_passphrase_confirm_btn")
            ) {
                if (isEncrypting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(exportLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isEncrypting,
                modifier = Modifier.testTag("export_passphrase_cancel_btn")
            ) {
                Text(cancelLabel)
            }
        }
    )
}

/**
 * Material 3 dialog prompting for the backup passphrase after a v3 or legacy v2 encrypted
 * backup file is selected. Retains inline error state on wrong passphrase so the user can retry
 * without re-picking the file.
 */
@Composable
fun RestoreBackupPassphraseDialog(
    lang: String,
    envelopeInfo: BackupEnvelopeInfo,
    isRestoring: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmRestore: (CharArray, Boolean) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var replaceExisting by remember { mutableStateOf(true) }

    val badgeText = when (envelopeInfo) {
        is BackupEnvelopeInfo.PortableV3Encrypted ->
            "Backup v3 • AES-256-GCM • PBKDF2 (${envelopeInfo.iterations})"
        is BackupEnvelopeInfo.LegacyV2Encrypted ->
            "Backup v2 (Legacy) • AES-256-GCM • PBKDF2 (${envelopeInfo.iterations})"
        else -> "AES-256-GCM"
    }

    val title = when (lang) {
        "ar" -> "فك تشفير واستعادة النسخة"
        "darija" -> "فتح واسترجاع النسخة المشفّرة"
        "en" -> "Decrypt & Restore Backup"
        else -> "Déchiffrer et restaurer la sauvegarde"
    }
    val passLabel = when (lang) {
        "ar" -> "كلمة مرور النسخة الاحتياطية"
        "darija" -> "كلمة سر النسخة الاحتياطية"
        "en" -> "Backup passphrase"
        else -> "Mot de passe de la sauvegarde"
    }
    val replaceLabel = when (lang) {
        "ar" -> "استبدال البيانات الحالية بالكامل"
        "darija" -> "تعويض البيانات الحالية بالكامل"
        "en" -> "Replace existing data completely"
        else -> "Remplacer complètement les données actuelles"
    }
    val restoreLabel = when (lang) {
        "ar" -> "استعادة"
        "darija" -> "استرجاع"
        "en" -> "Restore"
        else -> "Restaurer"
    }
    val cancelLabel = when (lang) {
        "ar" -> "إلغاء"
        "darija" -> "إلغاء"
        "en" -> "Cancel"
        else -> "Annuler"
    }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_backup_passphrase_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        if (!errorMessage.isNullOrBlank()) onClearError()
                    },
                    enabled = !isRestoring,
                    label = { Text(passLabel, fontSize = 12.sp) },
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = !errorMessage.isNullOrBlank(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassphrase = !showPassphrase },
                            modifier = Modifier.testTag("restore_passphrase_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_passphrase_input")
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("restore_passphrase_error_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = replaceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = replaceExisting,
                        onCheckedChange = { replaceExisting = it },
                        enabled = !isRestoring,
                        modifier = Modifier.testTag("restore_passphrase_replace_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chars = passphrase.trim().toCharArray()
                    onConfirmRestore(chars, replaceExisting)
                },
                enabled = !isRestoring && passphrase.trim().isNotEmpty(),
                modifier = Modifier.testTag("restore_passphrase_confirm_btn")
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(restoreLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRestoring,
                modifier = Modifier.testTag("restore_passphrase_cancel_btn")
            ) {
                Text(cancelLabel)
            }
        }
    )
}
