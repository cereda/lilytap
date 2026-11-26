// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import kotlin.concurrent.thread
import kotlin.math.roundToLong

/** The time the metronome waits before the first click: the window is ready by then. */
private const val TAKE_LEAD_NANOS = 800_000_000L

/**
 * The keys that are never a tap: the modifiers and the lock keys.
 *
 * They are swallowed by the window, so a take never records them and a tap round never
 * counts them.
 */
internal val IGNORED_KEYS =
    setOf(
        KeyEvent.VK_SHIFT,
        KeyEvent.VK_CONTROL,
        KeyEvent.VK_ALT,
        KeyEvent.VK_ALT_GRAPH,
        KeyEvent.VK_META,
        KeyEvent.VK_CAPS_LOCK,
    )

/**
 * What the take that runs has to say about the moment.
 *
 * @property index the beat of the session, counted from the first count-in click.
 * @property text what the status line says.
 * @property mood the mood of the status line and of the beat dots.
 * @property activeBeat the beat of the bar the dots mark (-1 when there is none).
 */
internal data class Guide(
    val index: Int,
    val text: String,
    val mood: Mood,
    val activeBeat: Int,
)

/**
 * What a key did to the take that runs.
 */
internal enum class KeyOutcome {
    /** The key was swallowed by the take, and nothing else happened. */
    CONSUMED,

    /** The key gave up the take. */
    ABORT,

    /** The take is over. */
    FINISH,
}

/**
 * What a take left behind: the score of the take and the number of notes that were played.
 *
 * @property transcription the score of the take.
 * @property notes how many notes and silences the take held.
 */
internal data class RecordedTake(
    val transcription: Transcription,
    val notes: Int,
)

/**
 * The recording of a take: the session that runs, the metronome that clicks and the keys.
 *
 * The recorder holds the logic of a take and nothing of the window: it starts the session,
 * it says what the status line and the beat dots have to show, it writes the keys into the
 * session and it turns the notes of a finished take into a score. The window owns the
 * widgets and applies what the recorder says.
 */
