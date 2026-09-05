package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceListeningManager(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onSpeechRecognized: (String) -> Unit,
    private val onErrorState: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isWakeWordTriggered = false
    private val handler = Handler(Looper.getMainLooper())

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorState("Cihazınızda ses tanıma özelliği desteklenmiyor.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d("VoiceListeningManager", "Speech error code: $error")
                        if (isListening) {
                            restartListeningDelay()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""

                        if (spokenText.isNotEmpty()) {
                            val lower = spokenText.lowercase(Locale("tr", "TR"))
                            if (!isWakeWordTriggered && lower.contains("ödev")) {
                                isWakeWordTriggered = true
                                onWakeWordDetected()
                            }
                            if (isWakeWordTriggered || lower.contains("ödev")) {
                                onSpeechRecognized(spokenText)
                            }
                        }

                        if (isListening) {
                            restartListeningDelay()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = matches?.firstOrNull() ?: ""
                        if (partialText.isNotEmpty()) {
                            val lower = partialText.lowercase(Locale("tr", "TR"))
                            if (!isWakeWordTriggered && lower.contains("ödev")) {
                                isWakeWordTriggered = true
                                onWakeWordDetected()
                            }
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
            isListening = true
        } catch (e: Exception) {
            Log.e("VoiceListeningManager", "Error starting listening: ${e.message}")
            onErrorState("Ses dinleme başlatılamadı: ${e.message}")
        }
    }

    private fun restartListeningDelay() {
        handler.postDelayed({
            if (isListening) {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.e("VoiceListeningManager", "Error restarting: ${e.message}")
                }
            }
        }, 1200)
    }

    fun stopListening() {
        isListening = false
        isWakeWordTriggered = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceListeningManager", "Error stopping: ${e.message}")
        }
        speechRecognizer = null
    }
}
