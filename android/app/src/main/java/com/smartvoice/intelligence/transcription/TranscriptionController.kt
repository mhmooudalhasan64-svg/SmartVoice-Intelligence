package com.aurevon.velora.transcription

import java.io.File

/**
 * Coordinates on-device transcription without exposing engine construction to the UI.
 * The controller does not upload audio or select a cloud provider.
 */
class TranscriptionController(
    private val modelDirectory: File,
    private val cacheDirectory: File
) {
    suspend fun transcribe(
        recordingId: String,
        audioFile: File,
        languageTag: String = "ar",
        onProgress: (Int?) -> Unit = {}
    ): TranscriptionResult {
        val engine = VoskTranscriptionEngine(
            modelDirectory = modelDirectory,
            workDirectory = File(cacheDirectory, "transcription")
        )
        return engine.transcribe(
            recordingId = recordingId,
            audioFile = audioFile,
            languageTag = languageTag,
            onProgress = onProgress
        )
    }
}
