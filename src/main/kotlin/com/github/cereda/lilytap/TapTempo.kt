// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.roundToInt

/**
 * The taps of a tap-tempo round, and the tempo they make.
 *
 * The user taps in time; the app watches the intervals between the taps and turns them into a
 * tempo. The median of the intervals decides, so one slipped tap does not wreck the result -
 * the same robustness the calibration uses.
 *
 * A pause longer than [IDLE_NANOS] starts a new sequence: a tap that comes long after the
 * last one says nothing about the tempo, and it would drag the result down.
 */
internal class TapTempo(
    private val minBpm: Int = 20,
    private val maxBpm: Int = 300,
) {
    /** The taps of the current sequence, in nanoseconds. */
    private val taps = mutableListOf<Long>()

    /**
     * Adds a tap to the sequence.
     *
     * @param nowNanos the clock when the tap happened.
     * @return the tempo the taps make, or null when there are not enough of them yet.
     */
    fun tap(nowNanos: Long): Int? {
        val last = taps.lastOrNull()
        if (last != null && nowNanos - last > IDLE_NANOS) taps.clear()
        taps.add(nowNanos)
        if (taps.size < 2) return null
        val intervals = taps.zipWithNext { from, to -> (to - from).toDouble() }
        val bpm = MINUTE_NANOS / median(intervals)
        return bpm.roundToInt().coerceIn(minBpm, maxBpm)
    }

    /**
     * Whether the sequence is still alive.
     *
     * A tap in the last [IDLE_NANOS] keeps it alive; after that the round is over and the next
     * tap starts a new one.
     *
     * @param nowNanos the clock now.
     * @return true when the round is still listening.
     */
    fun isListening(nowNanos: Long): Boolean {
        val last = taps.lastOrNull() ?: return false
        return nowNanos - last <= IDLE_NANOS
    }

    /** Forgets the taps: the next one starts a new sequence. */
    fun reset() {
        taps.clear()
    }

    /**
     * The middle of the numbers.
     *
     * With an even count there are two middle ones, and their average is the middle.
     *
     * @param values the numbers to sort and pick from.
     * @return the median.
     */
    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    companion object {
        /**
         * How long a round lives without a tap: three and a half seconds.
         *
         * The slowest tempo the app holds is 20 BPM, and its beats are three seconds apart, so
         * the window has to be longer than that: a tempo at the bottom of the range must still
         * be tappable. Half a second of slack absorbs the jitter of a human tap, and a longer
         * pause starts a new round.
         */
        const val IDLE_MS = 3_500

        /** The same window in nanoseconds, the unit of the clock. */
        const val IDLE_NANOS = IDLE_MS * 1_000_000L

        /** The nanoseconds in a minute. */
        private const val MINUTE_NANOS = 60_000_000_000.0
    }
}
