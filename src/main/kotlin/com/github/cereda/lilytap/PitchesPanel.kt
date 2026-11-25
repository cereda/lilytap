// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The pitches panel: the piano that writes the pitch of the selected note.
 *
 * The panel says where the cursor is among the notes of the take, which octave the tracker
 * plays, and the button next to them takes the cursor to the first note, so the melody can be
 * filled in from the top. The stepper beside the button moves the octave the tracker plays.
 */

import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * The pitches panel: the piano, the counter, the button that starts the walk and the stepper
 * that says which octave the tracker plays.
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

    /** The button that transposes the whole score. */
    val transpose = TransposeButton()

    /**
     * The stepper that says which octave the tracker plays, and moves it.
     *
     * The value is the octave of the C the tracker plays; the ends move it an octave up or
     * down, and a click on the number takes it back to the note that is selected.
     */
    val octave =
        Stepper(
            initial = Pitch.DEFAULT.octave.toDouble(),
            minValue = Pitch.MIN_OCTAVE.toDouble(),
            maxValue = Pitch.MAX_OCTAVE.toDouble(),
            stepSize = 1.0,
            width = 116,
            format = { "C${it.toInt()}" },
        )

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
                        add(
                            JPanel(BorderLayout(8, 0)).apply {
                                isOpaque = false
                                add(transpose, BorderLayout.WEST)
                                add(octave, BorderLayout.EAST)
                            },
                            BorderLayout.CENTER,
                        )
                    },
                    BorderLayout.WEST,
                )
                add(counter, BorderLayout.EAST)
            }
        counter.toolTipText = "Where the cursor is among the notes of the take."
        fill.toolTipText = "Take the cursor to the first note of the take, so the melody can be filled in."
        transpose.toolTipText = "Move the whole score by an interval. The key follows, and Ctrl+Z undoes it."
        octave.toolTipText =
            "The octave the tracker plays. Click the number to put the tracker back on the note you are on."
        add(header, BorderLayout.NORTH)
        add(piano, BorderLayout.CENTER)
    }

    /**
     * Says whether the controls that work on the score are live.
     *
     * The fill button and the transpose button act on the score, so they are off when there
     * is none to act on: while a take is being recorded and while the score plays.
     *
     * @param on whether the score can be worked on.
     */
    fun setEditingEnabled(on: Boolean) {
        fill.isEnabled = on
        transpose.isEnabled = on
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
     * The stepper puts the tracker back on the octave of the note that is selected when the
     * number is clicked, so the tooltip says which note that is.
     *
     * @param value the octave the tracker plays.
     * @param anchor the octave of the note that is selected, when there is one.
     */
    fun setOctave(
        value: Int,
        anchor: Int?,
    ) {
        octave.value = value.toDouble()
        octave.toolTipText =
            if (anchor == null) {
                "The octave the tracker plays (C$value)."
            } else {
                "The octave the tracker plays (C$value). The note you are on is C$anchor — " +
                    "click the number to go back to it."
            }
        piano.liveOctave = value
    }

    /** The octave the tracker plays, as the panel says it. */
    val octaveText: String get() = octave.text
}
