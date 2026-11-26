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
 * The audio line is opened when a take starts and closed when it ends, so the app does not
 * hold the sound card while nothing is playing. Opening it can take tens of milliseconds, so
 * it is opened before the schedule of the take is made and the first click is not late.
 *
 * Audio output latency cannot be reliably auto-measured through the Java Sound API, so the
 * caller must supply [audioLatencyMs] (the window's **Audio latency** setting).
 */
class Metronome : AutoCloseable {
    private val sampleRate = 44_100f
    private val format = AudioFormat(sampleRate, 16, 1, true, false)
    private val normal = ClickSound.NORMAL.wave(sampleRate)
    private val beat = ClickSound.BEAT.wave(sampleRate)
    private val downbeat = ClickSound.DOWNBEAT.wave(sampleRate)

    /** The audio line, while it is open. */
    private val line = AtomicReference<SourceDataLine?>(null)

    /** True while the audio line is open; false means the clicks go to the terminal bell. */
    val hasAudio: Boolean get() = line.get() != null

    private val closed = AtomicBoolean(false)

    /** The thread currently running [run()], so [close()] can interrupt it. */
    private val currentThread = AtomicReference<Thread?>(null)

    /**
     * Opens the audio line.
     *
     * The line is opened before the schedule of the take is made: opening it can take tens of
     * milliseconds, and a click that is late because of that is a click that is out of time.
     * Opening it twice is harmless: the line that is already open is kept.
     */
    fun open() {
        if (closed.get()) return
        if (line.get() != null) return
        line.set(openLine())
    }

    /**
     * Closes the audio line: the sound card is free again.
     *
     * The metronome can open it again later: a take opens the line, and the take after it
     * opens it once more.
     */
    fun release() {
        line.getAndSet(null)?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
    }

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

    /** Plays a single click (the plain one, the beat or the downbeat) on the audio line. */
    fun click(accent: ClickAccent) {
        if (closed.get()) return
        val data =
            when (accent) {
                ClickAccent.NORMAL -> normal
                ClickAccent.BEAT -> beat
                ClickAccent.DOWNBEAT -> downbeat
            }
        val l = line.get()
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
     * Blocks the calling thread, clicking at [startNanos] + [i] * [clickNanos]
     * minus [audioLatencyMs] for each click. Returns early if the thread is interrupted.
     *
     * Clicks are shifted earlier by [audioLatencyMs] so that the sound reaches
     * the listener's ears at the nominal click time, aligning the audible click
     * with the visual playhead.
     *
     * [audioLatencyMs] is the measured audio output latency in milliseconds —
     * there is no reliable way to auto-measure this in the Java Sound API, so
     * the caller provides it (the window's **Audio latency** setting).
     *
     * The clicks are marked by [clickAccent]: the beat is accented, and the downbeat has a
     * sound of its own when the beat is subdivided.
     *
     * @param totalClicks how many clicks to play: the count-in plus the take.
     * @param clicksPerBeat how many clicks make a musical beat.
     * @param clicksPerBar how many clicks make a bar.
     */
    @Suppress("LongParameterList")
    fun run(
        startNanos: Long,
        clickNanos: Long,
        totalClicks: Int,
        clicksPerBeat: Int,
        clicksPerBar: Int,
        audioLatencyMs: Double,
    ) {
        if (closed.get()) return
        val me = Thread.currentThread()
        currentThread.set(me)
        try {
            val latencyNanos = (audioLatencyMs * 1_000_000).toLong()
            for (i in 0 until totalClicks) {
                // shift click earlier so sound arrives at the nominal beat time
                val clickTime = startNanos + i * clickNanos - latencyNanos
                sleepUntil(clickTime)
                if (me.isInterrupted) return // recording was aborted or finished
                click(accent = clickAccent(i, clicksPerBeat, clicksPerBar))
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
        release()
    }
}
