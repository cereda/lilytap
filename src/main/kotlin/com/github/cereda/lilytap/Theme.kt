// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * Colours, fonts and the drawing helpers that every widget shares.
 */

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Theme + drawing helpers
// ---------------------------------------------------------------------------------------------

@Suppress("LongParameterList")
internal class Palette(
    val bg: Color,
    val card: Color,
    val field: Color,
    val fieldHover: Color,
    val line: Color,
    val code: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accent2: Color,
    val rec: Color,
    val rec2: Color,
    val ok: Color,
    val warn: Color,
    /** Text/icon colour used on top of the accent gradient. */
    val onAccent: Color,
)

internal object Theme {
    private val DARK =
        Palette(
            bg = Color(0x0F111A),
            card = Color(0x181B27),
            field = Color(0x222637),
            fieldHover = Color(0x2C3148),
            line = Color(0x2C3147),
            code = Color(0x11131C),
            text = Color(0xE9EBF5),
            dim = Color(0x8D93AB),
            faint = Color(0x5A607A),
            accent = Color(0x8F7CFF),
            accent2 = Color(0x3FD0C9),
            rec = Color(0xFF5577),
            rec2 = Color(0xFF8A5C),
            ok = Color(0x46E08F),
            warn = Color(0xFFB84D),
            onAccent = Color(0x0F111A),
        )
    private val LIGHT =
        Palette(
            bg = Color(0xF1F2F8),
            card = Color(0xFFFFFF),
            field = Color(0xEEF0F7),
            fieldHover = Color(0xE2E5F1),
            line = Color(0xD9DCEA),
            code = Color(0xF6F7FC),
            text = Color(0x1B1E2E),
            dim = Color(0x5E647C),
            faint = Color(0x9AA0B8),
            accent = Color(0x6B57EE),
            accent2 = Color(0x0DA79F),
            rec = Color(0xE0345C),
            rec2 = Color(0xF0703A),
            ok = Color(0x14A562),
            warn = Color(0xD98710),
            onAccent = Color(0xFFFFFF),
        )

    private var p = DARK

    val isDark: Boolean get() = p === DARK

    fun set(dark: Boolean) {
        p = if (dark) DARK else LIGHT
    }

    val bg: Color get() = p.bg
    val card: Color get() = p.card
    val field: Color get() = p.field
    val fieldHover: Color get() = p.fieldHover
    val line: Color get() = p.line
    val code: Color get() = p.code

    val text: Color get() = p.text
    val dim: Color get() = p.dim
    val faint: Color get() = p.faint

    val accent: Color get() = p.accent // lily violet
    val accent2: Color get() = p.accent2 // teal
    val rec: Color get() = p.rec
    val rec2: Color get() = p.rec2
    val ok: Color get() = p.ok
    val warn: Color get() = p.warn
    val onAccent: Color get() = p.onAccent

    fun font(
        size: Float,
        style: Int = Font.PLAIN,
    ): Font = Font("SansSerif", style, 12).deriveFont(size)

    fun mono(size: Float): Font = Font(Font.MONOSPACED, Font.PLAIN, 12).deriveFont(size)
}

internal fun Color.alpha(a: Int): Color = Color(red, green, blue, a.coerceIn(0, 255))

internal fun Color.mix(
    other: Color,
    t: Double,
): Color =
    Color(
        (red + (other.red - red) * t).roundToInt().coerceIn(0, 255),
        (green + (other.green - green) * t).roundToInt().coerceIn(0, 255),
        (blue + (other.blue - blue) * t).roundToInt().coerceIn(0, 255),
        alpha,
    )

/** Positive = lighter, negative = darker. */
internal fun Color.lift(t: Double): Color = if (t >= 0) mix(Color.WHITE, t) else mix(Color.BLACK, -t)

/** Rounded rectangle given a corner *radius*. */
internal fun rr(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    r: Double,
) = RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)

internal fun Graphics2D.smooth() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
}

/** Runs [block] on a throw-away, anti-aliased copy of the graphics context. */
internal fun Graphics.paint2d(block: (Graphics2D) -> Unit) {
    val g2 = create() as Graphics2D
    try {
        g2.smooth()
        block(g2)
    } finally {
        g2.dispose()
    }
}
