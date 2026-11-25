// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The MIDI file of a score: what the file says when it is read back.
 */
class MidiFileTest {
    private fun cfg(
        bpm: Double = 60.0,
        signature: TimeSignature = TimeSignature(4, 4),
        bars: Int = 1,
        key: KeySignature = KeySignature.C_MAJOR,
    ) = Config(bpm, signature, 2, bars, 0.0, key = key)

    /** A score with one note: a C4 that fills the bar. */
    private val one = Score(listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)), cfg())

    /** A score with one note that fills the bar of the settings given. */
    private fun scoreIn(config: Config): Score = Score(listOf(Event(TapKind.NOTE, 0, config.totalUnits, Pitch.DEFAULT)), config)

    @Test
    fun `the file has the header a MIDI file needs`() {
        val file = readMidiFile(midiFile(one))

        assertEquals(1, file.format, "the file has a track for the music and one for the tempo")
        assertEquals(384, file.division, "a quarter note is 384 ticks long")
        assertEquals(listOf("lilytap"), file.trackNames, "the track of the app has its name")
    }

    @Test
    fun `the note of the score is in the file`() {
        val file = readMidiFile(midiFile(one))

        assertEquals(1, file.notes.size, "the file holds the one note of the score")
        val note = file.notes[0]
        assertEquals(60, note.pitch, "the note is a C4")
        assertEquals(0, note.onset, "the note starts at the beginning")
        assertEquals(64 * 24, note.duration, "the note lasts as long as the score says")
    }

    @Test
    fun `a rest is the gap between the notes`() {
        val score =
            Score(
                listOf(
                    Event(TapKind.NOTE, 0, 16, Pitch.DEFAULT),
                    Event(TapKind.REST, 16, 16),
                    Event(TapKind.NOTE, 32, 32, Pitch.DEFAULT),
                ),
                cfg(),
            )

        val file = readMidiFile(midiFile(score))

        assertEquals(2, file.notes.size, "the rest is not a note")
        assertEquals(0, file.notes[0].onset, "the first note starts at the beginning")
        assertEquals(32 * 24, file.notes[1].onset, "the second note starts after the rest")
        assertEquals(32 * 24, file.notes[1].duration, "the second note lasts as long as the score says")
    }

    @Test
    fun `the tempo of the file is the tempo of the score`() {
        val file = readMidiFile(midiFile(scoreIn(cfg(bpm = 120.0))))

        assertEquals(500_000, file.microsPerQuarter, "120 beats a minute is half a second a beat")
    }

    @Test
    fun `the tempo of a compound meter counts the quarter note`() {
        val file = readMidiFile(midiFile(scoreIn(cfg(bpm = 60.0, signature = TimeSignature(6, 8)))))

        // The beat of 6/8 is the dotted quarter: it lasts a second, and a quarter note is
        // two thirds of it. LilyPond writes the same for a dotted quarter of 60 a minute:
        // 666 666 microseconds a quarter note.
        assertEquals(666_667, file.microsPerQuarter, "the tempo is written in quarter notes")
    }

    @Test
    fun `the tempo of an irregular meter counts the note value`() {
        val file = readMidiFile(midiFile(scoreIn(cfg(bpm = 60.0, signature = TimeSignature(5, 8)))))

        // The beat of 5/8 is the eighth: it lasts a second, and a quarter note lasts two.
        assertEquals(2_000_000, file.microsPerQuarter, "the tempo is written in quarter notes")
    }

    @Test
    fun `the file plays at the tempo of the score`() {
        // A bar of 6/8 at 60 beats a minute lasts two seconds: the beat is the dotted
        // quarter, and there are two of them in the bar. The bar is three quarter notes
        // long, and the tempo says how long a quarter note lasts.
        val file = readMidiFile(midiFile(scoreIn(cfg(bpm = 60.0, signature = TimeSignature(6, 8)))))

        val micros = file.microsPerQuarter!!.toDouble() * 3
        assertEquals(2_000_000.0, micros, 3.0, "the bar lasts two seconds")
    }

    @Test
    fun `a simple meter plays at the tempo of the score`() {
        // A bar of 4/4 at 60 beats a minute lasts four seconds: the beat is the quarter,
        // and there are four of them in the bar.
        val file = readMidiFile(midiFile(scoreIn(cfg(bpm = 60.0))))

        val micros = file.microsPerQuarter!!.toDouble() * 4
        assertEquals(4_000_000.0, micros, 3.0, "the bar lasts four seconds")
    }

    @Test
    fun `the meter of the file is the meter of the score`() {
        val file = readMidiFile(midiFile(scoreIn(cfg(signature = TimeSignature(6, 8)))))

        assertEquals(6, file.beats, "the bar has six beats")
        assertEquals(8, file.beatUnit, "the beat is an eighth note")
    }

    @Test
    fun `the key of the file is the key of the score`() {
        val key = assertNotNull(KeySignature.parse("F major"))
        val file = readMidiFile(midiFile(scoreIn(cfg(key = key))))

        assertEquals(-1, file.fifths, "the key has one flat")
        assertEquals(false, file.minor, "the key is a major one")
    }

    @Test
    fun `a minor key says so`() {
        val key = assertNotNull(KeySignature.parse("A minor"))
        val file = readMidiFile(midiFile(scoreIn(cfg(key = key))))

        assertEquals(0, file.fifths, "the key has no accidentals")
        assertEquals(true, file.minor, "the key is a minor one")
    }

    @Test
    fun `the notes of the score keep their order and their pitches`() {
        val pitches = listOf("c'", "e'", "g'", "c''")
        val score =
            Score(
                pitches.mapIndexed { index, name ->
                    Event(TapKind.NOTE, index * 16, 16, assertNotNull(Pitch.parse(name)))
                },
                cfg(),
            )

        val file = readMidiFile(midiFile(score))

        assertEquals(
            listOf(60, 64, 67, 72),
            file.notes.map { it.pitch },
            "the notes are the notes of the score",
        )
        assertEquals(
            listOf(0, 16 * 24, 32 * 24, 48 * 24),
            file.notes.map { it.onset },
            "the notes start where the score says",
        )
    }

    @Test
    fun `the file of a score with one rest holds no note`() {
        val score = Score(listOf(Event(TapKind.REST, 0, 64)), cfg())

        val file = readMidiFile(midiFile(score))

        assertTrue(file.notes.isEmpty(), "a score of rests has no note")
        assertNull(file.microsPerQuarter?.takeIf { it == 0 }, "the tempo is there")
    }
}
