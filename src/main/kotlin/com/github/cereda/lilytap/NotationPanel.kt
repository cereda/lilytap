// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The score strip: the notes and rests of the take, drawn on the grid the timeline uses.
 *
 * Clicking a block turns it into a rest (and back again), dragging the border between two
 * blocks makes the first one longer and the second one shorter. The score itself does the
 * work - this panel only turns clicks and drags into edits.
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.max

/**
 * The score strip: the notes and rests of the take, editable in place.
 *
 * The strip shares its grid with the recording timeline, so a block sits exactly under the
 * beat it belongs to. Clicking a block turns it into a rest, dragging the border between two
 * blocks resizes them.
 */
internal class NotationPanel(
    private val grid: GridView,
) : JComponent() {
    /** The score to show. */
    var score: Score? = null
        set(value) {
            field = value
            repaint()
        }

    /** Called whenever the user has edited the score. */
    var onEdit: ((Score) -> Unit)? = null

    private var dragIndex = -1 // the border being dragged
    private var dragOrigin: Score? = null // the score when the drag started
    private var dragCell = 0 // the cell the dragged border was on
    private var pressX = 0.0
    private var pressIndex = -1 // the block that was pressed
    private var hoverBorder = -1

    /** Current playback position in ms (-1 when not playing). */
    var playbackPositionMs: Double = -1.0
        set(v) {
            field = v
            repaint()
        }

    init {
        isOpaque = false
        preferredSize = Dimension(760, 68)
        toolTipText = "Click a note to turn it into a rest. Drag a border to change its length."
        // The strip shares the grid with the timeline: when it scrolls, the strip follows.
        grid.onChange { repaint() }
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
                    if (dragIndex >= 0) {
                        dragIndex = -1
                        dragOrigin = null
                        return
                    }
                    // A click is a press and a release that barely moved.
                    if (pressIndex >= 0 && abs(e.x - pressX) <= 3.0) toggle(pressIndex)
                    pressIndex = -1
                }

                override fun mouseMoved(e: MouseEvent) {
                    val border = borderAt(e.x.toDouble())
                    if (border != hoverBorder) {
                        hoverBorder = border
                        repaint()
                    }
                    cursor =
                        Cursor.getPredefinedCursor(
                            if (border >= 0) Cursor.E_RESIZE_CURSOR else Cursor.DEFAULT_CURSOR,
                        )
                }

                override fun mouseExited(e: MouseEvent) {
                    hoverBorder = -1
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        // The wheel scrolls the grid, and with it the timeline.
        addMouseWheelListener { e ->
            if (grid.scrollable) {
                grid.scrollTo(grid.scrollX + e.preciseWheelRotation * 60)
                repaint()
            }
        }
    }

    /** The border closest to the x pixel, or -1 when the mouse is not near one. */
    private fun borderAt(x: Double): Int {
        val s = score ?: return -1
        for (i in 0 until s.events.size - 1) {
            val bx = grid.xOfCell(s.events[i].endCell.toDouble())
            if (abs(x - bx) <= GRAB_PX) return i
        }
        return -1
    }

    /** The block under the x pixel, or -1. */
    private fun blockAt(x: Double): Int {
        val s = score ?: return -1
        val cell = grid.cellOfX(x)
        for ((i, event) in s.events.withIndex()) {
            if (cell >= event.startCell && cell < event.endCell) return i
        }
        return -1
    }

    private fun startDrag(index: Int) {
        val s = score ?: return
        dragIndex = index
        dragOrigin = s
        dragCell = s.events[index].endCell
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
        val edited = origin.moveBoundary(index, target - dragCell)
        if (edited == score) return
        score = edited
        onEdit?.invoke(edited)
        repaint()
    }

    private fun toggle(index: Int) {
        val s = score ?: return
        val edited = s.toggleRest(index)
        score = edited
        onEdit?.invoke(edited)
        repaint()
    }

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
            for (event in s.events) {
                val x1 = grid.xOfCell(event.startCell.toDouble()) + 1.0
                val x2 = grid.xOfCell(event.endCell.toDouble()) - 1.0
                drawBlock(g2, event, s.config, BlockBox(x1, x2, top, top + blockH))
            }
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
        val units = event.lengthCells * config.cellUnits
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

    /** The border under the mouse, so it is clear what can be dragged. */
    private fun drawHoveredBorder(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val index = hoverBorder
        if (index < 0 || index >= s.events.size - 1) return
        val x = grid.xOfCell(s.events[index].endCell.toDouble())
        g2.color = Theme.accent.alpha(200)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(x.toInt(), (top - 4).toInt(), x.toInt(), (top + hh + 4).toInt())
    }

    /** What the strip says before there is a take to show. */
    private fun drawHint(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        val title = "Your score will show up here"
        val hint = "Click a note to turn it into a rest, drag a border to change its length."
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
        g2.drawString(hint, ((w - hintFm.stringWidth(hint)) / 2).toFloat(), (blockTop + titleFm.height + gap + hintFm.ascent).toFloat())
    }

    companion object {
        /** How close to a border the mouse has to be to grab it, in pixels. */
        private const val GRAB_PX = 7.0
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
