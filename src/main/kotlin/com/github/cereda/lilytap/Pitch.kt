// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The pitch of a note: the step (the letter), the accidental and the octave.
 *
 * The app speaks two vocabularies for the same thing, and they are exact translations of
 * each other:
 *
 *  - LilyPond (lowercase): `c'`, `cis'`, `ces'`, `c`, `c,` - what you type in and what the
 *    LilyPond output says.
 *  - Scientific notation (uppercase): `C4`, `C♯4`, `C♭3` - what the score strip shows.
 *
 * LilyPond writes the notes `c` to `b` in the octave below middle C, so middle C is `c'`
 * there and C4 in scientific notation: the two vocabularies differ by one octave mark, and
 * the app always puts the right one in.
 */

import kotlin.math.pow

/** The seven letters of the scale, with the number of semitones they stand for. */
enum class PitchStep(
    /** The LilyPond letter of the step. */
    val letter: Char,
    /** The number of semitones the step is above C. */
    val semitone: Int,
) {
    C('c', 0),
    D('d', 2),
    E('e', 4),
    F('f', 5),
    G('g', 7),
    A('a', 9),
    B('b', 11),
    ;

    companion object {
        /**
         * The step of a letter.
         *
         * @param letter the letter to look up.
         * @return the step, or null when the letter is not a note name.
         */
        fun of(letter: Char): PitchStep? = entries.find { it.letter == letter }
    }
}

/**
 * A pitch: the step of the scale, the accidental and the octave.
 *
 * @property step the letter of the note (C, D, E...).
 * @property alter the accidental: -1 for a flat, 0 for a natural, 1 for a sharp.
 * @property octave the octave in scientific notation: middle C is C4.
 */
data class Pitch(
    val step: PitchStep,
    val alter: Int = 0,
    val octave: Int = 4,
) {
    init {
        require(alter in -1..1) { "A pitch carries at most one sharp or one flat" }
        require(octave in MIN_OCTAVE..MAX_OCTAVE) {
            "The octave of a pitch is between $MIN_OCTAVE and $MAX_OCTAVE"
        }
        require(midi in 12..127) { "The pitch must be a note a MIDI keyboard can play" }
    }

    /** The MIDI note number: middle C is 60. */
    val midi: Int get() = 12 * (octave + 1) + step.semitone + alter

    /** The frequency in Hz, with A4 at 440 Hz. */
    val frequency: Double get() = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** The LilyPond name of the pitch: `c'`, `cis'`, `ces'`, `c`, `c,`. */
    val lilypond: String
        get() {
            val accidental =
                when (alter) {
                    1 -> "is"
                    -1 -> "es"
                    else -> ""
                }
            val marks =
                when {
                    octave > LILYPOND_BASE_OCTAVE -> "'".repeat(octave - LILYPOND_BASE_OCTAVE)
                    octave < LILYPOND_BASE_OCTAVE -> ",".repeat(LILYPOND_BASE_OCTAVE - octave)
                    else -> ""
                }
            return "${step.letter}$accidental$marks"
        }

    /** The scientific name of the pitch: `C4`, `C#4`, `C-3`. */
    val scientific: String
        get() {
            val accidental =
                when (alter) {
                    1 -> "♯"
                    -1 -> "♭"
                    else -> ""
                }
            return "${step.letter.uppercase()}$accidental$octave"
        }

    companion object {
        /**
         * The lowest octave a pitch can have (C0).
         *
         * The app keeps to the octaves LilyPond writes in absolute mode, from `c,,,` to
         * `c''''`: the note below C0 would be a `c,,,,` nobody writes.
         */
        const val MIN_OCTAVE = 0

        /** The highest octave a pitch can have (C8). */
        const val MAX_OCTAVE = 8

        /**
         * The octave LilyPond writes without a mark: `c` is the C below middle C.
         *
         * LilyPond puts the notes `c` to `b` in the octave below middle C, so a name without
         * octave marks is scientific C3, and middle C (scientific C4) is written `c'`.
         */
        const val LILYPOND_BASE_OCTAVE = 3

        /** Middle C: the pitch a note gets when nothing else is known. */
        val DEFAULT = Pitch(PitchStep.C, 0, 4)

        /**
         * Parses a LilyPond note name.
         *
         * A name is a letter, an optional accidental (`is` for a sharp, `es` for a flat) and
         * any number of octave marks: `'` goes up and `,` goes down. A name without marks is
         * the octave below middle C, so `c` is C3 and `c'` is middle C.
         *
         * The name has to be lowercase: in LilyPond an uppercase letter is the absolute
         * octave, and the app does not write absolute names.
         *
         * @param text the name to read.
         * @return the pitch, or null when the text is not a note name.
         */
        fun parse(text: String): Pitch? {
            val name = text.trim()
            if (name.isEmpty()) return null
            val step = PitchStep.of(name[0]) ?: return null
            val rest = name.substring(1)
            val alter =
                when {
                    rest.startsWith("is") -> 1
                    rest.startsWith("es") -> -1
                    else -> 0
                }
            val marks = if (alter == 0) rest else rest.substring(2)
            var octave = LILYPOND_BASE_OCTAVE
            for (mark in marks) {
                when (mark) {
                    '\'' -> octave++
                    ',' -> octave--
                    else -> return null
                }
            }
            return try {
                Pitch(step, alter, octave)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
