package com.example.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.security.AppSecurityManager
import com.example.ui.setLocalModeEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * FOSS / F-Droid distribution implementation of [PlatformCloudBridge]:
 * strictly local-only with zero Firebase, GMS, or Google Sign-In dependencies.
 */
object PlatformCloudBridge {

    const val isCloudSupported: Boolean = false
    const val distributionName: String = "foss"

    fun currentAuthenticatedUidOrNull(): String? = null

    fun observeAuthUserId(context: Context): Flow<String?> = flowOf(null)

    fun signOut(
        context: Context,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope,
        clearLocalCache: Boolean = AppSecurityManager.isClearCacheOnSignOutEnabled(context),
        isPinRecovery: Boolean = false
    ) {
        AppSecurityManager.clearSensitiveClipboard(context)
        setLocalModeEnabled(context, true)
        onSignOutComplete()
    }

    fun resolveWebClientId(context: Context): String = ""

    @Composable
    fun CloudAuthGateScreen(
        onAuthSuccess: () -> Unit,
        onContinueLocal: () -> Unit
    ) {
        LaunchedEffect(Unit) {
            onContinueLocal()
        }
    }
}
