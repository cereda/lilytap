// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The referee: the LilyPond file of the transposed scores, written out for LilyPond to read.
 *
 * The test writes a file for every case - the LilyPond file of the score and the pitches the
 * model says it has - and the script in `tools/lilypond-referee` has LilyPond play the text
 * and reads the MIDI back. The pitches LilyPond plays must be the pitches the model says.
 *
 * The file is the one the app writes when the user saves a score: the referee thus proves
 * that what the app saves is a document LilyPond reads.
 *
 * LilyPond is not on the PATH and is not a dependency of the build, so the reading of the
 * MIDI is left to the script: the test itself only writes the files and checks the model.
 */
class LilyPondRefereeTest {
    /** The folder the files are written to. */
    private val folder = File("build/lilypond-referee")

    @Test
    fun `the transposed scores are written out for the referee`() {
        folder.mkdirs()
        // The folder holds the cases of the last run: they are cleared, so the referee only
        // sees the cases of this one.
        folder.listFiles()?.forEach { it.delete() }
        for ((index, case) in cases.withIndex()) {
            val key = assertNotNull(KeySignature.parse(case.key))
            val score = scaleIn(key)
            val moved =
                assertIs<TransposeResult.Moved>(
                    score.transpose(Transposition(case.interval, case.up)),
                    "the transposition of ${case.key} was refused",
                )
            val name =
                "%02d-%s-%s%s".format(
                    index,
                    case.key.replace(" ", "-"),
                    case.interval.label,
                    if (case.up) "-up" else "-down",
                )
            val pitches = moved.score.events.mapNotNull { it.pitch }
            // Every case is written twice: once with the octaves in full and once with them
            // relative, because the app saves the file in either way. Each case carries the
            // MIDI file of the app as well, so the referee can hold it against the MIDI that
            // LilyPond writes for the same text.
            for (relative in listOf(false, true)) {
                val suffix = if (relative) "-relative" else ""
                File(folder, "$name$suffix.ly").writeText(
                    lilyPondFile(moved.score, relative = relative, midi = true),
                )
                File(folder, "$name$suffix.mid").writeBytes(midiFile(moved.score))
                File(folder, "$name$suffix.expected")
                    .writeText(pitches.joinToString("\n") { it.midi.toString() } + "\n")
            }
            assertEquals(pitches.size, 8, "the scale of the key has eight notes")
        }
        assertEquals(cases.size * 6, folder.listFiles()?.size, "every case has its files")
    }

    @Test
    fun `the scale of the key is written with the notes of the key`() {
        val key = assertNotNull(KeySignature.parse("F major"))
        val score = scaleIn(key)
        assertEquals(
            listOf("f'", "g'", "a'", "bes'", "c''", "d''", "e''", "f''"),
            score.events.map { it.pitch?.lilypond },
        )
    }

    /** A score with the scale of the key, one note per degree. */
    private fun scaleIn(key: KeySignature): Score {
        val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, key = key)
        val events =
            scaleOf(key).mapIndexed { index, pitch ->
                Event(TapKind.NOTE, index * 8, 8, pitch)
            }
        return Score(events, config)
    }

    /**
     * The scale of a key: its eight notes, from the tonic up.
     *
     * The scale of a key is the letters of the scale with the accidentals of the signature,
     * so the scale of F major has a B flat and the scale of B flat major has an E flat.
     */
    private fun scaleOf(key: KeySignature): List<Pitch> {
        val tonic = ((key.tonic.semitone + key.alter) % 12 + 12) % 12
        val start = 60 + tonic
        return (0 until 8).map { degree ->
            val step = PitchStep.entries[(key.tonic.ordinal + degree) % 7]
            val alter = key.altered[step] ?: 0
            val raw = step.semitone + alter - tonic
            val distance = (if (raw >= 0) raw else raw + 12) + if (degree == 7) 12 else 0
            key.spell(start + distance)
        }
    }

    /**
     * A case of the referee: the key of the piece, the interval and the direction.
     *
     * @property key the name of the key the piece is in.
     * @property interval the interval the score is transposed by.
     * @property up whether the notes move up.
     */
    private data class Case(
        val key: String,
        val interval: Interval,
        val up: Boolean,
    )

    companion object {
        /** The cases the referee checks. */
        private val cases =
            listOf(
                Case("C major", Interval.THIRD, true),
                Case("C minor", Interval.THIRD, true),
                Case("C major", Interval.SEMITONE, true),
                Case("C major", Interval.FIFTH, false),
                Case("C major", Interval.OCTAVE, true),
                Case("A minor", Interval.FIFTH, true),
                Case("G major", Interval.FOURTH, true),
                Case("F major", Interval.SECOND, false),
                Case("B♭ major", Interval.SEMITONE, true),
                Case("D minor", Interval.SECOND, true),
                // The sharp keys: the spelling needs the accidentals of the signature.
                Case("G♯ minor", Interval.THIRD, true),
                Case("E♭ major", Interval.FOURTH, false),
                // The keys past seven accidentals: the signature carries a double accidental,
                // and the notes are written with it.
                Case("G# major", Interval.SECOND, true),
                Case("Fisis major", Interval.THIRD, false),
                Case("Cisis major", Interval.SECOND, false),
                Case("Ceses major", Interval.THIRD, true),
                Case("Aisis minor", Interval.SECOND, false),
            )
    }
}
