// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/**
 * The tempo of a calibration round: one beat per second.
 *
 * The measurement does not depend on the tempo, and a slow beat leaves room around every beat
 * for the whole output latency: at the user's tempo (120 BPM is half a beat) a tap that is late
 * by more than half a beat lands closer to the next one and is read as early.
 */
internal const val CALIBRATION_BPM = 60.0

/** The time signature of a calibration round: four beats to the bar. */
internal val CALIBRATION_SIGNATURE = TimeSignature(4, 4)

/** The number of bars to tap along with in a calibration round. */
internal const val CALIBRATION_BARS = 2

/** The largest audio latency the setting can hold; an output delay is never negative. */
private const val MAX_AUDIO_LATENCY_MS = 500.0

/**
 * The offset of a calibration tap from the nearest beat, in milliseconds, or null when the tap
 * falls outside the recorded bars. [ms] is measured from the first recorded beat, not from the
 * count-in, so the first bar is included.
 */
internal fun calibrationOffsetMs(
    ms: Double,
    beatMs: Double,
    totalBeats: Int,
): Double? {
    if (!ms.isFinite() || !beatMs.isFinite()) return null
    if (beatMs <= 0 || totalBeats <= 0 || ms < 0) return null
    val beatIndex = Math.round(ms / beatMs).toInt()
    if (beatIndex !in 0 until totalBeats) return null
    return ms - beatIndex * beatMs
}

/**
 * The audio latency after a calibration round.
 *
 * [offsetsMs] are the signed distances from the taps to the nearest beat. The median is used
 * instead of the average, so a single stray tap or one missed beat cannot move the result.
 *
 * A tap that answers a beep which reached the ears late lands after the beat, so the distance is
 * positive and the latency grows by it. A result outside the range of the setting is a sign that
 * the taps did not follow the beeps; such a measurement is dropped and the old value is kept,
 * which keeps a stray tap from moving the setting by a whole beat.
 */
internal fun calibratedLatency(
    currentMs: Double,
    offsetsMs: List<Double>,
): Double {
    if (offsetsMs.isEmpty()) return currentMs
    val sorted = offsetsMs.sorted()
    val middle = sorted.size / 2
    val median =
        if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    val corrected = currentMs + median
    return if (corrected in 0.0..MAX_AUDIO_LATENCY_MS) corrected else currentMs
}

/**
 * A calibration round: the taps that answer the beeps, and what they say about the audio delay.
 *
 * The round runs on a grid of its own - 60 BPM, four beats to the bar - and the measurement
 * does not depend on the settings of the next take. The class holds the taps of the round and
 * the state of the round itself; the window starts it and shows what it says.
 */
internal class Calibration {
    /** The offsets of the taps of the round, in milliseconds. */
    private val offsets = mutableListOf<Double>()

    /** Whether a round is running. */
    var running: Boolean = false
        private set

    /** The number of taps the round has collected. */
    val taps: Int get() = offsets.size

    /** Starts a round: the taps of the last one are forgotten. */
    fun begin() {
        running = true
        offsets.clear()
    }

    /** Gives up the round: the taps are forgotten. */
    fun cancel() {
        running = false
        offsets.clear()
    }

    /**
     * Records a tap of the round.
     *
     * @param ms the time of the tap, in milliseconds from the first recorded beat.
     * @param beatMs the length of a beat, in milliseconds.
     * @param totalBeats the number of beats the round records.
     * @return true when the tap was close enough to a beat to be measured.
     */
    fun tap(
        ms: Double,
        beatMs: Double,
        totalBeats: Int,
    ): Boolean {
        val offset = calibrationOffsetMs(ms, beatMs, totalBeats) ?: return false
        offsets += offset
        return true
    }

    /**
     * The audio latency after the round: the current one plus the median of the taps.
     *
     * @param currentMs the latency the setting holds now.
     * @return the latency the setting should hold.
     */
    fun latencyAfter(currentMs: Double): Double = calibratedLatency(currentMs, offsets)

    /** The status of a running round: a count of the taps, never a beat number. */
    fun status(): String =
        if (offsets.isEmpty()) {
            "Calibrating \u00B7 tap with the beeps"
        } else {
            "Calibrating \u00B7 ${offsets.size} taps"
        }
}
