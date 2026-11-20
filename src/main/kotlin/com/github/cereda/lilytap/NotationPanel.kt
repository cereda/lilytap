// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The score strip: the notes and rests of the take, drawn on the grid the timeline uses.
 *
 * Clicking a block selects it, double-clicking turns it into a rest (and back again),
 * dragging the border between two blocks makes the first one longer and the second one
 * shorter. The score itself does the work - this panel only turns clicks, keys and drags
 * into edits.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.max

/**
 * A stretch of the score the user has selected: the units from [start] to [end].
 *
 * The selection is kept as a stretch of time and not as a pair of indices: an edit moves
 * the blocks around, but the music under the cursor stays where it was.
 */
internal data class ScoreSelection(
    val start: Int,
    val end: Int,
)

/**
 * The score strip: the notes and rests of the take, editable in place.
 *
 * The strip shares its grid with the recording timeline, so a block sits exactly under the
 * beat it belongs to. Clicking a block selects it, double-clicking turns it into a rest,
 * dragging the border between two blocks resizes them.
 */
internal class NotationPanel(
    private val grid: GridView,
) : JComponent() {
    /** The score to show. */
    var score: Score? = null
        set(value) {
            field = value
            clearSelection()
            repaint()
        }

    /** Called whenever the user has edited the score. */
    var onEdit: ((Score) -> Unit)? = null

    /** Called whenever the selection changes, so the header can follow it. */
    var onSelectionChange: (() -> Unit)? = null

    /** What the header says about the selection: what is selected, or what just happened. */
    var caption: String = NOTHING_SELECTED
        private set

    /** The stretch of the score the user has selected. */
    internal var selection: ScoreSelection? = null
        private set

    /** The block the selection started from: a shift-click extends the selection from here. */
    private var anchor: ScoreSelection? = null

    private var dragIndex = -1 // the border being dragged
    private var dragOrigin: Score? = null // the score when the drag started
    private var dragCell = 0 // the cell the dragged border was on
    private var pressX = 0.0
    private var pressIndex = -1 // the block that was pressed
    private var hoverBorder = -1
    private var hoverBlock = -1

    /** Current playback position in ms (-1 when not playing). */
    var playbackPositionMs: Double = -1.0
        set(v) {
            field = v
            repaint()
        }

    /** Removes this panel's grid-change listener, so GridView doesn't hold a stale reference. */
    private val removeGridListener = grid.onChange { repaint() }

    /** Detaches this panel from the shared grid. Call when the panel is no longer needed. */
    fun detach() {
        removeGridListener()
    }

    init {
        isOpaque = false
        isFocusable = true
        // The arrow keys belong to the selection, not to the focus traversal.
        focusTraversalKeysEnabled = false
        preferredSize = Dimension(760, 68)
        toolTipText =
            "Click a block to select it, double-click to turn it into a rest, " +
            "drag a border to change its length. Keys: S splits, X swaps, M merges."
        // The strip shares the grid with the timeline: when it scrolls, the strip follows.
        // The cell-to-pixel mapping depends on the panel's width; keep it in sync on resize
        // instead of mutating shared state during paint.
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    grid.viewWidth = e.component.width
                }
            },
        )
        val mouse =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    // The click belongs to the strip: it must not reach the window, which
                    // parks the focus on the background when it sees one.
                    e.consume()
                    requestFocusInWindow()
                    val border = borderAt(e.x.toDouble())
                    if (border >= 0) {
                        startDrag(border)
                        return
                    }
                    pressX = e.x.toDouble()
                    pressIndex = blockAt(e.x.toDouble())
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (dragIndex >= 0) dragTo(e.x.toDouble())
                }

                override fun mouseReleased(e: MouseEvent) {
                    e.consume()
                    if (dragIndex >= 0) {
                        dragIndex = -1
                        dragOrigin = null
                        return
                    }
                    // A click is a press and a release that barely moved.
                    if (pressIndex >= 0 && abs(e.x - pressX) <= 3.0) {
                        if (e.clickCount >= 2) {
                            flip(pressIndex)
                        } else {
                            select(pressIndex, e.isShiftDown)
                        }
                    }
                    pressIndex = -1
                }

                override fun mouseMoved(e: MouseEvent) {
                    val border = borderAt(e.x.toDouble())
                    if (border != hoverBorder) {
                        hoverBorder = border
                        repaint()
                    }
                    val block = if (border >= 0) -1 else blockAt(e.x.toDouble())
                    if (block != hoverBlock) {
                        hoverBlock = block
                        repaint()
                    }
                    cursor =
                        Cursor.getPredefinedCursor(
                            if (border >= 0) Cursor.E_RESIZE_CURSOR else Cursor.DEFAULT_CURSOR,
                        )
                }

                override fun mouseExited(e: MouseEvent) {
                    hoverBorder = -1
                    hoverBlock = -1
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    handleKey(e.keyCode)
                }
            },
        )
        // The wheel scrolls the grid, and with it the timeline.
        // grid.scrollTo() notifies listeners (repaint), so no extra repaint needed here.
        addMouseWheelListener { e ->
            if (grid.scrollable) {
                grid.scrollTo(grid.scrollX + e.preciseWheelRotation * 60)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // The selection
    // -------------------------------------------------------------------------------------------

    /** The number of blocks the selection covers. */
    val selectedBlocks: Int
        get() {
            val s = score ?: return 0
            val range = selectedRange(s) ?: return 0
            return range.last - range.first + 1
        }

    /** Whether the selected block can be split in half. */
    val canSplit: Boolean
        get() {
            val s = score ?: return false
            val range = selectedRange(s) ?: return false
            if (range.first != range.last) return false
            return s.events[range.first].lengthUnits >= 2
        }

    /** Whether the two selected blocks can be swapped. */
    val canSwap: Boolean get() = selectedBlocks == 2

    /** Whether the selected blocks can be merged into one. */
    val canMerge: Boolean get() = selectedBlocks >= 2

    /** The indices of the blocks the selection covers. */
    private fun selectedRange(s: Score): IntRange? {
        val sel = selection ?: return null
        val indices = s.events.indices.filter { i -> covers(s.events[i], sel) }
        if (indices.isEmpty()) return null
        return indices.first()..indices.last()
    }

    /** Whether an event lies inside a stretch of the score. */
    private fun covers(
        event: Event,
        range: ScoreSelection,
    ): Boolean = event.startUnits < range.end && event.endUnits > range.start

    /** Selects a block, or extends the selection to it. */
    private fun select(
        index: Int,
        extend: Boolean,
    ) {
        val s = score ?: return
        val event = s.events.getOrNull(index) ?: return
        val clicked = ScoreSelection(event.startUnits, event.endUnits)
        val base = anchor
        if (extend && base != null) {
            selection = ScoreSelection(minOf(base.start, clicked.start), maxOf(base.end, clicked.end))
        } else {
            anchor = clicked
            selection = clicked
        }
        caption = describe(s, selection!!)
        repaint()
        onSelectionChange?.invoke()
    }

    /** Forgets the selection. */
    fun clearSelection() {
        selection = null
        anchor = null
        caption = NOTHING_SELECTED
        repaint()
        onSelectionChange?.invoke()
    }

    /** Moves the selection one block to the left or to the right. */
    private fun moveSelection(direction: Int) {
        val s = score ?: return
        val range = selectedRange(s)
        if (range == null) {
            // Nothing is selected: the first arrow key picks the block at the edge.
            select(if (direction > 0) 0 else s.events.size - 1, false)
            return
        }
        val count = range.last - range.first + 1
        val first = (range.first + direction).coerceIn(0, s.events.size - count)
        val last = first + count - 1
        val moved = ScoreSelection(s.events[first].startUnits, s.events[last].endUnits)
        selection = moved
        anchor = moved
        caption = describe(s, moved)
        repaint()
        onSelectionChange?.invoke()
    }

    /** A short description of what is selected: "c2 selected", "3 blocks selected". */
    private fun describe(
        s: Score,
        range: ScoreSelection,
    ): String {
        val indices = s.events.indices.filter { i -> covers(s.events[i], range) }
        if (indices.isEmpty()) return NOTHING_SELECTED
        if (indices.size == 1) return "${eventText(s.events[indices.first()], s.config)} selected"
        return "${indices.size} blocks selected"
    }

    // -------------------------------------------------------------------------------------------
    // The edits
    // -------------------------------------------------------------------------------------------

    /** Splits the selected block in half: the second half becomes a note. */
    fun splitSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.first != range.last) return
        val index = range.first
        if (s.events[index].lengthUnits < 2) return
        val edited = s.split(index)
        applyEdit(edited, "Split → ${eventText(edited.events[index], edited.config)} " + eventText(edited.events[index + 1], edited.config))
    }

    /** Swaps the two selected blocks: each keeps its length and moves in time. */
    fun swapSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.last - range.first + 1 != 2) return
        val edited = s.swap(range.first)
        applyEdit(edited, "Swapped")
    }

    /** Merges the selected blocks into one: a note wins over a silence. */
    fun mergeSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.last == range.first) return
        val edited = s.merge(range.first, range.last)
        applyEdit(edited, "Merged into ${eventText(edited.events[range.first], edited.config)}")
    }

    /** Turns the selected block into a rest, or a rest back into a note. */
    private fun flip(index: Int) {
        val s = score ?: return
        val wasNote = s.events.getOrNull(index)?.kind == TapKind.NOTE
        val edited = s.toggleRest(index)
        applyEdit(edited, if (wasNote) "Turned into a rest" else "Turned into a note")
    }

    /**
     * Puts an edited score on the strip and keeps the selection on the same stretch of music.
     */
    private fun applyEdit(
        edited: Score,
        message: String,
    ) {
        val range = selection
        score = edited
        // The edit moved the blocks around; the selection follows the music, not the indices.
        range?.let { restoreSelection(it) }
        caption = message
        repaint()
        onEdit?.invoke(edited)
        onSelectionChange?.invoke()
    }

    /** Selects the blocks that cover a stretch of the score. */
    private fun restoreSelection(range: ScoreSelection) {
        val s = score ?: return
        val indices = s.events.indices.filter { i -> covers(s.events[i], range) }
        if (indices.isEmpty()) {
            selection = null
            anchor = null
            return
        }
        val restored = ScoreSelection(s.events[indices.first()].startUnits, s.events[indices.last()].endUnits)
        selection = restored
        anchor = restored
    }

    /**
     * Handles a key press in the strip.
     *
     * The keys are read from the key code, so they work with or without shift and whatever
     * the caps lock says.
     *
     * @param keyCode the key that was pressed.
     */
    internal fun handleKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.VK_S -> splitSelected()
            KeyEvent.VK_X -> swapSelected()
            KeyEvent.VK_M -> mergeSelected()
            KeyEvent.VK_ESCAPE -> clearSelection()
            KeyEvent.VK_LEFT -> moveSelection(-1)
            KeyEvent.VK_RIGHT -> moveSelection(1)
        }
    }

    // -------------------------------------------------------------------------------------------
    // The borders
    // -------------------------------------------------------------------------------------------

    /** The border closest to the x pixel, or -1 when the mouse is not near one. */
    private fun borderAt(x: Double): Int {
        val s = score ?: return -1
        for (i in 0 until s.events.size - 1) {
            // Only a border that sits on a cell can be dragged: the mouse cannot tell a
            // sixty-fourth of a cell from its neighbour.
            if (s.events[i].endUnits % s.config.cellUnits != 0) continue
            val bx = grid.xOfCell(s.events[i].endUnits.toDouble() / s.config.cellUnits)
            if (abs(x - bx) <= GRAB_PX) return i
        }
        return -1
    }

    /** The block under the x pixel, or -1. */
    private fun blockAt(x: Double): Int {
        val s = score ?: return -1
        val units = grid.cellOfX(x) * s.config.cellUnits
        for ((i, event) in s.events.withIndex()) {
            if (units >= event.startUnits && units < event.endUnits) return i
        }
        return -1
    }

    private fun startDrag(index: Int) {
        val s = score ?: return
        dragIndex = index
        dragOrigin = s
        dragCell = s.events[index].endUnits / s.config.cellUnits
    }

    /**
     * Moves the dragged border to the cell under the mouse. The border snaps to the nearest
     * cell, and bar lines pull it harder, so a note can be lined up exactly with the bar.
     */
    private fun dragTo(x: Double) {
        val origin = dragOrigin ?: return
        val index = dragIndex
        if (index < 0) return
        val target = origin.snapBorder(grid.cellOfX(x))
        val edited = origin.moveBoundary(index, (target - dragCell) * origin.config.cellUnits)
        if (edited == score) return
        score = edited
        onEdit?.invoke(edited)
        repaint()
    }

    // -------------------------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------------------------

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val w = width.toDouble()
            val h = height.toDouble()
            g2.color = Theme.code
            g2.fill(rr(0.0, 0.0, w, h, 14.0))
            g2.color = Theme.line
            g2.draw(rr(0.5, 0.5, w - 1, h - 1, 14.0))

            val s = score
            if (s == null || grid.span <= 0) {
                drawHint(g2, w, h)
                return@paint2d
            }
            g2.clip = rr(1.0, 1.0, w - 2, h - 2, 13.0)

            val top = 13.0
            val blockH = h - 26.0
            drawBarLines(g2, h)
            drawSelectionBand(g2, s, top, blockH)
            for (event in s.events) {
                val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
                val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
                drawBlock(g2, event, s.config, BlockBox(x1, x2, top, top + blockH))
            }
            drawHoveredBlock(g2, s, top, blockH)
            drawSelection(g2, s, top, blockH)
            drawHoveredBorder(g2, s, top, blockH)

            // Playback playhead: a thin line moving across the score blocks.
            if (playbackPositionMs >= 0) {
                val cell = playbackPositionMs / s.config.cellMs
                val px = grid.xOfCell(cell)
                if (px >= -10 && px <= w + 10) {
                    g2.color = Theme.ok
                    g2.stroke = BasicStroke(2f)
                    g2.draw(Line2D.Double(px, top - 6, px, top + blockH + 6))
                    val head = Path2D.Double()
                    head.moveTo(px - 4, top - 6)
                    head.lineTo(px + 4, top - 6)
                    head.lineTo(px, top + 1)
                    head.closePath()
                    g2.fill(head)
                }
            }
            if (hasFocus()) drawFocus(g2, w, h)
        }
    }

    /** The bar lines, so the strip reads as the same grid as the timeline. */
    private fun drawBarLines(
        g2: Graphics2D,
        h: Double,
    ) {
        g2.stroke = BasicStroke(1f)
        for (bar in 0..grid.bars) {
            val x = grid.xOfCell((bar * grid.cellsPerBar).toDouble())
            if (x < -2 || x > width + 2) continue
            g2.color = Theme.text.alpha(70)
            g2.drawLine(x.toInt(), 4, x.toInt(), (h - 4).toInt())
        }
    }

    /** One note or rest: a filled block for a note, an outlined one for a rest. */
    private fun drawBlock(
        g2: Graphics2D,
        event: Event,
        config: Config,
        box: BlockBox,
    ) {
        if (box.width <= 1.0) return
        val note = event.kind == TapKind.NOTE
        if (note) {
            val r = minOf(6.0, box.width / 2, box.height / 2)
            g2.paint =
                GradientPaint(
                    box.x1.toFloat(),
                    0f,
                    Theme.accent,
                    (box.x1 + max(box.width, 40.0)).toFloat(),
                    0f,
                    Theme.accent2,
                )
            g2.fill(rr(box.x1, box.top, box.width, box.height, r))
        } else {
            g2.color = Theme.warn.alpha(35)
            g2.fill(rr(box.x1, box.top, box.width, box.height, 4.0))
            g2.color = Theme.warn.alpha(150)
            g2.stroke = BasicStroke(1.2f)
            g2.draw(rr(box.x1 + 0.5, box.top + 0.5, box.width - 1, box.height - 1, 4.0))
        }
        drawToken(g2, event, config, box, if (note) Theme.onAccent else Theme.warn)
    }

    /** The LilyPond text of a block, drawn in it when there is room for it. */
    private fun drawToken(
        g2: Graphics2D,
        event: Event,
        config: Config,
        box: BlockBox,
        color: Color,
    ) {
        val units = event.lengthUnits
        val name = if (event.kind == TapKind.NOTE) config.pitch else "r"
        val sep = if (event.kind == TapKind.NOTE) " ~ " else " "
        val token = decompose(units).joinToString(sep) { name + lilyDuration(it) }
        g2.font = Theme.font(11f, Font.BOLD)
        val fm = g2.fontMetrics
        if (fm.stringWidth(token) > box.width - 10) return
        g2.color = color
        val tx = (box.centerX - fm.stringWidth(token) / 2.0).toFloat()
        g2.drawString(token, tx, (box.centerY + fm.ascent / 2.0 - 1).toFloat())
    }

    /** A soft band behind a range of blocks, so it reads as one thing. */
    private fun drawSelectionBand(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val range = selectedRange(s) ?: return
        if (range.first == range.last) return
        val first = s.events[range.first]
        val last = s.events[range.last]
        val x1 = grid.xOfCell(first.startUnits.toDouble() / s.config.cellUnits)
        val x2 = grid.xOfCell(last.endUnits.toDouble() / s.config.cellUnits)
        g2.color = Theme.accent.alpha(28)
        g2.fill(Rectangle2D.Double(x1, top - 4, x2 - x1, hh + 8))
    }

    /** An accent ring around every selected block. */
    private fun drawSelection(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val range = selectedRange(s) ?: return
        g2.color = Theme.accent
        g2.stroke = BasicStroke(2f)
        for (index in range) {
            val event = s.events[index]
            val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
            val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
            g2.draw(rr(x1 - 1, top - 1, x2 - x1 + 2, hh + 2, 6.0))
        }
    }

    /** A thin outline around the block under the mouse. */
    private fun drawHoveredBlock(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val index = hoverBlock
        if (index < 0 || index >= s.events.size) return
        val event = s.events[index]
        val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
        val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
        g2.color = Theme.dim.alpha(140)
        g2.stroke = BasicStroke(1f)
        g2.draw(rr(x1, top, x2 - x1, hh, 6.0))
    }

    /** The border under the mouse, so it is clear what can be dragged. */
    private fun drawHoveredBorder(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val index = hoverBorder
        if (index < 0 || index >= s.events.size - 1) return
        val x = grid.xOfCell(s.events[index].endUnits.toDouble() / s.config.cellUnits)
        g2.color = Theme.accent.alpha(200)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(x.toInt(), (top - 4).toInt(), x.toInt(), (top + hh + 4).toInt())
    }

    /** A thin accent line, so it is clear the strip has the keyboard. */
    private fun drawFocus(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        g2.color = Theme.accent.alpha(170)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(14, (h - 4).toInt(), (w - 14).toInt(), (h - 4).toInt())
    }

    /** What the strip says before there is a take to show. */
    private fun drawHint(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        val title = "Your score will show up here"
        val hint = "Click a block to select it, double-click to turn it into a rest, drag a border to change its length."
        g2.font = Theme.font(13f, Font.BOLD)
        val titleFm = g2.fontMetrics
        g2.font = Theme.font(12f)
        val hintFm = g2.fontMetrics
        // The two lines are one block, and the block sits in the middle of the panel.
        val gap = 6.0
        val blockTop = (h - (titleFm.ascent + titleFm.descent + gap + hintFm.ascent + hintFm.descent)) / 2
        g2.font = Theme.font(13f, Font.BOLD)
        g2.color = Theme.dim
        g2.drawString(title, ((w - titleFm.stringWidth(title)) / 2).toFloat(), (blockTop + titleFm.ascent).toFloat())
        g2.font = Theme.font(12f)
        g2.color = Theme.faint
        g2.drawString(hint, ((w - hintFm.stringWidth(hint)) / 2).toFloat(), (blockTop + hintFm.height + gap + hintFm.ascent).toFloat())
    }

    companion object {
        /** How close to a border the mouse has to be to grab it, in pixels. */
        private const val GRAB_PX = 7.0

        /** What the header says when nothing is selected. */
        const val NOTHING_SELECTED = "Nothing selected"
    }

    /** The box a block is drawn in. */
    private data class BlockBox(
        val x1: Double,
        val x2: Double,
        val top: Double,
        val bottom: Double,
    ) {
        val width: Double get() = x2 - x1
        val height: Double get() = bottom - top
        val centerX: Double get() = (x1 + x2) / 2
        val centerY: Double get() = (top + bottom) / 2
    }
}
