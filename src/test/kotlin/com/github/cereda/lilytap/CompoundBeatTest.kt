// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The timing of a compound meter: the beat is the dotted quarter, so the tempo counts it and
 * the metronome clicks it - while the grid keeps cutting the note value, so the eighths of a
 * 6/8 bar land exactly on it.
 */
class CompoundBeatTest {
    @Test
    fun `a compound meter has a beat of three note values`() {
        assertEquals(2, TimeSignature(6, 8).pulsesPerBar)
        assertEquals(3, TimeSignature(9, 8).pulsesPerBar)
        assertEquals(4, TimeSignature(12, 8).pulsesPerBar)
        assertEquals(2, TimeSignature(6, 4).pulsesPerBar)
    }

    @Test
    fun `a simple meter has a beat of one note value`() {
        assertEquals(4, TimeSignature(4, 4).pulsesPerBar)
        assertEquals(3, TimeSignature(3, 4).pulsesPerBar)
        assertEquals(5, TimeSignature(5, 8).pulsesPerBar)
        assertEquals(7, TimeSignature(7, 8).pulsesPerBar)
    }

    @Test
    fun `the beat of a compound meter is the dotted quarter`() {
        assertEquals(24, TimeSignature(6, 8).unitsPerPulse) // three eighths
        assertEquals(48, TimeSignature(6, 4).unitsPerPulse) // three quarters
        assertEquals(16, TimeSignature(4, 4).unitsPerPulse) // one quarter
    }

    @Test
    fun `the tempo counts the musical beat`() {
        val config = Config(60.0, TimeSignature(6, 8), 1, 1)
        assertEquals(1000.0, config.pulseMs) // a dotted quarter a second
        assertEquals(1000.0 / 3, config.cellMs) // an eighth is a third of it
        assertEquals(2000.0, config.totalCells * config.cellMs) // two beats a bar
    }

    @Test
    fun `the grid cuts the note value`() {
        val config = Config(60.0, TimeSignature(6, 8), 2, 1)
        assertEquals(4, config.cellUnits) // a sixteenth
        assertEquals(12, config.cellsPerBar) // six eighths
        assertEquals(6, config.cellsPerPulse) // three eighths to a beat
    }

    @Test
    fun `a simple meter keeps its clock`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1)
        assertEquals(500.0, config.cellMs)
        assertEquals(1000.0, config.pulseMs)
        assertEquals(2, config.cellsPerPulse)
        assertEquals(4000.0, config.totalCells * config.cellMs)
    }

    @Test
    fun `the two beats of a six eight bar are dotted quarters`() {
        val config = Config(60.0, TimeSignature(6, 8), 1, 1)
        val taps = listOf(Tap(0.0, TapKind.NOTE), Tap(1000.0, TapKind.NOTE))
        assertEquals("c'4. c'4. |", transcribe(taps, config).lilypond)
    }

    @Test
    fun `the eighths of a six eight bar land on the grid`() {
        val config = Config(60.0, TimeSignature(6, 8), 1, 1)
        val eighth = 1000.0 / 3
        val taps = (0 until 6).map { Tap(it * eighth, TapKind.NOTE) }
        assertEquals("c'8 c'8 c'8 c'8 c'8 c'8 |", transcribe(taps, config).lilypond)
    }

    @Test
    fun `an off-beat eighth stays inside its group`() {
        // The second eighth of the first dotted quarter, then the second beat of the bar: the
        // note that fills the rest of the group is a quarter, and it does not cross the border.
        val config = Config(60.0, TimeSignature(6, 8), 1, 1)
        val taps = listOf(Tap(1000.0 / 3, TapKind.NOTE), Tap(1000.0, TapKind.NOTE))
        assertEquals("r8 c'4 c'4. |", transcribe(taps, config).lilypond)
    }

    @Test
    fun `a whole bar of a compound meter is written inside its groups`() {
        val config = Config(60.0, TimeSignature(6, 8), 1, 1)
        assertEquals("c'4. ~ c'4. |", transcribe(listOf(Tap(0.0, TapKind.NOTE)), config).lilypond)
    }
}
