package com.aurevon.velora.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Installs the official Vosk Arabic MGB-2 model.
 *
 * The .part archive is deliberately retained after network failures. HTTP Range
 * requests resume from its current length, so WorkManager retries do not restart
 * a large download from zero. A completion marker protects a fully downloaded
 * archive if the process is killed while extraction is running.
 */
class ArabicModelInstaller(private val filesDirectory: File) {
    companion object {
        const val MODEL_ID = "vosk-model-ar-mgb2-0.4"
        const val DOWNLOAD_SIZE_LABEL = "حوالي 318 MB"
        private const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"
        private const val MAX_ARCHIVE_BYTES = 700L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 1_200L * 1024L * 1024L
    }

    val installedDirectory: File
        get() = File(filesDirectory, "speech-models/$MODEL_ID")

    fun isInstalled(): Boolean =
        File(installedDirectory, "am/final.mdl").isFile &&
            File(installedDirectory, "conf/mfcc.conf").isFile &&
            File(installedDirectory, "graph/words.txt").isFile

    suspend fun install(onProgress: (downloadPercent: Int?) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            check(!isInstalled()) { "Arabic model is already installed" }
            val root = File(filesDirectory, "speech-models")
            check(root.exists() || root.mkdirs()) { "Could not create model directory" }
            val archive = File(root, "$MODEL_ID.zip.part")
            val completeMarker = File(root, "$MODEL_ID.zip.complete")
            val staging = File(root, "$MODEL_ID.installing")
            val destination = installedDirectory

            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not create model staging directory" }

            var archiveReady = completeMarker.isFile && archive.isFile
            try {
                if (!archiveReady) {
                    downloadArchive(archive, onProgress)
                    check(archive.isFile && archive.length() > 0L) {
                        "Downloaded model archive is empty"
                    }
                    check(completeMarker.writeText("complete").let { true }) {
                        "Could not mark completed model download"
                    }
                    archiveReady = true
                }

                var extractedBytes = 0L
                val stagingCanonical = staging.canonicalFile
                ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val safeName = entry.name.replace('\\\\', '/')
                        check(!safeName.startsWith("/") && !safeName.split('/').any { it == ".." }) {
                            "Unsafe model archive entry"
                        }
                        val output = File(staging, safeName).canonicalFile
                        check(output.path.startsWith(stagingCanonical.path + File.separator)) {
                            "Model archive path escapes install directory"
                        }
                        if (entry.isDirectory) {
                            check(output.exists() || output.mkdirs()) {
                                "Could not create model directory entry"
                            }
                        } else {
                            check(output.parentFile?.exists() == true || output.parentFile?.mkdirs() == true) {
                                "Could not create model extraction directory"
                            }
                            FileOutputStream(output).use { fileOut ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    extractedBytes += count
                                    check(extractedBytes <= MAX_EXTRACTED_BYTES) {
                                        "Extracted model exceeds size limit"
                                    }
                                    fileOut.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }

                val modelRoot = findModelRoot(staging)
                check(File(modelRoot, "am/final.mdl").isFile) { "Model archive is incomplete" }
                check(File(modelRoot, "conf/mfcc.conf").isFile) { "Model configuration is missing" }
                check(File(modelRoot, "graph/words.txt").isFile) { "Model vocabulary is missing" }

                destination.deleteRecursively()
                check(modelRoot.renameTo(destination)) { "Could not finalize model installation" }
                archive.delete()
                completeMarker.delete()
                onProgress(100)
                destination
            } catch (error: Exception) {
                // Preserve only an incomplete network download. A completed but
                // invalid ZIP must be discarded so a retry can fetch it again.
                if (archiveReady) {
                    archive.delete()
                    completeMarker.delete()
                }
                throw error
            } finally {
                staging.deleteRecursively()
            }
        }

    private fun downloadArchive(
        archive: File,
        onProgress: (downloadPercent: Int?) -> Unit
    ) {
        var existingBytes = archive.length()
        if (existingBytes > MAX_ARCHIVE_BYTES) {
            archive.delete()
            existingBytes = 0L
        }

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        try {
            connection.connect()
            val response = connection.responseCode
            if (response == HttpURLConnection.HTTP_REQUESTED_RANGE_NOT_SATISFIABLE) {
                archive.delete()
                throw IOException("The server rejected the resume range; restarting download")
            }

            val append = existingBytes > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (response != HttpURLConnection.HTTP_OK && !append) {
                throw IOException("Model download failed: HTTP $response")
            }

            if (!append) existingBytes = 0L
            val rangeTotal = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull()
            val contentLength = connection.contentLengthLong
            val expectedLength = if (append) {
                rangeTotal ?: if (contentLength > 0L) existingBytes + contentLength else -1L
            } else {
                contentLength
            }
            check(expectedLength <= 0L || expectedLength <= MAX_ARCHIVE_BYTES) {
                "Unexpected model archive size"
            }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(archive, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = existingBytes
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        check(downloaded <= MAX_ARCHIVE_BYTES) {
                            "Model archive exceeds size limit"
                        }
                        output.write(buffer, 0, count)
                        val percent = if (expectedLength > 0L) {
                            (downloaded * 100L / expectedLength).toInt().coerceIn(0, 100)
                        } else null
                        onProgress(percent)
                    }
                    output.fd.sync()
                }
            }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    private fun findModelRoot(staging: File): File {
        if (File(staging, "am/final.mdl").isFile) return staging
        val children = staging.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return children.firstOrNull { File(it, "am/final.mdl").isFile }
            ?: throw IllegalStateException("Model files were not found in archive")
    }
}
