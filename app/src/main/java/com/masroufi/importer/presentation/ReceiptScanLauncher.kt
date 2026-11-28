package com.masroufi.importer.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.importer.data.ReceiptFileHelper
import com.masroufi.importer.data.ReceiptOcrEngine
import com.masroufi.importer.domain.ImporterStrings
import com.masroufi.importer.domain.ParsedReceipt
import java.io.File
import kotlinx.coroutines.launch

/**
 * Zero-permission Receipt Scanner trigger for [com.example.ui.TransactionEditorSheet].
 * Uses Android's system camera (`TakePicture` into strictly confined `cache/receipts/`)
 * and Photo Picker (`PickVisualMedia`), runs bundled offline ML Kit Latin OCR,
 * and deletes the temporary photo immediately after scanning.
 */
@Composable
fun ScanReceiptActionChip(
    lang: String,
    onReceiptScanned: (ParsedReceipt) -> Unit,
    onScanError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!com.example.platform.PlatformReceiptOcrBridge.isReceiptOcrSupported) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiStrings = remember(lang) { ImporterStrings.forLang(lang) }

    var showSourceDialog by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Scrub any stale receipt images left behind if an earlier scan was killed mid-flight
    LaunchedEffect(Unit) {
        ReceiptFileHelper.clearReceiptCache(context)
    }

    fun runOcr(uri: Uri, tempFile: File?) {
        isScanning = true
        scope.launch {
            runCatching {
                ReceiptOcrEngine.scanReceiptFromUri(
                    context = context,
                    uri = uri,
                    tempFileToDelete = tempFile
                )
            }.onSuccess { parsed ->
                isScanning = false
                onReceiptScanned(parsed)
            }.onFailure {
                ReceiptFileHelper.deleteQuietly(tempFile)
                isScanning = false
                onScanError(uiStrings.fileReadError)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        pendingCameraFile = null
        pendingCameraUri = null
        if (saved && uri != null) {
            runOcr(uri, file)
        } else {
            ReceiptFileHelper.deleteQuietly(file)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri ->
        if (selectedUri != null) {
            runOcr(selectedUri, null)
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            modifier = Modifier.testTag("receipt_scan_source_dialog"),
            title = {
                Text(
                    text = uiStrings.scanReceiptDialogTitle,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showSourceDialog = false
                                runCatching {
                                    val (file, uri) = ReceiptFileHelper.createReceiptPhotoUri(context)
                                    pendingCameraFile = file
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }.onFailure {
                                    ReceiptFileHelper.deleteQuietly(pendingCameraFile)
                                    pendingCameraFile = null
                                    pendingCameraUri = null
                                    onScanError(uiStrings.fileReadError)
                                }
                            }
                            .testTag("receipt_scan_take_photo_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = uiStrings.scanTakePhoto,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = uiStrings.scanTakePhoto,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showSourceDialog = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("receipt_scan_gallery_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = uiStrings.scanChoosePhoto,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = uiStrings.scanChoosePhoto,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = uiStrings.offlinePrivacyNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text(uiStrings.cancelBtn)
                }
            }
        )
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = !isScanning) { showSourceDialog = true }
            .testTag("tx_scan_receipt_btn")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = uiStrings.scanReceiptBtn,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (isScanning) uiStrings.scanningReceiptLabel else uiStrings.scanReceiptBtn,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
