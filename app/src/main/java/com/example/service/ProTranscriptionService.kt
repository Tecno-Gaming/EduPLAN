package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ProTranscriptionService
 * 
 * High-reliability Foreground Service with PARTIAL_WAKE_LOCK and continuous SpeechRecognizer loop.
 * Designed for up to 8 hours uninterrupted operation with on-device memory-safe real-time .md file streaming.
 */
class ProTranscriptionService : Service() {

    companion object {
        const val TAG = "ProTranscriptionService"
        const val CHANNEL_ID = "pro_transcription_channel"
        const val NOTIFICATION_ID = 8888
        
        const val ACTION_START_SERVICE = "com.example.service.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP"

        private val _isRecordingState = MutableStateFlow(false)
        val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

        private val _elapsedSecondsState = MutableStateFlow(0)
        val elapsedSecondsState: StateFlow<Int> = _elapsedSecondsState.asStateFlow()

        private val _lastTranscriptState = MutableStateFlow("")
        val lastTranscriptState: StateFlow<String> = _lastTranscriptState.asStateFlow()

        private val _currentFileNameState = MutableStateFlow<String?>(null)
        val currentFileNameState: StateFlow<String?> = _currentFileNameState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, ProTranscriptionService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProTranscriptionService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var bufferedWriter: BufferedWriter? = null
    private var activeFile: File? = null

    private var isListeningLoopActive = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var timerJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ProTranscriptionService created")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                if (!_isRecordingState.value) {
                    startForegroundWithWakeLock()
                    initRecordingAndFileStream()
                    startContinuousListeningLoop()
                }
            }
            ACTION_STOP_SERVICE -> {
                stopTranscriptionService()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pro Mod Transkripsiyon Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "8 saatlik kesintisiz ses transkripsiyonu ve arka plan dinleme servisi"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithWakeLock() {
        // 1. Acquire PARTIAL_WAKE_LOCK (8 hour timeout safeguard)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EduPLAN:ProTranscriptionWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(8 * 60 * 60 * 1000L) // max 8 hours
        }
        Log.d(TAG, "PARTIAL_WAKE_LOCK acquired")

        // 2. Start Foreground Notification
        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isRecordingState.value = true
        _elapsedSecondsState.value = 0

        // 3. Start Elapsed Seconds Timer
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isRecordingState.value) {
                delay(1000)
                val newElapsed = _elapsedSecondsState.value + 1
                _elapsedSecondsState.value = newElapsed

                // Update notification every 10 seconds
                if (newElapsed % 10 == 0) {
                    updateNotification(newElapsed)
                }

                // 8 hours safety auto-stop (8 * 3600 = 28800 seconds)
                if (newElapsed >= 8 * 3600) {
                    Log.d(TAG, "8 hours continuous transcription completed. Stopping service.")
                    mainHandler.post {
                        stopTranscriptionService()
                    }
                    break
                }
            }
        }
    }

    private fun buildNotification(elapsedSecs: Int): Notification {
        val hours = elapsedSecs / 3600
        val mins = (elapsedSecs % 3600) / 60
        val secs = elapsedSecs % 60
        val timeFormatted = String.format("%02d:%02d:%02d", hours, mins, secs)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ProTranscriptionService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EduPLAN Pro Mod Active 🎙️")
            .setContentText("Kesintisiz Transkripsiyon Devam Ediyor: $timeFormatted")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_media_pause, "Kaydı Durdur", pendingStopIntent)
            .build()
    }

    private fun updateNotification(elapsedSecs: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(elapsedSecs))
    }

    /**
     * Memory-Friendly File Writer Initialization:
     * Appends text directly to disk using BufferedWriter + flush().
     * Keeps 0 bytes of historical transcript in RAM!
     */
    private fun initRecordingAndFileStream() {
        try {
            val dir = File(filesDir, "pro_recordings")
            if (!dir.exists()) dir.mkdirs()

            val now = LocalDateTime.now()
            val timeStamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val fileName = "ProMod_$timeStamp.md"
            val file = File(dir, fileName)

            val startFormatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val headerContent = """
                # EduPLAN Pro Mod Transkripsiyon
                - **Başlangıç:** $startFormatted
                - **Durum:** 8 Saatlik Kesintisiz Dinleme Aktif
                
                ---
                
            """.trimIndent() + "\n\n"

            file.writeText(headerContent)

            activeFile = file
            _currentFileNameState.value = fileName
            bufferedWriter = BufferedWriter(FileWriter(file, true))
            Log.d(TAG, "Initialized Markdown FileWriter at: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing file stream: ${e.message}")
        }
    }

    /**
     * Appends a speech result directly to the disk .md file with timestamp header [HH:mm].
     * Calls flush() immediately to guarantee persistent disk writing without memory leaks.
     */
    @Synchronized
    private fun appendTextToDiskStream(text: String) {
        if (text.isBlank()) return
        try {
            val timeHeader = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val line = "[$timeHeader] $text\n\n"
            bufferedWriter?.write(line)
            bufferedWriter?.flush() // Immediate disk flush

            _lastTranscriptState.value = text
            Log.d(TAG, "Flushed to .md file: $line")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing speech to disk stream: ${e.message}")
        }
    }

    /**
     * Continuous Speech Recognizer Loop:
     * Catches SpeechRecognizer errors & end-of-speech events, immediately restarting the listening cycle.
     */
    private fun startContinuousListeningLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer is not available on this device!")
            return
        }

        isListeningLoopActive = true
        mainHandler.post {
            createAndStartSpeechRecognizer()
        }
    }

    private fun createAndStartSpeechRecognizer() {
        if (!isListeningLoopActive || !_isRecordingState.value) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        // Speech segment ended. Restart listener immediately.
                        restartListeningWithDelay(150)
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "SpeechRecognizer Error Code: $error")
                        // Ignore non-fatal speech timeouts/no-match and restart continuous loop instantly
                        if (isListeningLoopActive && _isRecordingState.value) {
                            restartListeningWithDelay(300)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""
                        if (spokenText.isNotBlank()) {
                            appendTextToDiskStream(spokenText)
                        }
                        // Continue continuous listening loop
                        if (isListeningLoopActive && _isRecordingState.value) {
                            restartListeningWithDelay(200)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            _lastTranscriptState.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "SpeechRecognizer startListening executed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in createAndStartSpeechRecognizer: ${e.message}")
            if (isListeningLoopActive && _isRecordingState.value) {
                restartListeningWithDelay(1000)
            }
        }
    }

    private fun restartListeningWithDelay(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isListeningLoopActive && _isRecordingState.value) {
                createAndStartSpeechRecognizer()
            }
        }, delayMs)
    }

    private fun stopTranscriptionService() {
        Log.d(TAG, "Stopping ProTranscriptionService...")
        isListeningLoopActive = false
        _isRecordingState.value = false

        mainHandler.removeCallbacksAndMessages(null)
        timerJob?.cancel()

        // 1. Destroy Speech Recognizer
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null

        // 2. Close & Flush File Stream with footer
        try {
            val endFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val footer = "\n---\n- **Bitiş:** $endFormatted\n- **Durum:** Kayıt Tamamlandı\n"
            bufferedWriter?.write(footer)
            bufferedWriter?.flush()
            bufferedWriter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing file stream: ${e.message}")
        }
        bufferedWriter = null
        activeFile = null

        // 3. Release WakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "PARTIAL_WAKE_LOCK released")
        }
        wakeLock = null

        // 4. Stop Foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {
        stopTranscriptionService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
