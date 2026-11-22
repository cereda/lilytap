// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The pitches panel: the piano that writes the pitch of the selected note.
 *
 * The panel says where the cursor is among the notes of the take, which octave the tracker
 * plays, and the button next to them takes the cursor to the first note, so the melody can be
 * filled in from the top.
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

    /** The button that shows the octave the tracker plays, and puts it back on the anchor. */
    val octave = PillButton("C4", Glyph.RESET, ButtonKind.GHOST, minW = 52, heightPx = 32)

    /** The counter: where the cursor is among the notes. */
    private val counter = GlyphLabel(null, "", 12f)

    init {
        isOpaque = false
        val header =
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(
                    JPanel(BorderLayout(8, 0)).apply {
                        isOpaque = false
                        add(fill, BorderLayout.WEST)
                        add(octave, BorderLayout.EAST)
                    },
                    BorderLayout.WEST,
                )
                add(counter, BorderLayout.EAST)
            }
        counter.toolTipText = "Where the cursor is among the notes of the take."
        fill.toolTipText = "Take the cursor to the first note of the take, so the melody can be filled in."
        octave.toolTipText =
            "The octave the tracker plays. Click it to put the tracker back on the note you are on."
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

    /**
     * Says which octave the tracker plays.
     *
     * The button puts the tracker back on the octave of the note that is selected, so the
     * tooltip says which note that is.
     *
     * @param value the octave the tracker plays.
     * @param anchor the octave of the note that is selected, when there is one.
     */
    fun setOctave(
        value: Int,
        anchor: Int?,
    ) {
        octave.text = "C$value"
        octave.toolTipText =
            if (anchor == null) {
                "The octave the tracker plays (C$value)."
            } else {
                "The octave the tracker plays (C$value). The note you are on is C$anchor — " +
                    "click to go back to it."
            }
        piano.liveOctave = value
    }

    /** The octave the tracker plays, as the panel says it. */
    val octaveText: String get() = octave.text
}
