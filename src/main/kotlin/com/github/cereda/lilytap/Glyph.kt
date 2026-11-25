// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * Vector icons, drawn with Java2D so that they look the same on every OS and need no icon files.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

// ---------------------------------------------------------------------------------------------
// Vector icons
// ---------------------------------------------------------------------------------------------

internal enum class Glyph {
    PLAY,
    STOP,
    COPY,
    SAVE,
    CHECK,
    MINUS,
    PLUS,
    METRONOME,
    CLOCK,
    NOTE,
    BARS,
    GRID,
    PAUSE,
    BOLT,
    LILY,
    SUN,
    MOON,
    LAYERS,
    RESET,
    EYE,
    SPLIT,
    SWAP,
    MERGE,
    UNDO,
    REDO,
    TAP,
    BEATS,
    RELATIVE,
    KEY,
}

/** Draws [glyph] inside the square with top-left (x, y) and side [s]. */
@Suppress("LongParameterList")
internal fun drawGlyph(
    g: Graphics2D,
    glyph: Glyph,
    x: Double,
    y: Double,
    s: Double,
    color: Color,
) {
    val oldStroke = g.stroke
    val oldColor = g.color
    g.color = color
    g.stroke = BasicStroke((s * 0.11).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    fun px(v: Double) = x + v * s

    fun py(v: Double) = y + v * s

    fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ) {
        g.draw(Line2D.Double(px(x1), py(y1), px(x2), py(y2)))
    }

    fun path(
        close: Boolean,
        vararg p: Double,
    ): Path2D.Double {
        val pa = Path2D.Double()
        var i = 0
        while (i + 1 < p.size) {
            if (i == 0) pa.moveTo(px(p[0]), py(p[1])) else pa.lineTo(px(p[i]), py(p[i + 1]))
            i += 2
        }
        if (close) pa.closePath()
        return pa
    }

    /** Draws a filled arrow head with its tip at (x, y), pointing along (dx, dy). */
    fun arrowHead(
        x: Double,
        y: Double,
        dx: Double,
        dy: Double,
    ) {
        val len = Math.hypot(dx, dy)
        if (len == 0.0) return
        val ux = dx / len
        val uy = dy / len
        val nx = -uy
        val ny = ux
        val length = 0.20 * s
        val half = 0.13 * s
        val tipX = px(x)
        val tipY = py(y)
        val baseX = tipX - ux * length
        val baseY = tipY - uy * length
        val head = Path2D.Double()
        head.moveTo(tipX, tipY)
        head.lineTo(baseX + nx * half, baseY + ny * half)
        head.lineTo(baseX - nx * half, baseY - ny * half)
        head.closePath()
        g.fill(head)
    }

    when (glyph) {
        Glyph.PLAY -> {
            g.fill(path(true, 0.30, 0.18, 0.82, 0.50, 0.30, 0.82))
        }

        Glyph.STOP -> {
            g.fill(rr(px(0.24), py(0.24), 0.52 * s, 0.52 * s, 0.10 * s))
        }

        Glyph.COPY -> {
            g.draw(rr(px(0.14), py(0.32), 0.54 * s, 0.54 * s, 0.10 * s))
            g.draw(path(false, 0.32, 0.32, 0.32, 0.16, 0.84, 0.16, 0.84, 0.68, 0.68, 0.68))
        }

        Glyph.SAVE -> {
            // An arrow that goes down into the tray the file falls into.
            line(0.50, 0.10, 0.50, 0.46)
            arrowHead(0.50, 0.66, 0.0, 1.0)
            line(0.16, 0.86, 0.84, 0.86)
        }

        Glyph.CHECK -> {
            g.draw(path(false, 0.20, 0.54, 0.42, 0.74, 0.80, 0.30))
        }

        Glyph.MINUS -> {
            line(0.25, 0.5, 0.75, 0.5)
        }

        Glyph.PLUS -> {
            line(0.25, 0.5, 0.75, 0.5)
            line(0.5, 0.25, 0.5, 0.75)
        }

        Glyph.METRONOME -> {
            g.draw(path(true, 0.34, 0.12, 0.66, 0.12, 0.86, 0.88, 0.14, 0.88))
            line(0.50, 0.72, 0.66, 0.28)
            g.fill(Ellipse2D.Double(px(0.62) - 0.06 * s, py(0.40) - 0.06 * s, 0.12 * s, 0.12 * s))
        }

        Glyph.CLOCK -> {
            g.draw(Ellipse2D.Double(px(0.12), py(0.12), 0.76 * s, 0.76 * s))
            line(0.5, 0.5, 0.5, 0.28)
            line(0.5, 0.5, 0.66, 0.60)
        }

        Glyph.NOTE -> {
            g.fill(Ellipse2D.Double(px(0.12), py(0.62), 0.34 * s, 0.25 * s))
            line(0.45, 0.74, 0.45, 0.14)
            val flag = Path2D.Double()
            flag.moveTo(px(0.45), py(0.14))
            flag.curveTo(px(0.52), py(0.42), px(0.84), py(0.40), px(0.78), py(0.66))
            g.draw(flag)
        }

        Glyph.BARS -> {
            line(0.22, 0.40, 0.22, 0.82)
            line(0.50, 0.16, 0.50, 0.82)
            line(0.78, 0.54, 0.78, 0.82)
        }

        Glyph.GRID -> {
            g.draw(rr(px(0.14), py(0.14), 0.72 * s, 0.72 * s, 0.10 * s))
            line(0.5, 0.14, 0.5, 0.86)
            line(0.14, 0.5, 0.86, 0.5)
        }

        Glyph.PAUSE -> {
            line(0.36, 0.20, 0.36, 0.80)
            line(0.64, 0.20, 0.64, 0.80)
        }

        Glyph.BOLT -> {
            g.fill(path(true, 0.56, 0.08, 0.24, 0.56, 0.46, 0.56, 0.40, 0.92, 0.76, 0.42, 0.54, 0.42))
        }

        Glyph.SUN -> {
            g.draw(Ellipse2D.Double(px(0.32), py(0.32), 0.36 * s, 0.36 * s))
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val c = Math.cos(a)
                val sn = Math.sin(a)
                line(0.5 + 0.30 * c, 0.5 + 0.30 * sn, 0.5 + 0.42 * c, 0.5 + 0.42 * sn)
            }
        }

        Glyph.MOON -> {
            val moon = Area(Ellipse2D.Double(px(0.16), py(0.14), 0.68 * s, 0.68 * s))
            moon.subtract(Area(Ellipse2D.Double(px(0.38), py(0.04), 0.64 * s, 0.64 * s)))
            g.fill(moon)
        }

        Glyph.LAYERS -> {
            g.draw(rr(px(0.12), py(0.16), 0.52 * s, 0.42 * s, 0.08 * s))
            g.draw(rr(px(0.36), py(0.42), 0.52 * s, 0.42 * s, 0.08 * s))
        }

        Glyph.RESET -> {
            drawResetArrow(g, x, y, s)
        }

        Glyph.EYE -> {
            // An eye: the upper and lower lid as two curves, the pupil as a disc.
            val eye = Path2D.Double()
            eye.moveTo(px(0.08), py(0.50))
            eye.curveTo(px(0.30), py(0.12), px(0.70), py(0.12), px(0.92), py(0.50))
            eye.curveTo(px(0.70), py(0.88), px(0.30), py(0.88), px(0.08), py(0.50))
            eye.closePath()
            g.draw(eye)
            g.fill(Ellipse2D.Double(px(0.5) - 0.15 * s, py(0.5) - 0.15 * s, 0.30 * s, 0.30 * s))
        }

        Glyph.LILY -> {
            val cx = px(0.5)
            val cy = py(0.58)
            val before = g.transform
            for (k in 0 until 6) {
                g.fill(Ellipse2D.Double(cx - 0.10 * s, cy - 0.50 * s, 0.20 * s, 0.42 * s))
                g.rotate(Math.toRadians(60.0), cx, cy)
            }
            g.transform = before
        }

        Glyph.SPLIT -> {
            // A block with a cut through it: the border that the split makes.
            g.draw(rr(px(0.10), py(0.30), 0.80 * s, 0.40 * s, 0.08 * s))
            line(0.5, 0.18, 0.5, 0.82)
        }

        Glyph.SWAP -> {
            // Two arrows pointing in opposite directions: the blocks trade places.
            line(0.10, 0.36, 0.76, 0.36)
            arrowHead(0.86, 0.36, 1.0, 0.0)
            line(0.90, 0.64, 0.24, 0.64)
            arrowHead(0.14, 0.64, -1.0, 0.0)
        }

        Glyph.MERGE -> {
            // Two lines that come together and become one arrow.
            line(0.06, 0.28, 0.30, 0.28)
            line(0.06, 0.72, 0.30, 0.72)
            line(0.30, 0.28, 0.50, 0.50)
            line(0.30, 0.72, 0.50, 0.50)
            line(0.50, 0.50, 0.78, 0.50)
            arrowHead(0.88, 0.50, 1.0, 0.0)
        }

        Glyph.UNDO -> {
            drawTurnArrow(g, x, y, s, false)
        }

        Glyph.REDO -> {
            drawTurnArrow(g, x, y, s, true)
        }

        Glyph.TAP -> {
            // The ripples of a tap: a dot in the middle and two rings around it.
            val cx = px(0.5)
            val cy = py(0.5)
            g.draw(Ellipse2D.Double(cx - 0.40 * s, cy - 0.40 * s, 0.80 * s, 0.80 * s))
            g.draw(Ellipse2D.Double(cx - 0.24 * s, cy - 0.24 * s, 0.48 * s, 0.48 * s))
            g.fill(Ellipse2D.Double(cx - 0.08 * s, cy - 0.08 * s, 0.16 * s, 0.16 * s))
        }

        Glyph.BEATS -> {
            drawBeats(g, x, y, s)
        }

        Glyph.RELATIVE -> {
            // A step: two dashes, the lower one on the left and the upper one on the right,
            // joined by a diagonal. The two dashes are the two notes, and the diagonal is the
            // step from the first to the second - the octave of the second is read from the
            // first.
            line(0.06, 0.72, 0.36, 0.72)
            line(0.36, 0.72, 0.64, 0.28)
            line(0.64, 0.28, 0.94, 0.28)
        }

        Glyph.KEY -> {
            // The sharp sign: two upright strokes and two strokes that lean.
            line(0.34, 0.20, 0.30, 0.80)
            line(0.60, 0.14, 0.56, 0.74)
            line(0.16, 0.44, 0.72, 0.32)
            line(0.14, 0.64, 0.70, 0.52)
        }
    }

    g.stroke = oldStroke
    g.color = oldColor
}

