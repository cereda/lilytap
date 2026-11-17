// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals

class HeldNotesTest {
    // 4/4, 60 BPM, 2 cells per beat => 500 ms per cell, smallest rest = 1 beat = 2 cells
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0, "c")

    private fun held(vararg p: Pair<Int, Int>) = p.map { HeldNote(it.first.toDouble(), it.second.toDouble()) }

    private fun lily(
        notes: List<HeldNote>,
        minRest: Int = 2,
        config: Config = cfg(),
    ) = transcribeHeld(notes, config, minRest).lilypond

    @Test
    fun `slightly detached quarter notes stay quarter notes`() {
        assertEquals("c4 c4 c4 c4 |", lily(held(0 to 800, 1000 to 1800, 2000 to 2800, 3000 to 3800)))
    }

    @Test
    fun `a gap shorter than the smallest rest is absorbed`() {
        assertEquals("c4 c4 c4 c4 |", lily(held(0 to 600, 1000 to 1600, 2000 to 2600, 3000 to 3600)))
    }

    @Test
    fun `holding a key gives a longer note`() {
        assertEquals("c2 c4 c4 |", lily(held(0 to 1900, 2000 to 2900, 3000 to 3900)))
    }

    @Test
    fun `a real gap becomes a rest`() {
        assertEquals("c4 r4 c4 c4 |", lily(held(0 to 900, 2000 to 2900, 3000 to 3900)))
    }

    @Test
    fun `smaller rests can be allowed`() {
        assertEquals("c8 r8 c8 r2 r8 |", lily(held(0 to 400, 1000 to 1400), minRest = 1))
    }

    @Test
    fun `overlapping notes are cut at the next press`() {
        assertEquals("c2 c2 |", lily(held(0 to 2600, 2000 to 3900)))
    }

    @Test
    fun `late first press gives a leading rest`() {
        assertEquals("r4 c4 c2 |", lily(held(1000 to 1900, 2000 to 3900)))
    }

    @Test
    fun `nothing pressed is a full bar of rest`() {
        assertEquals("r1 |", lily(emptyList()))
    }

    @Test
    fun `held note across the barline is tied`() {
        assertEquals("r2 c2 ~ |\nc2 r2 |", lily(held(2000 to 5900), config = cfg(bars = 2)))
    }

    @Test
    fun `snapped notes report grid cells and keep the raw press`() {
        val notes = held(0 to 900, 2000 to 2900)
        val snapped = transcribeHeld(notes, cfg(), 2).snapped
        // 500 ms cells: first note 0..2 (then a real gap = rest), second starts at cell 4
        assertEquals(listOf(SnappedNote(0, 2, notes[0]), SnappedNote(4, 6, notes[1])), snapped)
    }

    @Test
    fun `an absorbed gap extends the snapped note to the next press`() {
        val notes = held(0 to 600, 1000 to 1600)
        val snapped = transcribeHeld(notes, cfg(), 2).snapped
        assertEquals(SnappedNote(0, 2, notes[0]), snapped[0])
    }

    @Test
    fun `a rest lasts until the next press`() {
        val notes = listOf(HeldNote(0.0, 0.0, TapKind.REST), HeldNote(1000.0, 1900.0))
        assertEquals("r4 c4 r2 |", lily(notes))
    }

    @Test
    fun `two rests in a row are written as a single rest`() {
        val notes = listOf(HeldNote(0.0, 0.0, TapKind.REST), HeldNote(1000.0, 0.0, TapKind.REST))
        assertEquals("r1 |", lily(notes))
    }

    @Test
    fun `a rest after a note keeps the silence in one piece`() {
        val notes = listOf(HeldNote(0.0, 900.0), HeldNote(2000.0, 2000.0, TapKind.REST))
        // The note ends in cell 2, the gap is long enough for a rest, and the rest that was
        // pressed continues the same silence.
        assertEquals("c4 r2. |", lily(notes))
    }
}
