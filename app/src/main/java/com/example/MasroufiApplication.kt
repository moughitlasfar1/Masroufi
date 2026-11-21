package com.example

import android.app.Application
import com.example.security.AppSecurityManager

/**
 * Custom Application entry point ensuring SQLCipher native libraries are loaded
 * before any local encrypted Room database access occurs.
 */
class MasroufiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureSqlCipherLoaded()
    }

    companion object {
        @Volatile
        private var nativeLoaded: Boolean = false

        val isSqlCipherNativeLoaded: Boolean
            get() = nativeLoaded

        /**
         * Loads `libsqlcipher.so` via [System.loadLibrary] once per process.
         * In local JVM / Robolectric unit tests (where Android native `.so` binaries
         * are absent on the host OS), safely returns `false` without throwing.
         */
        fun ensureSqlCipherLoaded(): Boolean {
            if (nativeLoaded) return true
            return synchronized(this) {
                if (nativeLoaded) return true
                try {
                    System.loadLibrary("sqlcipher")
                    nativeLoaded = true
                    true
                } catch (e: UnsatisfiedLinkError) {
                    if (AppSecurityManager.isRunningInUnitTest()) {
                        false
                    } else {
                        throw e
                    }
                }
            }
        }
    }
}