/**
 * Draws three beats: three dots at the corners of an open triangle.
 *
 * The dots are the beats of the group and the two lines that join them to the top one are the
 * group itself. The bottom is left open, so the shape is an upside-down V and not a closed
 * triangle - the group of three is not a bar of its own.
 *
 * @param g where to draw.
 * @param x the left edge of the square.
 * @param y the top edge of the square.
 * @param s the side of the square.
 */
private fun drawBeats(
    g: Graphics2D,
    x: Double,
    y: Double,
    s: Double,
) {
    val r = 0.12 * s
    val halfBase = 0.31 * s
    val height = halfBase * Math.sqrt(3.0) // an equilateral triangle
    val middleX = x + 0.5 * s
    val topY = y + 0.5 * s - height / 2
    val bottomY = y + 0.5 * s + height / 2
    val leftX = middleX - halfBase
    val rightX = middleX + halfBase
    g.stroke = BasicStroke((s * 0.07).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(middleX, topY, leftX, bottomY))
    g.draw(Line2D.Double(middleX, topY, rightX, bottomY))
    for (p in listOf(middleX to topY, leftX to bottomY, rightX to bottomY)) {
        g.fill(Ellipse2D.Double(p.first - r, p.second - r, 2 * r, 2 * r))
    }
}

/**
 * Draws the reset arrow: an open circle with a clockwise arrow head at the top right.
 *
 * The arc runs counter-clockwise from the top right around the bottom to the bottom right,
 * leaving a gap at the top right where the arrow head points clockwise. The stroke is thinner
 * and the head larger than the other glyphs have, so the arrow stays legible at the small
 * sizes the icon buttons use.
 *
 * @param g where to draw.
 * @param x the left edge of the square.
 * @param y the top edge of the square.
 * @param s the side of the square.
 */
