// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RhythmEdgeTest {
    @Test
    fun `time signature parse rejects missing slash`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("44") }
    }

    @Test
    fun `time signature parse rejects non-numeric parts`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("ab/cd") }
    }

    @Test
    fun `time signature parse rejects invalid beat unit`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("4/3") }
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("4/6") }
    }

    @Test
    fun `time signature parse rejects zero beats`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("0/4") }
    }

    @Test
    fun `decompose rejects zero`() {
        assertFailsWith<IllegalArgumentException> { decompose(0) }
    }

    @Test
    fun `decompose rejects negative`() {
        assertFailsWith<IllegalArgumentException> { decompose(-5) }
    }

    @Test
    fun `quantize ignores taps before count-in and after end`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        // totalCells = 8, cellMs = 500. cell 0 is at 0ms, cell 7 at 3500ms
        val taps =
            listOf(
                Tap(-5000.0, TapKind.NOTE), // before count-in
                Tap(5000.0, TapKind.NOTE), // after end (4 bars * 8 cells * 500ms = 4000ms end... wait)
            )
        val result = quantize(taps, config)
        assertEquals(0, result.onsets.size)
    }

    @Test
    fun `quantizeHeld ignores notes outside recording window`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        val notes =
            listOf(
                HeldNote(-1000.0, -500.0), // before start
                HeldNote(10000.0, 11000.0), // after end
            )
        val result = quantizeHeld(notes, config, 2)
        assertEquals(0, result.onsets.size)
    }

    @Test
    fun `transcribe with single tap produces one whole note`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        val result = transcribe(listOf(Tap(0.0, TapKind.NOTE)), config)
        assertEquals("c1 |", result.lilypond)
    }

    @Test
    fun `transcribe with no taps produces full bar rest`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        val result = transcribe(emptyList(), config)
        assertEquals("r1 |", result.lilypond)
    }

    @Test
    fun `buildEvents with single onset on cell 0 produces one event`() {
        val events = buildEvents(listOf(Onset(0, TapKind.NOTE)), 8)
        assertEquals(1, events.size)
        assertEquals(Event(TapKind.NOTE, 0, 8), events[0])
    }

    @Test
    fun `buildEvents with onset on cell 4 splits into two events`() {
        val events = buildEvents(listOf(Onset(4, TapKind.NOTE)), 8)
        assertEquals(2, events.size)
        assertEquals(Event(TapKind.REST, 0, 4), events[0])
        assertEquals(Event(TapKind.NOTE, 4, 4), events[1])
    }

    @Test
    fun `layout splits note crossing barline with tie`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 2, 0.0, "c")
        // 8 cells per bar, cellUnits = 8. Note from cell 6 to cell 12 crosses barline at cell 8
        val events = listOf(Event(TapKind.NOTE, 6, 6))
        val bars = layout(events, config)
        assertEquals(2, bars.size)
        // bar 0: cells 6-7 -> 2 cells * 8 units = 16 units (eighth note) with tie
        // bar 1: cells 8-11 -> 4 cells * 8 units = 32 units (half note)
        assertEquals(listOf(Atom(TapKind.NOTE, 16, tieToNext = true)), bars[0])
        assertEquals(listOf(Atom(TapKind.NOTE, 32, tieToNext = false)), bars[1])
    }
}
