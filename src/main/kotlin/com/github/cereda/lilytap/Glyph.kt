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
            // Open circle with a clockwise arrow head at top-right: reset (↻).
            // The arc runs counter-clockwise from top-right around the bottom to bottom-right,
            // leaving a gap at top-right where the arrow head points clockwise. The stroke is
            // thinner and the head larger than the other glyphs so the arrow stays legible at
            // the small sizes the icon buttons use.
            val stroke = BasicStroke((s * 0.09).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val old = g.stroke
            g.stroke = stroke
            val r = 0.28 * s
            g.draw(Arc2D.Double(px(0.5) - r, py(0.5) - r, 2 * r, 2 * r, 45.0, 270.0, Arc2D.OPEN))

            val cx = px(0.5)
            val cy = py(0.5)
            val a = Math.toRadians(45.0)
            val tipX = cx + r * Math.cos(a)
            val tipY = cy - r * Math.sin(a)
            // clockwise tangent at top-right: down-right
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
    }

    g.stroke = oldStroke
    g.color = oldColor
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
