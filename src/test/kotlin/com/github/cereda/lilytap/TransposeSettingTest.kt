// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The control that transposes the score: the button and the panel of intervals. */
class TransposeSettingTest {
    /** The panel of intervals, with the key of the piece in it. */
    private fun panel(key: String = "C major"): IntervalPanel = IntervalPanel {}.apply { this.key = assertNotNull(KeySignature.parse(key)) }

    /** The place of a cell of the panel: the column of the direction and the row of the interval. */
    private fun place(
        interval: Interval,
        up: Boolean,
    ): Pair<Int, Int> {
        val column = if (up) 170 else 60
        val row = 10 + 24 + Interval.entries.indexOf(interval) * 38 + 10
        return column to row
    }

    @Test
    fun `the panel has a cell for every interval and both directions`() {
        val panel = panel()
        val found = mutableSetOf<Transposition>()
        for (y in 0 until panel.preferredSize.height) {
            for (x in 0 until panel.preferredSize.width) {
                val picked = panel.pickAt(x, y)
                if (picked != null) found += picked
            }
        }
        assertEquals(
            Interval.entries.size * 2,
            found.size,
            "every interval has a cell in each direction",
        )
    }

    @Test
    fun `the cells are in the order of the intervals`() {
        val panel = panel()
        for (interval in Interval.entries) {
            val (downX, downY) = place(interval, up = false)
            val (upX, upY) = place(interval, up = true)
            assertEquals(Transposition(interval, false), panel.pickAt(downX, downY), interval.label)
            assertEquals(Transposition(interval, true), panel.pickAt(upX, upY), interval.label)
        }
    }

    @Test
    fun `the titles of the columns are not cells`() {
        val panel = panel()
        assertNull(panel.pickAt(60, 10), "the titles are above the cells")
        assertNull(panel.pickAt(60, 10 + 24 + 6 * 38), "there is nothing below the last row")
    }

    @Test
    fun `the gap between the columns is not a cell`() {
        val panel = panel()
        assertNull(panel.pickAt(118, 50), "the columns are apart")
    }

    @Test
    fun `the tooltip says where the interval would take the piece`() {
        val panel = panel()
        val (x, y) = place(Interval.THIRD, up = true)
        val tip = panel.getToolTipText(MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false))
        assertEquals("Up a major third: C major → E major", tip)
    }

    @Test
    fun `the tooltip of a cell that cannot be used says so`() {
        val panel = panel("C𝄪 major")
        val (x, y) = place(Interval.FIFTH, up = true)
        val tip = panel.getToolTipText(MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false))
        assertEquals("Up a perfect fifth: C𝄪 major → a key the app cannot hold", tip)
    }

    @Test
    fun `clicking a cell picks the transposition`() {
        var picked: Transposition? = null
        val panel = IntervalPanel { picked = it }
        val (x, y) = place(Interval.SECOND, up = true)
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false))
        assertEquals(Transposition(Interval.SECOND, true), picked)
    }

    @Test
    fun `the panel paints without complaining`() {
        val panel = panel()
        panel.setSize(panel.preferredSize)
        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertTrue(image.width > 0, "the panel drew something")
    }

    @Test
    fun `the button says what it does`() {
        val button = TransposeButton()
        assertTrue(
            button.toolTipText?.contains("interval") == true,
            "the button says that it moves the score by an interval",
        )
        assertEquals(124, button.preferredSize.width, "the button is wide enough for its word")
    }

    @Test
    fun `the button paints without complaining`() {
        val button = TransposeButton()
        button.setSize(button.preferredSize)
        val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        button.paint(graphics)
        graphics.dispose()
        assertTrue(image.width > 0, "the button drew something")
    }
}
