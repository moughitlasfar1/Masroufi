package com.example.lan

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LanCompanionError {
    NONE,
    WIFI_REQUIRED,
    START_FAILED
}

data class LanCompanionState(
    val isRunning: Boolean = false,
    val url: String = "",
    val bindHost: String = "",
    val port: Int = 0,
    val pin: String = "",
    val activeSessionsCount: Int = 0,
    val idleTimeoutMinutes: Int = LanAuthSessionManager.DEFAULT_IDLE_TIMEOUT_MINUTES,
    val error: LanCompanionError = LanCompanionError.NONE
)

/**
 * Process-wide in-memory controller for Masroufi's opt-in Local LAN Companion.
 *
 * Security invariants:
 * - Off by default; starts only on explicit user opt-in.
 * - Never persists PINs, Bearer tokens, or active URLs to disk or SharedPreferences.
 */
object LanCompanionController {

    private val _state = MutableStateFlow(LanCompanionState())
    val state: StateFlow<LanCompanionState> = _state.asStateFlow()

    @Volatile
    private var activeAuthManager: LanAuthSessionManager? = null

    fun start(
        context: Context,
        userId: String,
        idleTimeoutMinutes: Int = _state.value.idleTimeoutMinutes
    ) {
        val appContext = context.applicationContext
        val wifiIp = LanNetworkUtils.findActiveWifiLanIpv4(appContext)
        if (wifiIp == null) {
            _state.update {
                it.copy(
                    isRunning = false,
                    url = "",
                    bindHost = "",
                    port = 0,
                    pin = "",
                    activeSessionsCount = 0,
                    error = LanCompanionError.WIFI_REQUIRED
                )
            }
            return
        }

        val clampedIdle = idleTimeoutMinutes.coerceIn(
            LanAuthSessionManager.MIN_IDLE_TIMEOUT_MINUTES,
            LanAuthSessionManager.MAX_IDLE_TIMEOUT_MINUTES
        )
        _state.update { it.copy(idleTimeoutMinutes = clampedIdle, error = LanCompanionError.NONE) }

        val intent = Intent(appContext, LanCompanionService::class.java).apply {
            action = LanCompanionService.ACTION_START
            putExtra(LanCompanionService.EXTRA_USER_ID, userId)
            putExtra(LanCompanionService.EXTRA_IDLE_TIMEOUT_MINUTES, clampedIdle)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, LanCompanionService::class.java).apply {
            action = LanCompanionService.ACTION_STOP
        }
        runCatching { appContext.startService(intent) }
        runCatching { appContext.stopService(Intent(appContext, LanCompanionService::class.java)) }
        onServiceStopped()
    }

    fun regeneratePin() {
        val manager = activeAuthManager ?: return
        val freshPin = manager.regeneratePin()
        _state.update {
            it.copy(
                pin = freshPin,
                activeSessionsCount = manager.activeSessionsCount()
            )
        }
    }

    fun setIdleTimeoutMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(
            LanAuthSessionManager.MIN_IDLE_TIMEOUT_MINUTES,
            LanAuthSessionManager.MAX_IDLE_TIMEOUT_MINUTES
        )
        activeAuthManager?.setIdleTimeoutMinutes(clamped)
        _state.update { it.copy(idleTimeoutMinutes = clamped) }
    }

    internal fun attachAuthManager(manager: LanAuthSessionManager) {
        activeAuthManager = manager
    }

    internal fun onServiceRunning(
        bindHost: String,
        port: Int,
        url: String,
        pin: String,
        idleTimeoutMinutes: Int
    ) {
        _state.value = LanCompanionState(
            isRunning = true,
            url = url,
            bindHost = bindHost,
            port = port,
            pin = pin,
            activeSessionsCount = activeAuthManager?.activeSessionsCount() ?: 0,
            idleTimeoutMinutes = idleTimeoutMinutes,
            error = LanCompanionError.NONE
        )
    }

    internal fun onSessionsChanged() {
        val count = activeAuthManager?.activeSessionsCount() ?: 0
        _state.update { it.copy(activeSessionsCount = count) }
    }

    internal fun onServiceError(error: LanCompanionError) {
        activeAuthManager?.wipeAll()
        activeAuthManager = null
        _state.update {
            LanCompanionState(
                isRunning = false,
                idleTimeoutMinutes = it.idleTimeoutMinutes,
                error = error
            )
        }
    }

    internal fun onServiceStopped() {
        activeAuthManager?.wipeAll()
        activeAuthManager = null
        _state.update {
            LanCompanionState(
                isRunning = false,
                idleTimeoutMinutes = it.idleTimeoutMinutes,
                error = LanCompanionError.NONE
            )
        }
    }
}
