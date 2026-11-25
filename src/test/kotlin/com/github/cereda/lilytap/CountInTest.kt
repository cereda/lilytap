// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The count-in before a take: a whole number of bars, and never shorter than two seconds.
 *
 * The count-in is there to let the player find the beat and settle into it, so at a fast
 * tempo - where a bar passes in a moment - it grows until there is time to lock on to it.
 */
class CountInTest {
    private val simple = Config(60.0, TimeSignature(4, 4), 1, 1)
    private val compound = Config(60.0, TimeSignature(6, 8), 1, 1)

    @Test
    fun `a slow bar is one bar of count-in`() {
        assertEquals(1, simple.countInBars)
        assertEquals(4, simple.countInBeats)
        assertEquals(8, simple.totalClicks) // four of the count-in, four of the take
    }

    @Test
    fun `a bar of two seconds is still one bar`() {
        // 4/4 at 120 BPM: a bar lasts exactly two seconds.
        val config = Config(120.0, TimeSignature(4, 4), 1, 1)
        assertEquals(1, config.countInBars)
        assertEquals(4, config.countInBeats)
    }

    @Test
    fun `a fast bar grows the count-in`() {
        // 4/4 at 180 BPM: a bar lasts 1.33 s, so the count-in takes two bars.
        val config = Config(180.0, TimeSignature(4, 4), 1, 1)
        assertEquals(2, config.countInBars)
        assertEquals(8, config.countInBeats)
        assertEquals(12, config.totalClicks) // eight of the count-in, four of the take
    }

    @Test
    fun `the count-in is never shorter than two seconds`() {
        for (bpm in listOf(20.0, 60.0, 90.0, 120.0, 150.0, 180.0, 240.0, 300.0)) {
            for (
            signature in
            listOf(
                TimeSignature(4, 4),
                TimeSignature(3, 4),
                TimeSignature(6, 8),
                TimeSignature(5, 8),
                TimeSignature(12, 8),
            )
            ) {
                val config = Config(bpm, signature, 1, 1)
                val ms = config.countInBeats * config.pulseMs
                assertTrue(
                    ms >= MIN_COUNT_IN_MS - 0.001,
                    "the count-in of $signature at $bpm BPM lasts ${ms / 1000.0} s",
                )
            }
        }
    }

    @Test
    fun `the count-in is a whole number of bars`() {
        val config = Config(300.0, TimeSignature(6, 8), 1, 1)
        assertEquals(5, config.countInBars)
        assertEquals(
            0,
            config.countInBeats % config.timeSignature.pulsesPerBar,
            "the count-in holds whole bars",
        )
    }

    @Test
    fun `the downbeat of the take falls on a bar line of the clicks`() {
        // The count-in is a whole number of bars, so the first click of the take is the
        // downbeat of the take, and the accent marks it.
        val config = Config(180.0, TimeSignature(6, 8), 1, 1, threeFeel = true)
        val first = config.countInBars * config.clicksPerBar
        assertEquals(ClickAccent.DOWNBEAT, clickAccent(first, config.clicksPerBeat, config.clicksPerBar))
    }

    @Test
    fun `the simple meter accents the downbeat of the take`() {
        val config = Config(180.0, TimeSignature(4, 4), 1, 1)
        val first = config.countInBars * config.clicksPerBar
        assertEquals(ClickAccent.BEAT, clickAccent(first, config.clicksPerBeat, config.clicksPerBar))
    }

    @Test
    fun `the count-in of a compound meter counts its own beats`() {
        // 6/8 at 120 BPM: a bar lasts a second, so the count-in is two bars, four beats.
        val config = compound.copy(bpm = 120.0)
        assertEquals(2, config.countInBars)
        assertEquals(4, config.countInBeats)
    }

    @Test
    fun `the calibration keeps its own count-in`() {
        // The calibration runs at 60 BPM in 4/4: a bar is four seconds, so it is one bar.
        val config = Config(60.0, TimeSignature(4, 4), 1, 2)
        assertEquals(1, config.countInBars)
    }
}
