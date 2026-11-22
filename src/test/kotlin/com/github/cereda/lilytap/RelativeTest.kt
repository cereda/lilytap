// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The LilyPond text with the octaves relative: the way LilyPond reads them.
 *
 * The rule is the one of LilyPond itself: in a relative block, the octave of a note is
 * worked out from the note before it, counting the letters of the scale and not the
 * accidentals.
 */
class RelativeTest {
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    /** The internal units in a grid cell of this configuration. */
    private val cellUnits = 8

    /** A number of grid cells as internal units. */
    private fun cells(count: Int) = count * cellUnits

    private fun note(
        start: Int,
        length: Int,
        pitch: Pitch,
    ) = Event(TapKind.NOTE, cells(start), cells(length), pitch)

    private fun rest(
        start: Int,
        length: Int,
    ) = Event(TapKind.REST, cells(start), cells(length))

    private fun score(
        vararg events: Event,
        config: Config = cfg(),
    ) = Score(events.toList(), config)

    /** A pitch from the name LilyPond writes it with. */
    private fun pitch(name: String): Pitch = requireNotNull(Pitch.parse(name)) { "cannot read '$name'" }

    /** The name of a note that follows another one. */
    private fun relativeTo(
        previous: String,
        current: String,
    ): String = relativeName(pitch(current), pitch(previous))

    // -------------------------------------------------------------------------------------------
    // The rule of the octave
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the same note again is in the same octave`() {
        assertEquals("c", relativeTo("c'", "c'"))
        assertEquals("d", relativeTo("d'", "d'"))
    }

    @Test
    fun `an octave up or down needs a mark`() {
        assertEquals("c'", relativeTo("c'", "c''"))
        assertEquals("c,", relativeTo("c'", "c"))
        // The c below the c' is an octave down, so it takes a mark of its own.
        assertEquals("c'", relativeTo("c", "c'"))
    }

    @Test
    fun `a second, a third and a fourth up need no mark at all`() {
        assertEquals("d", relativeTo("c'", "d'"))
        assertEquals("e", relativeTo("c'", "e'"))
        assertEquals("f", relativeTo("c'", "f'"))
    }

    @Test
    fun `a fifth up would be too far, so the note goes down instead`() {
        // The g below the c' is a fourth away, the g above it is a fifth.
        assertEquals("g'", relativeTo("c'", "g'"))
        assertEquals("g", relativeTo("c'", "g"))
    }

    @Test
    fun `the same holds for the sixth and the seventh`() {
        assertEquals("a'", relativeTo("c'", "a'"))
        assertEquals("a", relativeTo("c'", "a"))
        assertEquals("b'", relativeTo("c'", "b'"))
        assertEquals("b", relativeTo("c'", "b"))
    }

    @Test
    fun `the octave of the note below is the one that is nearest`() {
        assertEquals("c", relativeTo("b'", "c''"))
        assertEquals("b", relativeTo("c''", "b'"))
        assertEquals("c", relativeTo("b", "c'"))
    }

    @Test
    fun `the accidentals do not count for the octave`() {
        // The sharp is a semitone above, but LilyPond counts the letters only.
        assertEquals("cis", relativeTo("c'", "cis'"))
        assertEquals("cis,", relativeTo("c'", "cis"))
        assertEquals("ces", relativeTo("c'", "ces'"))
        assertEquals("dis", relativeTo("c'", "dis'"))
        // The flat of the g is a fourth below the c, so it needs a mark of its own.
        assertEquals("ges'", relativeTo("c'", "ges'"))
    }

    @Test
    fun `the ends of the keyboard are written with many marks`() {
        assertEquals("c", relativeTo("c,,,", "c,,,"))
        assertEquals("c''''''''", relativeTo("c,,,", "c'''''"))
        assertEquals("c,,,,,,,,", relativeTo("c'''''", "c,,,"))
    }

    @Test
    fun `the first note of the block is written in full`() {
        assertEquals("c'", relativeName(pitch("c'"), null))
        assertEquals("c", relativeName(pitch("c"), null))
        assertEquals("c''", relativeName(pitch("c''"), null))
        assertEquals("cis''", relativeName(pitch("cis''"), null))
        assertEquals("c,,,", relativeName(pitch("c,,,"), null))
    }

    // -------------------------------------------------------------------------------------------
    // The examples of the LilyPond manual
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the first example of the manual comes out the same`() {
        // The manual writes "c'' g c f," and says it sounds C5, G4, C5, F4.
        assertEquals(
            "\\relative {\nc''4 g4 c4 f,4 |\n}",
            score(
                note(0, 2, pitch("c''")),
                note(2, 2, pitch("g'")),
                note(4, 2, pitch("c''")),
                note(6, 2, pitch("f'")),
            ).lilypondRelative,
        )
    }

    @Test
    fun `the second example of the manual comes out the same`() {
        // The manual writes "c' a, e'' c" and says it sounds C4, A2, E4, C4.
        assertEquals(
            "\\relative {\nc'4 a,4 e''4 c4 |\n}",
            score(
                note(0, 2, pitch("c'")),
                note(2, 2, pitch("a,")),
                note(4, 2, pitch("e'")),
                note(6, 2, pitch("c'")),
            ).lilypondRelative,
        )
    }

    @Test
    fun `the third example of the manual comes out the same`() {
        // The manual writes "c f b e a d g c": a scale that spans large intervals.
        val names = listOf("c", "f", "b", "e'", "a'", "d''", "g''", "c'''")
        assertEquals(
            "\\relative {\nc8 f8 b8 e8 a8 d8 g8 c8 |\n}",
            score(*names.mapIndexed { i, name -> note(i, 1, pitch(name)) }.toTypedArray()).lilypondRelative,
        )
    }

