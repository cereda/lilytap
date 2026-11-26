// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The control that transposes the score: a button that opens a panel of intervals, and the
 * panel itself.
 *
 * The panel has two columns - one for each direction - and a row for every interval. Every
 * cell says where it would take the piece, so the user sees the key the score would land in
 * before the click.
 */

import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.JPopupMenu

/**
 * The button that opens the panel of intervals.
 *
 * The button is a pill with the word **Transpose** on it and a small arrow that says which
 * way the panel opens: down when it is closed, up when it is open.
 */
internal class TransposeButton : PopupButton() {
    /** Called when an interval is picked: the score is transposed by it. */
    var onPick: ((Transposition) -> Unit)? = null

    /** The key the piece is in: the panel says where every interval would take it. */
    var key: KeySignature = KeySignature.C_MAJOR
        set(value) {
            field = value
            panel.key = value
        }

    /** The panel of intervals: it is built once and filled every time the popup opens. */
    private val panel = IntervalPanel { transposition -> onPick?.invoke(transposition) }

    init {
        toolTipText = TOOLTIP
        font = Theme.font(14f, Font.BOLD)
        preferredSize = Dimension(WIDTH, HEIGHT)
    }

    /**
     * The panel of intervals, with the key of the piece in it.
     *
     * @return the popup the button opens.
     */
    override fun buildPopup(): JPopupMenu {
        val made = menuPopup()
        panel.key = key
        made.add(panel)
        return made
    }

    /**
     * The popup opens above the button.
     *
     * The button sits at the top of the pitches panel, which is at the bottom of the window:
     * there is no room below it, and a popup that hangs out of the window is cut off.
     *
     * @param size the size the popup wants.
     * @return the place of the popup, in the coordinates of the button.
     */
    override fun popupPoint(size: Dimension): Point = Point(width - size.width, -size.height - POPUP_GAP)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val w = width.toDouble()
            val h = height.toDouble()
            val shape = rr(0.5, 0.5, w - 1, h - 1, (h - 1) / 2)
            g2.color = if (hover && isEnabled) Theme.fieldHover else Theme.field
            g2.fill(shape)
            g2.color = Theme.line
            g2.draw(shape)
            g2.font = font
            val fm = g2.fontMetrics
            g2.color = Theme.text
            val tw = fm.stringWidth(LABEL)
            g2.drawString(
                LABEL,
                ((w - tw) / 2 - ARROW / 2).toFloat(),
                ((h - fm.height) / 2 + fm.ascent).toFloat(),
            )
            drawArrow(g2, w - ARROW - 6.0, h / 2.0)
        }
    }

    /**
     * The arrow of the button: it says which way the panel opens.
     *
     * @param g2 the graphics to draw on.
     * @param cx the place of the middle of the arrow.
     * @param cy the place of the middle of the arrow.
     */
    private fun drawArrow(
        g2: Graphics2D,
        cx: Double,
        cy: Double,
    ) {
        val arrow = Path2D.Double()
        if (up) {
            arrow.moveTo(cx - 4.0, cy + 2.0)
            arrow.lineTo(cx + 4.0, cy + 2.0)
            arrow.lineTo(cx, cy - 3.0)
        } else {
            arrow.moveTo(cx - 4.0, cy - 2.0)
            arrow.lineTo(cx + 4.0, cy - 2.0)
            arrow.lineTo(cx, cy + 3.0)
        }
        arrow.closePath()
        g2.color = Theme.dim
        g2.fill(arrow)
    }

    companion object {
        /** The word on the button. */
        private const val LABEL = "Transpose"

        /** How wide the button is, in pixels. */
        private const val WIDTH = 124

        /** How tall the button is, in pixels. */
        private const val HEIGHT = 32

        /** The room the arrow takes at the right of the button, in pixels. */
        private const val ARROW = 12

        /** What the button says about itself. */
        private const val TOOLTIP =
            "Move the whole score by an interval. The key follows, and Ctrl+Z undoes it."
    }
}

/**
 * The message the window shows when a transposition is refused.
 *
 * @param message the reason the transposition was refused.
 * @return the message of the dialog.
 */
internal fun transposeRefused(message: String): Message =
    Message(
        "Cannot transpose",
        "The score stays where it is",
        message,
        Glyph.BOLT,
        Theme.rec,
    )

/**
 * The panel of intervals: two columns, one for each direction, and a row for every interval.
 *
 * Every cell says where it would take the piece: the key it would land in, under the name
 * of the interval. A cell that would take the piece to a key the app cannot hold says so
 * instead.
 */
