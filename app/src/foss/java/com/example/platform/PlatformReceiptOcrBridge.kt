package com.example.platform

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.masroufi.importer.data.ReceiptFileHelper
import com.masroufi.importer.domain.ParsedReceipt
import java.io.File

/**
 * FOSS / F-Droid distribution implementation of [PlatformReceiptOcrBridge]:
 * zero ML Kit / Play Services dependencies; Receipt Photo OCR is disabled and hidden in UI,
 * while CSV and digital bank PDF (PDFBox) imports remain 100% functional.
 */
object PlatformReceiptOcrBridge {

    const val isReceiptOcrSupported: Boolean = false

    suspend fun scanReceiptFromUri(
        context: Context,
        uri: Uri,
        tempFileToDelete: File? = null
    ): ParsedReceipt {
        ReceiptFileHelper.deleteQuietly(tempFileToDelete)
        return ParsedReceipt(
            totalAmountMad = null,
            isoDate = null,
            merchantName = null,
            rawLineCount = 0
        )
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = ""
}
