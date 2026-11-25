// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The vector icons: the ones of the score editor have to draw something, and they have to differ. */
class GlyphTest {
    @Test
    fun `the score editor icons draw something`() {
        for (glyph in EDITOR_GLYPHS) {
            assertTrue(ink(glyph) > 0, "${glyph.name} draws nothing")
        }
    }

    @Test
    fun `the tap icon draws a ripple`() {
        assertTrue(ink(Glyph.TAP) > 0, "the tap icon draws nothing")
        assertTrue(pixels(Glyph.TAP) != pixels(Glyph.METRONOME), "the tap icon looks like the metronome")
        assertTrue(pixels(Glyph.TAP) != pixels(Glyph.CLOCK), "the tap icon looks like the clock")
    }

    @Test
    fun `the beats icon shows three beats`() {
        assertTrue(ink(Glyph.BEATS) > 0, "the beats icon draws nothing")
        // The top dot stands alone, the two lines of the triangle come down from it, and the
        // two bottom dots are not joined: the bottom of the group is open.
        assertEquals(1, groupsInRow(Glyph.BEATS, (0.19 * BIG).toInt()), "the top dot stands alone")
        assertEquals(2, groupsInRow(Glyph.BEATS, (0.50 * BIG).toInt()), "the sides of the triangle")
        assertEquals(2, groupsInRow(Glyph.BEATS, (0.79 * BIG).toInt()), "the bottom dots are not joined")
    }

    @Test
    fun `the relative icon is a step from the lower note to the upper one`() {
        assertTrue(ink(Glyph.RELATIVE) > 0, "the relative icon draws nothing")
        // The two dashes stand alone on their rows, and the diagonal stands alone between them.
        assertEquals(1, groupsInRow(Glyph.RELATIVE, (0.72 * BIG).toInt()), "the lower dash stands alone")
        assertEquals(1, groupsInRow(Glyph.RELATIVE, (0.28 * BIG).toInt()), "the upper dash stands alone")
        assertEquals(1, groupsInRow(Glyph.RELATIVE, (0.50 * BIG).toInt()), "the diagonal stands alone")
        // The lower note is on the left and the upper one on the right: the two do not overlap.
        val lower = inkBoundsInRow(Glyph.RELATIVE, (0.72 * BIG).toInt())
        val upper = inkBoundsInRow(Glyph.RELATIVE, (0.28 * BIG).toInt())
        assertTrue(lower.second < upper.first, "the step goes from the lower left to the upper right")
        assertTrue(pixels(Glyph.RELATIVE) != pixels(Glyph.NOTE), "the relative icon looks like the note")
        assertTrue(pixels(Glyph.RELATIVE) != pixels(Glyph.COPY), "the relative icon looks like the copy mark")
    }

    @Test
    fun `the key icon is a sharp sign`() {
        assertTrue(ink(Glyph.KEY) > 0, "the key icon draws nothing")
        // Two upright strokes: the row near the top shows them apart.
        assertEquals(2, groupsInRow(Glyph.KEY, (0.24 * BIG).toInt()), "the two upright strokes")
        // The two leaning strokes reach out on both sides of the upright ones.
        val bounds = inkBounds(Glyph.KEY)
        assertTrue(bounds.first < 0.20 * BIG, "the leaning stroke reaches to the left")
        assertTrue(bounds.second > 0.70 * BIG, "the leaning stroke reaches to the right")
    }

    @Test
    fun `the save icon is an arrow that falls into a tray`() {
        assertTrue(ink(Glyph.SAVE) > 0, "the save icon draws nothing")
        // The stem of the arrow stands alone on its row, and the tray is a line of its own.
        assertEquals(1, groupsInRow(Glyph.SAVE, (0.28 * BIG).toInt()), "the stem of the arrow")
        assertEquals(1, groupsInRow(Glyph.SAVE, (0.86 * BIG).toInt()), "the tray the file falls into")
        assertTrue(pixels(Glyph.SAVE) != pixels(Glyph.COPY), "the save icon looks like the copy mark")
    }

    @Test
    fun `the score editor icons are different from each other`() {
        val drawn = EDITOR_GLYPHS.map { pixels(it) }
        for (i in drawn.indices) {
            for (j in i + 1 until drawn.size) {
                assertTrue(drawn[i] != drawn[j], "the icons look the same")
            }
        }
    }

    /** The pixels a glyph draws on a transparent square. */
    private fun pixels(glyph: Glyph): List<Int> {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        drawGlyph(g, glyph, 0.0, 0.0, SIZE.toDouble(), Color.BLACK)
        g.dispose()
        return (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> image.getRGB(x, y) } }
    }

    /** How many pixels a glyph draws. */
    private fun ink(glyph: Glyph): Int = pixels(glyph).count { it != 0 }

    /**
     * The leftmost and the rightmost pixel a glyph draws.
     *
     * @param glyph the glyph to draw.
     * @return the first and the last column that carry ink.
     */
    private fun inkBounds(glyph: Glyph): Pair<Int, Int> {
        val image = BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        drawGlyph(g, glyph, 0.0, 0.0, BIG.toDouble(), Color.BLACK)
        g.dispose()
        var first = BIG
        var last = -1
        for (x in 0 until BIG) {
            for (y in 0 until BIG) {
                if (image.getRGB(x, y) != 0) {
                    first = minOf(first, x)
                    last = maxOf(last, x)
                }
            }
        }
        return first to last
    }

    /**
     * The leftmost and the rightmost pixel a glyph draws on one row.
     *
     * @param glyph the glyph to draw.
     * @param row the row to look at.
     * @return the first and the last column of the row that carry ink.
     */
    private fun inkBoundsInRow(
        glyph: Glyph,
        row: Int,
    ): Pair<Int, Int> {
        val image = BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        drawGlyph(g, glyph, 0.0, 0.0, BIG.toDouble(), Color.BLACK)
        g.dispose()
        var first = BIG
        var last = -1
        for (x in 0 until BIG) {
            if (image.getRGB(x, row) != 0) {
                first = minOf(first, x)
                last = maxOf(last, x)
            }
        }
        return first to last
    }

    /**
     * How many separate strokes a glyph draws on a row.
     *
     * Two runs of ink that touch count as one: the groups are the pieces of the drawing that
     * stand apart from each other.
     */
    private fun groupsInRow(
        glyph: Glyph,
        row: Int,
    ): Int {
        val image = BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        drawGlyph(g, glyph, 0.0, 0.0, BIG.toDouble(), Color.BLACK)
        g.dispose()
        var groups = 0
        var inside = false
        for (x in 0 until BIG) {
            val on = image.getRGB(x, row) != 0
            if (on && !inside) groups++
            inside = on
        }
        return groups
    }

    companion object {
        /** The size of the square the icons are drawn on, in pixels. */
        private const val SIZE = 16

        /** The size of the square the bigger drawings are made on, in pixels. */
        private const val BIG = 64

        private val EDITOR_GLYPHS = listOf(Glyph.SPLIT, Glyph.SWAP, Glyph.MERGE, Glyph.UNDO, Glyph.REDO)
    }
}
