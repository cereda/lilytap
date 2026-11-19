// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grid the timeline and the score strip share: cells to pixels and back.
 */
class GridViewTest {
    // 4/4, 2 cells per beat => 8 cells per bar
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0, "c")

    private fun grid(
        bars: Int = 1,
        width: Int = 400,
    ) = GridView().apply {
        config = cfg(bars)
        viewWidth = width
    }

    @Test
    fun `the grid knows the shape of the recording`() {
        val g = grid(bars = 4)
        assertEquals(32, g.totalCells)
        assertEquals(8, g.cellsPerBar)
        assertEquals(4, g.bars)
    }

    @Test
    fun `the first cell is at the left edge and the last at the right one`() {
        val g = grid()
        assertEquals(g.padX, g.xOfCell(0.0))
        assertEquals(g.viewWidth - g.padX, g.xOfCell(8.0))
    }

    @Test
    fun `a pixel maps back to its cell`() {
        val g = grid()
        for (cell in 0..8) {
            assertEquals(cell.toDouble(), g.cellOfX(g.xOfCell(cell.toDouble())), 1e-9)
        }
    }

    @Test
    fun `a cell maps back to its pixel`() {
        val g = grid()
        for (cell in 0..8) {
            assertEquals(g.xOfCell(cell.toDouble()), g.xOfCell(g.cellOfX(g.xOfCell(cell.toDouble()))), 1e-9)
        }
    }

    @Test
    fun `a recording that fits does not scroll`() {
        val g = grid(bars = 1)
        assertFalse(g.scrollable)
        assertEquals(0.0, g.maxScroll)
    }

    @Test
    fun `a recording that is too wide scrolls`() {
        val g = grid(bars = 4)
        assertTrue(g.scrollable)
        assertTrue(g.maxScroll > 0)
    }

    @Test
    fun `scrolling stops at the ends`() {
        val g = grid(bars = 4)
        g.scrollTo(1000.0)
        assertEquals(g.maxScroll, g.scrollX)
        g.scrollTo(-100.0)
        assertEquals(0.0, g.scrollX)
    }

    @Test
    fun `scrolling moves the pixels with it`() {
        val g = grid(bars = 4)
        val before = g.xOfCell(4.0)
        g.scrollTo(100.0)
        assertEquals(before - 100.0, g.xOfCell(4.0), 1e-9)
    }

    @Test
    fun `a new recording scrolls back to the start`() {
        val g = grid(bars = 4)
        g.scrollTo(100.0)
        g.config = cfg(bars = 2)
        assertEquals(0.0, g.scrollX)
    }

    @Test
    fun `scrolling notifies listeners but a no-op scroll does not`() {
        val g = grid(bars = 4)
        var calls = 0
        g.onChange { calls++ }
        g.scrollTo(100.0)
        assertEquals(1, calls)
        g.scrollTo(g.scrollX) // same place: no change, no notification
        assertEquals(1, calls)
        g.scrollTo(0.0)
        assertEquals(2, calls)
        assertEquals(0.0, g.scrollX)
    }
}
