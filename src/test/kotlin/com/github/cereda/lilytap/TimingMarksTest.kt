// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The marks of the timing on the score strip.
 *
 * A note that was pressed off the beat carries a mark above its block: one that points to the
 * right when the note was late, one that points to the left when it was early.
 */
class TimingMarksTest {
    private val config = Config(60.0, TimeSignature(4, 4), 1, 1, 0.0)

    /** The units in a grid cell of this configuration. */
    private val cellUnits = 16

    private fun event(
        kind: TapKind,
        start: Int,
        length: Int,
    ) = Event(kind, start * cellUnits, length * cellUnits, if (kind == TapKind.NOTE) Pitch.DEFAULT else null)

    /** The events of the strip: three notes. */
    private fun events() =
        listOf(
            event(TapKind.NOTE, 0, 1),
            event(TapKind.NOTE, 1, 1),
            event(TapKind.NOTE, 2, 2),
        )

    /** The strip with a score of three notes, ready to be painted. */
    private fun panel(): Pair<NotationPanel, GridView> {
        val grid =
            GridView().apply {
                this.config = config
                viewWidth = 400
            }
        val panel = NotationPanel(grid)
        panel.setSize(400, 56)
        panel.score = Score(events(), config)
        return panel to grid
    }

    /** A report that says the first note was late and the second one early. */
    private fun report(
        first: Double,
        second: Double,
    ): TimingReport =
        TimingReport.of(
            Score(events(), config),
            listOf(first, second, 0.0),
        )!!

    @Test
    fun `a note that was late carries an amber mark`() {
        val (panel, grid) = panel()
        panel.timing = report(40.0, 0.0)
        val image = paint(panel)
        val late = markPixels(image, grid, 0, Theme.warn)
        assertTrue(late.isNotEmpty(), "a late note carries a mark")
        assertTrue(late.isEmpty().not(), "the mark of a late note is amber")
    }

    @Test
    fun `the tolerance is where the mark starts`() {
        val (panel, grid) = panel()
        panel.timing = report(TimingReport.TOLERANCE_MS, 0.0)
        assertTrue(
            markPixels(paint(panel), grid, 0, Theme.warn).isEmpty(),
            "a note that is on the edge of the tolerance carries no mark",
        )

        panel.timing = report(TimingReport.TOLERANCE_MS + 1.0, 0.0)
        assertTrue(
            markPixels(paint(panel), grid, 0, Theme.warn).isNotEmpty(),
            "a note that is over the tolerance carries a mark",
        )
    }

    @Test
    fun `a note that was early carries a teal mark`() {
        val (panel, grid) = panel()
        panel.timing = report(0.0, -40.0)
        val image = paint(panel)
        val early = markPixels(image, grid, 1, Theme.accent2)
        assertTrue(early.isNotEmpty(), "an early note carries a mark")
    }

    @Test
    fun `the mark of a late note points to the right`() {
        val (panel, grid) = panel()
        panel.timing = report(40.0, 0.0)
        val image = paint(panel)
        val pixels = markPixels(image, grid, 0, Theme.warn)
        val (base, apex) = ends(pixels)
        assertTrue(base > apex, "the mark of a late note points to the right: $base vs $apex")
    }

    @Test
    fun `the mark of an early note points to the left`() {
        val (panel, grid) = panel()
        panel.timing = report(0.0, -40.0)
        val image = paint(panel)
        val pixels = markPixels(image, grid, 1, Theme.accent2)
        val (base, apex) = ends(pixels)
        assertTrue(apex > base, "the mark of an early note points to the left: $base vs $apex")
    }

    @Test
    fun `a note that was on time carries no mark`() {
        val (panel, grid) = panel()
        panel.timing = report(10.0, 0.0)
        val image = paint(panel)
        assertTrue(markPixels(image, grid, 0, Theme.warn).isEmpty(), "an on-time note has no mark")
        assertTrue(markPixels(image, grid, 0, Theme.accent2).isEmpty(), "an on-time note has no mark")
    }

    @Test
    fun `a strip without a report carries no marks`() {
        val (panel, grid) = panel()
        val image = paint(panel)
        assertTrue(markPixels(image, grid, 0, Theme.warn).isEmpty(), "there is no mark without a report")
    }

    @Test
    fun `the tooltip of a block says how late it was`() {
        val (panel, grid) = panel()
        panel.timing = report(40.0, -40.0)
        val event = MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, grid.xOfCell(0.5).toInt(), 28, 0, false)
        val text = assertNotNull(panel.getToolTipText(event))
        assertTrue(text.contains("40 ms late"), "the tooltip says how late the note was: $text")
    }

    @Test
    fun `the tooltip of a block says nothing about the timing when there is none`() {
        val (panel, grid) = panel()
        val event = MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, grid.xOfCell(0.5).toInt(), 28, 0, false)
        val text = assertNotNull(panel.getToolTipText(event))
        assertTrue(!text.contains("ms"), "the tooltip says nothing about the timing: $text")
    }

    @Test
    fun `a new score takes the marks with it`() {
        val (panel, _) = panel()
        panel.timing = report(40.0, -40.0)
        panel.score = Score(listOf(event(TapKind.NOTE, 0, 4)), config)
        assertNull(panel.timing, "the marks belong to the score of the take")
    }

    /** Paints the strip and returns the image it drew on. */
    private fun paint(panel: NotationPanel): BufferedImage {
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        return image
    }

    /**
     * The pixels of a mark above a cell.
     *
     * @param image the painted strip.
     * @param grid the grid of the strip.
     * @param cell the cell of the note.
     * @param color the colour of the mark.
     * @return the pixels of the mark, as (x, y).
     */
    private fun markPixels(
        image: BufferedImage,
        grid: GridView,
        cell: Int,
        color: Color,
    ): List<Pair<Int, Int>> {
        val left = markX(grid, cell).toInt()
        val pixels = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until 13) {
            for (x in left until left + MARK_WIDTH.toInt() + 1) {
                if (x < 0 || x >= image.width) continue
                if (Color(image.getRGB(x, y)).rgb == color.rgb) pixels += x to y
            }
        }
        return pixels
    }

    /** Where the mark of the note of a cell starts. */
    private fun markX(
        grid: GridView,
        cell: Int,
    ): Double = grid.xOfCell(cell.toDouble()) + 1.0 + 3.0

    /**
     * The two ends of a mark: the wide one and the point.
     *
     * The mark is a triangle: one end is a whole edge and the other is a point.
     *
     * @param pixels the pixels of the mark.
     * @return the number of pixels of the wide end and of the point.
     */
    private fun ends(pixels: List<Pair<Int, Int>>): Pair<Int, Int> {
        val columns = pixels.groupBy { it.first }.mapValues { it.value.size }
        val first = columns.keys.min()
        val last = columns.keys.max()
        return columns.getValue(first) to columns.getValue(last)
    }
}
