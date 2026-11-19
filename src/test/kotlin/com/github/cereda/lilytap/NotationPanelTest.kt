// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The score strip: clicking a block toggles it, dragging a border resizes it. */
class NotationPanelTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
    private val wide = Config(60.0, TimeSignature(4, 4), 2, 2, 0.0, "c")

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
                Event(TapKind.NOTE, 0, 2),
                Event(TapKind.NOTE, 2, 2),
                Event(TapKind.NOTE, 4, 4),
            ),
            config,
        )

    private fun click(
        panel: NotationPanel,
        x: Int,
    ) {
        val press = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false)
        panel.dispatchEvent(press)
        val release = MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false)
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

    @Test
    fun `clicking a note turns it into a rest`() {
        val (panel, grid) = panel()
        panel.score = score()
        var edited: Score? = null
        panel.onEdit = { edited = it }
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(
                Event(TapKind.REST, 0, 2),
                Event(TapKind.NOTE, 2, 2),
                Event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        assertEquals(panel.score, edited)
        assertEquals("r4 c4 c2 |", panel.score?.lilypond)
    }

    @Test
    fun `clicking a rest turns it back into a note`() {
        val (panel, grid) = panel()
        panel.score = Score(listOf(Event(TapKind.REST, 0, 2), Event(TapKind.NOTE, 2, 6)), config)
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(Event(TapKind.NOTE, 0, 2), Event(TapKind.NOTE, 2, 6)),
            panel.score?.events,
        )
    }

    @Test
    fun `dragging a border resizes the two notes`() {
        val (panel, grid) = panel()
        panel.score = score()
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(3.0).toInt())
        assertEquals(
            listOf(
                Event(TapKind.NOTE, 0, 3),
                Event(TapKind.NOTE, 3, 1),
                Event(TapKind.NOTE, 4, 4),
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
            1,
            panel.score
                ?.events
                ?.get(1)
                ?.lengthCells,
        )
    }

    @Test
    fun `a drag near a bar line sticks to it`() {
        val (panel, grid) = panel(wide)
        panel.score = Score(listOf(Event(TapKind.NOTE, 0, 2), Event(TapKind.NOTE, 2, 14)), wide)
        // Cell 7 is close to the bar line at cell 8: the bar line wins.
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(7.4).toInt())
        assertEquals(
            8,
            panel.score
                ?.events
                ?.get(0)
                ?.lengthCells,
        )
    }

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
