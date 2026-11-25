// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The export of the MIDI file: the button that writes it, the dialog and the write.
 */

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.Timer

/**
 * The export of the score as a MIDI file.
 *
 * The button writes the score to a file the user names: the window that asks is the one of
 * the app, and the file it writes is the one the app builds itself - a standard MIDI file
 * with the notes of the score, the tempo, the meter and the key.
 *
 * @param frame the window the dialog belongs to.
 * @param score the score to write, or null when there is none.
 * @param chooseFile what asks the user where the file goes. It takes the path the dialog
 * suggests and gives the file the user chose, or null when the user gave the dialog up. It
 * is a parameter so that the tests can write a file without opening a dialog.
 */
internal class MidiExport(
    private val frame: JFrame,
    private val score: () -> Score?,
    private val chooseFile: (File) -> File? = { suggested ->
        SaveDialog(frame, suggested, MIDI_FORMAT).choose()
    },
) {
    /** The button that writes the score to a MIDI file. */
    val button = PillButton("", Glyph.MIDI, heightPx = 32)

    /** The folder the dialog opens in: the last one used, or the home directory. */
    private var folder: File? = null

    /** Puts the button back the way it was. */
    private val reset = Timer(1500) { resetButton() }.apply { isRepeats = false }

    init {
        button.onClick = { export() }
        button.toolTipText = TOOLTIP
    }

    /**
     * Writes the score to a MIDI file, if there is a score to write.
     */
    fun export() {
        val s = score() ?: return
        val start = folder ?: File(System.getProperty("user.home"))
        val chosen = chooseFile(File(start, MIDI_FORMAT.name)) ?: return
        val target = withExtension(chosen, MIDI_FORMAT.extension)
        folder = target.parentFile
        val reason = writeMidiFile(target.toPath(), s)
        if (reason != null) {
            showMessage(frame, saveRefused(reason))
            return
        }
        button.glyph = Glyph.CHECK
        button.accentOverride = Theme.ok
        button.toolTipText = SAVED_TOOLTIP
        reset.restart()
    }

    /** Puts the button back the way it was. */
    private fun resetButton() {
        button.glyph = Glyph.MIDI
        button.accentOverride = null
        button.toolTipText = TOOLTIP
    }

    companion object {
        /** What the button says. */
        const val TOOLTIP = "Write the score to a MIDI file."

        /** What the button says once the file is written. */
        const val SAVED_TOOLTIP = "The score is in the MIDI file."
    }
}

/**
 * Writes the MIDI file of a score.
 *
 * @param path where the file goes.
 * @param score the score to write.
 * @return null when the file was written, or the reason it was not.
 */
internal fun writeMidiFile(
    path: Path,
    score: Score,
): String? =
    try {
        Files.write(path, midiFile(score))
        null
    } catch (e: IOException) {
        e.message ?: "The file could not be written."
    }
