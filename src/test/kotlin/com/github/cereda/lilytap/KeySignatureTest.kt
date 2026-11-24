// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The key of the piece: its signature, its name, and the spelling of every note. */
class KeySignatureTest {
    /** The key of a name: "Bb major" is the key of B flat. */
    private fun key(name: String): KeySignature = KeySignature.parse(name) ?: error("the name $name is not a key")

    /** The names of the twelve notes of an octave, in the order of the pitch classes. */
    private fun spellings(key: KeySignature): List<String> = (0..11).map { key.spell(60 + it).lilypondName }

    @Test
    fun `the keys stand on a circle of fifths`() {
        val expected =
            mapOf(
                "C major" to 0,
                "G major" to 1,
                "D major" to 2,
                "A major" to 3,
                "E major" to 4,
                "B major" to 5,
                "F# major" to 6,
                "C# major" to 7,
                "F major" to -1,
                "Bb major" to -2,
                "Eb major" to -3,
                "Ab major" to -4,
                "Db major" to -5,
                "Gb major" to -6,
                "Cb major" to -7,
            )
        for ((name, fifths) in expected) {
            assertEquals(fifths, key(name).fifths, "the key of $name has the wrong fifths")
        }
    }

    @Test
    fun `a minor key is three fifths below the major key of the same tonic`() {
        val expected =
            mapOf(
                "A minor" to 0,
                "E minor" to 1,
                "B minor" to 2,
                "F# minor" to 3,
                "C# minor" to 4,
                "D minor" to -1,
                "G minor" to -2,
                "C minor" to -3,
                "F minor" to -4,
                "Bb minor" to -5,
                "Eb minor" to -6,
                "Ab minor" to -7,
            )
        for ((name, fifths) in expected) {
            assertEquals(fifths, key(name).fifths, "the key of $name has the wrong fifths")
        }
    }

    @Test
    fun `the signature says which notes are altered`() {
        assertEquals(emptyMap(), key("C major").altered, "C major alters nothing")
        assertEquals(mapOf(PitchStep.F to 1), key("G major").altered)
        assertEquals(mapOf(PitchStep.F to 1, PitchStep.C to 1), key("D major").altered)
        assertEquals(mapOf(PitchStep.B to -1), key("F major").altered)
        assertEquals(
            mapOf(PitchStep.B to -1, PitchStep.E to -1, PitchStep.A to -1),
            key("Eb major").altered,
        )
        assertEquals(
            emptyMap(),
            key("A minor").altered,
            "A minor has the signature of C major",
        )
    }

    @Test
    fun `the key says its name in English and in LilyPond`() {
        assertEquals("C major", key("C major").name)
        assertEquals("B♭ major", key("Bb major").name)
        assertEquals("F♯ minor", key("F# minor").name)
        assertEquals("C♭ major", key("Cb major").name)
        assertEquals("\\key c \\major", key("C major").lilypond)
        assertEquals("\\key bes \\major", key("Bb major").lilypond)
        assertEquals("\\key fis \\minor", key("F# minor").lilypond)
        assertEquals("bes", key("Bb major").tonicName)
    }

    @Test
    fun `the name of a key can be read`() {
        assertEquals(key("Bb major"), KeySignature.parse("Bb major"))
        assertEquals(key("Bb major"), KeySignature.parse("bb MAJOR"))
        assertEquals(key("F# minor"), KeySignature.parse("F# minor"))
        assertEquals(key("C♭ major"), KeySignature.parse("C♭ major"))
        assertEquals(key("C♯ minor"), KeySignature.parse("C♯ minor"))
        assertEquals(key("A minor"), KeySignature.parse("a min"))
    }

    @Test
    fun `the name of a key can be read the way LilyPond writes it`() {
        assertEquals(key("C♯ major"), KeySignature.parse("cis major"))
        assertEquals(key("B♭ minor"), KeySignature.parse("bes minor"))
        assertEquals(key("F♯ major"), KeySignature.parse("fis major"))
        assertEquals(key("E♭ major"), KeySignature.parse("ees major"))
        assertEquals(key("D♭ major"), KeySignature.parse("des major"))
        assertEquals(key("G♭ major"), KeySignature.parse("ges major"))
        assertEquals(key("A♭ major"), KeySignature.parse("aes major"))
    }

