package com.aurevon.velora.transcription

import java.io.File

/**
 * A word-level recognition result tied to the original recording timeline.
 * Times are milliseconds from the beginning of the audio file.
 */
data class RecognizedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float? = null
) {
    init {
        require(startMs >= 0) { "startMs must not be negative" }
        require(endMs >= startMs) { "endMs must be >= startMs" }
        require(confidence == null || confidence in 0f..1f) {
            "confidence must be between 0 and 1"
        }
    }
}

/**
 * A readable phrase assembled from recognized words.
 * Word timestamps remain available for precise evidence and later navigation.
 */
data class TranscriptSegment(
    val sequence: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<RecognizedWord>,
    val confidence: Float? = null
) {
    init {
        require(sequence >= 0)
        require(text.isNotBlank())
        require(startMs >= 0 && endMs >= startMs)
        require(words.all { it.startMs >= startMs && it.endMs <= endMs })
    }
}

data class TranscriptionResult(
    val recordingId: String,
    val languageTag: String,
    val segments: List<TranscriptSegment>,
    val engineId: String,
    val modelId: String,
    val completedAtEpochMs: Long
) {
    val fullText: String
        get() = segments.joinToString(separator = " ") { it.text }
}

sealed interface TranscriptionState {
    data object ModelMissing : TranscriptionState
    data object Ready : TranscriptionState
    data class Processing(val progressPercent: Int? = null) : TranscriptionState
    data class Completed(val result: TranscriptionResult) : TranscriptionState
    data class Failed(val reason: TranscriptionFailure) : TranscriptionState
}

enum class TranscriptionFailure {
    UNSUPPORTED_AUDIO,
    MODEL_LOAD_FAILED,
    DECODING_FAILED,
    RECOGNITION_FAILED,
    CANCELLED
}

/**
 * Implementations must process audio on-device unless a separate cloud provider
 * is explicitly selected and approved by the user.
 */
interface TranscriptionEngine {
    val engineId: String
    val modelId: String

    suspend fun transcribe(
        recordingId: String,
        audioFile: File,
        languageTag: String,
        onProgress: (Int?) -> Unit = {}
    ): TranscriptionResult
}
