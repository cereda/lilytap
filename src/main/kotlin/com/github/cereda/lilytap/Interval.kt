// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The intervals the score can be transposed by, and the transposition that names one.
 */

/**
 * The intervals the score can be transposed by.
 *
 * An interval is a distance in two measures: the letters the notes move and the semitones
 * they move. The semitones of a diatonic interval come from the scale of the key the piece
 * is in, so a third is a major third in a major key and a minor third in a minor one, and
 * the piece keeps its shape when it moves.
 *
 * The semitone is the one chromatic interval: the letters of the notes stay where they are
 * and the accidentals move, so a piece in C major moves to C sharp major and not to D flat
 * major.
 *
 * @property steps how many letters the notes move: a second moves one, an octave seven.
 * @property majorSemitones how many semitones the interval is in a major key.
 * @property minorSemitones how many semitones the interval is in a minor key.
 * @property label the name of the interval, as the panel of intervals writes it.
 */
enum class Interval(
    val steps: Int,
    val majorSemitones: Int,
    val minorSemitones: Int,
    val label: String,
) {
    SECOND(1, 2, 2, "2nd"),
    THIRD(2, 4, 3, "3rd"),
    FOURTH(3, 5, 5, "4th"),
    FIFTH(4, 7, 7, "5th"),
    OCTAVE(7, 12, 12, "Octave"),
    SEMITONE(0, 1, 1, "Semitone"),
    ;

    /** Whether the interval keeps the letters of the notes where they are. */
    val chromatic: Boolean get() = steps == 0

    /**
     * The size of the interval in a key.
     *
     * The scale of the key says how big the interval is: the third of a major key is a major
     * third and the third of a minor one a minor third. The other intervals are the same in
     * both scales.
     *
     * @param major whether the key is a major one.
     * @return the number of semitones the interval is.
     */
    fun semitones(major: Boolean): Int = if (major) majorSemitones else minorSemitones

    /**
     * The name of the interval in a key, as it is written in English.
     *
     * @param major whether the key is a major one.
     * @return the name of the interval.
     */
    fun name(major: Boolean): String =
        when (this) {
            SECOND -> "major second"
            THIRD -> if (major) "major third" else "minor third"
            FOURTH -> "perfect fourth"
            FIFTH -> "perfect fifth"
            OCTAVE -> "octave"
            SEMITONE -> "semitone"
        }
}

/**
 * A transposition: an interval and the direction the notes move in it.
 *
 * @property interval the interval the notes move by.
 * @property up whether the notes move up.
 */
data class Transposition(
    val interval: Interval,
    val up: Boolean,
) {
    /** How many letters the notes move: up is positive, down is negative. */
    val steps: Int get() = if (up) interval.steps else -interval.steps

    /**
     * How many semitones the notes move in a key.
     *
     * @param key the key the piece is in.
     * @return the number of semitones, negative when the notes move down.
     */
    fun semitones(key: KeySignature): Int {
        val size = interval.semitones(key.major)
        return if (up) size else -size
    }

    /**
     * The name of the transposition, as the tooltip of the panel writes it.
     *
     * @param key the key the piece is in.
     * @return the name of the transposition.
     */
    fun name(key: KeySignature): String {
        val article = if (interval == Interval.OCTAVE) "an" else "a"
        val direction = if (up) "Up" else "Down"
        return "$direction $article ${interval.name(key.major)}"
    }
}

/**
 * What a transposition does.
 */
sealed interface TransposeResult {
    /**
     * The score moved.
     *
     * @property score the score in its new key.
     */
    data class Moved(
        val score: Score,
    ) : TransposeResult

    /**
     * The transposition was refused, and this says why.
     *
     * @property message the words of the refusal.
     */
    data class Refused(
        val message: String,
    ) : TransposeResult
}