    @Test
    fun `the two spellings of a name say the same key`() {
        val pairs =
            listOf(
                "C# major" to "cis major",
                "F# minor" to "fis minor",
                "Bb major" to "bes major",
                "C♭ major" to "ces major",
                "C♯ minor" to "cis minor",
                "C♯ major" to "C♯ major",
                "B♭ major" to "B♭ major",
            )
        for ((english, lilypond) in pairs) {
            assertEquals(
                KeySignature.parse(english),
                KeySignature.parse(lilypond),
                "$english and $lilypond are the same key",
            )
        }
    }

    @Test
    fun `every form of a name says the same key`() {
        val forms =
            listOf(
                "C# major",
                "C♯ major",
                "cis major",
                "C♯ MAJOR",
            )
        for (form in forms) {
            assertEquals(key("C# major"), KeySignature.parse(form), "$form is C sharp major")
        }
        val flats = listOf("Bb major", "B♭ major", "bes major", "BB major")
        for (form in flats) {
            assertEquals(key("Bb major"), KeySignature.parse(form), "$form is B flat major")
        }
    }

    @Test
    fun `a name that is not a key is not read`() {
        assertNull(KeySignature.parse(""))
        assertNull(KeySignature.parse("Bb"))
        assertNull(KeySignature.parse("Bb major extra"))
        assertNull(KeySignature.parse("H major"), "H is not a note")
        assertNull(KeySignature.parse("Bb dorian"), "there is no dorian here")
        assertNull(KeySignature.parse("B## major"), "two sharps is one too many")
    }

    @Test
    fun `a name that is not a key is malformed`() {
        assertEquals(KeyFault.Malformed, KeySignature.fault(""))
        assertEquals(KeyFault.Malformed, KeySignature.fault("Bb"))
        assertEquals(KeyFault.Malformed, KeySignature.fault("Bb dorian"))
        assertEquals(KeyFault.Malformed, KeySignature.fault("H major"))
        assertNull(KeySignature.fault("Bb major"), "a good name has no fault")
        assertNull(KeySignature.fault("cis major"), "a good name has no fault")
    }

    @Test
    fun `a key with more than seven accidentals has a fault of its own`() {
        assertEquals(KeyFault.TooManyAccidentals(8), KeySignature.fault("gis major"))
        assertEquals(KeyFault.TooManyAccidentals(8), KeySignature.fault("fes major"))
        assertEquals(KeyFault.TooManyAccidentals(11), KeySignature.fault("eis major"))
        assertEquals(KeyFault.TooManyAccidentals(10), KeySignature.fault("ais major"))
        assertEquals(KeyFault.TooManyAccidentals(9), KeySignature.fault("bis minor"))
        assertNull(KeySignature.parse("gis major"), "the app does not hold that key")
    }

    @Test
    fun `the fault says what is wrong`() {
        assertTrue(
            KeyFault.Malformed.message.contains("Not the name of a key"),
            "the fault says the name is not a key",
        )
        assertTrue(
            KeyFault.TooManyAccidentals(8).message.contains("eight") ||
                KeyFault.TooManyAccidentals(8).message.contains("8"),
            "the fault says how many accidentals the key has",
        )
        assertTrue(
            KeyFault.TooManyAccidentals(8).message.contains("seven"),
            "the fault says what the limit is",
        )
    }

    @Test
    fun `a key with more than seven accidentals does not exist`() {
        assertFailsWith<IllegalArgumentException> { KeySignature(PitchStep.G, 1, true) }
        assertFailsWith<IllegalArgumentException> { KeySignature(PitchStep.C, -1, false) }
    }

