// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The intervals the score is transposed by, and the keys they land in.
 */
class TranspositionTest {
    /** The key of a name: "Bb major" is the key of B flat. */
    private fun key(name: String): KeySignature = KeySignature.parse(name) ?: error("the name $name is not a key")

    /** A transposition up by an interval. */
    private fun up(interval: Interval): Transposition = Transposition(interval, true)

    /** A transposition down by an interval. */
    private fun down(interval: Interval): Transposition = Transposition(interval, false)

    /** Every key the app holds, with its name. */
    private val keys =
        listOf(
            "C major",
            "G major",
            "D major",
            "A major",
            "E major",
            "B major",
            "F# major",
            "C# major",
            "F major",
            "Bb major",
            "Eb major",
            "Ab major",
            "Db major",
            "Gb major",
            "Cb major",
            "A minor",
            "E minor",
            "B minor",
            "F# minor",
            "C# minor",
            "D minor",
            "G minor",
            "C minor",
            "F minor",
            "Bb minor",
            "Eb minor",
            "Ab minor",
        ).map { key(it) }

    // -------------------------------------------------------------------------------------------
    // The size of the intervals
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a third is a major third in a major key and a minor third in a minor one`() {
        assertEquals(4, up(Interval.THIRD).semitones(key("C major")))
        assertEquals(3, up(Interval.THIRD).semitones(key("C minor")))
        assertEquals(-4, down(Interval.THIRD).semitones(key("C major")))
        assertEquals(-3, down(Interval.THIRD).semitones(key("C minor")))
    }

    @Test
    fun `the other intervals are the same in both scales`() {
        val same = listOf(Interval.SECOND, Interval.FOURTH, Interval.FIFTH, Interval.OCTAVE, Interval.SEMITONE)
        for (interval in same) {
            assertEquals(interval.majorSemitones, interval.semitones(false), interval.label)
        }
    }

    @Test
    fun `the interval moves the letters of the notes`() {
        assertEquals(1, up(Interval.SECOND).steps)
        assertEquals(2, up(Interval.THIRD).steps)
        assertEquals(3, up(Interval.FOURTH).steps)
        assertEquals(4, up(Interval.FIFTH).steps)
        assertEquals(7, up(Interval.OCTAVE).steps)
        assertEquals(-2, down(Interval.THIRD).steps)
        assertEquals(0, up(Interval.SEMITONE).steps, "the semitone keeps the letters where they are")
    }

    @Test
    fun `the transposition says what it is`() {
        assertEquals("Up a major third", up(Interval.THIRD).name(key("C major")))
        assertEquals("Down a minor third", down(Interval.THIRD).name(key("C minor")))
        assertEquals("Up an octave", up(Interval.OCTAVE).name(key("C major")))
        assertEquals("Up a semitone", up(Interval.SEMITONE).name(key("C major")))
    }

    // -------------------------------------------------------------------------------------------
    // The key that follows
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the key follows the notes`() {
        assertEquals(key("E major"), key("C major").transposed(up(Interval.THIRD)))
        assertEquals(key("G major"), key("C major").transposed(up(Interval.FIFTH)))
        assertEquals(key("F major"), key("C major").transposed(up(Interval.FOURTH)))
        assertEquals(key("D major"), key("C major").transposed(up(Interval.SECOND)))
        assertEquals(key("C major"), key("C major").transposed(up(Interval.OCTAVE)))
    }

    @Test
    fun `a minor key stays a minor key`() {
        assertEquals(key("Eb minor"), key("C minor").transposed(up(Interval.THIRD)))
        assertEquals(key("F minor"), key("C minor").transposed(up(Interval.FOURTH)))
        assertEquals(key("G minor"), key("C minor").transposed(up(Interval.FIFTH)))
        assertEquals(key("D minor"), key("C minor").transposed(up(Interval.SECOND)))
        assertEquals(key("C minor"), key("C minor").transposed(up(Interval.OCTAVE)))
    }

