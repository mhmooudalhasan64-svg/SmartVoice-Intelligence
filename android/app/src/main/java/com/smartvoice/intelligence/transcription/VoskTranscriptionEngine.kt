package com.smartvoice.intelligence.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-device Arabic-capable Vosk adapter.
 *
 * The supplied modelDirectory must already contain an extracted Vosk model.
 * Audio is decoded locally from the app's M4A/AAC recording into mono PCM 16 kHz.
 */
class VoskTranscriptionEngine(
    private val modelDirectory: File,
    private val workDirectory: File
) : TranscriptionEngine {
    override val engineId: String = "vosk-android"
    override val modelId: String = "vosk-model-ar-mgb2-0.4"

    override suspend fun transcribe(
        recordingId: String,
        audioFile: File,
        languageTag: String,
        onProgress: (Int?) -> Unit
    ): TranscriptionResult {
        require(audioFile.isFile) { "Recording does not exist" }
        require(modelDirectory.isDirectory && modelDirectory.list()?.isNotEmpty() == true) {
            "Arabic speech model is not installed"
        }
        workDirectory.mkdirs()
        val pcmFile = File(workDirectory, "$recordingId-16k-mono.pcm")
        try {
            onProgress(2)
            decodeToPcm16kMono(audioFile, pcmFile)
            onProgress(15)

            val segments = mutableListOf<TranscriptSegment>()
            Model(modelDirectory.absolutePath).use { model ->
                Recognizer(model, 16_000.0f).use { recognizer ->
                    recognizer.setWords(true)
                    val buffer = ByteArray(8 * 1024)
                    val totalBytes = max(1L, pcmFile.length())
                    var consumed = 0L
                    var sequence = 0
                    BufferedInputStream(FileInputStream(pcmFile)).use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            consumed += count
                            if (recognizer.acceptWaveForm(buffer, count)) {
                                parseUtterance(recognizer.result, sequence, segments)?.let {
                                    segments += it
                                    sequence++
                                }
                            }
                            onProgress((15 + (consumed * 80 / totalBytes).toInt()).coerceIn(15, 95))
                        }
                    }
                    parseUtterance(recognizer.finalResult, sequence, segments)?.let { segments += it }
                }
            }
            onProgress(100)
            return TranscriptionResult(
                recordingId = recordingId,
                languageTag = languageTag,
                segments = segments,
                engineId = engineId,
                modelId = modelId,
                completedAtEpochMs = System.currentTimeMillis()
            )
        } catch (error: Exception) {
            throw error
        } finally {
            pcmFile.delete()
        }
    }

    private fun parseUtterance(
        json: String,
        sequence: Int,
        output: MutableList<TranscriptSegment>
    ): TranscriptSegment? {
        val root = JSONObject(json)
        val wordsJson = root.optJSONArray("result") ?: return null
        if (wordsJson.length() == 0) return null
        val words = buildList {
            for (index in 0 until wordsJson.length()) {
                val item = wordsJson.getJSONObject(index)
                val word = item.optString("word").trim()
                if (word.isEmpty()) continue
                val start = (item.optDouble("start", 0.0) * 1000).roundToInt().toLong().coerceAtLeast(0)
                val end = (item.optDouble("end", start / 1000.0) * 1000).roundToInt().toLong().coerceAtLeast(start)
                val confidence = if (item.has("conf")) item.optDouble("conf").toFloat().coerceIn(0f, 1f) else null
                add(RecognizedWord(word, start, end, confidence))
            }
        }
        if (words.isEmpty()) return null
        val startMs = words.first().startMs
        val endMs = words.last().endMs
        val confidenceValues = words.mapNotNull { it.confidence }
        val confidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return TranscriptSegment(
            sequence = sequence,
            text = words.joinToString(" ") { it.text },
            startMs = startMs,
            endMs = endMs,
            words = words,
            confidence = confidence
        )
    }

    private fun decodeToPcm16kMono(inputFile: File, outputFile: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val rawFile = File(workDirectory, outputFile.nameWithoutExtension + "-decoded.pcm")
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    inputFormat = format
                    break
                }
            }
            require(audioTrack >= 0 && inputFormat != null) { "No audio track found" }
            extractor.selectTrack(audioTrack)
            val format = requireNotNull(inputFormat)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            BufferedOutputStream(FileOutputStream(rawFile)).use { raw ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                            if (info.size > 0) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size)
                                outputBuffer.get(chunk)
                                raw.write(chunk)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            require(pcmEncoding == MediaFormat.ENCODING_PCM_16BIT) {
                "Unsupported decoder PCM format"
            }
            require(sampleRate > 0 && channelCount > 0) { "Invalid decoded audio format" }
            resampleAndDownmix(rawFile, outputFile, sampleRate, channelCount)
            require(outputFile.length() >= 2) { "Decoded audio is empty" }
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            extractor.release()
            rawFile.delete()
        }
    }

    /** Convert interleaved signed PCM16 to mono 16 kHz using frame averaging and linear interpolation. */
    private fun resampleAndDownmix(rawFile: File, outputFile: File, sourceRate: Int, channels: Int) {
        val frameBytes = channels * 2
        val frameCount = rawFile.length() / frameBytes
        require(frameCount > 0) { "No decoded PCM frames" }
        BufferedInputStream(FileInputStream(rawFile)).use { input ->
            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                val frame = ByteArray(frameBytes)
                fun readMono(): Short? {
                    var offset = 0
                    while (offset < frameBytes) {
                        val n = input.read(frame, offset, frameBytes - offset)
                        if (n < 0) return null
                        offset += n
                    }
                    val data = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
                    var sum = 0L
                    repeat(channels) { sum += data.short.toLong() }
                    return (sum / channels).toShort()
                }

                var left = readMono() ?: return
                var right = readMono() ?: left
                var sourceIndex = 0L
                val targetFrames = (frameCount.toDouble() * 16_000.0 / sourceRate).toLong()
                for (targetIndex in 0 until targetFrames) {
                    val sourcePosition = targetIndex.toDouble() * sourceRate / 16_000.0
                    val desiredIndex = sourcePosition.toLong()
                    while (sourceIndex < desiredIndex) {
                        left = right
                        right = readMono() ?: left
                        sourceIndex++
                    }
                    val fraction = sourcePosition - desiredIndex
                    val sample = (left * (1.0 - fraction) + right * fraction).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    output.write(sample.toInt() and 0xff)
                    output.write((sample.toInt() shr 8) and 0xff)
                }
            }
        }
    }
}
