package com.smartvoice.intelligence.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Installs the official Vosk Arabic MGB-2 model only when explicitly invoked by the user.
 * The archive is downloaded over HTTPS and extracted with ZIP-slip protection.
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
            val staging = File(root, "$MODEL_ID.installing")
            val destination = installedDirectory
            archive.delete()
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not create model staging directory" }

            try {
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                try {
                    connection.connect()
                    check(connection.responseCode in 200..299) {
                        "Model download failed: HTTP ${connection.responseCode}"
                    }
                    val expectedLength = connection.contentLengthLong
                    check(expectedLength <= 0 || expectedLength <= MAX_ARCHIVE_BYTES) {
                        "Unexpected model archive size"
                    }
                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(archive).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                check(downloaded <= MAX_ARCHIVE_BYTES) {
                                    "Model archive exceeds size limit"
                                }
                                output.write(buffer, 0, count)
                                val percent = if (expectedLength > 0) {
                                    (downloaded * 100 / expectedLength).toInt().coerceIn(0, 100)
                                } else null
                                onProgress(percent)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
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
                onProgress(100)
                destination
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }

    private fun findModelRoot(staging: File): File {
        if (File(staging, "am/final.mdl").isFile) return staging
        val children = staging.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return children.firstOrNull { File(it, "am/final.mdl").isFile }
            ?: throw IllegalStateException("Arabic model files were not found in archive")
    }
}
