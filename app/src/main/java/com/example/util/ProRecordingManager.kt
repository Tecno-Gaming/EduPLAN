package com.example.util

import android.content.Context
import android.content.Intent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.service.ProTranscriptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProRecordFile(
    val fileName: String,
    val filePath: String,
    val createdTimeFormatted: String,
    val fileSizeBytes: Long,
    val contentPreview: String
)

class ProRecordingManager(private val context: Context) {

    val isRecording: StateFlow<Boolean> = ProTranscriptionService.isRecordingState
    val elapsedSeconds: StateFlow<Int> = ProTranscriptionService.elapsedSecondsState
    val currentFileName: StateFlow<String?> = ProTranscriptionService.currentFileNameState
    val lastRecognizedText: StateFlow<String> = ProTranscriptionService.lastTranscriptState

    val recordingsList = MutableStateFlow<List<ProRecordFile>>(emptyList())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val recordingsDir: File
        get() {
            val dir = File(context.filesDir, "pro_recordings")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        loadRecordings()

        // Observe recording state changes to refresh recordings list when stopped
        coroutineScope.launch {
            ProTranscriptionService.isRecordingState.collect { active ->
                if (!active) {
                    loadRecordings()
                }
            }
        }
    }

    fun loadRecordings() {
        val files = recordingsDir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()
        val list = files.map { file ->
            val content = try { file.readText() } catch (e: Exception) { "" }
            val created = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .format(LocalDateTime.ofEpochSecond(file.lastModified() / 1000, 0, java.time.ZoneOffset.UTC))
            ProRecordFile(
                fileName = file.name,
                filePath = file.absolutePath,
                createdTimeFormatted = created,
                fileSizeBytes = file.length(),
                contentPreview = content.take(200)
            )
        }.sortedByDescending { it.fileName }
        recordingsList.value = list
    }

    fun startRecording(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("ProRecordingManager", "Speech recognition not available")
            return false
        }

        ProTranscriptionService.startService(context)
        return true
    }

    fun stopRecording() {
        ProTranscriptionService.stopService(context)
        loadRecordings()
    }

    fun deleteRecording(fileName: String) {
        val file = File(recordingsDir, fileName)
        if (file.exists()) {
            file.delete()
        }
        loadRecordings()
    }

    fun getRecordingContent(fileName: String): String {
        val file = File(recordingsDir, fileName)
        return if (file.exists()) file.readText() else "Dosya bulunamadı."
    }
}
