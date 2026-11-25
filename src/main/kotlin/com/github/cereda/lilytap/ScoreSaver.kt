// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The save of the LilyPond text: the dialog that asks where the file goes, and the write.
 */

import java.io.File
import javax.swing.JFrame

/**
 * What a save did.
 */
internal sealed interface SaveResult {
    /** The file was written. */
    data object Saved : SaveResult

    /** The user gave the dialog up. */
    data object Cancelled : SaveResult

    /**
     * The file was not written.
     *
     * @property reason what went wrong.
     */
    data class Failed(
        val reason: String,
    ) : SaveResult
}

/**
 * The dialog that asks where the LilyPond file goes, and the write that follows it.
 *
 * The dialog is the one of the app: a field with the path and a save button. Its colours
 * are the ones of the theme - see `applyDialogTheme`.
 *
 * @param frame the window the dialog belongs to.
 * @param chooseFile what asks the user where the file goes. It takes the path the dialog
 * suggests and gives the file the user chose, or null when the user gave the dialog up. It
 * is a parameter so that the tests can write a file without opening a dialog.
 */
internal class ScoreSaver(
    private val frame: JFrame,
    private val chooseFile: (File) -> File? = { suggested -> SaveDialog(frame, suggested).choose() },
) {
    /** The folder the dialog opens in: the last one used, or the home directory. */
    private var folder: File? = null

    /**
     * Asks where the file goes and writes it.
     *
     * @param score the score to write.
     * @param relative whether the octaves are written relative to the note before.
     * @return what the save did.
     */
    fun save(
        score: Score,
        relative: Boolean,
    ): SaveResult {
        val start = folder ?: File(System.getProperty("user.home"))
        val chosen = chooseFile(File(start, DEFAULT_NAME)) ?: return SaveResult.Cancelled
        val target = withExtension(chosen)
        folder = target.parentFile
        val reason = writeLilyPondFile(target.toPath(), score, relative)
        if (reason != null) {
            showMessage(frame, saveRefused(reason))
            return SaveResult.Failed(reason)
        }
        return SaveResult.Saved
    }

    companion object {
        /** The name the dialog suggests. */
        private const val DEFAULT_NAME = SaveDialog.DEFAULT_NAME
    }
}

/**
 * The file with the extension of a LilyPond file.
 *
 * A name that already has an extension is left alone: the user may want another one.
 *
 * @param file the file the dialog gave.
 * @return the file to write.
 */
internal fun withExtension(file: File): File {
    val name = file.name
    if (name.substringAfterLast('.', "").isNotEmpty()) return file
    return File(file.parentFile, "$name.$LILYPOND_EXTENSION")
}

/**
 * The message the window shows when the file was not written.
 *
 * @param reason what went wrong.
 * @return the message of the dialog.
 */
internal fun saveRefused(reason: String): Message =
    Message(
        "Cannot write the file",
        "The file was not written",
        reason,
        Glyph.BOLT,
        Theme.rec,
    )

/** The extension of a LilyPond file. */
private const val LILYPOND_EXTENSION = "ly"