internal class TakeRecorder(
    /** The guard against the keys that repeat themselves. */
    private val autoRepeat: AutoRepeat,
    /** What the status line says while a calibration round runs. */
    private val calibrationStatus: () -> String,
) {
    /** The metronome that clicks the count-in and the take. */
    private val metronome = Metronome()

    /**
     * Starts a take: the session that runs and the metronome that clicks it.
     *
     * The take begins after a short lead, so the window is ready before the first click.
     * The count-in is part of the schedule of the metronome, and the downbeat of the take
     * falls after it.
     *
     * @param config the settings of the take.
     * @param minRestCells the smallest silence that becomes a rest of its own.
     * @param audioLatencyMs how much earlier the clicks are written to the audio output.
     * @return the session that runs.
     */
    fun start(
        config: Config,
        minRestCells: Int,
        audioLatencyMs: Double,
    ): Session {
        val pulses = config.timeSignature.pulsesPerBar
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        val clickNanos = (config.clickMs * 1_000_000.0).roundToLong()
        val startNanos = System.nanoTime() + TAKE_LEAD_NANOS
        val origin = startNanos + config.countInBeats * beatNanos
        val end = origin + config.bars.toLong() * pulses * beatNanos
        val session = Session(config, minRestCells, startNanos, origin, end, beatNanos)
        autoRepeat.reset()
        session.ticker =
            thread(isDaemon = true, name = "metronome") {
                metronome.run(
                    startNanos,
                    clickNanos,
                    config.totalClicks,
                    config.clicksPerBeat,
                    config.clicksPerBar,
                    audioLatencyMs,
                )
            }
        return session
    }

    /**
     * The guide of a session: what the status line says and which beat the dots mark.
     *
     * The guide follows the nominal beat, not the moment the click is written to the audio
     * line: the line is written early by the audio latency, so a guide that followed the
     * write would sit that much ahead of the playhead and of the sound the listener
     * actually hears.
     *
     * @param session the session that runs.
     * @param nowNanos the current time.
     * @param calibrating whether the session is a calibration round.
     * @return the guide of the moment.
     */
    fun guide(
        session: Session,
        nowNanos: Long,
        calibrating: Boolean,
    ): Guide {
        val index = beatIndexAt(nowNanos, session.startNanos, session.beatNanos)
        val pulses = session.config.timeSignature.pulsesPerBar
        val countInBeats = session.config.countInBeats
        return when {
            index < 0 -> {
                Guide(index, "Get ready...", Mood.COUNT_IN, -1)
            }

            index < countInBeats -> {
                Guide(
                    index,
                    // While calibrating there is no beat count: a beat number is a claim about
                    // when the beat is, and while the audio is late that claim is wrong.
                    if (calibrating) "Get ready..." else "Count-in: ${countInBeats - index}",
                    Mood.COUNT_IN,
                    // The dots keep the beat of the bar, so a count-in of several bars goes on
                    // marking the bar the way a metronome does.
                    index % pulses,
                )
            }

            else -> {
                val r = index - countInBeats
                Guide(
                    index,
                    if (calibrating) {
                        calibrationStatus()
                    } else {
                        "Recording \u00B7 bar ${r / pulses + 1}/${session.config.bars}, " +
                            "beat ${r % pulses + 1}"
                    },
                    Mood.REC,
                    r % pulses,
                )
            }
        }
    }

    /**
     * Handles a key of the take that runs.
     *
     * A press starts a note, a release ends it, and the key `r` starts a silence. The
     * count-in is not part of the take: a key pressed before the downbeat is not a note of
     * the piece, so it is not recorded - and the timeline must not show it either. A key
     * that lands in the first cell of the take is the first note of the take, though: the
     * quantizer keeps such a tap, so the window keeps it too. A tap that comes a hair
     * early is a tap on the beat.
     *
     * @param session the session that runs.
     * @param event the key event.
     * @param nowNanos the current time.
     * @return what the key did.
     */
    fun key(
        session: Session,
        event: KeyEvent,
        nowNanos: Long,
    ): KeyOutcome {
        if (event.id == KeyEvent.KEY_TYPED) return KeyOutcome.CONSUMED
        if (event.keyCode == KeyEvent.VK_ESCAPE) {
            return if (event.id == KeyEvent.KEY_PRESSED) KeyOutcome.ABORT else KeyOutcome.CONSUMED
        }
        if (event.keyCode in IGNORED_KEYS) return KeyOutcome.CONSUMED
        if (nowNanos >= session.endNanos) return KeyOutcome.FINISH
        val ms = (nowNanos - session.originNanos) / 1e6
        val beforeTake = ms < 0.0 && tapCell(ms, session.config) < 0
        if (!beforeTake) {
            when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    // Auto-repeat sends the same press again; count it only once.
                    if (!autoRepeat.isRepeat(event)) {
                        autoRepeat.note(event)
                        if (event.keyCode == KeyEvent.VK_R) {
                            session.rests += ms
                        } else if (event.keyCode !in session.held) {
                            session.held[event.keyCode] = ms
                        }
                    }
                }

                KeyEvent.KEY_RELEASED -> {
                    // repeats are ignored
                    if (!autoRepeat.isRepeat(event)) {
                        val down = session.held.remove(event.keyCode)
                        if (down != null) session.finished += HeldNote(down, ms)
                    }
                }
            }
        }
        return KeyOutcome.CONSUMED
    }

    /**
     * The score of a finished take: the notes that were played, transcribed.
     *
     * A note that was still held when the take ended lasts until the end of the take, and
     * a silence is a note of its own kind.
     *
     * @param session the session that ended.
     * @return the transcription of the take and the number of notes it held.
     */
    fun transcription(session: Session): RecordedTake {
        val endMs = (session.endNanos - session.originNanos) / 1e6
        val played = ArrayList<HeldNote>(session.finished)
        for (down in session.held.values) played += HeldNote(down, endMs)
        for (rest in session.rests) played += HeldNote(rest, rest, TapKind.REST)
        session.held.clear()
        session.rests.clear()
        return RecordedTake(transcribeHeld(played, session.config, session.minRestCells), played.size)
    }

    /**
     * Stops the take: the metronome stops and the session is closed.
     *
     * @param session the session that runs.
     */
    fun stop(session: Session) {
        session.done = true
        session.ticker.interrupt()
        session.ticker.join(300)
    }

    /** Closes the metronome: the app is going down. */
    fun close() {
        metronome.close()
    }
}
