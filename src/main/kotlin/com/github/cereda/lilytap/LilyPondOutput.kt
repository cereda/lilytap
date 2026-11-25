// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.Timer

/**
 * The card that holds the LilyPond text.
 *
 * The card carries the text and three icon buttons: one that copies it, one that writes it to
 * a file, and one that chooses how the octaves are written, in full or relative to the note
 * before. The first line of the text is a comment that says which way it is written, so a
 * fragment that is copied into a file says what it is.
 *
 * @param putOnClipboard what the copy button does with the text. It is a parameter so that
 * the tests can watch the copy without touching the clipboard of the machine.
 * @param saveTo what the save button does with the score. It is a parameter so that the
 * tests can watch the save without opening a dialog.
 */
internal class LilyPondOutput(
    private val putOnClipboard: (String) -> Unit = { text ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    },
    private val saveTo: (Score, Boolean) -> SaveResult = { _, _ -> SaveResult.Cancelled },
) {
    /** The text of the transcription. */
    val area = CodeArea()

    /** The box the text is in. */
    val scroll = JScrollPane(area)

    /** The button that copies the text. */
    val copy = PillButton("", Glyph.COPY, minW = 40, heightPx = 28)

    /** The button that writes the text to a file. */
    val save = PillButton("", Glyph.SAVE, minW = 40, heightPx = 28)

    /** The button that writes the octaves relative to the note before. */
    val relative = PillButton("", Glyph.RELATIVE, ButtonKind.GHOST, heightPx = 28)

    /** The card that holds the text: the third of the bottom row. */
    val card = Card()

    /** Whether the octaves are written relative to the note before. */
    var relativeOctaves = false
        private set

    /** The score the card is showing, or null when there is none. */
    private var shown: Score? = null

    /** Puts the copy button back the way it was. */
    private val copyReset = Timer(1500) { resetCopy() }.apply { isRepeats = false }

    /** Puts the save button back the way it was. */
    private val saveReset = Timer(1500) { resetSave() }.apply { isRepeats = false }

    init {
        scroll.apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = Theme.code
            verticalScrollBar.ui = ThinScrollBar()
            verticalScrollBar.preferredSize = Dimension(10, 0)
            horizontalScrollBar.ui = ThinScrollBar()
            horizontalScrollBar.preferredSize = Dimension(0, 10)
        }
        card.apply {
            layout = BorderLayout(0, 6)
            border = BorderFactory.createEmptyBorder(14, 18, 16, 18)
            add(buildOutputHeader(relative, copy, save), BorderLayout.NORTH)
            add(buildCodeCard(scroll), BorderLayout.CENTER)
        }
        copy.onClick = { copyText() }
        copy.toolTipText = COPY_TOOLTIP
        save.onClick = { saveFile() }
        save.toolTipText = SAVE_TOOLTIP
        relative.toolTipText =
            "Write the octaves relative to the note before, the way LilyPond reads them."
        updateButtons()
    }

    /**
     * Puts the LilyPond text of the score on the screen, in the way it is written.
     *
     * The first line is a comment that names the mode, so the text that is copied says
     * whether its octaves are absolute or relative.
     *
     * @param score the score to write, or null when there is none.
     */
    fun show(score: Score?) {
        if (score == null) {
            clear()
            return
        }
        shown = score
        val mode = if (relativeOctaves) "relative" else "absolute"
        val text = if (relativeOctaves) score.lilypondRelative else score.lilypond
        area.text = "% $mode\n$text"
        area.caretPosition = 0
        updateButtons()
    }

    /** Throws the text away: there is nothing to show until the next take. */
    fun clear() {
        shown = null
        area.text = ""
        resetCopy()
        copyReset.stop()
        resetSave()
        saveReset.stop()
        updateButtons()
    }

    /**
     * Writes the octaves in full, or relative to the note before.
     *
     * @param score the score to write again, in the new way.
     */
    fun toggleRelative(score: Score?) {
        relativeOctaves = !relativeOctaves
        relative.accentOverride = if (relativeOctaves) Theme.accent else null
        show(score)
    }

    /** Puts the LilyPond text on the clipboard. */
    fun copyText() {
        if (area.text.isBlank()) return
        putOnClipboard(area.text)
        copy.glyph = Glyph.CHECK
        copy.accentOverride = Theme.ok
        copy.toolTipText = COPIED_TOOLTIP
        copyReset.restart()
    }

    /**
     * Writes the LilyPond text to a file.
     *
     * The file is a whole document: the version, the score block and the bars, so that
     * LilyPond reads it on its own.
     */
    fun saveFile() {
        val s = shown ?: return
        if (saveTo(s, relativeOctaves) !is SaveResult.Saved) return
        save.glyph = Glyph.CHECK
        save.accentOverride = Theme.ok
        save.toolTipText = SAVED_TOOLTIP
        saveReset.restart()
    }

    /** Puts the card back in the colours of the theme. */
    fun refreshTheme() {
        area.refreshTheme()
        scroll.viewport.background = Theme.code
        relative.accentOverride = if (relativeOctaves) Theme.accent else null
    }

    /**
     * The buttons that read the text follow the text.
     *
     * With no score there is nothing to copy and nothing to save, so the buttons are off:
     * a click never lands on a button that would do nothing.
     */
    private fun updateButtons() {
        val live = shown != null
        copy.isEnabled = live
        save.isEnabled = live
    }

    /** Puts the copy button back the way it was. */
    private fun resetCopy() {
        copy.glyph = Glyph.COPY
        copy.accentOverride = null
        copy.toolTipText = COPY_TOOLTIP
    }

    /** Puts the save button back the way it was. */
    private fun resetSave() {
        save.glyph = Glyph.SAVE
        save.accentOverride = null
        save.toolTipText = SAVE_TOOLTIP
    }

    private companion object {
        /** What the copy button says when there is nothing to copy yet. */
        const val COPY_TOOLTIP = "Copy the LilyPond text."

        /** What the copy button says once the text is on the clipboard. */
        const val COPIED_TOOLTIP = "The LilyPond text is on the clipboard."

        /** What the save button says. */
        const val SAVE_TOOLTIP = "Write the LilyPond text to a file."

        /** What the save button says once the file is written. */
        const val SAVED_TOOLTIP = "The LilyPond text is in the file."
    }
}
