// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The recording timeline: the bar/beat grid, the notes as you play them and the glowing playhead.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

internal class Session(
    val config: Config,
    val minRestCells: Int,
    val originNanos: Long,
    val endNanos: Long,
) {
    val finished = mutableListOf<HeldNote>()
    val held = HashMap<Int, Double>() // keyCode -> press time in ms
    val rests = mutableListOf<Double>() // rest press times in ms
    var done = false
    var snapped: List<SnappedNote> = emptyList() // filled in when the take finishes
    lateinit var ticker: Thread
}

/**
 * Draws the bar/beat grid, the notes as you play them, and a glowing playhead.
 * Each bar gets a minimum width; when the bars don't fit, the grid scrolls horizontally
 * (mouse wheel, or drag the slim bar at the bottom) and follows the playhead while recording.
 */
internal class TimelinePanel(
    private val grid: GridView,
) : JComponent() {
    var session: Session? = null
        set(v) {
            field = v
            grid.config = v?.config
            repaint()
        }

    /** Show raw presses as ghosts under the snapped notes once a take is finished. */
    var showRaw: Boolean = false
        set(v) {
            field = v
            repaint()
        }

    private var grab = -1.0 // offset inside the scrollbar thumb while dragging, < 0 otherwise

    init {
        isOpaque = false
        preferredSize = Dimension(760, 130)
        // The grid tells us when it scrolls, so the score strip below keeps up with us.
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
                    if (!grid.scrollable || e.y < height - 26) return
                    val thumbX = thumbX()
                    val thumbW = thumbW()
                    grab = if (e.x >= thumbX && e.x <= thumbX + thumbW) e.x - thumbX else thumbW / 2
                    dragTo(e.x.toDouble())
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (grab >= 0) dragTo(e.x.toDouble())
                }

                override fun mouseReleased(e: MouseEvent) {
                    grab = -1.0
                }
            }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener { e ->
            if (grid.scrollable) {
                grid.scrollTo(grid.scrollX + e.preciseWheelRotation * 60)
            }
        }
    }

    private fun thumbW() = max(grid.span / grid.totalPx * grid.span, 24.0)

    private fun thumbX() = grid.padX + grid.scrollX / grid.maxScroll * (grid.span - thumbW())

    private fun dragTo(mouseX: Double) {
        val room = grid.span - thumbW()
        if (room <= 0) return
        grid.scrollTo((mouseX - grab - grid.padX) / room * grid.maxScroll)
    }

    /** While recording, keeps the playhead in view (called every frame). */
    fun follow() {
        val s = session ?: return
        if (s.done) return
        if (!grid.scrollable) return
        val nowMs = (System.nanoTime() - s.originNanos) / 1e6
        val totalMs = s.config.totalCells * s.config.cellMs
        if (nowMs < 0 || nowMs > totalMs) return
        val target = nowMs / totalMs * grid.totalPx - grid.span * 0.7
        if (target > grid.scrollX) {
            grid.scrollTo(target)
        }
    }

    /** The horizontal position of a time in ms on the timeline. */
    private fun x(ms: Double): Double {
        val s = session ?: return 0.0
        val totalMs = s.config.totalCells * s.config.cellMs
        return grid.xOfCell(ms.coerceIn(0.0, totalMs) / s.config.cellMs)
    }

    /** Draws the alternating bar bands, the bar numbers and the grid lines. */
    private fun drawGrid(
        g2: Graphics2D,
        cfg: Config,
        rulerTop: Double,
        laneTop: Double,
        laneBottom: Double,
    ) {
        val w = width.toDouble()
        val laneH = laneBottom - laneTop
        val barMs = cfg.cellsPerBar * cfg.cellMs
        g2.font = Theme.font(11f, Font.BOLD)
        for (b in 0 until cfg.bars) {
            val x0 = x(b * barMs)
            val x1 = x((b + 1) * barMs)
            if (x1 < -40 || x0 > w + 40) continue
            if (b % 2 == 1) {
                g2.color = Color(255, 255, 255, 7)
                g2.fill(Rectangle2D.Double(x0, laneTop, x1 - x0, laneH))
            }
            g2.color = Theme.dim
            g2.drawString("${b + 1}", (x0 + 6).toFloat(), (rulerTop + 14).toFloat())
        }

        g2.stroke = BasicStroke(1f)
        for (cell in 0..cfg.totalCells) {
            val xx = x(cell * cfg.cellMs)
            if (xx <= -40 || xx >= w + 40) continue
            when {
                cell % cfg.cellsPerBar == 0 -> {
                    g2.color = Theme.text.alpha(95)
                    g2.draw(Line2D.Double(xx, rulerTop, xx, laneBottom))
                }

                cell % cfg.subdivisions == 0 -> {
                    g2.color = Theme.text.alpha(42)
                    g2.draw(Line2D.Double(xx, laneTop - 6, xx, laneBottom))
                }

                else -> {
                    g2.color = Theme.text.alpha(16)
                    g2.draw(Line2D.Double(xx, laneTop, xx, laneBottom))
                }
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val w = width.toDouble()
            val h = height.toDouble()
            g2.color = Theme.code
            g2.fill(rr(0.0, 0.0, w, h, 14.0))
            g2.color = Theme.line
            g2.draw(rr(0.5, 0.5, w - 1, h - 1, 14.0))

            val s = session
            if (s == null || grid.span <= 0) {
                drawEmptyState(g2, w, h)
                return@paint2d
            }
            g2.clip = rr(1.0, 1.0, w - 2, h - 2, 13.0)

            val cfg = s.config
            val totalMs = cfg.totalCells * cfg.cellMs
            val rulerTop = 10.0
            val laneTop = 34.0
            val laneBottom = h - if (grid.scrollable) 26.0 else 14.0
            val laneH = laneBottom - laneTop

            drawGrid(g2, cfg, rulerTop, laneTop, laneBottom)

            val nowMs = if (s.done) -1.0 else (System.nanoTime() - s.originNanos) / 1e6
            val pulse = 0.5 + 0.5 * sin(System.nanoTime() / 1.2e8)
            val overlay = showRaw && s.done && s.snapped.isNotEmpty()

            // Fixed corner radius, so long notes stay rounded rectangles instead of pills
            fun solid(
                x1: Double,
                x2: Double,
                y: Double,
                hh: Double,
                live: Boolean,
            ) {
                if (x2 < -10 || x1 > w + 10) return
                val nw = x2 - x1
                val r = minOf(7.0, nw / 2, hh / 2)
                g2.color = Theme.accent.alpha(if (live) (40 + 40 * pulse).toInt() else 34)
                g2.fill(rr(x1 - 3, y - 3, nw + 6, hh + 6, r + 3))
                g2.paint = GradientPaint(x1.toFloat(), 0f, Theme.accent, (x1 + max(nw, 40.0)).toFloat(), 0f, Theme.accent2)
                g2.fill(rr(x1, y, nw, hh, r))
                g2.color = Color(255, 255, 255, if (live) 120 else 50)
                g2.stroke = BasicStroke(1f)
                g2.draw(rr(x1 + 0.5, y + 0.5, nw - 1, hh - 1, r))
            }

            fun ghost(
                x1: Double,
                x2: Double,
                y: Double,
                hh: Double,
            ) {
                if (x2 < -10 || x1 > w + 10) return
                val nw = x2 - x1
                val r = minOf(5.0, nw / 2, hh / 2)
                g2.color = Theme.accent.alpha(40)
                g2.fill(rr(x1, y, nw, hh, r))
                g2.color = Theme.accent.alpha(190)
                g2.stroke = BasicStroke(1.2f)
                g2.draw(rr(x1 + 0.5, y + 0.5, nw - 1, hh - 1, r))
            }

            /** A rest: an outlined, dim block that spans the silence. */
            fun rest(
                x1: Double,
                x2: Double,
                y: Double,
                hh: Double,
            ) {
                if (x2 < -10 || x1 > w + 10) return
                val nw = max(x2 - x1, 6.0)
                g2.color = Theme.warn.alpha(35)
                g2.fill(rr(x1, y, nw, hh, 4.0))
                g2.color = Theme.warn.alpha(150)
                g2.stroke = BasicStroke(1.2f)
                g2.draw(rr(x1 + 0.5, y + 0.5, nw - 1, hh - 1, 4.0))
            }

            if (overlay) {
                // Top row: what was written (snapped to the grid). Bottom row: what you actually played.
                val snY = laneTop + laneH * 0.10
                val snH = laneH * 0.46
                val gY = snY + snH + laneH * 0.12
                val gH = laneH * 0.20
                val midY = snY + snH + laneH * 0.06
                for (n in s.snapped) {
                    val sx1 = x(n.startCell * cfg.cellMs)
                    val sx2 = max(x(n.endCell * cfg.cellMs) - 1.5, sx1 + 4)
                    if (n.raw.kind == TapKind.REST) {
                        rest(sx1, sx2, snY, snH)
                        continue
                    }
                    val rx1 = x(n.raw.downMs)
                    val rx2 = max(x(n.raw.upMs), rx1 + 4)
                    solid(sx1, sx2, snY, snH, false)
                    ghost(rx1, rx2, gY, gH)
                    val shift = rx1 - sx1
                    if (abs(shift) > 1.5) {
                        g2.color = if (shift > 0) Theme.warn else Theme.accent2 // late : early
                        g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g2.draw(Line2D.Double(sx1, midY, rx1, midY))
                        g2.fill(Ellipse2D.Double(rx1 - 2.5, midY - 2.5, 5.0, 5.0))
                    }
                }
            } else {
                val noteY = laneTop + laneH * 0.22
                val noteH = laneH * 0.56
                for (n in s.finished) solid(x(n.downMs), max(x(n.upMs), x(n.downMs) + 8), noteY, noteH, false)
                for (down in s.held.values) solid(x(down), max(x(nowMs), x(down) + 8), noteY, noteH, true)
                for (restAt in s.rests) {
                    val rx = x(restAt)
                    g2.color = Theme.warn.alpha(160)
                    g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(Line2D.Double(rx, noteY, rx, noteY + noteH))
                }
            }

            // Playhead
            if (nowMs in 0.0..totalMs) {
                val px = x(nowMs)
                g2.color = Theme.rec.alpha(26)
                g2.stroke = BasicStroke(10f)
                g2.draw(Line2D.Double(px, rulerTop + 6, px, laneBottom))
                g2.color = Theme.rec.alpha(56)
                g2.stroke = BasicStroke(5f)
                g2.draw(Line2D.Double(px, rulerTop + 6, px, laneBottom))
                g2.color = Theme.rec
                g2.stroke = BasicStroke(2f)
                g2.draw(Line2D.Double(px, rulerTop + 6, px, laneBottom + 2))
                val head = Path2D.Double()
                head.moveTo(px - 6, rulerTop)
                head.lineTo(px + 6, rulerTop)
                head.lineTo(px, rulerTop + 9)
                head.closePath()
                g2.fill(head)
            }

            if (grid.scrollable) {
                // Soft fades where more content is hidden
                val clear = Theme.code.alpha(0)
                if (grid.scrollX > 1) {
                    g2.paint = GradientPaint(0f, 0f, Theme.code, 30f, 0f, clear)
                    g2.fill(Rectangle2D.Double(0.0, 1.0, 30.0, h - 2))
                }
                if (grid.scrollX < grid.maxScroll - 1) {
                    g2.paint = GradientPaint((w - 30).toFloat(), 0f, clear, w.toFloat(), 0f, Theme.code)
                    g2.fill(Rectangle2D.Double(w - 30, 1.0, 30.0, h - 2))
                }
                // Slim scrollbar
                val trackY = h - 14
                g2.color = Theme.field
                g2.fill(rr(grid.padX, trackY, grid.span, 5.0, 2.5))
                g2.color = if (grab >= 0) Theme.accent else Theme.faint
                g2.fill(rr(thumbX(), trackY, thumbW(), 5.0, 2.5))
            }

            if (overlay) drawLegend(g2, w, rulerTop)
        }
    }

    private fun drawLegend(
        g2: Graphics2D,
        w: Double,
        rulerTop: Double,
    ) {
        g2.font = Theme.font(11f)
        val fm = g2.fontMetrics
        val labels = listOf("snapped", "raw", "late", "early", "rest")
        val widths = labels.map { 14 + 6 + fm.stringWidth(it) + 14 }
        val total = widths.sum().toDouble()
        val x0 = w - 14 - total
        val cy = rulerTop + 8
        g2.color = Theme.code.alpha(235)
        g2.fill(rr(x0, rulerTop - 3, total + 8, 22.0, 8.0))
        var ix = x0 + 8
        labels.forEachIndexed { i, label ->
            when (i) {
                0 -> {
                    g2.color = Theme.accent
                    g2.fill(rr(ix, cy - 4, 14.0, 8.0, 3.0))
                }

                1 -> {
                    g2.color = Theme.accent.alpha(40)
                    g2.fill(rr(ix, cy - 4, 14.0, 8.0, 3.0))
                    g2.color = Theme.accent.alpha(190)
                    g2.stroke = BasicStroke(1.2f)
                    g2.draw(rr(ix + 0.5, cy - 3.5, 13.0, 7.0, 3.0))
                }

                4 -> {
                    g2.color = Theme.warn.alpha(35)
                    g2.fill(rr(ix, cy - 4, 14.0, 8.0, 3.0))
                    g2.color = Theme.warn.alpha(150)
                    g2.stroke = BasicStroke(1.2f)
                    g2.draw(rr(ix + 0.5, cy - 3.5, 13.0, 7.0, 3.0))
                }

                else -> {
                    g2.color = if (i == 2) Theme.warn else Theme.accent2
                    g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(Line2D.Double(ix + 1, cy, ix + 13, cy))
                }
            }
            g2.color = Theme.dim
            g2.drawString(label, (ix + 20).toFloat(), (cy + fm.ascent / 2 - 1).toFloat())
            ix += widths[i]
        }
    }

    private fun drawEmptyState(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        drawGlyph(g2, Glyph.NOTE, w / 2 - 15, h / 2 - 40, 30.0, Theme.faint)
        g2.font = Theme.font(14f, Font.BOLD)
        g2.color = Theme.dim
        var fm = g2.fontMetrics
        var msg = "Your rhythm will show up here"
        g2.drawString(msg, ((w - fm.stringWidth(msg)) / 2).toFloat(), (h / 2 + 18).toFloat())
        g2.font = Theme.font(12f)
        g2.color = Theme.faint
        fm = g2.fontMetrics
        msg = "Press Start, wait out the count-in, then play with any key. Hold a key to sustain."
        g2.drawString(msg, ((w - fm.stringWidth(msg)) / 2).toFloat(), (h / 2 + 38).toFloat())
    }
}
