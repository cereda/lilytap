// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The buttons that open a popup: the chevron and the click that closes it again. */
class PopupButtonTest {
    /** A button that opens a popup with an empty panel in it. */
    private class TestButton : PopupButton() {
        /** The popup the button opened. */
        var last: JPopupMenu? = null

        /** Clicks the button. */
        fun click() = onClick()

        /** Points the chevron up, as it is when the popup is open. */
        fun pointUp() {
            up = true
        }

        override fun buildPopup(): JPopupMenu {
            val made = menuPopup()
            made.add(JPanel().apply { preferredSize = Dimension(50, 40) })
            last = made
            return made
        }
    }

    @Test
    fun `the chevron points down while the popup is closed`() {
        val button = TestButton()

        val rows = inkRows(button)
        val top = rows.indexOfFirst { it > 0 }
        val bottom = rows.indexOfLast { it > 0 }
        assertTrue(rows[top] > rows[bottom], "the chevron is wide at the top and pointed below")
    }

    @Test
    fun `the chevron points up while the popup is open`() {
        val button = TestButton()
        button.pointUp()

        val rows = inkRows(button)
        val top = rows.indexOfFirst { it > 0 }
        val bottom = rows.indexOfLast { it > 0 }
        assertTrue(rows[top] < rows[bottom], "the chevron is pointed above and wide at the bottom")
    }

    @Test
    fun `the button opens the popup and closes it again`() {
        val frame = JFrame("test")
        val button = TestButton()
        frame.contentPane.add(button)
        frame.setSize(200, 200)
        frame.isVisible = true

        press(button)
        assertTrue(button.open, "the popup is open")
        assertTrue(button.up, "the chevron points up")
        assertTrue(button.last?.isVisible == true, "the popup is showing")

        press(button)
        assertFalse(button.open, "the popup is closed")
        assertFalse(button.up, "the chevron points down")
        assertFalse(button.last?.isVisible == true, "the popup is hidden")

        frame.dispose()
    }

    /**
     * Presses the button, as the mouse would.
     *
     * The event goes through the toolkit, so it is the same path a click of the mouse takes.
     *
     * @param button the button to press.
     */
    private fun press(button: JComponent) {
        val event =
            MouseEvent(
                button,
                MouseEvent.MOUSE_PRESSED,
                0,
                0,
                0,
                0,
                1,
                false,
                MouseEvent.BUTTON1,
            )
        button.dispatchEvent(event)
    }

    /**
     * The number of pixels of the chevron in every line of the button.
     *
     * @param button the button to draw.
     * @return the count of every line, from the top of the button down.
     */
    private fun inkRows(button: JComponent): List<Int> {
        button.size = button.preferredSize
        val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.color = Theme.field
        g.fillRect(0, 0, image.width, image.height)
        button.paint(g)
        g.dispose()
        return (0 until image.height).map { y ->
            (0 until image.width).count { x -> isInk(Color(image.getRGB(x, y))) }
        }
    }

    /**
     * Whether a pixel is the colour of the chevron and not the colour of the button.
     *
     * @param pixel the pixel.
     * @return true when the pixel belongs to the chevron.
     */
    private fun isInk(pixel: Color): Boolean = distance(pixel, Theme.dim) < distance(pixel, Theme.field)

    /**
     * How far a colour is from another one.
     *
     * @param a the first colour.
     * @param b the second colour.
     * @return the distance between the two colours.
     */
    private fun distance(
        a: Color,
        b: Color,
    ): Int = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}
