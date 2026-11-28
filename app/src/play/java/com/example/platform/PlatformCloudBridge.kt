package com.example.platform

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.security.AppSecurityManager
import com.example.ui.AuthScreen
import com.example.ui.resolveDefaultWebClientId
import com.example.ui.signOutUser
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Play distribution implementation of [PlatformCloudBridge]: backs optional Google Sign-In,
 * Firebase Auth observation, and cloud sign-out/cache clearing.
 */
object PlatformCloudBridge {

    const val isCloudSupported: Boolean = true
    const val distributionName: String = "play"

    fun currentAuthenticatedUidOrNull(): String? {
        return runCatching { Firebase.auth.currentUser?.uid }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun observeAuthUserId(context: Context): Flow<String?> = callbackFlow {
        PlatformIntegrityBridge.initializeFirebaseOnly(context)
        val auth = runCatching { Firebase.auth }.getOrNull()
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        trySend(auth.currentUser?.uid)
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun signOut(
        context: Context,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope,
        clearLocalCache: Boolean = AppSecurityManager.isClearCacheOnSignOutEnabled(context),
        isPinRecovery: Boolean = false
    ) {
        signOutUser(
            context = context,
            onSignOutComplete = onSignOutComplete,
            scope = scope,
            clearLocalCache = clearLocalCache,
            isPinRecovery = isPinRecovery
        )
    }

    fun resolveWebClientId(context: Context): String = resolveDefaultWebClientId(context)

    @Composable
    fun CloudAuthGateScreen(
        onAuthSuccess: () -> Unit,
        onContinueLocal: () -> Unit
    ) {
        AuthScreen(
            onAuthSuccess = onAuthSuccess,
            onContinueLocal = onContinueLocal
        )
    }
}
