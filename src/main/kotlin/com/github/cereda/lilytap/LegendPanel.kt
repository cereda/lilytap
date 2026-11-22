// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The legend of the top column: what the colours of the raw taps mean, and which keys play
 * the piano.
 *
 * The legend is a small block of text with two coloured marks. It sits above the Start
 * button, in the room the settings card leaves beside it.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Line2D
import javax.swing.JComponent

/**
 * The legend: the colours of the raw taps and the keys of the tracker.
 *
 * The raw taps are ghosts under the snapped notes: an amber one was pressed late and a teal
 * one early. The tracker keys name the notes of the scale: the number row carries the white
 * keys and the top row the sharps, and the octave they sound is the one the panel shows.
 */
internal class LegendPanel : JComponent() {
    /** How wide the legend is, in pixels. */
    private val legendWidth = 168

    /** The lines of the legend that tell what the keys of the tracker do. */
    internal val trackerLines =
        listOf(
            "1–7 = C D E F G A B",
            "Q W E R T = the sharps",
            "8 / 9 = octave down / up",
            "0 = back to the note",
        )

    init {
        isOpaque = false
        font = Theme.font(11f)
        toolTipText =
            "Raw taps: amber = pressed late, teal = pressed early. " +
            "Tracker keys: 1 to 7 are C to B, Q W E R T the sharps, " +
            "8 moves the octave down, 9 moves it up and 0 puts it back on the note you are on."
    }

    override fun getPreferredSize(): Dimension = Dimension(legendWidth, 80)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            g2.font = Theme.font(11f)
            val fm = g2.fontMetrics
            // The first line: the two colours of the raw taps.
            var x = 0.0
            x = swatch(g2, x, 8.0, Theme.warn)
            x = word(g2, fm, x, 8.0, "late")
            x += 10
            x = swatch(g2, x, 8.0, Theme.accent2)
            word(g2, fm, x, 8.0, "early")
            // The next lines: the keys of the tracker.
            g2.color = Theme.dim
            trackerLines.forEachIndexed { index, line ->
                g2.drawString(line, 0f, (27 + index * 15).toFloat())
            }
        }
    }

    /**
     * Draws a coloured mark, the way the raw taps are drawn in the timeline.
     *
     * @param g2 the graphics context.
     * @param x where the mark starts.
     * @param y the middle of the mark.
     * @param colour the colour of the mark.
     * @return where the text after the mark starts.
     */
    private fun swatch(
        g2: Graphics2D,
        x: Double,
        y: Double,
        colour: Color,
    ): Double {
        g2.color = colour
        g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(Line2D.Double(x + 1, y, x + 11, y))
        return x + 17
    }

    /**
     * Draws a word of the legend.
     *
     * @param g2 the graphics context.
     * @param fm the metrics of the font.
     * @param x where the word starts.
     * @param y the middle of the word.
     * @param text the word.
     * @return where the text after the word starts.
     */
    private fun word(
        g2: Graphics2D,
        fm: FontMetrics,
        x: Double,
        y: Double,
        text: String,
    ): Double {
        g2.color = Theme.dim
        g2.drawString(text, x.toFloat(), (y + fm.ascent / 2.0 - 1).toFloat())
        return x + fm.stringWidth(text)
    }
}
