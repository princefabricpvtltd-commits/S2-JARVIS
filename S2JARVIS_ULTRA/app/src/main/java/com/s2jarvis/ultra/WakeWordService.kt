package com.s2jarvis.ultra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Foreground speech-based wake-word listener. This is a practical prototype,
 * not a low-power DSP wake-word engine. Android/provider behavior varies by device.
 */
class WakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var handlingWake = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            val notification = buildNotification("Listening for ‘Hey S2’")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startRecognition()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "S2 JARVIS Wake Word", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_WAKE_DETECTED, handlingWake)
        val pending = PendingIntent.getActivity(this, 71, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("S2 JARVIS")
            .setContentText(message)
            .setContentIntent(pending)
            .setOngoing(!handlingWake)
            .build()
    }

    private fun startRecognition() {
        if (!running || handlingWake) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable on this device")
            handler.postDelayed({ startRecognition() }, 5000)
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { scheduleRestart(900) }
                override fun onResults(results: android.os.Bundle?) {
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val normalized = said.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
                    val wake = normalized.contains("hey s2") || normalized.contains("hey s two") ||
                        normalized.contains("hey ess two") || normalized.contains("hey s too") ||
                        said.contains("हे एस टू") || said.contains("हे एस2")
                    if (wake) onWakeDetected() else scheduleRestart(350)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val said = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (said.contains("hey", ignoreCase = true) && (said.contains("s2", true) || said.contains("ess two", true))) onWakeDetected()
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
            val languageIndex = getSharedPreferences("s2_jarvis", MODE_PRIVATE).getInt("voice_language_index", 0)
            val language = when (languageIndex) { 1 -> "en-IN"; 2 -> "pa-IN"; else -> "hi-IN" }
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            scheduleRestart(2000)
        }
    }

    private fun scheduleRestart(delay: Long) {
        if (running && !handlingWake) handler.postDelayed({ startRecognition() }, delay)
    }

    private fun onWakeDetected() {
        if (handlingWake) return
        handlingWake = true
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        sendBroadcast(Intent(ACTION_WAKE_WORD).setPackage(packageName))
        val notification = buildNotification("‘Hey S2’ detected — tap to speak")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        stopForeground(false)
        stopSelf()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_WAKE_WORD = "com.s2jarvis.ultra.WAKE_WORD"
        const val ACTION_STOP = "com.s2jarvis.ultra.STOP_WAKE"
        const val EXTRA_WAKE_DETECTED = "wake_detected"
        private const val CHANNEL_ID = "s2_wake_word"
        private const val NOTIFICATION_ID = 2202
    }
}
