// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The key of the piece: the signature that says which notes are black, and the spelling of
 * every note that follows it.
 */

import kotlin.math.abs

/**
 * What is wrong with the name of a key.
 */
sealed interface KeyFault {
    /** The text is not the name of a key at all. */
    data object Malformed : KeyFault

    /**
     * The key is a real one, but its signature carries more accidentals than the app holds.
     *
     * @property fifths the number of accidentals the signature would carry.
     */
    data class TooManyAccidentals(
        val fifths: Int,
    ) : KeyFault
}

/**
 * A key signature: the tonic of the key and the mode it is in.
 *
 * The key does two things. It says which accidentals the score carries - `\key f \major`
 * tells LilyPond that the B is flat - and it says how every note is spelled: `ees` in F
 * major, `dis` in E major.
 *
 * @property tonic the letter of the tonic.
 * @property alter the accidental of the tonic: -1 for a flat, 0 for a natural, 1 for a sharp.
 * @property major true for a major key, false for a minor one.
 */
data class KeySignature(
    val tonic: PitchStep,
    val alter: Int = 0,
    val major: Boolean = true,
) {
    init {
        require(alter in -1..1) { "A tonic carries at most one sharp or one flat" }
        require(fifths in -7..7) { "A key signature has at most seven accidentals" }
    }

    /**
     * The number of sharps (positive) or flats (negative) of the signature.
     *
     * The keys stand on a circle of fifths: every step to the right adds a sharp, every step
     * to the left adds a flat. The minor keys are three steps to the left of the major key
     * with the same tonic.
     */
    val fifths: Int
        get() = MAJOR_FIFTHS.getValue(tonic) + 7 * alter - (if (major) 0 else 3)

    /** The steps the signature alters, and by how much. */
    val altered: Map<PitchStep, Int>
        get() {
            val count = abs(fifths)
            val steps = if (fifths > 0) SHARP_ORDER else FLAT_ORDER
            return steps.take(count).associateWith { if (fifths > 0) 1 else -1 }
        }

    /** The LilyPond name of the tonic: `f`, `bes`. */
    val tonicName: String
        get() =
            "${tonic.letter}" +
                when (alter) {
                    1 -> "is"
                    -1 -> "es"
                    else -> ""
                }

    /** The LilyPond line that declares the key: `\key f \major`. */
    val lilypond: String get() = "\\key $tonicName \\${if (major) "major" else "minor"}"

    /**
     * The name of the key as the app writes it: `B♭ major`.
     *
     * The accidental is the sign the app draws everywhere else - a pitch is `C♯4`, not
     * `C#4` - and the name is read back in any of the forms it accepts.
     */
    val name: String
        get() =
            "${tonic.letter.uppercase()}" +
                when (alter) {
                    1 -> "♯"
                    -1 -> "♭"
                    else -> ""
                } + " ${if (major) "major" else "minor"}"

    /** Whether the key is C major: the key the LilyPond text does not have to declare. */
    val isCMajor: Boolean get() = this == C_MAJOR

    /**
     * The pitch of a MIDI note in this key.
     *
     * The note of the key comes first: in F major the B is a B♭, and it is written `bes`.
     * The notes that are not in the key follow the direction of the key: a flat key spells
     * them with flats, a sharp key with sharps. The seventh of a minor key is the one
     * exception: it is raised, so in D minor the C is a C♯ and not a D♭.
     *
     * A note keeps the sound it has and only changes its name: a B♯4 is a C5, and a C♭5 is
     * a B4.
     *
     * @param midi the MIDI number of the note.
     * @return the pitch of the note in this key.
     */
    fun spell(midi: Int): Pitch {
        val pitchClass = midi % 12
        for ((step, alter) in spellings(pitchClass)) {
            val octave = octaveOf(midi, step, alter)
            if (octave in Pitch.MIN_OCTAVE..Pitch.MAX_OCTAVE) return Pitch(step, alter, octave)
        }
        throw IllegalArgumentException("The note $midi has no spelling in $name")
    }

    /**
     * The spellings of a pitch class in this key, the best one first.
     *
     * @param pitchClass the pitch class of the note.
     * @return the letters the note can be written with.
     */
    private fun spellings(pitchClass: Int): List<Pair<PitchStep, Int>> {
        val candidates = mutableListOf<Pair<PitchStep, Int>>()
        // The note of the key: the letter the signature alters.
        for (step in PitchStep.entries) {
            val alter = altered[step] ?: 0
            if ((step.semitone + alter - pitchClass) % 12 == 0) candidates += step to alter
        }
        // The raised seventh of a minor key: the leading tone of the harmonic minor.
        raisedSeventh()?.let { (step, alter) ->
            if ((step.semitone + alter - pitchClass) % 12 == 0) candidates += step to alter
        }
        // The natural note, when the note is a natural one.
        NATURAL[pitchClass]?.let { candidates += it }
        // The note the key points at: a sharp in a sharp key, a flat in a flat key.
        if (fifths >= 0) {
            SHARP[pitchClass]?.let { candidates += it }
            FLAT[pitchClass]?.let { candidates += it }
        } else {
            FLAT[pitchClass]?.let { candidates += it }
            SHARP[pitchClass]?.let { candidates += it }
        }
        return candidates.distinct()
    }

    /**
     * The raised seventh of a minor key: the note a semitone above the seventh of the scale.
     *
     * In D minor it is a C♯, and in A♭ minor it is a G natural. A key whose seventh is
     * already a sharp cannot raise it any further, and has no raised seventh.
     *
     * @return the letter and the accidental of the raised seventh, or null when there is none.
     */
    private fun raisedSeventh(): Pair<PitchStep, Int>? {
        if (major) return null
        val letter = PitchStep.entries[(tonic.ordinal + 6) % 7]
        val alter = (altered[letter] ?: 0) + 1
        if (alter !in -1..1) return null
        return letter to alter
    }

    companion object {
        /**
         * The fifths of the major key of every letter, before the tonic is altered.
         *
         * The tables come before the keys they are used for: a key is built when it is read,
         * and it needs the tables to know how many accidentals it has.
         */
        private val MAJOR_FIFTHS =
            mapOf(
                PitchStep.C to 0,
                PitchStep.G to 1,
                PitchStep.D to 2,
                PitchStep.A to 3,
                PitchStep.E to 4,
                PitchStep.B to 5,
                PitchStep.F to -1,
            )

        /** The order the sharps of a signature come in. */
        private val SHARP_ORDER =
            listOf(
                PitchStep.F,
                PitchStep.C,
                PitchStep.G,
                PitchStep.D,
                PitchStep.A,
                PitchStep.E,
                PitchStep.B,
            )

        /** The order the flats of a signature come in. */
        private val FLAT_ORDER =
            listOf(
                PitchStep.B,
                PitchStep.E,
                PitchStep.A,
                PitchStep.D,
                PitchStep.G,
                PitchStep.C,
                PitchStep.F,
            )

        /** The natural note of every pitch class that is a natural one. */
        private val NATURAL =
            mapOf(
                0 to (PitchStep.C to 0),
                2 to (PitchStep.D to 0),
                4 to (PitchStep.E to 0),
                5 to (PitchStep.F to 0),
                7 to (PitchStep.G to 0),
                9 to (PitchStep.A to 0),
                11 to (PitchStep.B to 0),
            )

        /** The sharp of every pitch class that is a black key. */
        private val SHARP =
            mapOf(
                1 to (PitchStep.C to 1),
                3 to (PitchStep.D to 1),
                6 to (PitchStep.F to 1),
                8 to (PitchStep.G to 1),
                10 to (PitchStep.A to 1),
            )

        /** The flat of every pitch class that is a black key. */
        private val FLAT =
            mapOf(
                1 to (PitchStep.D to -1),
                3 to (PitchStep.E to -1),
                6 to (PitchStep.G to -1),
                8 to (PitchStep.A to -1),
                10 to (PitchStep.B to -1),
            )

        /** What separates the words of a key name. */
        private val WHITESPACE = Regex("\\s+")

        /** The key the app starts in: no accidentals at all. */
        val C_MAJOR = KeySignature(PitchStep.C, 0, true)

        /** The minor key with no accidentals: the relative minor of C major. */
        val A_MINOR = KeySignature(PitchStep.A, 0, false)

        /**
         * Reads the name of a key: `Bb major`, `F# minor`, `C♭ major`, `bes minor`.
         *
         * The tonic is a letter with an optional accidental, and the accidental can be written
         * the way it is written in English (`#`, `♯`, `b`, `♭`) or the way LilyPond writes it
         * (`is` for a sharp, `es` for a flat). The mode is `major` or `minor`.
         *
         * @param text the name to read.
         * @return the key, or null when the text is not the name of a key.
         */
        fun parse(text: String): KeySignature? = read(text).key

        /**
         * Says what is wrong with the name of a key.
         *
         * A text that is not the name of a key at all is one thing; the name of a key that
         * carries more accidentals than the app holds is another, and the second one deserves
         * its own words.
         *
         * @param text the name to read.
         * @return the fault, or null when the text is the name of a key the app can hold.
         */
        fun fault(text: String): KeyFault? = read(text).fault

        /**
         * Reads the name of a key and says what is wrong with it, when something is.
         *
         * @param text the name to read.
         * @return the key and the fault: one of them is always there.
         */
        private fun read(text: String): Reading {
            val parts = text.trim().lowercase().split(WHITESPACE)
            if (parts.size != 2) return Reading(null, KeyFault.Malformed)
            val major =
                when (parts[1]) {
                    "major", "maj" -> true
                    "minor", "min" -> false
                    else -> return Reading(null, KeyFault.Malformed)
                }
            val step =
                PitchStep.of(parts[0].firstOrNull() ?: return Reading(null, KeyFault.Malformed))
                    ?: return Reading(null, KeyFault.Malformed)
            val alter =
                when (parts[0].drop(1)) {
                    "" -> 0
                    "#", "♯" -> 1
                    "b", "♭" -> -1
                    "is" -> 1
                    "es" -> -1
                    else -> return Reading(null, KeyFault.Malformed)
                }
            val fifths = MAJOR_FIFTHS.getValue(step) + 7 * alter - (if (major) 0 else 3)
            if (fifths !in -7..7) {
                return Reading(null, KeyFault.TooManyAccidentals(abs(fifths)))
            }
            return Reading(KeySignature(step, alter, major), null)
        }

        /**
         * The name of a key read: the key, or what is wrong with the name.
         *
         * @property key the key, when the name is one the app can hold.
         * @property fault what is wrong with the name, when there is something wrong.
         */
        private class Reading(
            val key: KeySignature?,
            val fault: KeyFault?,
        )
    }
}

/**
 * The octave a note has when it is written with a letter and an accidental.
 *
 * The octave of a note is not changed by its accidental: a B♯4 is the C5, and the two are
 * the same note.
 *
 * @param midi the MIDI number of the note.
 * @param step the letter of the note.
 * @param alter the accidental of the note.
 * @return the octave of the note, in scientific notation.
 */
private fun octaveOf(
    midi: Int,
    step: PitchStep,
    alter: Int,
): Int = Math.floorDiv(midi - step.semitone - alter, 12) - 1
