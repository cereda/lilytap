// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a [Score] through the default audio output, advancing a playhead across its events.
 *
 * Each [Event] is played at its grid position: notes sound for [lengthCells] cells, rests are
 * silence. The pitch letter sets the tone frequency; rests are silent. Playback runs on its own
 * thread and reports the current position in milliseconds via [onPosition].
 *
 * Audio output latency cannot be reliably auto-measured through the Java Sound API, so the caller
 * must supply [audioLatencyMs] (see --audio-latency on the CLI). Playback is shifted earlier by
 * this amount so the sound reaches the listener's ears in time with the visual playhead.
 */
class Player(
    private val audioLatencyMs: Double = 0.0,
) : AutoCloseable {
    private val sampleRate = 44_100f
    private val format = AudioFormat(sampleRate, 16, 1, true, false)
    private val line: SourceDataLine? = openLine()

    /** True if an audio output line could be opened; false means playback is silent. */
    val hasAudio: Boolean get() = line != null

    private val playing = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    private fun openLine(): SourceDataLine? =
        try {
            AudioSystem.getSourceDataLine(format).also {
                it.open(format, 4096)
                it.start()
            }
        } catch (_: Exception) {
            null
        }

    /** Frequency in Hz for a pitch letter (c = middle C). Unknown letters fall back to middle C. */
    private fun pitchFrequency(pitch: String): Double {
        val name = pitch.trim().take(1).lowercase()
        return when (name) {
            "c" -> 261.63
            "d" -> 293.66
            "e" -> 329.63
            "f" -> 349.23
            "g" -> 392.00
            "a" -> 440.00
            "b" -> 493.88
            else -> 261.63
        }
    }

    /** Sine-tone samples for [freq] Hz lasting [seconds], with ADSR envelope and a second harmonic. */
    private fun toneSamples(
        freq: Double,
        seconds: Double,
    ): ByteArray {
        val frames = (sampleRate * seconds).toInt().coerceAtLeast(1)
        val out = ByteArray(frames * 2)
        val attackFrames = (sampleRate * 0.01).toInt().coerceAtLeast(1) // 10 ms fade-in
        val releaseFrames = (sampleRate * 0.08).toInt().coerceAtLeast(1) // 80 ms fade-out
        val sustainLevel = 0.7
        for (i in 0 until frames) {
            val t = i / sampleRate.toDouble()
            // Attack fades in from 0 to 1 over attackFrames.
            val attackEnv = if (i < attackFrames) i.toDouble() / attackFrames else 1.0
            // Release fades from sustainLevel to 0 over the last releaseFrames.
            val releaseEnv = if (i > frames - releaseFrames) (frames - i).toDouble() / releaseFrames else 1.0
            val env = minOf(attackEnv, releaseEnv) * sustainLevel
            // Fundamental plus a quieter second harmonic for warmth.
            val sample = sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)
            val value = (sample * env * 0.4 * Short.MAX_VALUE).toInt()
            out[2 * i] = (value and 0xFF).toByte()
            out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Plays [samples] on the audio line, blocking until done or playback stops. */
    private fun playSamples(samples: ByteArray) {
        val l = line ?: return
        val chunk = 512
        var offset = 0
        while (offset < samples.size && playing.get()) {
            val n = if (chunk < samples.size - offset) chunk else samples.size - offset
            l.write(samples, offset, n)
            offset += n
        }
    }

    /**
     * Plays [score] from the start. [onPosition] is invoked (from the playback thread) with the
     * current position in milliseconds relative to the start of the score.
     *
     * Returns when playback reaches the end or [stop] is called. Throws if already playing.
     * [startNanos] is the reference time for the score start (System.nanoTime() base), so that
     * audio and visual playhead stay in sync even if the playback thread starts late.
     */
    fun play(
        score: Score,
        startNanos: Long,
        onPosition: (Double) -> Unit = {},
        onDone: (() -> Unit)? = null,
    ) {
        require(!playing.get()) { "Already playing" }
        // Ensure any previous playback thread has fully exited before starting a new one.
        playbackThread?.join(2_000)
        if (playbackThread?.isAlive == true) {
            // Still alive after join budget; interrupt and wait once more.
            playbackThread?.interrupt()
            playbackThread?.join(2_000)
        }
        playing.set(true)

        val thread =
            thread(isDaemon = true, name = "playback") {
                val latencyNanos = (audioLatencyMs * 1_000_000).toLong()
                val totalCells = score.totalCells
                val config = score.config
                val freq = pitchFrequency(config.pitch)

                try {
                    for (event in score.events) {
                        if (!advanceEvent(event, startNanos, config, freq, latencyNanos)) break
                        val positionMs = event.startCell.toDouble() * config.cellMs
                        onPosition(positionMs)
                    }
                } finally {
                    playing.set(false)
                    onPosition(0.0)
                    onDone?.invoke()
                }
            }
        playbackThread = thread
    }

    /** Stops playback immediately. Safe to call from any thread. */
    fun stop() {
        playing.set(false)
        playbackThread?.interrupt()
    }

    /**
     * Advances playback through a single event: waits for its start time, plays the tone if it is
     * a note. Returns false if playback was stopped while advancing, in which case the caller
     * should end the loop.
     */
    private fun advanceEvent(
        event: Event,
        startNanos: Long,
        config: Config,
        freq: Double,
        latencyNanos: Long,
    ): Boolean {
        val eventStartNanos = startNanos + event.startCell.toLong() * config.cellMs.toLong() * 1_000_000L
        val clickTime = eventStartNanos - latencyNanos
        sleepUntil(clickTime)
        if (!playing.get()) return false
        if (event.kind == TapKind.NOTE) {
            val durationSeconds = event.lengthCells * config.cellMs / 1000.0
            playSamples(toneSamples(freq, durationSeconds))
        }
        return playing.get()
    }

    private fun sleepUntil(target: Long) {
        while (playing.get() && !Thread.currentThread().isInterrupted) {
            val remaining = target - System.nanoTime()
            if (remaining <= 0) return
            if (remaining > 2_000_000) LockSupport.parkNanos(remaining - 1_500_000) else Thread.onSpinWait()
        }
    }

    override fun close() {
        stop()
        // Close the line first to unblock any in-progress write
        line?.let {
            it.stop()
            it.close()
        }
        // Then join the thread with a longer timeout
        playbackThread?.join(1_000)
    }
}