    @Test
    fun `the notes of C major are the sharps`() {
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("C major")),
        )
    }

    @Test
    fun `the notes of F major are the flats`() {
        assertEquals(
            listOf("c", "des", "d", "ees", "e", "f", "ges", "g", "aes", "a", "bes", "b"),
            spellings(key("F major")),
        )
    }

    @Test
    fun `the notes of G major are the sharps`() {
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("G major")),
        )
    }

    @Test
    fun `the notes of D major follow the key`() {
        // The C of the key is a C sharp, and the C natural keeps its natural sign.
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("D major")),
        )
    }

    @Test
    fun `the notes of F sharp major follow the key`() {
        // The E of the key is an E sharp, and the F natural keeps its natural sign.
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "eis", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("F# major")),
        )
    }

    @Test
    fun `the notes of C sharp major follow the key`() {
        // The B of the key is a B sharp, and the C natural keeps its natural sign.
        assertEquals(
            listOf("bis", "cis", "d", "dis", "e", "eis", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("C# major")),
        )
    }

    @Test
    fun `the notes of Bb major follow the key`() {
        assertEquals(
            listOf("c", "des", "d", "ees", "e", "f", "ges", "g", "aes", "a", "bes", "b"),
            spellings(key("Bb major")),
        )
    }

    @Test
    fun `the notes of Cb major follow the key`() {
        // The F of the key is an F flat, and the B of the key is a C flat.
        assertEquals(
            listOf("c", "des", "d", "ees", "fes", "f", "ges", "g", "aes", "a", "bes", "ces"),
            spellings(key("Cb major")),
        )
    }

    @Test
    fun `the seventh of a minor key is raised`() {
        assertEquals("gis", key("A minor").spell(68).lilypondName, "the leading tone of A minor")
        assertEquals("dis", key("E minor").spell(63).lilypondName, "the leading tone of E minor")
        assertEquals("cis", key("D minor").spell(61).lilypondName, "the leading tone of D minor")
        assertEquals("fis", key("G minor").spell(66).lilypondName, "the leading tone of G minor")
        assertEquals("b", key("C minor").spell(71).lilypondName, "the leading tone of C minor")
        assertEquals("e", key("F minor").spell(64).lilypondName, "the leading tone of F minor")
        assertEquals("g", key("Ab minor").spell(67).lilypondName, "the leading tone of Ab minor")
    }

    @Test
    fun `the leading tone of a sharp minor key can be a double sharp`() {
        assertEquals("fisis", key("G# minor").spell(67).lilypondName, "the leading tone of G sharp minor")
        assertEquals("cisis", key("D# minor").spell(62).lilypondName, "the leading tone of D sharp minor")
        assertEquals("gisis", key("A# minor").spell(69).lilypondName, "the leading tone of A sharp minor")
    }

    @Test
    fun `the double sharp of the leading tone keeps its sound`() {
        val sharp = key("G# minor").spell(67)
        assertEquals(67, sharp.midi, "the note sounds the same")
        assertEquals(Pitch(PitchStep.F, 2, 4), sharp, "the leading tone of G sharp minor")
    }

    @Test
    fun `the other notes of a sharp minor key follow the key`() {
        // The F of the key is an F sharp, and the E keeps its natural sign.
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "fisis", "gis", "a", "ais", "b"),
            spellings(key("G# minor")),
        )
    }

    @Test
    fun `the other notes of a minor key follow the key`() {
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("A minor")),
        )
        assertEquals(
            listOf("c", "cis", "d", "dis", "e", "f", "fis", "g", "gis", "a", "ais", "b"),
            spellings(key("E minor")),
        )
        assertEquals(
            listOf("c", "cis", "d", "ees", "e", "f", "ges", "g", "aes", "a", "bes", "b"),
            spellings(key("D minor")),
        )
    }

    @Test
    fun `a note keeps the sound it has and only changes its name`() {
        val before = Pitch(PitchStep.B, 1, 4)
        val after = key("C major").spell(before.midi)
        assertEquals(before.midi, after.midi, "the note sounds the same")
        assertEquals("c", after.lilypondName, "a B sharp is a C")
        assertEquals(5, after.octave, "a B sharp of the fourth is a C of the fifth")
    }

    @Test
    fun `a flat that crosses the C takes its octave with it`() {
        val before = Pitch(PitchStep.C, -1, 5)
        val after = key("C major").spell(before.midi)
        assertEquals(before.midi, after.midi, "the note sounds the same")
        assertEquals("b", after.lilypondName, "a C flat is a B")
        assertEquals(4, after.octave, "a C flat of the fifth is a B of the fourth")
    }

    @Test
    fun `the lowest note of the keyboard has a name in every key`() {
        for (name in listOf("C major", "C# major", "Cb major", "A minor", "Ab minor")) {
            val pitch = key(name).spell(12)
            assertEquals(12, pitch.midi, "the lowest note sounds the same in $name")
            assertTrue(pitch.octave in Pitch.MIN_OCTAVE..Pitch.MAX_OCTAVE)
        }
    }

    @Test
    fun `the highest note of the keyboard has a name in every key`() {
        for (name in listOf("C major", "C# major", "Cb major", "A minor", "Ab minor")) {
            val pitch = key(name).spell(119)
            assertEquals(119, pitch.midi, "the highest note sounds the same in $name")
            assertTrue(pitch.octave in Pitch.MIN_OCTAVE..Pitch.MAX_OCTAVE)
        }
    }

    @Test
    fun `only C major needs no declaration`() {
        assertTrue(KeySignature.C_MAJOR.isCMajor)
        assertTrue(!key("A minor").isCMajor, "A minor is not C major")
        assertTrue(!key("F major").isCMajor)
    }
}
