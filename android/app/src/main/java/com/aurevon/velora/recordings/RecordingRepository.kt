package com.aurevon.velora.recordings

import java.io.File

/**
 * Owns the on-disk catalogue of recordings and their sidecar metadata.
 * This class has no Android dependencies, so its file behavior is unit-testable.
 */
internal class RecordingRepository(private val directory: File) {

    fun listRecordings(): List<RecordingItem> {
        return (directory.listFiles()?.filter { it.isFile && it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList())
            .map { file ->
                val durationFile = durationFileFor(file)
                val duration = durationFile.takeIf { it.isFile }
                    ?.readText()?.trim()?.toLongOrNull()
                RecordingItem(file, duration)
            }
    }

    fun ensureDirectory(): File {
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    fun deleteRecording(file: File): Boolean {
        val parent = file.canonicalFile.parentFile ?: return false
        if (parent != directory.canonicalFile || file.extension != "m4a" || !file.isFile) {
            return false
        }
        val audioDeleted = file.delete()
        val metadataDeleted = durationFileFor(file).let { !it.exists() || it.delete() }
        return audioDeleted && metadataDeleted
    }

    private fun durationFileFor(file: File): File =
        File(file.parentFile, "${file.nameWithoutExtension}.duration")
}
