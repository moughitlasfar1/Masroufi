package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device Moonshine voice controller providing:
 * - Low-latency offline-preferred speech recognition (Darija, Arabic, French, English)
 * - Real-time 16-bar acoustic waveform levels from RMS dB / PCM envelope
 * - Partial & final streaming transcripts for live multi-expense & Q&A parsing
 * - On-device TextToSpeech reply synthesis for hands-free budget questions
 */
class MoonshineVoiceController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val BAR_COUNT = 16
        private val IDLE_BARS = List(BAR_COUNT) { 0.14f }

        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _waveformLevels = MutableStateFlow(IDLE_BARS)
    val waveformLevels: StateFlow<List<Float>> = _waveformLevels.asStateFlow()

    private val _isCancelZoneActive = MutableStateFlow(false)
    val isCancelZoneActive: StateFlow<Boolean> = _isCancelZoneActive.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var waveformJob: Job? = null
    private var pcmJob: Job? = null
    private var latestRmsNormalized: Float = 0.22f
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    fun startListening(language: String) {
        _isCancelZoneActive.value = false
        _isListening.value = true
        latestRmsNormalized = 0.28f

        startWaveformAnimator()

        if (!hasRecordAudioPermission(context)) {
            return
        }

        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        latestRmsNormalized = 0.32f
                    }

                    override fun onBeginningOfSpeech() {
                        latestRmsNormalized = 0.55f
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Typical speech rmsdB is -2f..10f
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1.0f)
                        latestRmsNormalized = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        latestRmsNormalized = 0.18f
                    }

                    override fun onError(error: Int) {
                        latestRmsNormalized = 0.20f
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        if (best.isNotBlank()) {
                            _liveTranscript.value = best
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _liveTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val speechLocale = when (language) {
                    "darija", "ar" -> "ar-MA"
                    "en" -> "en-US"
                    else -> "fr-FR"
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(intent)
            } else {
                startPcmRmsMeter()
            }
        }.onFailure {
            startPcmRmsMeter()
        }
    }

    fun updateTranscriptManual(text: String) {
        _liveTranscript.value = text
        latestRmsNormalized = 0.72f
    }

    fun setCancelZoneActive(active: Boolean) {
        _isCancelZoneActive.value = active
    }

    fun stopAndConsumeTranscript(): String {
        val finalTranscript = _liveTranscript.value.trim()
        stopInternal()
        return finalTranscript
    }

    fun cancelListening() {
        _liveTranscript.value = ""
        _isCancelZoneActive.value = false
        stopInternal()
    }

    private fun stopInternal() {
        _isListening.value = false
        waveformJob?.cancel()
        waveformJob = null
        pcmJob?.cancel()
        pcmJob = null
        _waveformLevels.value = IDLE_BARS
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun startWaveformAnimator() {
        waveformJob?.cancel()
        waveformJob = scope.launch(Dispatchers.Main) {
            var tick = 0
            while (isActive && _isListening.value) {
                val baseAmp = latestRmsNormalized.coerceIn(0.16f, 1.0f)
                val bars = List(BAR_COUNT) { idx ->
                    val centerWeight = 1f - (abs(idx - (BAR_COUNT - 1) / 2f) / (BAR_COUNT / 2f)) * 0.45f
                    val waveA = sin((tick * 0.42f) + (idx * 0.65f)) * 0.24f
                    val waveB = cos((tick * 0.27f) - (idx * 0.9f)) * 0.16f
                    val level = (baseAmp * centerWeight) + waveA + waveB
                    level.coerceIn(0.12f, 0.98f)
                }
                _waveformLevels.value = bars
                // Gradually decay amplitude toward a gentle breathing floor until next speech frame
                latestRmsNormalized = max(0.22f, latestRmsNormalized * 0.93f)
                tick++
                delay(45L)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startPcmRmsMeter() {
        if (!hasRecordAudioPermission(context)) return
        pcmJob?.cancel()
        pcmJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@launch
            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            }.getOrNull() ?: return@launch

            val buffer = ShortArray(min(minBuf, 1024))
            try {
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    while (isActive && _isListening.value) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                val sample = buffer[i].toDouble() / 32768.0
                                sumSq += sample * sample
                            }
                            val rms = sqrt(sumSq / read).toFloat()
                            latestRmsNormalized = (rms * 5.5f).coerceIn(0.18f, 1.0f)
                        }
                        delay(40L)
                    }
                }
            } catch (_: Exception) {
                // Ignore hardware contention in emulator/unit test environments
            } finally {
                runCatching {
                    recorder.stop()
                    recorder.release()
                }
            }
        }
    }

    fun speakAnswer(text: String, language: String, hideAmounts: Boolean) {
        if (hideAmounts || text.isBlank()) return
        val targetLocale = when (language) {
            "ar", "darija" -> Locale("ar", "MA")
            "en" -> Locale.US
            else -> Locale.FRANCE
        }
        runCatching {
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        tts?.language = targetLocale
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "moonshine_qa_utt")
                    }
                }
            } else if (ttsReady) {
                tts?.language = targetLocale
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "moonshine_qa_utt")
            }
        }
    }

    fun shutdown() {
        stopInternal()
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
        }
    }
}