internal class IntervalPanel(
    private val onPick: (Transposition) -> Unit,
) : JComponent() {
    /** The key the piece is in: the cells say where every interval would take it. */
    var key: KeySignature = KeySignature.C_MAJOR
        set(value) {
            field = value
            repaint()
        }

    /** The cell under the mouse, or null. */
    private var hover: Transposition? = null

    init {
        isOpaque = false
        // The panel is a picker: it never takes the keyboard away from the score.
        isFocusable = false
        font = Theme.font(13f)
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val picked = pickAt(e.x, e.y) ?: return
                    onPick(picked)
                }
            },
        )
        addMouseMotionListener(
            object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val found = pickAt(e.x, e.y)
                    if (found != hover) {
                        hover = found
                        repaint()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    if (hover != null) {
                        hover = null
                        repaint()
                    }
                }
            },
        )
    }

    /**
     * The transposition under a point of the panel.
     *
     * @param x the place of the point.
     * @param y the place of the point.
     * @return the transposition the point is on, or null when it is not on one.
     */
    fun pickAt(
        x: Int,
        y: Int,
    ): Transposition? {
        val top = PAD + HEADER_H
        if (y < top) return null
        val row = (y - top) / ROW_H
        if (row >= Interval.entries.size) return null
        val left = PAD
        val right = PAD + CELL_W + GAP
        val up =
            when {
                x in left until left + CELL_W -> false
                x in right until right + CELL_W -> true
                else -> return null
            }
        return Transposition(Interval.entries[row], up)
    }

    override fun getPreferredSize(): Dimension =
        Dimension(
            2 * PAD + 2 * CELL_W + GAP,
            2 * PAD + HEADER_H + Interval.entries.size * ROW_H,
        )

    /**
     * What a cell says about itself.
     *
     * @param event the mouse event that asks for the tooltip.
     * @return the text of the tooltip, or null when the mouse is not on a cell.
     */
    override fun getToolTipText(event: MouseEvent): String? {
        val picked = pickAt(event.x, event.y) ?: return null
        val target = key.transposed(picked)
        val where = if (target == null) "a key the app cannot hold" else target.name
        return "${picked.name(key)}: ${key.name} → $where"
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val w = width.toDouble()
            val h = height.toDouble()
            g2.color = Theme.field
            g2.fill(rr(0.5, 0.5, w - 1, h - 1, 10.0))
            g2.color = Theme.line
            g2.draw(rr(0.5, 0.5, w - 1, h - 1, 10.0))
            g2.font = Theme.font(12f)
            g2.color = Theme.dim
            drawTitle(g2, "Down", PAD.toDouble(), false)
            drawTitle(g2, "Up", (PAD + CELL_W + GAP).toDouble(), true)
            Interval.entries.forEachIndexed { index, interval ->
                val top = PAD + HEADER_H + index * ROW_H
                drawCell(g2, interval, false, top)
                drawCell(g2, interval, true, top)
            }
        }
    }

    /**
     * The title of a column of the panel: the name of the direction and an arrow that says
     * which way it goes.
     *
     * @param g2 the graphics to draw on.
     * @param text the name of the direction.
     * @param left the place of the left edge of the column.
     * @param up whether the column is the one that moves the notes up.
     */
    private fun drawTitle(
        g2: Graphics2D,
        text: String,
        left: Double,
        up: Boolean,
    ) {
        val fm = g2.fontMetrics
        val baseline = PAD + HEADER_H - 6
        val cx = left + 6.0
        val cy = baseline - fm.ascent / 2.0
        val arrow = Path2D.Double()
        if (up) {
            arrow.moveTo(cx - 4.0, cy + 3.0)
            arrow.lineTo(cx + 4.0, cy + 3.0)
            arrow.lineTo(cx, cy - 3.0)
        } else {
            arrow.moveTo(cx - 4.0, cy - 3.0)
            arrow.lineTo(cx + 4.0, cy - 3.0)
            arrow.lineTo(cx, cy + 3.0)
        }
        arrow.closePath()
        g2.color = Theme.dim
        g2.fill(arrow)
        g2.drawString(text, (left + 14).toFloat(), baseline.toFloat())
    }

    /**
     * A cell of the panel: the name of the interval and the key it would take the piece to.
     *
     * @param g2 the graphics to draw on.
     * @param interval the interval of the cell.
     * @param up whether the cell moves the notes up.
     * @param top the place of the top edge of the cell.
     */
    private fun drawCell(
        g2: Graphics2D,
        interval: Interval,
        up: Boolean,
        top: Int,
    ) {
        val left = if (up) PAD + CELL_W + GAP else PAD
        val transposition = Transposition(interval, up)
        val here = hover == transposition
        if (here) {
            g2.color = Theme.fieldHover
            g2.fill(
                rr(
                    (left + 1).toDouble(),
                    (top + 1).toDouble(),
                    (CELL_W - 2).toDouble(),
                    (ROW_H - 2).toDouble(),
                    7.0,
                ),
            )
        }
        g2.font = font
        g2.color = Theme.text
        val fm = g2.fontMetrics
        val label = interval.label
        val x = left + (CELL_W - fm.stringWidth(label)) / 2
        g2.drawString(label, x.toFloat(), (top + 17).toFloat())
        val target = key.transposed(transposition)
        g2.font = Theme.font(11f)
        g2.color = Theme.dim
        val where = if (target == null) "—" else target.name
        val small = g2.fontMetrics
        val tx = left + (CELL_W - small.stringWidth(where)) / 2
        g2.drawString(where, tx.toFloat(), (top + 32).toFloat())
    }

    companion object {
        /** The room around the cells, in pixels. */
        private const val PAD = 10

        /** The gap between the two columns, in pixels. */
        private const val GAP = 8

        /** How wide a cell is, in pixels. */
        private const val CELL_W = 104

        /** How tall the row of the titles is, in pixels. */
        private const val HEADER_H = 24

        /** How tall a row of cells is, in pixels. */
        private const val ROW_H = 38
    }
}
