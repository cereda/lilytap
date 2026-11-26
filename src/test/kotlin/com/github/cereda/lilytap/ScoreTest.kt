// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The score model: the notes and rests of a take, and the edits you can make to them.
 */
class ScoreTest {
    // 4/4, 60 BPM, 2 cells per beat => 8 cells per bar, 500 ms per cell
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    private fun score(
        vararg events: Event,
        config: Config = cfg(),
    ) = Score(events.toList(), config)

    /** The internal units in a grid cell of this configuration (a sixteenth of a whole note). */
    private val cellUnits = 8

    /** A number of grid cells as internal units. */
    private fun cells(count: Int) = count * cellUnits

    private fun note(
        start: Int,
        length: Int,
        pitch: Pitch = Pitch.DEFAULT,
    ) = Event(TapKind.NOTE, cells(start), cells(length), pitch)

    private fun rest(
        start: Int,
        length: Int,
    ) = Event(TapKind.REST, cells(start), cells(length))

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
    fun `an event must last at least one unit`() {
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
        assertEquals(64, s.totalUnits)
        assertEquals(64, s.events.sumOf { it.lengthUnits })
    }

    // -------------------------------------------------------------------------------------------
    // The key of the piece
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the text of C major says nothing about the key`() {
        assertEquals("c'2 c'2 |", score(note(0, 4), note(4, 4)).lilypond)
    }

    @Test
    fun `the text declares the key of the piece`() {
        val config = cfg().copy(key = KeySignature.parse("F major")!!)
        val s = score(note(0, 4), note(4, 4), config = config)
        assertEquals("\\key f \\major\nc'2 c'2 |", s.lilypond)
    }

    @Test
    fun `the relative text declares the key inside the block`() {
        val config = cfg().copy(key = KeySignature.parse("Bb major")!!)
        val s = score(note(0, 4), note(4, 4), config = config)
        assertEquals("\\relative {\n\\key bes \\major\nc'2 c2 |\n}", s.lilypondRelative)
    }

    @Test
    fun `a score in a key spells its notes in it`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 1, 4)), note(4, 4))
        val flat = s.inKey(KeySignature.parse("F major")!!)
        assertEquals("\\key f \\major\ndes'2 c'2 |", flat.lilypond, "the C sharp is a D flat")
        assertEquals(s.events[0].pitch?.midi, flat.events[0].pitch?.midi, "the note sounds the same")
    }

    @Test
    fun `a score in a sharp minor key spells its leading tone`() {
        val config = cfg().copy(key = KeySignature.parse("G# minor")!!)
        val s = score(note(0, 4, Pitch(PitchStep.F, 2, 4)), note(4, 4), config = config)
        assertEquals("\\key gis \\minor\nfisis'2 c'2 |", s.lilypond)
    }

    @Test
    fun `the relative text keeps the double accidental`() {
        val config = cfg().copy(key = KeySignature.parse("G# minor")!!)
        val s = score(note(0, 4, Pitch(PitchStep.F, 2, 4)), note(4, 4), config = config)
        assertEquals("\\relative {\n\\key gis \\minor\nfisis'2 c2 |\n}", s.lilypondRelative)
    }

    @Test
    fun `every pitch is written with its own accidental`() {
        // LilyPond reads a note name as a pitch: the key signature only says what may be
        // left implicit, so a note that the signature alters still carries its accidental.
        // A natural is the plain letter - there is no suffix for it.
        val config = cfg().copy(key = KeySignature.parse("F major")!!)
        val s =
            score(
                note(0, 4, Pitch(PitchStep.B, 0, 4)),
                note(4, 4, Pitch(PitchStep.B, -1, 4)),
                config = config,
            )
        assertEquals("\\key f \\major\nb'2 bes'2 |", s.lilypond)
        assertEquals("\\relative {\n\\key f \\major\nb'2 bes2 |\n}", s.lilypondRelative)
    }

    @Test
    fun `a score in a key keeps its key in the settings`() {
        val s = score(note(0, 8))
        val flat = s.inKey(KeySignature.parse("F major")!!)
        assertEquals(KeySignature.parse("F major"), flat.config.key)
        assertEquals(KeySignature.C_MAJOR, s.config.key, "the original score did not change")
    }

    @Test
    fun `a score in a key keeps the rests as they are`() {
        val s = score(note(0, 4), rest(4, 4))
        val flat = s.inKey(KeySignature.parse("Bb major")!!)
        assertEquals("\\key bes \\major\nc'2 r2 |", flat.lilypond)
    }

    // -------------------------------------------------------------------------------------------
    // Transposing the score
    // -------------------------------------------------------------------------------------------

    /** The score a transposition gives, or the reason it was refused. */
    private fun moved(
        s: Score,
        interval: Interval,
        up: Boolean = true,
    ): TransposeResult = s.transpose(Transposition(interval, up))

    @Test
    fun `the notes move up a third and the key follows`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, 0, 4)))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.THIRD))
        assertEquals("\\key e \\major\ne'2 gis'2 |", moved.score.lilypond)
        assertEquals(KeySignature.parse("E major"), moved.score.config.key)
    }

    @Test
    fun `a minor key moves by a minor third`() {
        val config = cfg().copy(key = KeySignature.parse("C minor")!!)
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, -1, 4)), config = config)
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.THIRD))
        assertEquals("\\key ees \\minor\nees'2 ges'2 |", moved.score.lilypond)
    }

    @Test
    fun `the notes move down as well`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, 0, 4)))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.THIRD, up = false))
        assertEquals("\\key aes \\major\naes2 c'2 |", moved.score.lilypond)
    }

    @Test
    fun `an octave moves the notes and keeps the key`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, 0, 4)))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.OCTAVE))
        assertEquals(KeySignature.C_MAJOR, moved.score.config.key)
        assertEquals("c''2 e''2 |", moved.score.lilypond)
    }

    @Test
    fun `the semitone moves the accidental and not the letter`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, 0, 4)))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.SEMITONE))
        assertEquals("\\key cis \\major\ncis'2 eis'2 |", moved.score.lilypond)
    }

    @Test
    fun `the rests do not move`() {
        val s = score(note(0, 4), rest(4, 4))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.THIRD))
        assertEquals("\\key e \\major\ne'2 r2 |", moved.score.lilypond)
    }

    @Test
    fun `the shape of the score does not change`() {
        val s = score(note(0, 2), note(2, 2), note(4, 4))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.THIRD))
        assertEquals(s.events.map { it.lengthUnits }, moved.score.events.map { it.lengthUnits })
    }

    @Test
    fun `the notes keep the sound they had`() {
        val s = score(note(0, 4, Pitch(PitchStep.C, 0, 4)), note(4, 4, Pitch(PitchStep.E, 0, 4)))
        val moved = assertIs<TransposeResult.Moved>(moved(s, Interval.FIFTH))
        assertEquals(
            67,
            moved.score.events[0]
                .pitch
                ?.midi,
        )
        assertEquals(
            71,
            moved.score.events[1]
                .pitch
                ?.midi,
        )
    }

    @Test
    fun `a key the app cannot hold is refused`() {
        val config = cfg().copy(key = KeySignature.parse("C𝄪 major")!!)
        val s = score(note(0, 8, Pitch(PitchStep.C, 2, 4)), config = config)
        val refused = assertIs<TransposeResult.Refused>(moved(s, Interval.FIFTH))
        assertTrue(
            refused.lines.any { it.contains("G𝄪 major") },
            "the refusal names the key: ${refused.lines}",
        )
        assertTrue(
            refused.lines.any { it.contains("15") },
            "the refusal says how many accidentals: ${refused.lines}",
        )
        assertTrue(
            refused.lines.any { it.contains("fourteen") },
            "the refusal says what the limit is: ${refused.lines}",
        )
    }

    @Test
    fun `a note that would leave the keyboard is refused`() {
        val s = score(note(0, 8, Pitch(PitchStep.C, 0, 8)))
        val refused = assertIs<TransposeResult.Refused>(moved(s, Interval.OCTAVE))
        assertTrue(
            refused.lines.any { it.contains("keyboard") },
            "the refusal says what went wrong: ${refused.lines}",
        )
    }

    @Test
    fun `transposing a score leaves the original alone`() {
        val s = score(note(0, 4), note(4, 4))
        moved(s, Interval.THIRD)
        assertEquals("c'2 c'2 |", s.lilypond)
        assertEquals(KeySignature.C_MAJOR, s.config.key)
    }

    // -------------------------------------------------------------------------------------------
    // Turning a note into a rest
    // -------------------------------------------------------------------------------------------

    @Test
    fun `toggling a note turns it into a rest`() {
        val edited = score(note(0, 4), note(4, 4)).toggleRest(0)
        assertEquals(listOf(rest(0, 4), note(4, 4)), edited.events)
        assertEquals("r2 c'2 |", edited.lilypond)
    }

    @Test
    fun `toggling a rest turns it back into a note`() {
        val edited = score(rest(0, 4), note(4, 4)).toggleRest(0)
        assertEquals(listOf(note(0, 4), note(4, 4)), edited.events)
        assertEquals("c'2 c'2 |", edited.lilypond)
    }

    @Test
    fun `a note that becomes a rest joins the rest next to it`() {
        val edited = score(note(0, 2), rest(2, 2), note(4, 4)).toggleRest(0)
        assertEquals(listOf(rest(0, 4), note(4, 4)), edited.events)
        assertEquals("r2 c'2 |", edited.lilypond)
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
        s.moveBoundary(0, cells(1))
        assertEquals(listOf(note(0, 4), note(4, 4)), s.events)
    }

    // -------------------------------------------------------------------------------------------
    // Dragging a border
    // -------------------------------------------------------------------------------------------

    @Test
    fun `moving a border grows the left event and shrinks the right one`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, cells(2))
        assertEquals(listOf(note(0, 6), note(6, 2)), edited.events)
        assertEquals("c'2. c'4 |", edited.lilypond)
    }

    @Test
    fun `moving a border the other way shrinks the left event`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, cells(-2))
        assertEquals(listOf(note(0, 2), note(2, 6)), edited.events)
    }

    @Test
    fun `moving a border keeps the bar full`() {
        val edited = score(note(0, 4), note(4, 4)).moveBoundary(0, cells(-3))
        assertEquals(64, edited.totalUnits)
        assertEquals(64, edited.events.sumOf { it.lengthUnits })
        assertEquals(edited.events.last().endUnits, edited.totalUnits)
    }

    @Test
    fun `a border stops when an event would vanish`() {
        val s = score(note(0, 4), note(4, 4))
        assertEquals(listOf(note(0, 7), note(7, 1)), s.moveBoundary(0, cells(10)).events)
        assertEquals(listOf(note(0, 1), note(1, 7)), s.moveBoundary(0, cells(-10)).events)
    }

    @Test
    fun `a border can cross a bar line`() {
        val s = score(note(0, 6), note(6, 10), config = cfg(bars = 2))
        val edited = s.moveBoundary(0, cells(4))
        assertEquals(listOf(note(0, 10), note(10, 6)), edited.events)
        // The first note now runs over the bar line, so it is tied.
        assertEquals("c'1 ~ |\nc'4 c'2. |", edited.lilypond)
    }

    @Test
    fun `moving a border that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.moveBoundary(0, cells(1)) }
        assertFailsWith<IllegalArgumentException> { s.moveBoundary(1, cells(1)) }
    }

    // -------------------------------------------------------------------------------------------
    // Splitting a block
    // -------------------------------------------------------------------------------------------

    @Test
    fun `splitting a note gives two halves of the same kind`() {
        val edited = score(note(0, 4), note(4, 4)).split(0)
        assertEquals(listOf(note(0, 2), note(2, 2), note(4, 4)), edited.events)
        assertEquals("c'4 c'4 c'2 |", edited.lilypond)
    }

    @Test
    fun `splitting a silence makes the second half a note`() {
        val edited = score(rest(0, 4), note(4, 4)).split(0)
        assertEquals(listOf(rest(0, 2), note(2, 2), note(4, 4)), edited.events)
        assertEquals("r4 c'4 c'2 |", edited.lilypond)
    }

    @Test
    fun `an odd block splits into a shorter first half`() {
        val s = Score(listOf(Event(TapKind.NOTE, 0, 3, Pitch.DEFAULT), Event(TapKind.NOTE, 3, 61, Pitch.DEFAULT)), cfg())
        val edited = s.split(0)
        assertEquals(
            listOf(
                Event(TapKind.NOTE, 0, 1, Pitch.DEFAULT),
                Event(TapKind.NOTE, 1, 2, Pitch.DEFAULT),
                Event(TapKind.NOTE, 3, 61, Pitch.DEFAULT),
            ),
            edited.events,
        )
    }

    @Test
    fun `a block of one unit cannot be split`() {
        val s = Score(listOf(Event(TapKind.NOTE, 0, 1, Pitch.DEFAULT), Event(TapKind.NOTE, 1, 63, Pitch.DEFAULT)), cfg())
        assertFailsWith<IllegalArgumentException> { s.split(0) }
    }

    @Test
    fun `splitting a block that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.split(1) }
        assertFailsWith<IllegalArgumentException> { s.split(-1) }
    }

    @Test
    fun `splitting a block leaves the original alone`() {
        val s = score(note(0, 4), note(4, 4))
        s.split(0)
        assertEquals(listOf(note(0, 4), note(4, 4)), s.events)
    }

    // -------------------------------------------------------------------------------------------
    // Swapping two blocks
    // -------------------------------------------------------------------------------------------

    @Test
    fun `swapping two blocks moves them across each other`() {
        val edited = score(note(0, 2), rest(2, 2), note(4, 4)).swap(0)
        assertEquals(listOf(rest(0, 2), note(2, 2), note(4, 4)), edited.events)
        assertEquals("r4 c'4 c'2 |", edited.lilypond)
    }

    @Test
    fun `swapping keeps the length of each block`() {
        val edited = score(note(0, 4), rest(4, 2), note(6, 2)).swap(0)
        assertEquals(listOf(rest(0, 2), note(2, 4), note(6, 2)), edited.events)
    }

    @Test
    fun `swapping two blocks that leave two silences together merges them`() {
        val edited = score(rest(0, 2), note(2, 2), rest(4, 4)).swap(1)
        assertEquals(listOf(rest(0, 6), note(6, 2)), edited.events)
        assertEquals("r2. c'4 |", edited.lilypond)
    }

    @Test
    fun `swapping two notes changes nothing but their order`() {
        val edited = score(note(0, 2), note(2, 2), note(4, 4)).swap(0)
        assertEquals(listOf(note(0, 2), note(2, 2), note(4, 4)), edited.events)
    }

    @Test
    fun `swapping a pair that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.swap(0) }
        assertFailsWith<IllegalArgumentException> { s.swap(-1) }
    }

    // -------------------------------------------------------------------------------------------
    // Merging blocks
    // -------------------------------------------------------------------------------------------

    @Test
    fun `merging two notes makes one longer note`() {
        val edited = score(note(0, 2), note(2, 2), note(4, 4)).merge(0, 1)
        assertEquals(listOf(note(0, 4), note(4, 4)), edited.events)
        assertEquals("c'2 c'2 |", edited.lilypond)
    }

    @Test
    fun `merging a note and a silence lets the note win`() {
        val edited = score(note(0, 2), rest(2, 2), note(4, 4)).merge(0, 1)
        assertEquals(listOf(note(0, 4), note(4, 4)), edited.events)
        assertEquals("c'2 c'2 |", edited.lilypond)
    }

    @Test
    fun `merging a range keeps the bar full`() {
        val edited = score(note(0, 2), note(2, 2), note(4, 4)).merge(0, 2)
        assertEquals(64, edited.totalUnits)
        assertEquals(64, edited.events.sumOf { it.lengthUnits })
        assertEquals(listOf(note(0, 8)), edited.events)
    }

    @Test
    fun `merging a range that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.merge(0, 1) }
        assertFailsWith<IllegalArgumentException> { s.merge(1, 0) }
        assertFailsWith<IllegalArgumentException> { s.merge(-1, 0) }
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
        assertEquals("c'4 c'2. |", result.lilypond)
        assertEquals(result.score.lilypond, result.lilypond)
    }

    @Test
    fun `an edited score has a text of its own`() {
        val config = cfg()
        val result = transcribe(listOf(Tap(0.0, TapKind.NOTE), Tap(1000.0, TapKind.NOTE)), config)
        val edited = result.score.toggleRest(1)
        assertEquals("c'4 r2. |", edited.lilypond)
        assertEquals("c'4 c'2. |", result.lilypond)
    }

    // -------------------------------------------------------------------------------------------
    // The pitch of a note
    // -------------------------------------------------------------------------------------------

    /** A sharp C an octave up: C♯5. */
    private val sharp = Pitch(PitchStep.C, 1, 5)

    /** The E above middle C: E5. */
    private val high = Pitch(PitchStep.E, 0, 5)

    @Test
    fun `a note carries a pitch and a rest carries none`() {
        assertFailsWith<IllegalArgumentException> { Event(TapKind.NOTE, 0, 16, null) }
        assertFailsWith<IllegalArgumentException> { Event(TapKind.REST, 0, 16, Pitch.DEFAULT) }
    }

    @Test
    fun `setting the pitch of a note changes the text`() {
        val edited = score(note(0, 4), note(4, 4)).setPitch(0, sharp)
        assertEquals("cis''2 c'2 |", edited.lilypond)
        assertEquals(sharp, edited.events[0].pitch)
    }

    @Test
    fun `setting the pitch of a note that is not there is rejected`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.setPitch(1, sharp) }
        assertFailsWith<IllegalArgumentException> { s.setPitch(-1, sharp) }
    }

    @Test
    fun `a rest has no pitch to set`() {
        val s = score(rest(0, 4), note(4, 4))
        assertFailsWith<IllegalArgumentException> { s.setPitch(0, sharp) }
    }

    @Test
    fun `setting the pitch a note already has changes nothing`() {
        val s = score(note(0, 8))
        assertEquals(s, s.setPitch(0, Pitch.DEFAULT))
    }

    @Test
    fun `setting the pitch leaves the original alone`() {
        val s = score(note(0, 4), note(4, 4))
        s.setPitch(0, sharp)
        assertEquals(Pitch.DEFAULT, s.events[0].pitch)
    }

    @Test
    fun `a rest can be filled with a note`() {
        val edited = score(rest(0, 4), note(4, 4)).fillRest(0, sharp)
        assertEquals(TapKind.NOTE, edited.events[0].kind)
        assertEquals(sharp, edited.events[0].pitch)
        assertEquals(cells(4), edited.events[0].lengthUnits, "the note keeps the length of the rest")
        assertEquals("cis''2 c'2 |", edited.lilypond)
    }

    @Test
    fun `only a rest can be filled`() {
        val s = score(note(0, 8))
        assertFailsWith<IllegalArgumentException> { s.fillRest(0, sharp) }
        assertFailsWith<IllegalArgumentException> { s.fillRest(1, sharp) }
        assertFailsWith<IllegalArgumentException> { s.fillRest(-1, sharp) }
    }

    @Test
    fun `filling a rest leaves the original alone`() {
        val s = score(rest(0, 8))
        s.fillRest(0, sharp)
        assertEquals(TapKind.REST, s.events[0].kind)
        assertNull(s.events[0].pitch)
    }

    @Test
    fun `a rest that becomes a note takes the pitch of the note before it`() {
        val edited = score(note(0, 4, sharp), rest(4, 4)).toggleRest(1)
        assertEquals(sharp, edited.events[1].pitch)
        assertEquals("cis''2 cis''2 |", edited.lilypond)
    }

    @Test
    fun `the first note of a score starts on middle C`() {
        val edited = score(rest(0, 4), note(4, 4)).toggleRest(0)
        assertEquals(Pitch.DEFAULT, edited.events[0].pitch)
    }

    @Test
    fun `splitting a note keeps its pitch in both halves`() {
        val edited = score(note(0, 4, sharp), note(4, 4)).split(0)
        assertEquals(sharp, edited.events[0].pitch)
        assertEquals(sharp, edited.events[1].pitch)
    }

    @Test
    fun `splitting a silence gives the new note the pitch of the note before it`() {
        val edited = score(note(0, 4, sharp), rest(4, 4)).split(1)
        assertEquals(sharp, edited.events[2].pitch)
        assertEquals("cis''2 r4 cis''4 |", edited.lilypond)
    }

    @Test
    fun `merging keeps the pitch of the first note of the range`() {
        val edited = score(note(0, 2, sharp), note(2, 2, high), rest(4, 4)).merge(0, 2)
        assertEquals(sharp, edited.events[0].pitch)
        assertEquals("cis''1 |", edited.lilypond)
    }

    @Test
    fun `swapping moves the pitch with the note`() {
        val edited = score(note(0, 4, sharp), note(4, 4, high)).swap(0)
        assertEquals(high, edited.events[0].pitch)
        assertEquals(sharp, edited.events[1].pitch)
    }

    @Test
    fun `every note of a take starts on middle C`() {
        val result = transcribe(listOf(Tap(0.0, TapKind.NOTE), Tap(1000.0, TapKind.NOTE)), cfg())
        assertTrue(result.score.events.all { it.pitch == Pitch.DEFAULT })
    }

    @Test
    fun `the notes of a score can carry different pitches`() {
        val s = score(note(0, 2, sharp), note(2, 2, high), rest(4, 4))
        assertEquals("cis''4 e''4 r2 |", s.lilypond)
    }

    // -------------------------------------------------------------------------------------------
    // The bucket of the duration band
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the shortest values are in the first bucket`() {
        assertEquals(0, durationLevel(1))
        assertEquals(0, durationLevel(2))
        assertEquals(0, durationLevel(3))
        assertEquals(0, durationLevel(4))
    }

    @Test
    fun `the eighths are in the second bucket`() {
        assertEquals(1, durationLevel(5))
        assertEquals(1, durationLevel(6))
        assertEquals(1, durationLevel(8))
        assertEquals(1, durationLevel(12))
    }

    @Test
    fun `the quarters are in the third bucket`() {
        assertEquals(2, durationLevel(16))
        assertEquals(2, durationLevel(24))
    }

    @Test
    fun `the halves and the longer values are in the last bucket`() {
        assertEquals(3, durationLevel(32))
        assertEquals(3, durationLevel(64))
        assertEquals(3, durationLevel(96))
        assertEquals(3, durationLevel(200), "a block longer than a dotted whole is as long as one")
    }

    @Test
    fun `the bucket grows with the duration`() {
        assertTrue(durationLevel(1) < durationLevel(8))
        assertTrue(durationLevel(8) < durationLevel(16))
        assertTrue(durationLevel(16) < durationLevel(32))
    }

    @Test
    fun `a duration of no units is rejected`() {
        assertFailsWith<IllegalArgumentException> { durationLevel(0) }
        assertFailsWith<IllegalArgumentException> { durationName(0) }
    }

    @Test
    fun `the name of a duration says it in words`() {
        assertEquals("quarter", durationName(16))
        assertEquals("dotted quarter", durationName(24))
        assertEquals("eighth", durationName(8))
        assertEquals("whole", durationName(64))
    }

    @Test
    fun `the name of a duration that is not written names its pieces`() {
        assertEquals("sixteenth + sixty-fourth", durationName(5))
    }

    // -------------------------------------------------------------------------------------------
    // Compound meters
    // -------------------------------------------------------------------------------------------

    /** A meter whose beats come in groups of three: 6/8, 9/8, 12/8, 6/4... */
    private fun compound(
        beats: Int,
        unit: Int,
        bars: Int = 1,
    ) = Config(60.0, TimeSignature(beats, unit), 2, bars, 0.0)

    /** A note in the units of the meter: a whole note is 64 units, an eighth is 8. */
    private fun at(
        kind: TapKind,
        start: Int,
        length: Int,
        pitch: Pitch? = if (kind == TapKind.NOTE) Pitch.DEFAULT else null,
    ) = Event(kind, start, length, pitch)

    @Test
    fun `a meter is compound when its beats come in groups of three`() {
        assertTrue(TimeSignature(6, 8).isCompound)
        assertTrue(TimeSignature(9, 8).isCompound)
        assertTrue(TimeSignature(12, 8).isCompound)
        assertTrue(TimeSignature(6, 4).isCompound)
        assertFalse(TimeSignature(3, 4).isCompound, "3/4 is a simple triple")
        assertFalse(TimeSignature(4, 4).isCompound)
        assertFalse(TimeSignature(5, 8).isCompound, "5/8 is irregular")
        assertFalse(TimeSignature(7, 8).isCompound, "7/8 is irregular")
    }

    @Test
    fun `the beat of a compound meter is three of its beats`() {
        assertEquals(24, TimeSignature(6, 8).groupUnits)
        assertEquals(24, TimeSignature(9, 8).groupUnits)
        assertEquals(48, TimeSignature(6, 4).groupUnits)
    }

    @Test
    fun `a note that crosses a group is written as two tied notes`() {
        val s =
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 32),
                    at(TapKind.REST, 32, 16),
                ),
                compound(6, 8),
            )
        assertEquals("c'4. ~ c'8 r4 |", s.lilypond)
    }

    @Test
    fun `a silence that crosses a group is written as two rests`() {
        val s =
            Score(
                listOf(
                    at(TapKind.REST, 0, 32),
                    at(TapKind.NOTE, 32, 16),
                ),
                compound(6, 8),
            )
        assertEquals("r4. r8 c'4 |", s.lilypond)
    }

    @Test
    fun `a note that fills a group is written as a dotted quarter`() {
        val s =
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 24),
                    at(TapKind.REST, 24, 24),
                ),
                compound(6, 8),
            )
        assertEquals("c'4. r4. |", s.lilypond)
    }

    @Test
    fun `a whole bar of a compound meter is tied inside the groups`() {
        assertEquals("c'4. ~ c'4. |", Score(listOf(at(TapKind.NOTE, 0, 48)), compound(6, 8)).lilypond)
        assertEquals(
            "c'4. ~ c'4. ~ c'4. ~ c'4. |",
            Score(listOf(at(TapKind.NOTE, 0, 96)), compound(12, 8)).lilypond,
        )
        assertEquals("c'2. ~ c'2. |", Score(listOf(at(TapKind.NOTE, 0, 96)), compound(6, 4)).lilypond)
    }

    @Test
    fun `a simple meter is written the way it always was`() {
        assertEquals("c'2 r2 |", score(note(0, 4), rest(4, 4)).lilypond)
        assertEquals(
            "c'2 r4 |",
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 32),
                    at(TapKind.REST, 32, 16),
                ),
                compound(3, 4),
            ).lilypond,
        )
    }

    @Test
    fun `an irregular meter is left the way it was`() {
        assertEquals(
            "c'2 ~ c'8 c'4 |",
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 40),
                    at(TapKind.NOTE, 40, 16),
                ),
                compound(7, 8),
            ).lilypond,
        )
        assertEquals(
            "c'4. r4 |",
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 24),
                    at(TapKind.REST, 24, 16),
                ),
                compound(5, 8),
            ).lilypond,
        )
    }

    @Test
    fun `the blocks of a compound meter keep their own durations`() {
        val s =
            Score(
                listOf(
                    at(TapKind.NOTE, 0, 32),
                    at(TapKind.REST, 32, 16),
                ),
                compound(6, 8),
            )
        // The block knows how long it is; the text is what the meter makes of it.
        assertEquals("c'2", eventText(s.events[0]))
        assertEquals("r4", eventText(s.events[1]))
    }
}