    @Test
    fun `the key moves down as well`() {
        // The third of a major key is a major third, so C major down a third is A flat major.
        assertEquals(key("Ab major"), key("C major").transposed(down(Interval.THIRD)))
        assertEquals(key("G# minor"), key("B minor").transposed(down(Interval.THIRD)))
        assertEquals(key("D major"), key("G major").transposed(down(Interval.FOURTH)))
    }

    @Test
    fun `the mode is kept when the key moves`() {
        for (start in keys) {
            for (interval in Interval.entries) {
                val moved = start.transposed(up(interval)) ?: continue
                assertEquals(start.major, moved.major, "the mode of ${start.name} changed")
            }
        }
    }

    @Test
    fun `the semitone keeps the letter of the tonic`() {
        assertEquals(key("C# major"), key("C major").transposed(up(Interval.SEMITONE)))
        assertEquals(key("C# minor"), key("C minor").transposed(up(Interval.SEMITONE)))
    }

    @Test
    fun `the semitone moves the letter when the sound asks for it`() {
        assertEquals(key("D major"), key("C# major").transposed(up(Interval.SEMITONE)))
        assertEquals(key("B major"), key("C major").transposed(down(Interval.SEMITONE)))
        assertEquals(key("C major"), key("C# major").transposed(down(Interval.SEMITONE)))
    }

    @Test
    fun `a key the app cannot hold is refused`() {
        assertNull(key("C# major").transposed(up(Interval.FIFTH)), "G sharp major has eight sharps")
        assertNull(key("Cb major").transposed(down(Interval.FIFTH)), "F flat major has eight flats")
        assertNull(key("C# major").transposed(up(Interval.SECOND)), "D sharp major has nine sharps")
    }

    @Test
    fun `the refusal says how many accidentals the key would carry`() {
        assertEquals(8, key("C# major").fifthsAfter(up(Interval.FIFTH)))
        assertEquals(-8, key("Cb major").fifthsAfter(down(Interval.FIFTH)))
        assertEquals(9, key("C# major").fifthsAfter(up(Interval.SECOND)))
        assertEquals(1, key("C major").fifthsAfter(up(Interval.FIFTH)))
    }

    @Test
    fun `a key that fits is not refused`() {
        for (start in keys) {
            for (interval in Interval.entries) {
                for (upwards in listOf(true, false)) {
                    val transposition = Transposition(interval, upwards)
                    val moved = start.transposed(transposition)
                    if (moved == null) {
                        assertEquals(
                            true,
                            start.fifthsAfter(transposition) !in -7..7,
                            "${start.name} was refused for another reason",
                        )
                    } else {
                        assertEquals(start.fifthsAfter(transposition), moved.fifths)
                    }
                }
            }
        }
    }

    @Test
    fun `the way back is the way the notes came`() {
        val diatonic = listOf(Interval.SECOND, Interval.THIRD, Interval.FOURTH, Interval.FIFTH, Interval.OCTAVE)
        for (start in keys) {
            for (interval in diatonic) {
                for (upwards in listOf(true, false)) {
                    val there = start.transposed(Transposition(interval, upwards)) ?: continue
                    val back = there.transposed(Transposition(interval, !upwards))
                    assertEquals(start, back, "${start.name} did not come back from ${there.name}")
                }
            }
        }
    }

    @Test
    fun `the notes of the scale keep their intervals`() {
        // The scale of C major, up a major third: E, F sharp, G sharp, A, B, C sharp, D sharp.
        val moved = assertNotNull(key("C major").transposed(up(Interval.THIRD)))
        assertEquals(key("E major"), moved)
        val scale = listOf(0, 2, 4, 5, 7, 9, 11).map { key("C major").spell(60 + it).midi + 4 }
        assertEquals(
            listOf("e", "fis", "gis", "a", "b", "cis", "dis"),
            scale.map { moved.spell(it).lilypondName },
        )
    }
}
