package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lan.LanCompanionController
import com.example.lan.LanCompanionError
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald

internal data class LanCompanionStrings(
    val title: String,
    val subtitle: String,
    val statusOn: String,
    val statusOff: String,
    val startServerBtn: String,
    val stopServerBtn: String,
    val urlCardLabel: String,
    val copyUrlBtn: String,
    val copiedToast: String,
    val pinCardLabel: String,
    val pinCardHint: String,
    val regeneratePinBtn: String,
    val showPinDesc: String,
    val hidePinDesc: String,
    val activeSessionsLabel: String,
    val idleTimeoutTitle: String,
    val minutesShort: String,
    val wifiRequiredError: String,
    val startFailedError: String,
    val securityTitle: String,
    val securityBulletWifi: String,
    val securityBulletPin: String,
    val securityBulletAutoStop: String,
    val closeDesc: String
)

internal fun lanCompanionStringsFor(lang: String): LanCompanionStrings = when (lang) {
    "ar" -> LanCompanionStrings(
        title = "رفيق الشبكة المحلية (Wi-Fi)",
        subtitle = "استعمل مصروفي من متصفح الحاسوب على نفس شبكة الواي فاي بدون إنترنت.",
        statusOn = "مُفعّل",
        statusOff = "مُتوقّف",
        startServerBtn = "تشغيل الخادم المحلي",
        stopServerBtn = "إيقاف الخادم فوراً",
        urlCardLabel = "الرابط المحلي (نفس شبكة Wi-Fi)",
        copyUrlBtn = "نسخ الرابط",
        copiedToast = "تم نسخ الرابط المحلي",
        pinCardLabel = "رمز الجلسة المؤقت (PIN)",
        pinCardHint = "لا يظهر أبداً في الإشعارات ولا يُحفظ على الجهاز.",
        regeneratePinBtn = "تجديد الرمز وفصل الجلسات",
        showPinDesc = "إظهار الرمز",
        hidePinDesc = "إخفاء الرمز",
        activeSessionsLabel = "جلسات نشطة",
        idleTimeoutTitle = "إيقاف تلقائي عند الخمول",
        minutesShort = "د",
        wifiRequiredError = "اتصل بشبكة Wi-Fi خاصة أولاً (لا يعمل على بيانات الهاتف).",
        startFailedError = "تعذّر تشغيل الخادم المحلي. أعد المحاولة.",
        securityTitle = "ضمانات الأمان",
        securityBulletWifi = "مقيّد بعنوان Wi-Fi الخاص فقط (RFC1918) — بدون إنترنت أو سحابة.",
        securityBulletPin = "رمز مؤقت في الذاكرة فقط مع قفل تلقائي بعد 5 محاولات خاطئة.",
        securityBulletAutoStop = "إيقاف تلقائي عند الخمول أو انقطاع Wi-Fi مع مسح جميع الرموز.",
        closeDesc = "إغلاق"
    )
    "darija" -> LanCompanionStrings(
        title = "Companion Wi-Fi Local",
        subtitle = "T7ekkem f Masroufi men PC f nefs l-Wi-Fi bla internet.",
        statusOn = "KHEDDAM",
        statusOff = "TAFI",
        startServerBtn = "Ch3el serveur local",
        stopServerBtn = "Tfi serveur daba",
        urlCardLabel = "Lien local (nefs l-Wi-Fi)",
        copyUrlBtn = "Copier l-lien",
        copiedToast = "L-lien tcopia!",
        pinCardLabel = "Code PIN dial l-session",
        pinCardHint = "Ma kaybanch f notifications o ma kaytsajjelch f téléphone.",
        regeneratePinBtn = "Beddel l-PIN o qte3 l-sessions",
        showPinDesc = "Werri l-PIN",
        hidePinDesc = "Khebbi l-PIN",
        activeSessionsLabel = "Sessions active",
        idleTimeoutTitle = "Tfi automatique ila ma khdemtich",
        minutesShort = "min",
        wifiRequiredError = "Tconnecta b Wi-Fi khass be3da (ma kaykhdemch b 4G/5G).",
        startFailedError = "Ma qderch ych3el serveur local. 3awed jerreb.",
        securityTitle = "Sécurité locale",
        securityBulletWifi = "Kaykhdem ghir f Wi-Fi privé (RFC1918) — zéro cloud.",
        securityBulletPin = "PIN f mémoire ghir b session + blocage 5 min men be3d 5 ghalat.",
        securityBulletAutoStop = "Kaytfi bo7do ila tqte3 l-Wi-Fi ola fat l-waqt dial l-inactivité.",
        closeDesc = "Sed"
    )
    "en" -> LanCompanionStrings(
        title = "Local LAN Companion",
        subtitle = "Access and edit Masroufi from your desktop browser on the same Wi-Fi network.",
        statusOn = "ON",
        statusOff = "OFF",
        startServerBtn = "Start Local Server",
        stopServerBtn = "Stop Server Now",
        urlCardLabel = "Local Wi-Fi URL",
        copyUrlBtn = "Copy URL",
        copiedToast = "Local URL copied",
        pinCardLabel = "Ephemeral Session PIN",
        pinCardHint = "Never shown in notifications and never written to disk.",
        regeneratePinBtn = "Regenerate PIN & Revoke Sessions",
        showPinDesc = "Show PIN",
        hidePinDesc = "Hide PIN",
        activeSessionsLabel = "Active sessions",
        idleTimeoutTitle = "Auto-stop on inactivity",
        minutesShort = "min",
        wifiRequiredError = "Connect to a private Wi-Fi network first (cellular is blocked).",
        startFailedError = "Unable to start local server. Please try again.",
        securityTitle = "Security Guarantees",
        securityBulletWifi = "Bound strictly to your private Wi-Fi IPv4 (RFC1918) — zero external traffic.",
        securityBulletPin = "Memory-only ephemeral PIN & Bearer token with 5-attempt lockout.",
        securityBulletAutoStop = "Auto-stops on idle timeout or Wi-Fi disconnect, wiping all tokens.",
        closeDesc = "Close"
    )
    else -> LanCompanionStrings(
        title = "Compagnon LAN Local",
        subtitle = "Consultez et saisissez vos dépenses depuis un ordinateur sur le même Wi-Fi.",
        statusOn = "ACTIF",
        statusOff = "ARRÊTÉ",
        startServerBtn = "Démarrer le serveur local",
        stopServerBtn = "Arrêter le serveur",
        urlCardLabel = "Adresse locale (même réseau Wi-Fi)",
        copyUrlBtn = "Copier l'URL",
        copiedToast = "URL locale copiée",
        pinCardLabel = "Code PIN éphémère de session",
        pinCardHint = "Jamais affiché dans la notification, jamais enregistré sur disque.",
        regeneratePinBtn = "Régénérer le PIN et révoquer",
        showPinDesc = "Afficher le PIN",
        hidePinDesc = "Masquer le PIN",
        activeSessionsLabel = "Sessions actives",
        idleTimeoutTitle = "Arrêt automatique si inactif",
        minutesShort = "min",
        wifiRequiredError = "Connectez-vous d'abord à un Wi-Fi privé (données mobiles bloquées).",
        startFailedError = "Impossible de démarrer le serveur local. Réessayez.",
        securityTitle = "Garanties de sécurité",
        securityBulletWifi = "Lié uniquement à l'IP Wi-Fi privée (RFC1918) — zéro serveur externe.",
        securityBulletPin = "PIN et jeton Bearer uniquement en mémoire + blocage après 5 échecs.",
        securityBulletAutoStop = "Arrêt automatique à l'inactivité ou perte du Wi-Fi avec purge complète.",
        closeDesc = "Fermer"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanCompanionBottomSheet(
    state: DashboardState,
    currentUserId: String,
    onCopyUrl: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lanState by LanCompanionController.state.collectAsStateWithLifecycle()
    val s = remember(state.profile.language) { lanCompanionStringsFor(state.profile.language) }
    var showPin by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (lanState.isRunning) AccentEmerald.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LaptopMac,
                            contentDescription = null,
                            tint = if (lanState.isRunning) AccentEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = s.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (lanState.isRunning) {
                                    AccentEmerald.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.testTag("lan_status_badge")
                            ) {
                                Text(
                                    text = if (lanState.isRunning) s.statusOn else s.statusOff,
                                    color = if (lanState.isRunning) {
                                        AccentEmerald
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = s.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lan_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = s.closeDesc
                    )
                }
            }

            // Error banner if Wi-Fi unavailable or bind failed
            if (lanState.error != LanCompanionError.NONE) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentCoral.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentCoral.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .testTag("lan_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = AccentCoral
                        )
                        Text(
                            text = if (lanState.error == LanCompanionError.WIFI_REQUIRED) {
                                s.wifiRequiredError
                            } else {
                                s.startFailedError
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCoral
                        )
                    }
                }
            }

            // Primary Start / Stop CTA (48dp+)
            Button(
                onClick = {
                    if (lanState.isRunning) {
                        LanCompanionController.stop(context)
                    } else {
                        LanCompanionController.start(
                            context = context,
                            userId = currentUserId,
                            idleTimeoutMinutes = lanState.idleTimeoutMinutes
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (lanState.isRunning) AccentCoral else AccentEmerald,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("lan_toggle_btn")
            ) {
                Icon(
                    imageVector = if (lanState.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (lanState.isRunning) s.stopServerBtn else s.startServerBtn,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            // Live URL + PIN cards when running
            if (lanState.isRunning && lanState.url.isNotBlank()) {
                // URL Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = s.urlCardLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = lanState.url,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("lan_url_text")
                        )
                        FilledTonalButton(
                            onClick = { onCopyUrl(lanState.url, s.copiedToast) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("lan_copy_url_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = s.copyUrlBtn,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Ephemeral PIN Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = s.pinCardLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "${s.activeSessionsLabel}: ${lanState.activeSessionsCount}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val formattedPin = remember(lanState.pin, showPin) {
                                if (!showPin) {
                                    "••• •••"
                                } else if (lanState.pin.length == 6) {
                                    "${lanState.pin.substring(0, 3)} ${lanState.pin.substring(3, 6)}"
                                } else {
                                    lanState.pin
                                }
                            }
                            Text(
                                text = formattedPin,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("lan_pin_text")
                            )
                            IconButton(
                                onClick = { showPin = !showPin },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("lan_pin_visibility_btn")
                            ) {
                                Icon(
                                    imageVector = if (showPin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPin) s.hidePinDesc else s.showPinDesc
                                )
                            }
                        }

                        Text(
                            text = s.pinCardHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = { LanCompanionController.regeneratePin() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("lan_regenerate_pin_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = s.regeneratePinBtn,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Idle auto-stop timeout selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = s.idleTimeoutTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(5, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = lanState.idleTimeoutMinutes == mins,
                            onClick = { LanCompanionController.setIdleTimeoutMinutes(mins) },
                            label = {
                                Text(
                                    text = "$mins ${s.minutesShort}",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("lan_idle_chip_$mins")
                        )
                    }
                }
            }

            // Security guarantees card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = s.securityTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "• ${s.securityBulletWifi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${s.securityBulletPin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${s.securityBulletAutoStop}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
