// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "no rests" mode: every silence is absorbed into the note before it, so the transcription
 * never contains a rest. A silence before the very first note has no note to join and stays.
 */
class NoRestsTest {
    // 4/4, 60 BPM, 2 cells per beat => 500 ms per cell, 8 cells per bar
    private fun cfg(noRests: Boolean = true) = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c", noRests)

    private fun tap(
        ms: Int,
        kind: TapKind = TapKind.NOTE,
    ) = Tap(ms.toDouble(), kind)

    private fun lily(
        taps: List<Tap>,
        noRests: Boolean = true,
    ) = transcribe(taps, cfg(noRests)).lilypond

    @Test
    fun `a rest tap is absorbed into the note before it`() {
        val taps = listOf(tap(0), tap(1000, TapKind.REST), tap(2000), tap(3000))
        assertEquals("c2 c4 c4 |", lily(taps))
    }

    @Test
    fun `without the option the rest is written as before`() {
        val taps = listOf(tap(0), tap(1000, TapKind.REST), tap(2000), tap(3000))
        assertEquals("c4 r4 c4 c4 |", lily(taps, noRests = false))
    }

    @Test
    fun `a rest before the first note stays a rest`() {
        val taps = listOf(tap(0, TapKind.REST), tap(2000), tap(3000))
        assertEquals("r2 c4 c4 |", lily(taps))
    }

    @Test
    fun `a gap before the first note stays a rest`() {
        val taps = listOf(tap(1000), tap(2000))
        assertEquals("r4 c4 c2 |", lily(taps))
    }

    @Test
    fun `several rests in a row are all absorbed`() {
        val taps = listOf(tap(0), tap(1000, TapKind.REST), tap(1500, TapKind.REST), tap(2000))
        assertEquals("c2 c2 |", lily(taps))
    }

    @Test
    fun `a held note absorbs the silence and the rest press after it`() {
        val notes =
            listOf(
                HeldNote(0.0, 900.0),
                HeldNote(2000.0, 2000.0, TapKind.REST),
                HeldNote(3000.0, 3900.0),
            )
        assertEquals("c2. c4 |", transcribeHeld(notes, cfg(), 2).lilypond)
    }

    @Test
    fun `without the option the held note keeps its rest`() {
        val notes =
            listOf(
                HeldNote(0.0, 900.0),
                HeldNote(2000.0, 2000.0, TapKind.REST),
                HeldNote(3000.0, 3900.0),
            )
        assertEquals("c4 r2 c4 |", transcribeHeld(notes, cfg(noRests = false), 2).lilypond)
    }

    @Test
    fun `the snapped notes match the merged notation`() {
        val notes =
            listOf(
                HeldNote(0.0, 900.0),
                HeldNote(2000.0, 2000.0, TapKind.REST),
                HeldNote(3000.0, 3900.0),
            )
        val snapped = transcribeHeld(notes, cfg(), 2).snapped
        // The note runs from cell 0 to the next note at cell 6: the rest press in between is gone.
        assertEquals(listOf(SnappedNote(0, 6, notes[0]), SnappedNote(6, 8, notes[2])), snapped)
    }

    @Test
    fun `a rest press before the first note stays a rest`() {
        val notes = listOf(HeldNote(0.0, 0.0, TapKind.REST), HeldNote(1000.0, 1900.0))
        // The rest covers the first two cells, the note runs on to the end of the bar.
        assertEquals("r4 c2. |", transcribeHeld(notes, cfg(), 2).lilypond)
    }
}
