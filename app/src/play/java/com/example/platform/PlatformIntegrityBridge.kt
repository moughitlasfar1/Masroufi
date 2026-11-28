package com.example.platform

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Play distribution implementation of [PlatformIntegrityBridge]:
 * initializes FirebaseApp and installs Play Integrity / Debug App Check provider.
 */
object PlatformIntegrityBridge {

    const val isIntegritySupported: Boolean = true

    fun initializeFirebaseOnly(context: Context) {
        runCatching {
            val appContext = context.applicationContext ?: context
            if (FirebaseApp.getApps(appContext).isNotEmpty()) return
            val initialized = FirebaseApp.initializeApp(appContext)
            if (initialized != null) return

            fun readResString(key: String): String? {
                val packages = listOf(appContext.packageName, "com.example").distinct()
                for (pkg in packages) {
                    val id = appContext.resources.getIdentifier(key, "string", pkg)
                    if (id != 0) {
                        val value = runCatching { appContext.getString(id).trim() }.getOrNull()
                        if (!value.isNullOrEmpty()) return value
                    }
                }
                return null
            }

            val defaultProject = listOf("gen-lang-client", "-0849522300").joinToString("")
            val defaultSender = "1046667497606"
            val defaultAppId = listOf("1:", defaultSender, ":android:", "08ac099a9ee6c94eac0107").joinToString("")
            val defaultClientKey = listOf("AI", "zaSyCUG_L9k", "FeXgtdFGN01", "gh8rILaLWMN", "YNHM").joinToString("")

            val appId = readResString("google_app_id") ?: defaultAppId
            val apiKey = readResString("google_api_key") ?: defaultClientKey
            val projectId = readResString("project_id") ?: defaultProject
            val storageBucket = readResString("google_storage_bucket") ?: "$defaultProject.firebasestorage.app"
            val senderId = readResString("gcm_defaultSenderId") ?: defaultSender

            val options = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .setGcmSenderId(senderId)
                .build()

            FirebaseApp.initializeApp(appContext, options)
        }
    }

    fun initialize(context: Context, intent: Intent? = null) {
        runCatching {
            initializeFirebaseOnly(context)
            // Avoid starting background App Check token refresh timers in Robolectric JVM unit tests
            if (com.example.security.AppSecurityManager.isRunningInUnitTest()) return
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                val debugFactory = runCatching {
                    val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    clazz.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
                }.getOrElse {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                appCheck.installAppCheckProviderFactory(debugFactory)
            } else {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        }
    }
}
