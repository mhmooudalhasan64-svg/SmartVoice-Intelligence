package com.aurevon.velora.recordings

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Coordinates Android audio resources and delegates recording-file operations
 * to RecordingRepository. UI state and permission handling remain in Compose.
 */
internal class RecordingController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var player: MediaPlayer? = null
    private val repository = RecordingRepository(File(context.filesDir, "recordings"))

    fun listRecordings(): List<RecordingItem> = repository.listRecordings()

    fun deleteRecording(file: File): Boolean = repository.deleteRecording(file)

    fun startRecording(): Boolean {
        var newRecorder: MediaRecorder? = null
        var outputFile: File? = null
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_START)
            )
            val directory = repository.ensureDirectory()
            val file = File(directory, "SV_${System.currentTimeMillis()}.m4a")
            outputFile = file
            newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(128000)
            newRecorder.setAudioSamplingRate(44100)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            activeFile = file
            true
        } catch (_: Exception) {
            try { newRecorder?.release() } catch (_: Exception) { }
            outputFile?.delete()
            recorder = null
            activeFile = null
            stopRecordingForegroundService()
            false
        }
    }

    fun stopRecording(elapsedSeconds: Long): StopRecordingResult {
        val currentRecorder = recorder
        val file = activeFile
        var saved = false
        try {
            currentRecorder?.stop()
            file?.let {
                File(it.parentFile, "${it.nameWithoutExtension}.duration")
                    .writeText(elapsedSeconds.toString())
            }
            saved = true
        } catch (_: RuntimeException) {
            file?.let {
                it.delete()
                File(it.parentFile, "${it.nameWithoutExtension}.duration").delete()
            }
        } finally {
            try { currentRecorder?.reset() } catch (_: Exception) { }
            try { currentRecorder?.release() } catch (_: Exception) { }
            recorder = null
            activeFile = null
            stopRecordingForegroundService()
        }
        return if (saved) StopRecordingResult.Saved else StopRecordingResult.TooShort
    }

    fun play(file: File, onCompletion: () -> Unit): Boolean {
        stopPlayback()
        return try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    try { it.release() } catch (_: Exception) { }
                    if (player === it) player = null
                    onCompletion()
                }
                prepare()
                start()
            }
            player = mediaPlayer
            true
        } catch (_: Exception) {
            stopPlayback()
            false
        }
    }

    fun stopPlayback() {
        val currentPlayer = player
        player = null
        try { currentPlayer?.stop() } catch (_: Exception) { }
        try { currentPlayer?.release() } catch (_: Exception) { }
    }

    fun release() {
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        activeFile = null
        stopPlayback()
        stopRecordingForegroundService()
    }

    private fun stopRecordingForegroundService() {
        try {
            context.startService(
                Intent(context, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_STOP)
            )
        } catch (_: Exception) { }
    }
}

internal enum class StopRecordingResult {
    Saved,
    TooShort
}