    // -------------------------------------------------------------------------------------------
    // The shape of the text
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the relative text is a relative block`() {
        val s = score(note(0, 8, pitch("c'")))
        assertTrue(s.lilypondRelative.startsWith("\\relative {\n"), "the block is opened")
        assertTrue(s.lilypondRelative.endsWith("}"), "the block is closed")
    }

    @Test
    fun `the bar checks are kept`() {
        val s = score(note(0, 16, pitch("c'")), config = cfg(2))
        val lines = s.lilypondRelative.lines()
        assertEquals("\\relative {", lines.first())
        assertEquals("c'1 ~ |", lines[1])
        assertEquals("c1 |", lines[2])
        assertEquals("}", lines.last())
    }

    @Test
    fun `the absolute text is not changed`() {
        val s = score(note(0, 8, pitch("c'")))
        assertEquals("c'1 |", s.lilypond)
    }

    @Test
    fun `a rest does not move the reference`() {
        // C4, a rest, then G4: the rest carries no pitch, so the G is still read from the C.
        assertEquals(
            "\\relative {\nc'4 r4 g'2 |\n}",
            score(
                note(0, 2, pitch("c'")),
                rest(2, 2),
                note(4, 4, pitch("g'")),
            ).lilypondRelative,
        )
    }

    @Test
    fun `the first note after a rest is still written in full`() {
        assertEquals(
            "\\relative {\nr4 cis''2. |\n}",
            score(rest(0, 2), note(2, 6, pitch("cis''"))).lilypondRelative,
        )
    }

    @Test
    fun `the pieces of a tie are written one after the other`() {
        // A note that crosses a bar line is written as two tied notes. The second one is
        // relative to the first, and it has the same pitch, so it needs no marks.
        assertEquals(
            "\\relative {\nc'1 ~ |\nc1 |\n}",
            score(note(0, 16, pitch("c'")), config = cfg(2)).lilypondRelative,
        )
    }

    // -------------------------------------------------------------------------------------------
    // The round trip
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the relative text says the same pitches as the score`() {
        val names =
            listOf(
                "c'",
                "e'",
                "g'",
                "c''",
                "g",
                "a,",
                "cis",
                "f",
                "bes",
                "c,",
                "b''",
                "e",
                "aes",
                "d'",
                "gis,",
                "c",
            )
        val s = score(*names.mapIndexed { i, name -> note(i, 1, pitch(name)) }.toTypedArray(), config = cfg(2))
        assertEquals(names.map { pitch(it) }, pitchesOf(s.lilypondRelative))
    }

    @Test
    fun `the relative text of a score with rests says the same pitches`() {
        val s =
            score(
                note(0, 2, pitch("c'")),
                rest(2, 2),
                note(4, 2, pitch("g'")),
                rest(6, 2),
                config = cfg(),
            )
        assertEquals(listOf(pitch("c'"), pitch("g'")), pitchesOf(s.lilypondRelative))
    }

    /** The name of a note in a LilyPond text: the letter, the accidental and the marks. */
    private val name = Regex("^([a-g])(is|es)?([',]*)")

    /**
     * The pitches of a relative LilyPond text, read the way LilyPond reads them.
     *
     * The rule is worked out here in another way than the app does it: the note without
     * marks is the octave that puts it closest to the note before it, counting the letters
     * of the scale only, and the marks add whole octaves to that.
     *
     * @param text the LilyPond text of a relative block.
     * @return the pitches of the notes, in order.
     */
    private fun pitchesOf(text: String): List<Pitch> {
        val pitches = mutableListOf<Pitch>()
        var previous: Pitch? = null
        for (token in text.split(Regex("[\\s|~{}]+"))) {
            val pitch = pitchOf(token, previous)
            if (pitch != null) {
                pitches += pitch
                previous = pitch
            }
        }
        return pitches
    }

    /**
     * The pitch a token of a relative LilyPond text stands for.
     *
     * @param token the token of the text.
     * @param previous the pitch of the note before it, or null for the first note.
     * @return the pitch of the note, or null when the token is not a note.
     */
    private fun pitchOf(
        token: String,
        previous: Pitch?,
    ): Pitch? {
        if (token.isEmpty() || token.startsWith("\\")) return null
        if (token.startsWith("r")) return null // a rest carries no pitch
        val match = name.find(token) ?: return null
        val step = PitchStep.of(match.groupValues[1][0]) ?: return null
        val alter =
            when (match.groupValues[2]) {
                "is" -> 1
                "es" -> -1
                else -> 0
            }
        val marks = match.groupValues[3].count { it == '\'' } - match.groupValues[3].count { it == ',' }
        return readPitch(step, alter, marks, previous)
    }

    /**
     * The pitch a relative name stands for.
     *
     * @param step the letter of the note.
     * @param alter the accidental of the note.
     * @param marks how many octaves the marks of the name add.
     * @param previous the pitch of the note before it, or null for the first note.
     * @return the pitch of the note.
     */
    private fun readPitch(
        step: PitchStep,
        alter: Int,
        marks: Int,
        previous: Pitch?,
    ): Pitch {
        if (previous == null) return Pitch(step, alter, Pitch.LILYPOND_BASE_OCTAVE + marks)
        val above = previous.octave + if (previous.step.ordinal > step.ordinal) 1 else 0
        val below = previous.octave - if (previous.step.ordinal < step.ordinal) 1 else 0
        val distance = { octave: Int ->
            abs((octave * 7 + step.ordinal) - (previous.octave * 7 + previous.step.ordinal))
        }
        val base = if (distance(above) <= distance(below)) above else below
        return Pitch(step, alter, base + marks)
    }
}
