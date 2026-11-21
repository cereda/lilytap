// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The buttons of the app.
 *
 * A severe button is a quiet button in red: it says what it does and that it costs something,
 * without shouting over the button that stops a take.
 */
class PillButtonTest {
    @Test
    fun `a severe button draws a red border`() {
        val button = PillButton("Revert", null, ButtonKind.SEVERE, heightPx = 32)
        button.setSize(84, 32)
        val image = paint(button)

        val border = (0 until 3).map { y -> Color(image.getRGB(42, y)) }
        assertTrue(border.any { isRed(it) }, "the border should be red: $border")
    }

    @Test
    fun `a ghost button draws a neutral border`() {
        val button = PillButton("Revert", null, ButtonKind.GHOST, heightPx = 32)
        button.setSize(84, 32)
        val image = paint(button)

        val border = (0 until 3).map { y -> Color(image.getRGB(42, y)) }
        assertTrue(border.none { isRed(it) }, "the border should not be red: $border")
    }

    @Test
    fun `a severe button draws its word in red`() {
        val button = PillButton("Revert", null, ButtonKind.SEVERE, heightPx = 32)
        button.setSize(84, 32)
        val image = paint(button)

        val red = redPixels(image, 8, 24)
        assertTrue(red >= 5, "the word should be drawn in red: $red red pixels")
    }

    @Test
    fun `a ghost button draws its word in the text colour`() {
        val button = PillButton("Revert", null, ButtonKind.GHOST, heightPx = 32)
        button.setSize(84, 32)
        val image = paint(button)

        val red = redPixels(image, 8, 24)
        assertTrue(red == 0, "the word should not be drawn in red: $red red pixels")
    }

    @Test
    fun `a button without a glyph is narrower`() {
        val withGlyph = PillButton("Revert", Glyph.RESET, ButtonKind.GHOST, heightPx = 32)
        val without = PillButton("Revert", null, ButtonKind.GHOST, heightPx = 32)
        assertTrue(
            without.preferredSize.width < withGlyph.preferredSize.width,
            "the glyph should make the button wider",
        )
    }

    /** Paints the button and returns the image it drew on. */
    private fun paint(button: PillButton): BufferedImage {
        val image = BufferedImage(84, 32, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        button.paint(graphics)
        graphics.dispose()
        return image
    }

    /** How many red pixels the image has between the two rows. */
    private fun redPixels(
        image: BufferedImage,
        from: Int,
        to: Int,
    ): Int {
        var red = 0
        for (y in from until to) {
            for (x in 0 until image.width) {
                if (isRed(Color(image.getRGB(x, y)))) red++
            }
        }
        return red
    }

    /** Whether the colour is red: much more red than the other two. */
    private fun isRed(color: Color): Boolean = color.red > color.green + 40 && color.red > color.blue + 20
}
