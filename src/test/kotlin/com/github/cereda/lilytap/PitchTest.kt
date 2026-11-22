// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pitch vocabulary: the names that are read, the names that are written, and the numbers
 * behind them.
 */
class PitchTest {
    // -------------------------------------------------------------------------------------------
    // Reading a LilyPond name
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a letter without a mark is the C below middle C`() {
        assertEquals(Pitch(PitchStep.C, 0, 3), Pitch.parse("c"))
    }

    @Test
    fun `every letter is read in the same octave`() {
        assertEquals(Pitch(PitchStep.D, 0, 3), Pitch.parse("d"))
        assertEquals(Pitch(PitchStep.E, 0, 3), Pitch.parse("e"))
        assertEquals(Pitch(PitchStep.F, 0, 3), Pitch.parse("f"))
        assertEquals(Pitch(PitchStep.G, 0, 3), Pitch.parse("g"))
        assertEquals(Pitch(PitchStep.A, 0, 3), Pitch.parse("a"))
        assertEquals(Pitch(PitchStep.B, 0, 3), Pitch.parse("b"))
    }

    @Test
    fun `is is a sharp and es is a flat`() {
        assertEquals(Pitch(PitchStep.C, 1, 3), Pitch.parse("cis"))
        assertEquals(Pitch(PitchStep.C, -1, 3), Pitch.parse("ces"))
        assertEquals(Pitch(PitchStep.F, 1, 3), Pitch.parse("fis"))
        assertEquals(Pitch(PitchStep.B, -1, 3), Pitch.parse("bes"))
    }

    @Test
    fun `an apostrophe goes up and a comma goes down`() {
        assertEquals(Pitch(PitchStep.C, 0, 4), Pitch.parse("c'"))
        assertEquals(Pitch(PitchStep.C, 0, 5), Pitch.parse("c''"))
        assertEquals(Pitch(PitchStep.C, 0, 2), Pitch.parse("c,"))
        assertEquals(Pitch(PitchStep.C, 0, 1), Pitch.parse("c,,"))
    }

    @Test
    fun `middle C is written with an apostrophe`() {
        assertEquals(Pitch(PitchStep.C, 0, 4), Pitch.parse("c'"))
        assertEquals(Pitch(PitchStep.C, 1, 4), Pitch.parse("cis'"))
        assertEquals(Pitch(PitchStep.C, -1, 4), Pitch.parse("ces'"))
    }

    @Test
    fun `the accidental and the octave marks go together`() {
        assertEquals(Pitch(PitchStep.C, 1, 4), Pitch.parse("cis'"))
        assertEquals(Pitch(PitchStep.C, -1, 2), Pitch.parse("ces,"))
        assertEquals(Pitch(PitchStep.F, 1, 1), Pitch.parse("fis,,"))
    }

    @Test
    fun `spaces around a name do not matter`() {
        assertEquals(Pitch(PitchStep.C, 0, 3), Pitch.parse("  c  "))
        assertEquals(Pitch(PitchStep.C, 1, 3), Pitch.parse(" cis "))
    }

    @Test
    fun `a name that is not a note is not read`() {
        assertNull(Pitch.parse(""))
        assertNull(Pitch.parse("   "))
        assertNull(Pitch.parse("h"))
        assertNull(Pitch.parse("C"))
        assertNull(Pitch.parse("c4"))
        assertNull(Pitch.parse("cis#"))
        assertNull(Pitch.parse("cises"))
        assertNull(Pitch.parse("c!"))
        assertNull(Pitch.parse("4"))
        assertNull(Pitch.parse("c d"))
        assertNull(Pitch.parse("is"))
    }

    @Test
    fun `a name outside the keyboard is not read`() {
        assertNull(Pitch.parse("c" + "'".repeat(10)))
        assertNull(Pitch.parse("c" + ",".repeat(10)))
    }

    @Test
    fun `the lowest and the highest notes of the keyboard are read`() {
        assertEquals(Pitch(PitchStep.C, 0, 0), Pitch.parse("c" + ",".repeat(3)))
        assertEquals(Pitch(PitchStep.C, 0, 8), Pitch.parse("c" + "'".repeat(5)))
    }

    @Test
    fun `a note below the keyboard is not read`() {
        assertNull(Pitch.parse("c" + ",".repeat(4)), "C-1 is not a note the app writes")
    }

    @Test
    fun `the octave of a pitch is between C0 and C8`() {
        assertEquals(0, Pitch.MIN_OCTAVE)
        assertEquals(8, Pitch.MAX_OCTAVE)
    }

    // -------------------------------------------------------------------------------------------
    // Writing the names
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a pitch is written the way LilyPond writes it`() {
        assertEquals("c'", Pitch(PitchStep.C, 0, 4).lilypond)
        assertEquals("cis'", Pitch(PitchStep.C, 1, 4).lilypond)
        assertEquals("ces'", Pitch(PitchStep.C, -1, 4).lilypond)
        assertEquals("c''", Pitch(PitchStep.C, 0, 5).lilypond)
        assertEquals("c'''", Pitch(PitchStep.C, 0, 6).lilypond)
        assertEquals("c", Pitch(PitchStep.C, 0, 3).lilypond)
        assertEquals("c,", Pitch(PitchStep.C, 0, 2).lilypond)
        assertEquals("c,,", Pitch(PitchStep.C, 0, 1).lilypond)
        assertEquals("cis''", Pitch(PitchStep.C, 1, 5).lilypond)
        assertEquals("bes,", Pitch(PitchStep.B, -1, 2).lilypond)
    }

