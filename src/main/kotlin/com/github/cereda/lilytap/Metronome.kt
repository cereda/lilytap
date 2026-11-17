// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.util.concurrent.locks.LockSupport
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays a metronome click through the default audio output, falling back to the terminal bell
 * when no audio line is available.
 *
 * Click targets are computed from a fixed origin, so timing error never accumulates over the
 * course of a recording.
 *
 * Audio output latency cannot be reliably auto-measured through the Java Sound API, so the
 * caller must supply [audioLatencyMs] (see --audio-latency on the CLI).
 */
class Metronome : AutoCloseable {
    private val sampleRate = 44_100f
    private val format = AudioFormat(sampleRate, 16, 1, true, false)
    private val accent = clickSamples(1600.0)
    private val normal = clickSamples(1000.0)
    private val line: SourceDataLine? = openLine()

    /** True if an audio output line could be opened; false means the bell fallback is used. */
    val hasAudio: Boolean get() = line != null

    private fun openLine(): SourceDataLine? =
        try {
            // Small buffer = low latency; a click is ~2 KB so it fits.
            AudioSystem.getSourceDataLine(format).also {
                it.open(format, 4096)
                it.start()
            }
        } catch (_: Exception) {
            null
        }

    private fun clickSamples(freq: Double): ByteArray {
        val frames = (sampleRate * 0.025).toInt() // 25 ms
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val t = i / sampleRate.toDouble()
            val value = (sin(2 * PI * freq * t) * exp(-t * 180) * 0.8 * Short.MAX_VALUE).toInt()
            out[2 * i] = (value and 0xFF).toByte()
            out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Plays a single click (accented or normal) on the audio line, or the terminal bell. */
    fun click(accented: Boolean) {
        val data = if (accented) accent else normal
        val l = line
        if (l != null) {
            l.write(data, 0, data.size)
        } else {
            print("\u0007")
            System.out.flush()
        }
    }

    /**
     * Blocks the calling thread, clicking at [startNanos] + [i] * [beatNanos]
     * minus [audioLatencyMs] for each beat. Invokes [onBeat] after each click.
     * Returns early if the thread is interrupted.
     *
     * Clicks are shifted earlier by [audioLatencyMs] so that the sound reaches
     * the listener's ears at the nominal beat time, aligning the audible click
     * with the visual playhead.
     *
     * [audioLatencyMs] is the measured audio output latency in milliseconds —
     * there is no reliable way to auto-measure this in the Java Sound API, so
     * the caller provides it (see --audio-latency on the CLI).
     */
    @Suppress("LongParameterList")
    fun run(
        startNanos: Long,
        beatNanos: Long,
        totalBeats: Int,
        beatsPerBar: Int,
        audioLatencyMs: Double,
        onBeat: (Int) -> Unit,
    ) {
        val latencyNanos = (audioLatencyMs * 1_000_000).toLong()
        for (i in 0 until totalBeats) {
            // shift click earlier so sound arrives at the nominal beat time
            val clickTime = startNanos + i * beatNanos - latencyNanos
            sleepUntil(clickTime)
            if (Thread.currentThread().isInterrupted) return // recording was aborted or finished
            click(accented = i % beatsPerBar == 0)
            onBeat(i)
        }
    }

    private fun sleepUntil(target: Long) {
        while (true) {
            val remaining = target - System.nanoTime()
            if (remaining <= 0 || Thread.currentThread().isInterrupted) return
            if (remaining > 2_000_000) LockSupport.parkNanos(remaining - 1_500_000) else Thread.onSpinWait()
        }
    }

    override fun close() {
        line?.let {
            it.stop()
            it.close()
        }
    }
}
