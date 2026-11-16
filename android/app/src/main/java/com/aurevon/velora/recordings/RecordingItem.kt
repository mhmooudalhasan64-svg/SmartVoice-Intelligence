package com.aurevon.velora.recordings

import java.io.File

/**
 * UI-ready metadata for one locally stored audio recording.
 */
internal data class RecordingItem(
    val file: File,
    val durationSeconds: Long?
)
