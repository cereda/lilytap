// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The LilyPond file of a score: a complete document that LilyPond can read on its own.
 */

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The LilyPond file of a score: a complete document.
 *
 * The text of the card is a fragment: it is meant to be pasted into a score of your own. The
 * file is the whole thing - the version, the score block, the staff and the bars - so that
 * LilyPond reads it on its own.
 *
 * @param score the score to write.
 * @param relative whether the octaves are written relative to the note before.
 * @param midi whether the file asks LilyPond for a MIDI file as well. The referee reads the
 * MIDI back, so it asks for one; a file the user saves does not need it.
 * @return the text of the file.
 */
internal fun lilyPondFile(
    score: Score,
    relative: Boolean,
    midi: Boolean = false,
): String {
    val body = staffBody(score, relative)
    return buildString {
        append("\\version \"$LILYPOND_VERSION\"\n")
        append("\n")
        append("\\score {\n")
        append("  \\new Staff {\n")
        body.forEach { append(it).append("\n") }
        append("  }\n")
        if (midi) append("  \\midi { }\n")
        append("}\n")
    }
}

/**
 * The bars of the score, indented the way they are inside the file.
 *
 * The text of the score is a fragment, and the file puts it inside a staff: the bars go
 * deeper than the block that holds them, so the file reads the way it is written.
 *
 * @param score the score to write.
 * @param relative whether the octaves are written relative to the note before.
 * @return the lines of the staff, one bar per line.
 */
private fun staffBody(
    score: Score,
    relative: Boolean,
): List<String> {
    val text = if (relative) score.lilypondRelative else score.lilypond
    val lines = text.lines()
    if (!relative || lines.size < 2) return lines.map { "    $it" }
    // The \relative block opens and closes on lines of their own: the bars inside go deeper.
    val open = lines.first()
    val close = lines.last()
    val bars = lines.subList(1, lines.size - 1).map { "      $it" }
    return listOf("    $open") + bars + listOf("    $close")
}

/**
 * Writes the LilyPond file of a score.
 *
 * @param path where the file goes.
 * @param score the score to write.
 * @param relative whether the octaves are written relative to the note before.
 * @param midi whether the file asks LilyPond for a MIDI file as well.
 * @return null when the file was written, or the reason it was not.
 */
internal fun writeLilyPondFile(
    path: Path,
    score: Score,
    relative: Boolean,
    midi: Boolean = false,
): String? =
    try {
        Files.writeString(path, lilyPondFile(score, relative, midi))
        null
    } catch (e: IOException) {
        e.message ?: "The file could not be written."
    }

/** The version of LilyPond the files are written for. */
private const val LILYPOND_VERSION = "2.26.0"
