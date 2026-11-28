package com.example.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.data.LocalRoomMasroufiDataSource
import com.example.data.MasroufiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Opt-in Foreground Service hosting Masroufi's Local Wi-Fi LAN Companion (`ktor-server-cio`).
 *
 * Security & Android 14+ Foreground Service guarantees:
 * - Declared `android:exported="false"` and `android:foregroundServiceType="dataSync"`.
 * - Off by default; never starts on boot.
 * - Binds strictly to the active private RFC1918 Wi-Fi IPv4 address (never `0.0.0.0` or cellular).
 * - Persistent notification shows ONLY the local URL + Stop action; NEVER includes the PIN or amounts,
 *   uses `VISIBILITY_PRIVATE` + PIN-free/URL-free `publicVersion` on lock screen.
 * - Automatically shuts down on idle timeout (default 15 min) or Wi-Fi disconnect, wiping all
 *   in-memory PINs and session tokens.
 */
class LanCompanionService : Service() {

    companion object {
        private const val TAG = "LanCompanionService"
        const val CHANNEL_ID = "masroufi_lan_companion"
        const val NOTIFICATION_ID = 7201
        const val ACTION_START = "com.masroufi.app.action.LAN_COMPANION_START"
        const val ACTION_STOP = "com.masroufi.app.action.LAN_COMPANION_STOP"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_IDLE_TIMEOUT_MINUTES = "extra_idle_timeout_minutes"
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L

        /**
         * Builds the persistent foreground notification for the Local LAN Companion.
         * Security invariant: NEVER includes the session PIN or any financial amounts.
         */
        fun buildForegroundNotification(
            context: Context,
            localUrl: String
        ): Notification {
            ensureNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                7202,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(context, LanCompanionService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                context,
                7203,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = context.getString(R.string.app_name) + " · LAN"
            val contentText = if (localUrl.isNotBlank()) localUrl else "Local Wi-Fi server active"

            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText("Local Wi-Fi Companion active")
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(contentText)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setContentIntent(openPendingIntent)
                .addAction(0, "Stop", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Local LAN Companion",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Active while Masroufi Local Wi-Fi Companion is running"
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: LanCompanionServer? = null
    private var authManager: LanAuthSessionManager? = null
    private var idleWatchdogJob: Job? = null
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownAndStopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val userId = intent?.getStringExtra(EXTRA_USER_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: MasroufiRepository.LOCAL_DEVICE_USER_ID
                val idleMinutes = intent?.getIntExtra(
                    EXTRA_IDLE_TIMEOUT_MINUTES,
                    LanAuthSessionManager.DEFAULT_IDLE_TIMEOUT_MINUTES
                ) ?: LanAuthSessionManager.DEFAULT_IDLE_TIMEOUT_MINUTES

                startCompanionServer(userId, idleMinutes)
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startCompanionServer(userId: String, idleMinutes: Int) {
        // Call startForeground immediately to satisfy Android 14+ foreground service SLA
        val initialNotif = buildForegroundNotification(this, "")
        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotif, fgsType)
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Unable to enter foreground: ${t.javaClass.simpleName}")
            }
            LanCompanionController.onServiceError(LanCompanionError.START_FAILED)
            stopSelf()
            return
        }

        val wifiIp = LanNetworkUtils.findActiveWifiLanIpv4(this)
        if (wifiIp == null) {
            LanCompanionController.onServiceError(LanCompanionError.WIFI_REQUIRED)
            shutdownAndStopSelf()
            return
        }

        // Clean any prior server instance before binding
        stopServerOnly()

        val manager = LanAuthSessionManager(initialIdleTimeoutMinutes = idleMinutes)
        val dataSource = LocalRoomMasroufiDataSource(
            context = applicationContext,
            userIdProvider = { userId }
        )
        val assetProvider = LanAssetProvider { name ->
            val safeName = name.substringAfterLast('/')
            if (safeName.isEmpty() || safeName.contains("..")) return@LanAssetProvider null
            runCatching {
                assets.open("lan_web/$safeName").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
        }

        val newServer = LanCompanionServer(
            dataSource = dataSource,
            userId = userId,
            authManager = manager,
            assetProvider = assetProvider,
            onStateChanged = { LanCompanionController.onSessionsChanged() }
        )

        try {
            val url = newServer.start(
                host = wifiIp,
                preferredPort = LanCompanionServer.DEFAULT_PORT,
                allowLoopbackForTests = false
            )
            server = newServer
            authManager = manager
            LanCompanionController.attachAuthManager(manager)
            LanCompanionController.onServiceRunning(
                bindHost = newServer.bindHost,
                port = newServer.boundPort,
                url = url,
                pin = manager.currentPin(),
                idleTimeoutMinutes = manager.idleTimeoutMinutes()
            )

            // Update foreground notification with local URL (NEVER includes PIN)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildForegroundNotification(this, url))

            registerWifiDisconnectMonitor()
            startIdleWatchdog(manager)
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "LAN Companion failed to start: ${t.javaClass.simpleName}")
            }
            LanCompanionController.onServiceError(LanCompanionError.START_FAILED)
            shutdownAndStopSelf()
        }
    }

    private fun registerWifiDisconnectMonitor() {
        unregisterWifiMonitor()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (!LanNetworkUtils.isWifiConnected(this@LanCompanionService)) {
                    shutdownAndStopSelf()
                }
            }
        }
        runCatching {
            cm.registerNetworkCallback(request, cb)
            wifiCallback = cb
        }
    }

    private fun unregisterWifiMonitor() {
        val cb = wifiCallback ?: return
        wifiCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    private fun startIdleWatchdog(manager: LanAuthSessionManager) {
        idleWatchdogJob?.cancel()
        idleWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                if (manager.isIdleExpired()) {
                    shutdownAndStopSelf()
                    break
                }
            }
        }
    }

    private fun stopServerOnly() {
        idleWatchdogJob?.cancel()
        idleWatchdogJob = null
        unregisterWifiMonitor()
        runCatching { server?.stop() }
        server = null
        authManager?.wipeAll()
        authManager = null
    }

    private fun shutdownAndStopSelf() {
        stopServerOnly()
        LanCompanionController.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopServerOnly()
        LanCompanionController.onServiceStopped()
        serviceScope.cancel()
        super.onDestroy()
    }
}
