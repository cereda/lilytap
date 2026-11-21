// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The text field with the app's own shape.
 *
 * The ghost text is the name of the field while it is empty. It has to sit where the text
 * itself would sit, or the field jumps when a value is typed into it.
 */
class StyledFieldTest {
    @Test
    fun `the ghost text sits in the middle of the field`() {
        val withHint = StyledField("", 3, heightPx = 28, hint = "Pitch")
        withHint.setSize(66, 28)
        val without = StyledField("", 3, heightPx = 28)
        without.setSize(66, 28)

        val ink = inkOf(paint(withHint), paint(without))
        assertTrue(ink.isNotEmpty(), "the ghost text should be painted")

        val left = ink.minOf { it.first }
        val right = ink.maxOf { it.first }
        val middle = (left + right) / 2.0
        assertTrue(
            abs(middle - 33.0) <= 3.0,
            "the ghost text should sit in the middle: it runs from $left to $right",
        )
    }

    @Test
    fun `the ghost text and the text itself are centred alike`() {
        val withHint = StyledField("", 3, heightPx = 28, hint = "Pitch")
        withHint.setSize(66, 28)
        val withText = StyledField("c'4", 3, heightPx = 28)
        withText.setSize(66, 28)
        val empty = StyledField("", 3, heightPx = 28)
        empty.setSize(66, 28)

        val ghost = inkOf(paint(withHint), paint(empty))
        val written = inkOf(paint(withText), paint(empty))
        assertTrue(ghost.isNotEmpty(), "the ghost text should be painted")
        assertTrue(written.isNotEmpty(), "the text should be painted")

        val ghostMiddle = (ghost.minOf { it.first } + ghost.maxOf { it.first }) / 2.0
        val writtenMiddle = (written.minOf { it.first } + written.maxOf { it.first }) / 2.0
        assertTrue(
            abs(ghostMiddle - writtenMiddle) <= 2.0,
            "the ghost text should sit where the text sits: " +
                "$ghostMiddle and $writtenMiddle",
        )
    }

    @Test
    fun `a field with a hint asks for the same room as one without`() {
        val withHint = StyledField("", 3, heightPx = 28, hint = "Pitch")
        val without = StyledField("", 3, heightPx = 28)
        assertTrue(
            withHint.preferredSize.width == without.preferredSize.width,
            "the ghost text should not make the field wider",
        )
    }

    /** Paints the field and returns the image it drew on. */
    private fun paint(field: StyledField): BufferedImage {
        val image = BufferedImage(66, 28, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        field.paint(graphics)
        graphics.dispose()
        return image
    }

    /** The pixels where the two images are different. */
    private fun inkOf(
        a: BufferedImage,
        b: BufferedImage,
    ): List<Pair<Int, Int>> {
        val ink = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) ink += x to y
            }
        }
        return ink
    }
}
