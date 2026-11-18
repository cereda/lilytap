// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The score model: the notes and rests of a take, and the edits you can make to them.
 */
class ScoreTest {
    // 4/4, 60 BPM, 2 cells per beat => 8 cells per bar, 500 ms per cell
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0, "c")

    private fun score(
        vararg events: Event,
        config: Config = cfg(),
    ) = Score(events.toList(), config)

    private fun note(
        start: Int,
        length: Int,
    ) = Event(TapKind.NOTE, start, length)

    private fun rest(
        start: Int,
        length: Int,
    ) = Event(TapKind.REST, start, length)

    // -------------------------------------------------------------------------------------------
    // The shape of a score
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a score needs at least one event`() {
        assertFailsWith<IllegalArgumentException> { Score(emptyList(), cfg()) }
    }

    @Test
    fun `the events of a score must be contiguous`() {
        assertFailsWith<IllegalArgumentException> {
            Score(listOf(note(0, 2), note(3, 5)), cfg())
        }
    }

    @Test
    fun `a score must cover every cell of the recording`() {
        assertFailsWith<IllegalArgumentException> { Score(listOf(note(0, 4)), cfg()) }
    }

    @Test
    fun `an event must last at least one cell`() {
        assertFailsWith<IllegalArgumentException> {
            Score(listOf(note(0, 0), note(0, 8)), cfg())
        }
    }

    @Test
    fun `two rests in a row are rejected, a silence is one event`() {
        assertFailsWith<IllegalArgumentException> {
            Score(listOf(rest(0, 4), rest(4, 4)), cfg())
        }
    }

    @Test
    fun `the events add up to the whole recording`() {
        val s = score(note(0, 4), note(4, 4))
        assertEquals(8, s.totalCells)
        assertEquals(8, s.events.sumOf { it.lengthCells })
    }

    // -------------------------------------------------------------------------------------------
    // Turning a note into a rest
    // -------------------------------------------------------------------------------------------

    @Test
    fun `toggling a note turns it into a rest`() {
        val edited = score(note(0, 4), note(4, 4)).toggleRest(0)
        assertEquals(listOf(rest(0, 4), note(4, 4)), edited.events)
        assertEquals("r2 c2 |", edited.lilypond)
    }

    @Test
    fun `toggling a rest turns it back into a note`() {
        val edited = score(rest(0, 4), note(4, 4)).toggleRest(0)
        assertEquals(listOf(note(0, 4), note(4, 4)), edited.events)
        assertEquals("c2 c2 |", edited.lilypond)
    }

    @Test
    fun `a note that becomes a rest joins the rest next to it`() {
        val edited = score(note(0, 2), rest(2, 2), note(4, 4)).toggleRest(0)
        assertEquals(listOf(rest(0, 4), note(4, 4)), edited.events)
        assertEquals("r2 c2 |", edited.lilypond)
    }

    @Test
    fun `toggling an event that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.toggleRest(1) }
        assertFailsWith<IllegalArgumentException> { s.toggleRest(-1) }
    }

    @Test
    fun `editing a score leaves the original alone`() {
        val s = score(note(0, 4), note(4, 4))
        s.toggleRest(0)
        s.moveBoundary(0, 1)
        assertEquals(listOf(note(0, 4), note(4, 4)), s.events)
    }

    // -------------------------------------------------------------------------------------------
    // Dragging a border
    // -------------------------------------------------------------------------------------------

    @Test
    fun `moving a border grows the left event and shrinks the right one`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, 2)
        assertEquals(listOf(note(0, 6), note(6, 2)), edited.events)
        assertEquals("c2. c4 |", edited.lilypond)
    }

    @Test
    fun `moving a border the other way shrinks the left event`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, -2)
        assertEquals(listOf(note(0, 2), note(2, 6)), edited.events)
    }

    @Test
    fun `moving a border keeps the bar full`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, -3)
        assertEquals(8, edited.totalCells)
        assertEquals(8, edited.events.sumOf { it.lengthCells })
        assertEquals(edited.events.last().endCell, edited.totalCells)
    }

    @Test
    fun `a border stops when an event would vanish`() {
        val s = score(note(0, 4), note(4, 4))
        assertEquals(listOf(note(0, 7), note(7, 1)), s.moveBoundary(0, 10).events)
        assertEquals(listOf(note(0, 1), note(1, 7)), s.moveBoundary(0, -10).events)
    }

    @Test
    fun `a border can cross a bar line`() {
        val s = score(note(0, 6), note(6, 10), config = cfg(bars = 2))
        val edited = s.moveBoundary(0, 4)
        assertEquals(listOf(note(0, 10), note(10, 6)), edited.events)
        // The first note now runs over the bar line, so it is tied.
        assertEquals("c1 ~ |\nc4 c2. |", edited.lilypond)
    }

    @Test
    fun `moving a border that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.moveBoundary(0, 1) }
        assertFailsWith<IllegalArgumentException> { s.moveBoundary(1, 1) }
    }

    // -------------------------------------------------------------------------------------------
    // Snapping
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a border snaps to the nearest cell`() {
        val s = score(note(0, 8))
        assertEquals(2, s.snapBorder(2.4))
        assertEquals(3, s.snapBorder(2.6))
    }

    @Test
    fun `a bar line pulls a border that is dragged close to it`() {
        val s = score(note(0, 8))
        // Cell 7 is close enough to the bar line at cell 8: the bar line wins.
        assertEquals(8, s.snapBorder(7.4))
        assertEquals(6, s.snapBorder(6.4))
    }

    @Test
    fun `a border never leaves the score`() {
        val s = score(note(0, 8))
        assertEquals(0, s.snapBorder(-3.0))
        assertEquals(8, s.snapBorder(20.0))
    }

    @Test
    fun `the nearest bar line is the one closest to the position`() {
        val s = score(note(0, 16), config = cfg(bars = 2))
        assertEquals(8, s.nearestBarLine(7.0))
        assertEquals(16, s.nearestBarLine(13.0))
        assertEquals(0, s.nearestBarLine(-1.0))
    }

    // -------------------------------------------------------------------------------------------
    // The score of a take
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the transcription carries the score of the take`() {
        val config = cfg()
        val result = transcribe(listOf(Tap(0.0, TapKind.NOTE), Tap(1000.0, TapKind.NOTE)), config)
        assertEquals(listOf(note(0, 2), note(2, 6)), result.score.events)
        assertEquals("c4 c2. |", result.lilypond)
        assertEquals(result.score.lilypond, result.lilypond)
    }

    @Test
    fun `an edited score has a text of its own`() {
        val config = cfg()
        val result = transcribe(listOf(Tap(0.0, TapKind.NOTE), Tap(1000.0, TapKind.NOTE)), config)
        val edited = result.score.toggleRest(1)
        assertEquals("c4 r2. |", edited.lilypond)
        assertEquals("c4 c2. |", result.lilypond)
    }
}
