// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The parts of the window that are built once and never change: the column of the settings
 * card and the bottom row of the window.
 */

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * The column beside the settings: the legend above the Start button.
 *
 * The legend says what the colours of the raw taps mean and which keys play the piano, and
 * the Start button stays in the middle of the column.
 *
 * @param legend the legend of the raw taps and the tracker keys.
 * @param start the button that starts a take.
 * @return the column of the settings card.
 */
internal fun buildStartColumn(
    legend: JComponent,
    start: JComponent,
): JComponent =
    JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 20, 0, 0)
        add(
            legend,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.NORTH
                fill = GridBagConstraints.HORIZONTAL
            },
        )
        add(
            start,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                weighty = 1.0
                anchor = GridBagConstraints.CENTER
            },
        )
    }

/**
 * The header of the LilyPond card: the name of the text and the button that copies it.
 *
 * @param copy the button that copies the text.
 * @return the header of the card.
 */
internal fun buildOutputHeader(copy: JComponent): JComponent =
    JPanel(BorderLayout()).apply {
        isOpaque = false
        add(GlyphLabel(Glyph.NOTE, "LilyPond", 14f, { Theme.text }, Font.BOLD), BorderLayout.WEST)
        add(copy, BorderLayout.EAST)
    }

/**
 * The box that holds the LilyPond text.
 *
 * @param scroll the scroll pane of the text.
 * @return the box of the text.
 */
internal fun buildCodeCard(scroll: JScrollPane): JComponent =
    Card({ Theme.code }, 12.0).apply {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        add(scroll, BorderLayout.CENTER)
    }

/**
 * The card that holds the pitches panel.
 *
 * @param pitches the pitches panel: the piano and the counter.
 * @return the card of the pitches panel.
 */
internal fun buildPitchesCard(pitches: JComponent): JComponent =
    Card().apply {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(14, 18, 16, 18)
        add(pitches, BorderLayout.CENTER)
    }

/**
 * The bottom row of the window: the pitches panel and the LilyPond text.
 *
 * The pitches panel takes two thirds of the row and the LilyPond card the rest. The two
 * cards ask for the width they get, so the window keeps the size it has.
 *
 * @param pitchesCard the card that holds the pitches panel.
 * @param outputCard the card that holds the LilyPond text.
 * @return the bottom row of the window.
 */
internal fun buildBottom(
    pitchesCard: JComponent,
    outputCard: JComponent,
): JComponent {
    pitchesCard.preferredSize = Dimension(PITCHES_WIDTH, BOTTOM_HEIGHT)
    outputCard.preferredSize = Dimension(OUTPUT_WIDTH, BOTTOM_HEIGHT)
    return JPanel(GridBagLayout()).apply {
        isOpaque = false
        add(
            pitchesCard,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 2.0
                fill = GridBagConstraints.BOTH
            },
        )
        add(
            outputCard,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.BOTH
                insets = Insets(0, BOTTOM_GAP, 0, 0)
            },
        )
    }
}