private fun drawResetArrow(
    g: Graphics2D,
    x: Double,
    y: Double,
    s: Double,
) {
    val old = g.stroke
    g.stroke = BasicStroke((s * 0.09).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val cx = x + 0.5 * s
    val cy = y + 0.5 * s
    val r = 0.28 * s
    g.draw(Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 45.0, 270.0, Arc2D.OPEN))

    val a = Math.toRadians(45.0)
    val tipX = cx + r * Math.cos(a)
    val tipY = cy - r * Math.sin(a)
    // The clockwise tangent at the top right points down and to the right.
    val dx = Math.sin(a)
    val dy = Math.cos(a)
    val nx = -dy
    val ny = dx
    val headLen = 0.22 * s
    val headHalf = 0.15 * s
    val ax = tipX + dx * headLen
    val ay = tipY + dy * headLen
    val bx = tipX - dx * 0.02 * s
    val by = tipY - dy * 0.02 * s
    val head = Path2D.Double()
    head.moveTo(ax, ay)
    head.lineTo(bx + nx * headHalf, by + ny * headHalf)
    head.lineTo(bx - nx * headHalf, by - ny * headHalf)
    head.closePath()
    g.fill(head)
    g.stroke = old
}

/**
 * Draws the arrow that turns back (the undo) or forward (the redo).
 *
 * The arrow curves over the top of the square and comes down at one end: to the left for the
 * undo, to the right for the redo.
 *
 * @param g where to draw.
 * @param x the left edge of the square.
 * @param y the top edge of the square.
 * @param s the side of the square.
 * @param right true for the redo, false for the undo.
 */
