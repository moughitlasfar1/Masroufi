package com.example.platform

import android.content.Context
import android.content.Intent

/**
 * FOSS / F-Droid distribution implementation of [PlatformIntegrityBridge]:
 * pure no-op with zero Firebase or Play Integrity dependencies.
 */
object PlatformIntegrityBridge {

    const val isIntegritySupported: Boolean = false

    fun initializeFirebaseOnly(context: Context) {
        // No-op in FOSS distribution
    }

    fun initialize(context: Context, intent: Intent? = null) {
        // No-op in FOSS distribution
    }
}
