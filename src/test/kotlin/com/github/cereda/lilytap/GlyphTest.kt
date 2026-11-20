// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
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
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        drawGlyph(g, glyph, 0.0, 0.0, 16.0, Color.BLACK)
        g.dispose()
        return (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> image.getRGB(x, y) } }
    }

    /** How many pixels a glyph draws. */
    private fun ink(glyph: Glyph): Int = pixels(glyph).count { it != 0 }

    companion object {
        /** The size of the square the icons are drawn on, in pixels. */
        private const val SIZE = 16

        private val EDITOR_GLYPHS = listOf(Glyph.SPLIT, Glyph.SWAP, Glyph.MERGE)
    }
}