private fun drawTurnArrow(
    g: Graphics2D,
    x: Double,
    y: Double,
    s: Double,
    right: Boolean,
) {
    val start = if (right) 180.0 else 0.0
    val extent = if (right) -180.0 else 180.0
    g.draw(Arc2D.Double(x + 0.18 * s, y + 0.12 * s, 0.64 * s, 0.76 * s, start, extent, Arc2D.OPEN))

    // The head at the end of the arc, pointing down and away from the middle.
    val dx = if (right) 0.3 else -0.3
    val len = Math.hypot(dx, 1.0)
    val ux = dx / len
    val uy = 1.0 / len
    val tipX = x + (if (right) 0.82 else 0.18) * s
    val tipY = y + 0.50 * s
    val baseX = tipX - ux * 0.20 * s
    val baseY = tipY - uy * 0.20 * s
    val half = 0.13 * s
    val head = Path2D.Double()
    head.moveTo(tipX, tipY)
    head.lineTo(baseX - uy * half, baseY + ux * half)
    head.lineTo(baseX + uy * half, baseY - ux * half)
    head.closePath()
    g.fill(head)
}

internal fun drawLogo(
    g: Graphics2D,
    x: Double,
    y: Double,
    s: Double,
) {
    g.paint = GradientPaint(x.toFloat(), y.toFloat(), Theme.accent, (x + s).toFloat(), (y + s).toFloat(), Theme.accent2)
    g.fill(rr(x, y, s, s, s * 0.28))
    drawGlyph(g, Glyph.LILY, x + s * 0.14, y + s * 0.12, s * 0.72, Color.WHITE)
}

internal fun logoImage(size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.smooth()
    drawLogo(g, 0.0, 0.0, size.toDouble())
    g.dispose()
    return img
}
