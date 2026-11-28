package com.example.platform

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.masroufi.importer.data.ReceiptFileHelper
import com.masroufi.importer.domain.ParsedReceipt
import com.masroufi.importer.domain.ReceiptParser
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Play distribution implementation of [PlatformReceiptOcrBridge] using bundled offline ML Kit Latin OCR.
 */
object PlatformReceiptOcrBridge {

    const val isReceiptOcrSupported: Boolean = true

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }

    suspend fun scanReceiptFromUri(
        context: Context,
        uri: Uri,
        tempFileToDelete: File? = null
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context.applicationContext, uri)
            val result = recognizer.process(image).awaitResult()
            ReceiptParser.parse(result.text)
        } finally {
            runCatching { recognizer.close() }
            ReceiptFileHelper.deleteQuietly(tempFileToDelete)
        }
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).awaitResult()
            result.text
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
