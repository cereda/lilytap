// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays a metronome click through the default audio output, falling back to the terminal bell
 * when no audio line is available.
 *
 * Click targets are computed from a fixed origin, so timing error never accumulates over the
 * course of a recording.
 *
 * Audio output latency cannot be reliably auto-measured through the Java Sound API, so the
 * caller must supply [audioLatencyMs] (the window's **Audio latency** setting).
 */
class Metronome : AutoCloseable {
    private val sampleRate = 44_100f
    private val format = AudioFormat(sampleRate, 16, 1, true, false)
    private val accent = clickWave(1600.0, 0.025, 180.0, sampleRate)
    private val normal = clickWave(1000.0, 0.025, 180.0, sampleRate)
    private val line: SourceDataLine? = openLine()
    private val closed = AtomicBoolean(false)

    /** The thread currently running [run()], so [close()] can interrupt it. */
    private val currentThread = AtomicReference<Thread?>(null)

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

    /** Plays a single click (accented or normal) on the audio line, or the terminal bell. */
    fun click(accented: Boolean) {
        if (closed.get()) return
        val data = if (accented) accent else normal
        val l = line
        if (l != null) {
            try {
                l.write(data, 0, data.size)
            } catch (_: IllegalStateException) {
                // The line was closed by shutdown between the checks above.
            }
        } else {
            print("\u0007")
            System.out.flush()
        }
    }

    /**
     * Blocks the calling thread, clicking at [startNanos] + [i] * [beatNanos]
     * minus [audioLatencyMs] for each beat. Returns early if the thread is interrupted.
     *
     * Clicks are shifted earlier by [audioLatencyMs] so that the sound reaches
     * the listener's ears at the nominal beat time, aligning the audible click
     * with the visual playhead.
     *
     * [audioLatencyMs] is the measured audio output latency in milliseconds —
     * there is no reliable way to auto-measure this in the Java Sound API, so
     * the caller provides it (the window's **Audio latency** setting).
     */
    @Suppress("LongParameterList")
    fun run(
        startNanos: Long,
        beatNanos: Long,
        totalBeats: Int,
        beatsPerBar: Int,
        audioLatencyMs: Double,
    ) {
        if (closed.get()) return
        val me = Thread.currentThread()
        currentThread.set(me)
        try {
            val latencyNanos = (audioLatencyMs * 1_000_000).toLong()
            for (i in 0 until totalBeats) {
                // shift click earlier so sound arrives at the nominal beat time
                val clickTime = startNanos + i * beatNanos - latencyNanos
                sleepUntil(clickTime)
                if (me.isInterrupted) return // recording was aborted or finished
                click(accented = i % beatsPerBar == 0)
            }
        } finally {
            currentThread.compareAndSet(me, null)
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
        if (!closed.compareAndSet(false, true)) return
        // Interrupt any running run() thread so it exits sleepUntil() promptly.
        currentThread.getAndSet(null)?.interrupt()
        line?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
    }
}