    @Test
    fun `a pitch is written in scientific notation too`() {
        assertEquals("C4", Pitch(PitchStep.C, 0, 4).scientific)
        assertEquals("C♯4", Pitch(PitchStep.C, 1, 4).scientific)
        assertEquals("C♭4", Pitch(PitchStep.C, -1, 4).scientific)
        assertEquals("C5", Pitch(PitchStep.C, 0, 5).scientific)
        assertEquals("C3", Pitch(PitchStep.C, 0, 3).scientific)
        assertEquals("A4", Pitch(PitchStep.A, 0, 4).scientific)
        assertEquals("B♭3", Pitch(PitchStep.B, -1, 3).scientific)
    }

    @Test
    fun `a name survives the round trip`() {
        val pitches =
            listOf(
                Pitch(PitchStep.C, 0, 4),
                Pitch(PitchStep.C, 1, 4),
                Pitch(PitchStep.C, -1, 4),
                Pitch(PitchStep.C, 0, 5),
                Pitch(PitchStep.C, 0, 3),
                Pitch(PitchStep.D, 1, 2),
                Pitch(PitchStep.E, -1, 7),
                Pitch(PitchStep.F, 0, 8),
                Pitch(PitchStep.G, 1, 0),
                Pitch(PitchStep.A, -1, 0),
                Pitch(PitchStep.B, 0, 4),
            )
        for (pitch in pitches) {
            assertEquals(pitch, Pitch.parse(pitch.lilypond), "the name of $pitch should read back")
        }
    }

    // -------------------------------------------------------------------------------------------
    // The numbers behind a pitch
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the MIDI number of a pitch is the one of a keyboard`() {
        assertEquals(60, Pitch(PitchStep.C, 0, 4).midi)
        assertEquals(61, Pitch(PitchStep.C, 1, 4).midi)
        assertEquals(59, Pitch(PitchStep.C, -1, 4).midi)
        assertEquals(69, Pitch(PitchStep.A, 0, 4).midi)
        assertEquals(72, Pitch(PitchStep.C, 0, 5).midi)
        assertEquals(48, Pitch(PitchStep.C, 0, 3).midi)
        assertEquals(71, Pitch(PitchStep.B, 0, 4).midi)
    }

    @Test
    fun `the frequency of a pitch is the one of a tuning fork`() {
        assertEquals(440.0, Pitch(PitchStep.A, 0, 4).frequency, 0.0001)
        assertEquals(261.6256, Pitch(PitchStep.C, 0, 4).frequency, 0.001)
        assertEquals(523.2511, Pitch(PitchStep.C, 0, 5).frequency, 0.001)
        assertEquals(277.1826, Pitch(PitchStep.C, 1, 4).frequency, 0.001)
    }

    @Test
    fun `an octave doubles the frequency`() {
        val low = Pitch(PitchStep.D, 0, 4)
        val high = Pitch(PitchStep.D, 0, 5)
        assertEquals(2.0, high.frequency / low.frequency, 0.0001)
    }

    // -------------------------------------------------------------------------------------------
    // The shape of a pitch
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a pitch has at most one sharp or one flat`() {
        assertNull(Pitch.parse("cisis"))
        assertNull(Pitch.parse("ceses"))
    }

    @Test
    fun `the default pitch is middle C`() {
        assertEquals(60, Pitch.DEFAULT.midi)
        assertEquals("c'", Pitch.DEFAULT.lilypond)
        assertEquals("C4", Pitch.DEFAULT.scientific)
    }

    @Test
    fun `the step of a letter is found`() {
        assertEquals(PitchStep.C, PitchStep.of('c'))
        assertEquals(PitchStep.B, PitchStep.of('b'))
        assertNull(PitchStep.of('C'))
        assertNull(PitchStep.of('h'))
        assertNull(PitchStep.of('4'))
    }

    @Test
    fun `the letters of the scale are the seven of the piano`() {
        assertEquals(7, PitchStep.entries.size)
        assertTrue(PitchStep.entries.all { it.letter in 'a'..'g' })
    }
}
