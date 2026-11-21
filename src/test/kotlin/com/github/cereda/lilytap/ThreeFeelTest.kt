// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The 3-feel: the click of a compound meter falls on every note value (the eighth in 6/8)
 * instead of on the musical beat, so the subdivision of the beat is audible. The beat itself
 * does not move: the tempo, the count-in, the beat dots and the score stay what they were.
 */
class ThreeFeelTest {
    private val simple = Config(60.0, TimeSignature(4, 4), 1, 1)
    private val compound = Config(60.0, TimeSignature(6, 8), 1, 1)
    private val threeFeel = compound.copy(threeFeel = true)

    @Test
    fun `the click falls on the beat`() {
        assertEquals(16, simple.clickUnits) // a quarter
        assertEquals(24, compound.clickUnits) // a dotted quarter
        assertEquals(1000.0, simple.clickMs)
        assertEquals(1000.0, compound.clickMs)
    }

    @Test
    fun `the 3-feel clicks every note value`() {
        assertEquals(8, threeFeel.clickUnits) // an eighth
        assertEquals(1000.0 / 3, threeFeel.clickMs)
    }

    @Test
    fun `the 3-feel changes nothing in a simple meter`() {
        val config = simple.copy(threeFeel = true)
        assertEquals(simple.clickUnits, config.clickUnits)
        assertEquals(simple.clickMs, config.clickMs)
        assertEquals(simple.clicksPerBar, config.clicksPerBar)
    }

    @Test
    fun `the click does not follow the grid cells`() {
        // The cells may be finer than the note value (sixteenths in 6/8), but the click still
        // falls on the eighth: the 3-feel is a feel, not a grid resolution.
        val config = threeFeel.copy(subdivisions = 4)
        assertEquals(1000.0 / 3, config.clickMs)
    }

    @Test
    fun `the beat does not move`() {
        assertEquals(compound.pulseMs, threeFeel.pulseMs)
        assertEquals(compound.cellsPerPulse, threeFeel.cellsPerPulse)
        assertEquals(compound.cellsPerBar, threeFeel.cellsPerBar)
        assertEquals(compound.totalCells, threeFeel.totalCells)
    }

    @Test
    fun `the bar holds six clicks with the 3-feel`() {
        assertEquals(4, simple.clicksPerBar)
        assertEquals(2, compound.clicksPerBar)
        assertEquals(6, threeFeel.clicksPerBar)
    }

    @Test
    fun `the count-in is one bar of clicks`() {
        // Four bars of 6/8: a bar of count-in and four bars of take. The bar of the count-in
        // holds two clicks without the 3-feel and six with it.
        val config = threeFeel.copy(bars = 4)
        assertEquals(30, config.totalClicks)
        assertEquals(10, compound.copy(bars = 4).totalClicks)
    }

    @Test
    fun `the accent marks the bar when the click is the beat`() {
        // One click per beat: the downbeat is the beat, so it takes the accent.
        assertEquals(ClickAccent.BEAT, clickAccent(0, 1, 4))
        assertEquals(ClickAccent.NORMAL, clickAccent(1, 1, 4))
        assertEquals(ClickAccent.NORMAL, clickAccent(2, 1, 4))
        assertEquals(ClickAccent.BEAT, clickAccent(4, 1, 4))
    }

    @Test
    fun `the accent marks the beat when the click is finer`() {
        // In a 3-feel 6/8 the click falls on the eighth, the accent on the dotted quarter,
        // so the click and the beat dots agree, and the downbeat has a mark of its own.
        assertEquals(ClickAccent.DOWNBEAT, clickAccent(0, 3, 6))
        assertEquals(ClickAccent.NORMAL, clickAccent(1, 3, 6))
        assertEquals(ClickAccent.NORMAL, clickAccent(2, 3, 6))
        assertEquals(ClickAccent.BEAT, clickAccent(3, 3, 6))
        assertEquals(ClickAccent.NORMAL, clickAccent(4, 3, 6))
        assertEquals(ClickAccent.NORMAL, clickAccent(5, 3, 6))
        assertEquals(ClickAccent.DOWNBEAT, clickAccent(6, 3, 6))
    }

    @Test
    fun `a compound meter of three groups marks every group`() {
        // 9/8 with the 3-feel: nine clicks a bar, the beat on every third one.
        assertEquals(ClickAccent.DOWNBEAT, clickAccent(0, 3, 9))
        assertEquals(ClickAccent.BEAT, clickAccent(3, 3, 9))
        assertEquals(ClickAccent.BEAT, clickAccent(6, 3, 9))
        assertEquals(ClickAccent.DOWNBEAT, clickAccent(9, 3, 9))
    }

    @Test
    fun `only the 3-feel in a compound meter marks the downbeat`() {
        // 6/8 without the 3-feel: the click is the beat, so the accent is the downbeat and
        // the bar keeps the accent it has always had.
        assertEquals(ClickAccent.BEAT, clickAccent(0, 1, 2))
        assertEquals(ClickAccent.NORMAL, clickAccent(1, 1, 2))
        assertEquals(ClickAccent.BEAT, clickAccent(2, 1, 2))
    }

    @Test
    fun `the score of a 3-feel take is the score of the take`() {
        // The click is finer, but the transcription is the same: the taps are still written on
        // the grid of the note value.
        val taps = (0 until 6).map { Tap(it * 1000.0 / 3, TapKind.NOTE) }
        assertEquals(transcribe(taps, compound).lilypond, transcribe(taps, threeFeel).lilypond)
        assertEquals("c'8 c'8 c'8 c'8 c'8 c'8 |", transcribe(taps, threeFeel).lilypond)
    }
}
