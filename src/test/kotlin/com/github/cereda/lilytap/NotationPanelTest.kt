// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The score strip: selecting blocks, editing them, and dragging a border to resize it. */
class NotationPanelTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
    private val wide = Config(60.0, TimeSignature(4, 4), 2, 2, 0.0, "c")

    /** The internal units in a grid cell of these configurations. */
    private val cellUnits = 8

    /** An event that starts at a grid cell and lasts a number of grid cells. */
    private fun event(
        kind: TapKind,
        start: Int,
        length: Int,
    ) = Event(kind, start * cellUnits, length * cellUnits)

    private fun panel(config: Config = this.config): Pair<NotationPanel, GridView> {
        val grid =
            GridView().apply {
                this.config = config
                viewWidth = 400
            }
        val panel = NotationPanel(grid)
        panel.setSize(400, 56)
        return panel to grid
    }

    private fun score() =
        Score(
            listOf(
                event(TapKind.NOTE, 0, 2),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            config,
        )

    private fun click(
        panel: NotationPanel,
        x: Int,
        shift: Boolean = false,
    ) {
        val modifiers = if (shift) InputEvent.SHIFT_DOWN_MASK else 0
        val press = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, modifiers, x, 28, 1, false)
        panel.dispatchEvent(press)
        val release = MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, modifiers, x, 28, 1, false)
        panel.dispatchEvent(release)
    }

    /** Two clicks in a row: the second one carries the click count. */
    private fun doubleClick(
        panel: NotationPanel,
        x: Int,
    ) {
        click(panel, x)
        val press = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 2, false)
        panel.dispatchEvent(press)
        val release = MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 2, false)
        panel.dispatchEvent(release)
    }

    private fun drag(
        panel: NotationPanel,
        from: Int,
        to: Int,
    ) {
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, from, 28, 1, false))
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, 0, to, 28, 1, false))
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, to, 28, 1, false))
    }

    private fun press(
        panel: NotationPanel,
        keyCode: Int,
    ) {
        // The window sends the key to the focused component; here the panel gets it directly.
        val event = KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED)
        for (listener in panel.keyListeners) {
            listener.keyPressed(event)
        }
    }

    // -------------------------------------------------------------------------------------------
    // Selecting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `clicking a block selects it without changing the score`() {
        val (panel, grid) = panel()
        panel.score = score()
        var edited: Score? = null
        panel.onEdit = { edited = it }
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals(1, panel.selectedBlocks)
        assertEquals("c4 selected", panel.caption)
        assertNull(edited, "a click only selects")
        assertEquals("c4 c4 c2 |", panel.score?.lilypond)
    }

    @Test
    fun `shift-clicking extends the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(4.5).toInt(), shift = true)
        assertEquals(3, panel.selectedBlocks)
        assertEquals("3 blocks selected", panel.caption)
    }

    @Test
    fun `the escape key clears the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        press(panel, KeyEvent.VK_ESCAPE)
        assertEquals(0, panel.selectedBlocks)
        assertEquals(NotationPanel.NOTHING_SELECTED, panel.caption)
    }

    @Test
    fun `the arrow keys move the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(5.0).toInt())
        assertEquals(4 * cellUnits, panel.selection?.start)
        press(panel, KeyEvent.VK_LEFT)
        assertEquals(2 * cellUnits, panel.selection?.start)
        press(panel, KeyEvent.VK_LEFT)
        assertEquals(0, panel.selection?.start)
    }

    @Test
    fun `the arrow keys pick a block when nothing is selected`() {
        val (panel, grid) = panel()
        panel.score = score()
        press(panel, KeyEvent.VK_RIGHT)
        assertEquals(1, panel.selectedBlocks)
        assertEquals("c4 selected", panel.caption)
        press(panel, KeyEvent.VK_RIGHT)
        press(panel, KeyEvent.VK_RIGHT)
        assertEquals("c2 selected", panel.caption)
    }

    // -------------------------------------------------------------------------------------------
    // Editing
    // -------------------------------------------------------------------------------------------

    @Test
    fun `double-clicking a note turns it into a rest`() {
        val (panel, grid) = panel()
        panel.score = score()
        doubleClick(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(
                event(TapKind.REST, 0, 2),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        assertEquals("r4 c4 c2 |", panel.score?.lilypond)
    }

    @Test
    fun `double-clicking a rest turns it back into a note`() {
        val (panel, grid) = panel()
        panel.score = Score(listOf(event(TapKind.REST, 0, 2), event(TapKind.NOTE, 2, 6)), config)
        doubleClick(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(event(TapKind.NOTE, 0, 2), event(TapKind.NOTE, 2, 6)),
            panel.score?.events,
        )
    }

    @Test
    fun `the split key splits the selected block`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 1),
                event(TapKind.NOTE, 1, 1),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        assertEquals("Split → c8 c8", panel.caption)
    }

    @Test
    fun `the swap key swaps the two selected blocks`() {
        val (panel, grid) = panel()
        panel.score = Score(listOf(event(TapKind.NOTE, 0, 2), event(TapKind.REST, 2, 6)), config)
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(3.0).toInt(), shift = true)
        press(panel, KeyEvent.VK_X)
        assertEquals(
            listOf(event(TapKind.REST, 0, 6), event(TapKind.NOTE, 6, 2)),
            panel.score?.events,
        )
        assertEquals("Swapped", panel.caption)
    }

    @Test
    fun `the merge key merges the selected blocks`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_M)
        assertEquals(
            listOf(event(TapKind.NOTE, 0, 4), event(TapKind.NOTE, 4, 4)),
            panel.score?.events,
        )
        assertEquals("Merged into c2", panel.caption)
    }

    @Test
    fun `the split key does nothing without a single block`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_S)
        assertEquals(3, panel.score?.events?.size)
    }

    @Test
    fun `an edit keeps the selection on the same music`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals(2, panel.selectedBlocks)
        assertTrue(panel.canMerge)
    }

    // -------------------------------------------------------------------------------------------
    // Dragging a border
    // -------------------------------------------------------------------------------------------

    @Test
    fun `dragging a border resizes the two notes`() {
        val (panel, grid) = panel()
        panel.score = score()
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(3.0).toInt())
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 3),
                event(TapKind.NOTE, 3, 1),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
    }

    @Test
    fun `a border cannot be dragged past the end of a note`() {
        val (panel, grid) = panel()
        panel.score = score()
        // Drag far to the right: the second note keeps one cell.
        drag(panel, grid.xOfCell(2.0).toInt(), 390)
        assertEquals(
            cellUnits,
            panel.score
                ?.events
                ?.get(1)
                ?.lengthUnits,
        )
    }

    @Test
    fun `a drag near a bar line sticks to it`() {
        val (panel, grid) = panel(wide)
        panel.score = Score(listOf(event(TapKind.NOTE, 0, 2), event(TapKind.NOTE, 2, 14)), wide)
        // Cell 7 is close to the bar line at cell 8: the bar line wins.
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(7.4).toInt())
        assertEquals(
            8 * cellUnits,
            panel.score
                ?.events
                ?.get(0)
                ?.lengthUnits,
        )
    }

    // -------------------------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the strip paints the score without complaining`() {
        val (panel, _) = panel()
        panel.score = score()
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertNotNull(image.colorModel)
    }

    @Test
    fun `the strip paints a selection without complaining`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertNotNull(image.colorModel)
    }

    @Test
    fun `the empty hint is vertically centred in the strip`() {
        val (panel, _) = panel()
        panel.setSize(400, 68)
        panel.score = null
        val image = BufferedImage(400, 68, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        // Background is the card colour; rows that contain text ink differ from it.
        val background = image.getRGB(200, 2)
        val rowInk = IntArray(image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != background) rowInk[y]++
            }
        }
        var total = 0
        var weighted = 0L
        for (y in 0 until image.height) {
            total += rowInk[y]
            weighted += rowInk[y].toLong() * y
        }
        val centreY = weighted.toDouble() / total
        // The hint block sits between the border rows; its centre should be near the panel centre.
        assertTrue(
            abs(centreY - (image.height / 2.0)) <= 4.0,
            "hint centre $centreY is far from panel centre ${image.height / 2}",
        )
    }
}
