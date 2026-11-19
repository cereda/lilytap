// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The horizontal grid that the recording timeline and the score strip share: it maps
 * grid cells to pixels and back, and owns the horizontal scroll both panels follow.
 */

import kotlin.math.max

/**
 * The horizontal grid of the window.
 *
 * The timeline (what you played) and the score strip (what was written) show the same
 * grid, so they share one of these: the blocks of the strip sit exactly under the beats
 * of the timeline, and scrolling one scrolls the other.
 */
internal class GridView {
    /** The recording the grid shows. Setting it scrolls back to the start. */
    var config: Config? = null
        set(value) {
            field = value
            scrollX = 0.0
            changed()
        }

    private val listeners = mutableListOf<() -> Unit>()

    /**
     * Registers a listener that is called whenever the grid changes: when it is scrolled or
     * when another recording is set. The panels that draw the grid use this to redraw
     * themselves, so they never drift apart.
     */
    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    /** Tells the listeners that the grid has changed. */
    private fun changed() {
        for (listener in listeners) listener()
    }

    /**
     * The width of the view in pixels. Both panels must have the same width, or the strip
     * would not line up with the timeline.
     */
    var viewWidth: Int = 0

    /** How far the grid is scrolled to the right, in pixels. */
    var scrollX: Double = 0.0
        private set

    /** The padding on either side of the grid, in pixels. */
    val padX: Double = 18.0

    private val minBarW = 190.0

    /** The number of cells in the recording. */
    val totalCells: Int get() = config?.totalCells ?: 1

    /** The number of cells in one bar. */
    val cellsPerBar: Int get() = config?.cellsPerBar ?: 1

    /** The number of bars in the recording. */
    val bars: Int get() = config?.bars ?: 1

    /** The width of the visible area, without the padding on either side. */
    val span: Double get() = (viewWidth - 2 * padX).coerceAtLeast(0.0)

    /** The width the whole recording would need to be drawn without scrolling. */
    val totalPx: Double get() = max(span, bars * minBarW)

    /** True when the recording is wider than the view, so it has to scroll. */
    val scrollable: Boolean get() = totalPx > span + 0.5

    /** The furthest the grid can be scrolled to the right. */
    val maxScroll: Double get() = (totalPx - span).coerceAtLeast(0.0)

    /**
     * The x pixel of a cell position. The position may be fractional, so a border can be
     * drawn between two cells.
     */
    fun xOfCell(cell: Double): Double = padX + cell / totalCells * totalPx - scrollX

    /** The cell position at an x pixel. */
    fun cellOfX(x: Double): Double = (x - padX + scrollX) / totalPx * totalCells

    /** Scrolls the grid to [x] pixels from the left, as far as it goes. */
    fun scrollTo(x: Double) {
        val clamped = x.coerceIn(0.0, maxScroll)
        if (clamped == scrollX) return
        scrollX = clamped
        changed()
    }
}
