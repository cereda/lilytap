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
 * one early. The tracker keys play the piano: the number row carries the white keys and the
 * top row the black ones.
 */
internal class LegendPanel : JComponent() {
    /** How wide the legend is, in pixels. */
    private val legendWidth = 168

    init {
        isOpaque = false
        font = Theme.font(11f)
        toolTipText =
            "Raw taps: amber = pressed late, teal = pressed early. " +
            "Tracker keys: 1 to 0 are the white keys (middle C upwards), " +
            "Q W E R T Y I O P are the black ones."
    }

    override fun getPreferredSize(): Dimension = Dimension(legendWidth, 46)

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
            // The next two lines: the keys of the tracker.
            g2.color = Theme.dim
            g2.drawString("1–0 = white keys", 0f, 27f)
            g2.drawString("Q–P = black keys", 0f, 42f)
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
