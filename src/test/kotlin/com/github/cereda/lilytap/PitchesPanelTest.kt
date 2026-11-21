// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pitches panel and the legend of the top column. */
class PitchesPanelTest {
    /** Paints a component and returns the image it drew on. */
    private fun paint(
        component: javax.swing.JComponent,
        width: Int = component.preferredSize.width,
        height: Int = component.preferredSize.height,
    ): BufferedImage {
        component.setSize(maxOf(width, 1), maxOf(height, 1))
        val image = BufferedImage(maxOf(width, 1), maxOf(height, 1), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        component.paint(graphics)
        graphics.dispose()
        return image
    }

    /** Whether the image has a pixel of the given colour. */
    private fun hasColour(
        image: BufferedImage,
        colour: java.awt.Color,
    ): Boolean {
        val wanted = colour.rgb
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == wanted) return true
            }
        }
        return false
    }

    @Test
    fun `the panel has a piano and a button`() {
        val panel = PitchesPanel()
        assertTrue(panel.components.any { it is PianoPanel }, "the panel has the piano")
        assertEquals("Fill the melody", panel.fill.text, "the button starts the walk")
    }

    @Test
    fun `the counter says where the cursor is`() {
        val panel = PitchesPanel()
        assertEquals("", panel.counterText)
        panel.setCounter("3 / 12")
        assertEquals("3 / 12", panel.counterText)
    }

    @Test
    fun `the legend fits the column of the settings card`() {
        val legend = LegendPanel()
        assertTrue(
            legend.preferredSize.width <= 168,
            "the legend fits the column: ${legend.preferredSize.width}",
        )
        assertTrue(
            legend.preferredSize.height <= 85,
            "the legend fits above the Start button: ${legend.preferredSize.height}",
        )
    }

    @Test
    fun `the legend shows the colours of the raw taps`() {
        val legend = LegendPanel()
        val image = paint(legend)
        assertTrue(hasColour(image, Theme.warn), "the legend shows the colour of a late tap")
        assertTrue(hasColour(image, Theme.accent2), "the legend shows the colour of an early tap")
        assertTrue(hasColour(image, Theme.dim), "the legend writes its words")
    }
}
