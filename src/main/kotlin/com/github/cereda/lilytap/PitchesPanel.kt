// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The pitches panel: the piano that writes the pitch of the selected note.
 *
 * The panel says where the cursor is among the notes of the take, and the button next to it
 * takes the cursor to the first note, so the melody can be filled in from the top.
 */

import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * The pitches panel: the piano, the counter and the button that starts the walk.
 */
internal class PitchesPanel : JPanel(BorderLayout(0, 10)) {
    /** The piano: it writes the pitch of the selected note. */
    val piano = PianoPanel()

    /** The button that takes the cursor to the first note of the take. */
    val fill =
        PillButton(
            "Fill the melody",
            null,
            ButtonKind.GHOST,
            minW = 168,
            heightPx = 32,
        )

    /** The counter: where the cursor is among the notes. */
    private val counter = GlyphLabel(null, "", 12f)

    init {
        isOpaque = false
        val header =
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(fill, BorderLayout.WEST)
                add(counter, BorderLayout.EAST)
            }
        counter.toolTipText = "Where the cursor is among the notes of the take."
        fill.toolTipText = "Take the cursor to the first note of the take, so the melody can be filled in."
        add(header, BorderLayout.NORTH)
        add(piano, BorderLayout.CENTER)
    }

    /**
     * Says where the cursor is among the notes of the take.
     *
     * @param text the text of the counter.
     */
    fun setCounter(text: String) {
        counter.text = text
    }

    /** The text of the counter: where the cursor is among the notes of the take. */
    val counterText: String get() = counter.text
}
