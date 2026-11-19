package com.example.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DetectionLogEntry(
    val timeMillis: Long,
    val sourcePackage: String,
    val stage: String,
    val reasonCode: String,
    val messageLength: Int
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(Date(timeMillis))
    }
}

data class ListenerHealthState(
    val isConnected: Boolean = false,
    val lastConnectedMillis: Long = 0L,
    val lastDisconnectedMillis: Long = 0L,
    val lastEventMillis: Long = 0L
)

/**
 * Prompt 3: Privacy-safe diagnostic ring buffer (last 50 notification events).
 *
 * Privacy contract:
 * - NEVER stores notification titles, body text, amounts, or merchant names.
 * - Records ONLY timestamp, package name, pipeline stage, structured reason code, and character count.
 */
object MasroufiDetectionLog {
    const val MAX_ENTRIES = 50

    const val REASON_NOT_ALLOWED = "not_allowed"
    const val REASON_PAUSED = "paused"
    const val REASON_ONGOING = "ongoing"
    const val REASON_GROUP_SUMMARY = "group_summary"
    const val REASON_EMPTY = "empty_text"
    const val REASON_OTP = "otp"
    const val REASON_NO_AMOUNT = "no_amount"
    const val REASON_NO_KEYWORD = "no_keyword"
    const val REASON_DUPLICATE = "duplicate"
    const val REASON_IGNORED_MERCHANT = "ignored_merchant"
    const val REASON_OLD_THREAD_MESSAGE = "old_thread_msg"
    const val REASON_STORED = "stored"
    const val REASON_AUTO_CONFIRMED = "auto_confirmed"

    private val lock = Any()
    private val ringBuffer = ArrayDeque<DetectionLogEntry>(MAX_ENTRIES + 1)

    private val _entriesFlow = MutableStateFlow<List<DetectionLogEntry>>(emptyList())
    val entriesFlow: StateFlow<List<DetectionLogEntry>> = _entriesFlow.asStateFlow()

    private val _healthFlow = MutableStateFlow(ListenerHealthState())
    val healthFlow: StateFlow<ListenerHealthState> = _healthFlow.asStateFlow()

    fun record(
        sourcePackage: String,
        stage: String,
        reasonCode: String,
        messageLength: Int,
        timeMillis: Long = System.currentTimeMillis()
    ) {
        val safePkg = sourcePackage.take(80)
        val entry = DetectionLogEntry(
            timeMillis = timeMillis,
            sourcePackage = safePkg,
            stage = stage,
            reasonCode = reasonCode,
            messageLength = messageLength.coerceAtLeast(0)
        )
        synchronized(lock) {
            ringBuffer.addFirst(entry)
            while (ringBuffer.size > MAX_ENTRIES) {
                ringBuffer.removeLast()
            }
            _entriesFlow.value = ringBuffer.toList()
            _healthFlow.value = _healthFlow.value.copy(lastEventMillis = timeMillis)
        }
    }

    fun markListenerConnected(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            _healthFlow.value = _healthFlow.value.copy(
                isConnected = true,
                lastConnectedMillis = nowMillis
            )
        }
    }

    fun markListenerDisconnected(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            _healthFlow.value = _healthFlow.value.copy(
                isConnected = false,
                lastDisconnectedMillis = nowMillis
            )
        }
    }

    fun snapshotEntries(): List<DetectionLogEntry> = synchronized(lock) { ringBuffer.toList() }

    fun snapshotHealth(): ListenerHealthState = synchronized(lock) { _healthFlow.value }

    fun clear() {
        synchronized(lock) {
            ringBuffer.clear()
            _entriesFlow.value = emptyList()
        }
    }
}
