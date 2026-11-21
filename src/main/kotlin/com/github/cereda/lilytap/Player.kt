// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

/** How playback sounds: the score's pitches, or a percussive click on every note onset. */
enum class PlaybackSound { NOTE, BEAT }

/** Breathing room between [Player.play] and the first event, so the thread is ready in time. */
private const val PLAYBACK_LEAD_NANOS = 40_000_000L

/**
 * Plays a [Score] through the default audio output, advancing a playhead across its events.
 *
 * Each [Event] is played at its grid position: notes sound for [Event.lengthUnits] units, rests
 * are silence. [PlaybackSound.NOTE] plays the pitch of the note as a tone; [PlaybackSound.BEAT]
 * plays a short click at the note onset instead, with an accent on bar downbeats. Playback runs
 * on its own thread and reports the current position in milliseconds via [onPosition].
 *
 * Audio output latency cannot be reliably auto-measured through the Java Sound API, so the caller
 * must supply [audioLatencyMs] (the window's **Audio latency** setting). Playback is shifted
 * earlier by this amount so the sound reaches the listener's ears in time with the visual playhead.
 */
class Player(
    private val audioLatencyMs: Double = 0.0,
    private val sound: PlaybackSound = PlaybackSound.NOTE,
) : AutoCloseable {
    private val sampleRate = 44_100f
    private val format = AudioFormat(sampleRate, 16, 1, true, false)
    private val line: SourceDataLine? = openLine()

    /** Playback clicks: lower and longer than the recording metronome, so they are distinct. */
    private val beatClick = clickWave(600.0, 0.035, 120.0, sampleRate)
    private val beatAccent = clickWave(900.0, 0.035, 120.0, sampleRate)

    /** True if an audio output line could be opened; false means playback is silent. */
    val hasAudio: Boolean get() = line != null

    private val playing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
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
        while (offset < samples.size && playing.get() && !closed.get()) {
            val n = if (chunk < samples.size - offset) chunk else samples.size - offset
            try {
                l.write(samples, offset, n)
            } catch (_: IllegalStateException) {
                playing.set(false)
                return
            }
            offset += n
        }
    }

    /**
     * Starts playing [score] on its own thread. [onPosition] is invoked (from the playback thread)
     * with the current position in milliseconds, at each event start. Rests are reported but stay
     * silent. [onDone] is invoked when playback reaches the end or [stop] is called.
     *
     * Returns the `System.nanoTime()` reference used for the score start, or 0 when the player is
     * already closed. The reference is sampled here, after the audio line is open, and points a
     * short lead-in into the future: opening the line can take tens of milliseconds, and sampling
     * the reference before that would make the first note start late while later events, which
     * are scheduled from the same absolute origin, stay in time.
     *
     * The reference also includes the audio latency: every event is written [audioLatencyMs]
     * before its beat, so the first event needs that head start as well. Without it the first
     * event is written in the past and sounds late, while the later ones, which have the whole
     * latency available, stay in time.
     *
     * Throws if playback is already running.
     */
    fun play(
        score: Score,
        onPosition: (Double) -> Unit = {},
        onDone: (() -> Unit)? = null,
    ): Long {
        if (closed.get()) return 0L
        require(!playing.get()) { "Already playing" }
        // Ensure any previous playback thread has fully exited before starting a new one.
        playbackThread?.join(2_000)
        if (playbackThread?.isAlive == true) {
            // Still alive after join budget; interrupt and wait once more.
            playbackThread?.interrupt()
            playbackThread?.join(2_000)
        }
        if (!playing.compareAndSet(false, true)) {
            check(false) { "Already playing" }
        }
        if (closed.get()) {
            playing.set(false)
            return 0L
        }

        val latencyNanos = (audioLatencyMs * 1_000_000).roundToLong()
        // The first event is written `latencyNanos` before its beat, so the origin has to carry
        // that head start too: otherwise the first event is written in the past and sounds late.
        val origin = System.nanoTime() + latencyNanos + PLAYBACK_LEAD_NANOS
        val thread =
            thread(isDaemon = true, name = "playback") {
                val config = score.config

                try {
                    for (event in score.events) {
                        if (!advanceEvent(event, origin, config, latencyNanos, onPosition)) break
                    }
                } finally {
                    playing.set(false)
                    onDone?.invoke()
                }
            }
        playbackThread = thread
        if (closed.get()) stop()
        return origin
    }

    /** Stops playback immediately. Safe to call from any thread. */
    fun stop() {
        playing.set(false)
        playbackThread?.interrupt()
    }

    /**
     * Advances playback through a single event: waits for its start time, reports the position,
     * and plays the tone or click if it is a note. Returns false if playback was stopped.
     */
    @Suppress("LongParameterList")
    private fun advanceEvent(
        event: Event,
        startNanos: Long,
        config: Config,
        latencyNanos: Long,
        onPosition: (Double) -> Unit,
    ): Boolean {
        val durationNanos = (event.lengthUnits * config.unitMs * 1_000_000.0).roundToLong()
        val start = eventStartNanos(startNanos, event.startUnits, config.unitMs)
        val clickTime = start - latencyNanos
        sleepUntil(clickTime)
        if (!playing.get()) return false
        onPosition(event.startUnits.toDouble() * config.unitMs)
        if (event.kind == TapKind.NOTE) {
            when (sound) {
                PlaybackSound.NOTE -> {
                    val freq = event.pitch?.frequency ?: Pitch.DEFAULT.frequency
                    playSamples(toneSamples(freq, event.lengthUnits * config.unitMs / 1000.0))
                }

                PlaybackSound.BEAT -> {
                    val accent = event.startUnits % config.unitsPerBar == 0
                    playSamples(if (accent) beatAccent else beatClick)
                }
            }
            // A click is shorter than the note it stands for: wait out the rest of the event,
            // so the next event still starts on its grid time.
            sleepUntil(clickTime + durationNanos)
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
        if (!closed.compareAndSet(false, true)) return
        stop()
        // Close the line first to unblock any in-progress write
        line?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        playbackThread?.join(1_000)
    }
}

/**
 * The nanosecond time of an event, rounded to the nearest nanosecond.
 *
 * Rounding matters: the unit is fractional for most tempos (63 BPM with two subdivisions
 * is 476.190… ms), so truncating it once per event would drift by nearly a tenth of a
 * second over a long score.
 *
 * The time is measured in units, not in cells: a cell is a power of two in units, so
 * dividing the cell time by it is exact and a cell-aligned event keeps exactly the time
 * it had before the score was measured in units.
 */
internal fun eventStartNanos(
    startNanos: Long,
    startUnits: Int,
    unitMs: Double,
): Long = startNanos + (startUnits * unitMs * 1_000_000.0).roundToLong()
