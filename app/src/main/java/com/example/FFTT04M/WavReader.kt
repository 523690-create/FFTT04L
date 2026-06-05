package com.example.FFTT04M

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for the 16-bit PCM WAV files written by MainActivity.encodeWav.
 * Raw PCM WAV has no MediaCodec decoder (audio/raw), so the activities parse it directly.
 * Multi-channel files are downmixed to mono by averaging channels.
 */
object WavReader {

    data class WavData(val sampleRate: Int, val samples: FloatArray)

    /** Parse [file] into mono float samples, reading at most [maxFrames] frames. */
    fun read(file: File, maxFrames: Int = Int.MAX_VALUE): WavData {
        val bytes = file.readBytes()
        if (bytes.size < 44 ||
            bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()) {
            throw IllegalArgumentException("Not a RIFF/WAV file: ${file.name}")
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var sampleRate = 44100
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = bb.getShort(body + 2).toInt().coerceAtLeast(1)
                    sampleRate = bb.getInt(body + 4)
                    bitsPerSample = bb.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataOffset = body
                    dataLen = size.coerceAtMost(bytes.size - body)
                }
            }
            if (dataOffset >= 0) break
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        if (dataOffset < 0) throw IllegalArgumentException("No data chunk in WAV: ${file.name}")

        val bytesPerSample = bitsPerSample / 8
        val totalSamples = dataLen / bytesPerSample
        val totalFrames = totalSamples / channels
        val frames = minOf(totalFrames, maxFrames)
        val out = FloatArray(frames)
        val db = ByteBuffer.wrap(bytes, dataOffset, dataLen).order(ByteOrder.LITTLE_ENDIAN)
        when (bitsPerSample) {
            16 -> for (i in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += db.short / 32768f
                out[i] = sum / channels
            }
            8 -> for (i in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += ((db.get().toInt() and 0xFF) - 128) / 128f
                out[i] = sum / channels
            }
            else -> throw IllegalArgumentException("Unsupported WAV bit depth: $bitsPerSample")
        }
        return WavData(sampleRate, out)
    }
}
