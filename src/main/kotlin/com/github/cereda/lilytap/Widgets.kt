// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * Small custom-painted widget kit for the window version. Everything is drawn with Java2D so the
 * look is identical on every OS and needs no extra dependencies or icon files.
 */

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.plaf.basic.BasicScrollBarUI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Containers and labels
// ---------------------------------------------------------------------------------------------

/** A rounded panel with a hairline border. Set `layout` and `border` after constructing. */
internal open class Card(
    private val fillOf: () -> Color = { Theme.card },
    private val radius: Double = 16.0,
) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            g2.color = fillOf()
            g2.fill(rr(0.0, 0.0, width.toDouble(), height.toDouble(), radius))
            g2.color = Theme.line
            g2.draw(rr(0.5, 0.5, width - 1.0, height - 1.0, radius))
        }
    }
}

internal class GlyphLabel(
    private val glyph: Glyph?,
    var text: String,
    private val fontSize: Float = 12f,
    private val tintOf: () -> Color = { Theme.dim },
    fontStyle: Int = Font.PLAIN,
) : JComponent() {
    init {
        font = Theme.font(fontSize, fontStyle)
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val iconW = if (glyph != null) 19 else 0
        return Dimension(iconW + fm.stringWidth(text) + 2, maxOf(fm.height, 16))
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            g2.font = font
            val fm = g2.fontMetrics
            val tint = tintOf()
            var tx = 0
            if (glyph != null) {
                drawGlyph(g2, glyph, 0.0, (height - 14) / 2.0, 14.0, tint)
                tx = 19
            }
            g2.color = tint
            g2.drawString(text, tx, (height - fm.height) / 2 + fm.ascent)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons and inputs
// ---------------------------------------------------------------------------------------------

internal enum class ButtonKind { PRIMARY, DANGER, GHOST }

internal class PillButton(
    text: String,
    glyph: Glyph?,
    kind: ButtonKind = ButtonKind.GHOST,
    private val minW: Int = 0,
    private val heightPx: Int = 40,
) : JComponent() {
    var text: String = text
        set(v) {
            field = v
            revalidate()
            repaint()
        }
    var glyph: Glyph? = glyph
        set(v) {
            field = v
            revalidate()
            repaint()
        }
    var kind: ButtonKind = kind
        set(v) {
            field = v
            repaint()
        }

    /** When set, a ghost-style button is drawn in this colour (used for the "Copied" flash). */
    var accentOverride: Color? = null
        set(v) {
            field = v
            repaint()
        }
    var onClick: (() -> Unit)? = null

    private var hover = false
    private var pressed = false

    init {
        isOpaque = false
        isFocusable = false
        font = Theme.font(14f, Font.BOLD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hover = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hover = false
                    pressed = false
                    repaint()
                }

                override fun mousePressed(e: MouseEvent) {
                    if (isEnabled) {
                        pressed = true
                        repaint()
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    val wasPressed = pressed
                    pressed = false
                    repaint()
                    if (wasPressed && isEnabled && contains(e.point)) onClick?.invoke()
                }
            },
        )
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        // An empty text makes the button icon-only: a small round-ish pill with just the glyph.
        if (text.isEmpty()) return Dimension(maxOf(minW, 40), heightPx)
        var w = 40 + fm.stringWidth(text)
        if (glyph != null) w += 23
        return Dimension(maxOf(w, minW), heightPx)
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            if (pressed) g2.translate(0.0, 1.0)
            val w = width.toDouble()
            val h = height.toDouble()
            val shape = rr(0.5, 0.5, w - 1, h - 1, (h - 1) / 2)
            val tintOverride = accentOverride
            val fg: Color

            if (tintOverride == null && kind != ButtonKind.GHOST) {
                val lift =
                    if (pressed) {
                        -0.10
                    } else if (hover && isEnabled) {
                        0.14
                    } else {
                        0.0
                    }
                val primary = kind == ButtonKind.PRIMARY
                val c1 = (if (primary) Theme.accent else Theme.rec).lift(lift)
                val c2 = (if (primary) Theme.accent2 else Theme.rec2).lift(lift)
                g2.paint = GradientPaint(0f, 0f, c1, w.toFloat(), 0f, c2)
                g2.fill(shape)
                fg = Theme.onAccent
            } else {
                g2.color = if (hover && isEnabled) Theme.fieldHover else Theme.field
                g2.fill(shape)
                g2.color = tintOverride?.alpha(150) ?: Theme.line
                g2.draw(shape)
                fg = tintOverride ?: Theme.text
            }

            g2.font = font
            val fm = g2.fontMetrics
            val gl = glyph
            val tw = fm.stringWidth(text)
            val gw = if (gl != null) 23 else 0
            // An icon-only button centres its glyph; otherwise the glyph sits before the text.
            val startX = if (tw == 0 && gl != null) (w - 16.0) / 2 else (w - (gw + tw)) / 2
            if (gl != null) drawGlyph(g2, gl, startX, (h - 16) / 2, 16.0, fg)
            g2.color = fg
            if (tw > 0) {
                g2.drawString(text, (startX + gw).toFloat(), ((h - fm.height) / 2 + fm.ascent).toFloat())
            }
        }
    }
}

/** "- 60 +" control. Click the ends (hold to repeat), scroll the wheel, or drag the middle. */
internal class Stepper(
    initial: Double,
    private val minValue: Double,
    private val maxValue: Double,
    private val stepSize: Double,
    private val format: (Double) -> String,
) : JComponent() {
    var value: Double = initial.coerceIn(minValue, maxValue)
        set(v) {
            val n = v.coerceIn(minValue, maxValue)
            if (n != field) {
                field = n
                repaint()
            }
        }

    private val btn = 34.0
    private var hoverZone: Int? = null
    private var repeatDir = 0
    private var dragging = false
    private var dragStartX = 0
    private var dragStartValue = 0.0
    private val repeater = Timer(70) { bump(repeatDir) }.apply { initialDelay = 380 }

    init {
        isOpaque = false
        isFocusable = false
        font = Theme.font(15f, Font.BOLD)

        val mouse =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (!isEnabled) return
                    val z = zone(e.x)
                    if (z != 0) {
                        repeatDir = z
                        bump(z)
                        repeater.restart()
                    } else {
                        dragging = true
                        dragStartX = e.x
                        dragStartValue = value
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    repeater.stop()
                    dragging = false
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (dragging && isEnabled) {
                        val ticks = (e.x - dragStartX) / 6
                        value = dragStartValue + ticks * stepSize
                    }
                }

                override fun mouseMoved(e: MouseEvent) {
                    hoverZone = zone(e.x)
                    cursor =
                        Cursor.getPredefinedCursor(
                            if (hoverZone == 0) Cursor.E_RESIZE_CURSOR else Cursor.HAND_CURSOR,
                        )
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hoverZone = null
                    repaint()
                }
            }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener { e -> if (isEnabled) value = value - e.wheelRotation * stepSize }
    }

    private fun zone(x: Int): Int =
        when {
            x < btn -> -1
            x > width - btn -> 1
            else -> 0
        }

    private fun bump(dir: Int) {
        value = value + dir * stepSize
    }

    override fun getPreferredSize(): Dimension = Dimension(150, 38)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val w = width.toDouble()
            val h = height.toDouble()
            val shape = rr(0.5, 0.5, w - 1, h - 1, 10.0)
            g2.color = Theme.field
            g2.fill(shape)

            val hz = hoverZone
            if (isEnabled && (hz == -1 || hz == 1)) {
                g2.clip = shape
                g2.color = Theme.fieldHover
                g2.fill(if (hz == -1) Rectangle2D.Double(0.0, 0.0, btn, h) else Rectangle2D.Double(w - btn, 0.0, btn, h))
                g2.clip = null
            }
            g2.color = Theme.line
            g2.draw(shape)

            drawGlyph(g2, Glyph.MINUS, btn / 2 - 7, h / 2 - 7, 14.0, if (hz == -1) Theme.text else Theme.dim)
            drawGlyph(g2, Glyph.PLUS, w - btn / 2 - 7, h / 2 - 7, 14.0, if (hz == 1) Theme.text else Theme.dim)

            g2.font = font
            g2.color = Theme.text
            val fm = g2.fontMetrics
            val s = format(value)
            g2.drawString(s, ((w - fm.stringWidth(s)) / 2).toFloat(), ((h - fm.height) / 2 + fm.ascent).toFloat())
        }
    }
}

/** Pill-shaped option picker with a sliding highlight. */
internal class SegmentedControl(
    private val options: List<String>,
    selected: Int = 0,
) : JComponent() {
    var selectedIndex: Int = selected
        set(v) {
            if (v in options.indices && v != field) {
                field = v
                anim.start()
                repaint()
            }
        }

    val selectedText: String get() = options[selectedIndex]

    private var pillX = -1.0
    private val anim = Timer(14) { tick() }

    init {
        isOpaque = false
        isFocusable = false
        font = Theme.font(13f, Font.BOLD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (!isEnabled) return
                    selectedIndex = ((e.x - 3) / segW()).toInt().coerceIn(0, options.size - 1)
                }
            },
        )
    }

    private fun segW(): Double {
        val fm = getFontMetrics(font)
        return (options.maxOf { fm.stringWidth(it) } + 28).toDouble()
    }

    private fun target() = 3 + selectedIndex * segW()

    private fun tick() {
        val t = target()
        if (pillX < 0 || abs(t - pillX) < 0.5) {
            pillX = t
            anim.stop()
        } else {
            pillX += (t - pillX) * 0.32
        }
        repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension((segW() * options.size + 6).toInt(), 38)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val w = width.toDouble()
            val h = height.toDouble()
            val sw = segW()
            if (pillX < 0) pillX = target()

            val track = rr(0.5, 0.5, w - 1, h - 1, 10.0)
            g2.color = Theme.field
            g2.fill(track)
            g2.color = Theme.line
            g2.draw(track)

            g2.paint = GradientPaint(pillX.toFloat(), 0f, Theme.accent, (pillX + sw).toFloat(), 0f, Theme.accent.mix(Theme.accent2, 0.55))
            g2.fill(rr(pillX, 3.0, sw, h - 6, 8.0))

            g2.font = font
            val fm = g2.fontMetrics
            options.forEachIndexed { i, label ->
                g2.color = if (i == selectedIndex) Theme.onAccent else Theme.dim
                val tx = 3 + i * sw + (sw - fm.stringWidth(label)) / 2
                g2.drawString(label, tx.toFloat(), ((h - fm.height) / 2 + fm.ascent).toFloat())
            }
        }
    }
}

/**
 * A toggle box: a rounded square that fills with the accent and shows a check when selected.
 */
internal class CheckBox : JComponent() {
    var selected: Boolean = false
        set(v) {
            if (v != field) {
                field = v
                onChange?.invoke(v)
                repaint()
            }
        }

    /** Called whenever the selection changes. */
    var onChange: ((Boolean) -> Unit)? = null

    init {
        isOpaque = false
        isFocusable = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (isEnabled) selected = !selected
                }
            },
        )
    }

    override fun getPreferredSize(): Dimension = Dimension(38, 38)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val side = 18.0
            val x = (width - side) / 2.0
            val y = (height - side) / 2.0
            val box = rr(x, y, side, side, 5.0)
            if (selected) {
                g2.paint =
                    GradientPaint(
                        x.toFloat(),
                        y.toFloat(),
                        Theme.accent,
                        (x + side).toFloat(),
                        y.toFloat(),
                        Theme.accent2,
                    )
                g2.fill(box)
                drawGlyph(g2, Glyph.CHECK, x + 3, y + 3, side - 6, Theme.onAccent)
            } else {
                g2.color = Theme.field
                g2.fill(box)
                g2.color = Theme.line
                g2.stroke = BasicStroke(1.2f)
                g2.draw(box)
            }
        }
    }
}

internal class StyledField(
    initial: String,
    columns: Int,
) : JTextField(initial, columns) {
    private var focusedNow = false

    init {
        isOpaque = false
        font = Theme.font(15f, Font.BOLD)
        refreshTheme()
        border = BorderFactory.createEmptyBorder(0, 12, 0, 12)
        horizontalAlignment = JTextField.CENTER
        addFocusListener(
            object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    focusedNow = true
                    repaint()
                }

                override fun focusLost(e: FocusEvent) {
                    focusedNow = false
                    repaint()
                }
            },
        )
    }

    /** Re-reads the palette (call after switching theme). */
    fun refreshTheme() {
        foreground = Theme.text
        caretColor = Theme.accent
        disabledTextColor = Theme.faint
        selectionColor = Theme.accent.alpha(120)
        selectedTextColor = Theme.text
        repaint()
    }

    override fun getPreferredSize(): Dimension = Dimension(super.getPreferredSize().width, 38)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val shape = rr(0.5, 0.5, width - 1.0, height - 1.0, 10.0)
            g2.color = Theme.field
            g2.fill(shape)
            g2.stroke = BasicStroke(if (focusedNow) 1.6f else 1f)
            g2.color = if (focusedNow) Theme.accent else Theme.line
            g2.draw(shape)
        }
        super.paintComponent(g)
    }
}

/** Round icon-only button. */
internal class IconButton(
    glyph: Glyph,
    private val diameter: Int = 40,
    private val glyphPx: Int = 18,
) : JComponent() {
    var glyph: Glyph = glyph
        set(v) {
            field = v
            repaint()
        }
    var onClick: (() -> Unit)? = null

    private var hover = false
    private var pressed = false

    init {
        isOpaque = false
        isFocusable = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hover = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hover = false
                    pressed = false
                    repaint()
                }

                override fun mousePressed(e: MouseEvent) {
                    if (!isEnabled) return
                    pressed = true
                    repaint()
                }

                override fun mouseReleased(e: MouseEvent) {
                    val was = pressed
                    pressed = false
                    repaint()
                    if (was && isEnabled && contains(e.point)) onClick?.invoke()
                }
            },
        )
    }

    override fun getPreferredSize(): Dimension = Dimension(diameter, diameter)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            if (!isEnabled) g2.composite = AlphaComposite.SrcOver.derive(0.4f)
            val d = minOf(width, height) - 1.0
            g2.color = if (hover && isEnabled) Theme.fieldHover else Theme.field
            g2.fill(Ellipse2D.Double(0.5, 0.5, d, d))
            g2.color = Theme.line
            g2.draw(Ellipse2D.Double(0.5, 0.5, d, d))
            val gs = glyphPx.toDouble()
            drawGlyph(g2, glyph, (width - gs) / 2, (height - gs) / 2 + if (pressed) 1 else 0, gs, if (hover) Theme.text else Theme.dim)
        }
    }
}
// ---------------------------------------------------------------------------------------------
// Status widgets
// ---------------------------------------------------------------------------------------------

internal enum class Mood { IDLE, COUNT_IN, REC, DONE, STOPPED }

/** Coloured dot (pulsing while recording) + a line of status text. */
internal class StatusBadge : JComponent() {
    var mood: Mood = Mood.IDLE
        set(v) {
            field = v
            repaint()
        }
    var text: String = ""
        set(v) {
            field = v
            revalidate()
            repaint()
        }

    init {
        isOpaque = false
        font = Theme.font(16f, Font.BOLD)
    }

    override fun getPreferredSize(): Dimension = Dimension(32 + getFontMetrics(font).stringWidth(text), 34)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val cy = height / 2.0
            val cx = 14.0
            val c =
                when (mood) {
                    Mood.IDLE -> Theme.faint
                    Mood.COUNT_IN -> Theme.warn
                    Mood.REC -> Theme.rec
                    Mood.DONE -> Theme.ok
                    Mood.STOPPED -> Theme.dim
                }
            if (mood == Mood.REC || mood == Mood.COUNT_IN) {
                val p = (sin(System.nanoTime() / 1.6e8) + 1) / 2
                val r = 5 + 6 * p
                g2.color = c.alpha((90 * (1 - p) + 20).toInt())
                g2.fill(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
            }
            g2.color = c
            g2.fill(Ellipse2D.Double(cx - 5, cy - 5, 10.0, 10.0))

            g2.font = font
            g2.color = Theme.text
            val fm = g2.fontMetrics
            g2.drawString(text, 32, ((height - fm.height) / 2 + fm.ascent))
        }
    }
}

/** One dot per beat in the bar; the current beat lights up. */
internal class BeatDots : JComponent() {
    var beats: Int = 4
        set(v) {
            field = v
            revalidate()
            repaint()
        }
    var active: Int = -1
        set(v) {
            field = v
            repaint()
        }
    var mood: Mood = Mood.IDLE
        set(v) {
            field = v
            repaint()
        }

    private val pitchPx = 24.0

    init {
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension = Dimension((beats * pitchPx).toInt() + 4, 34)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val x0 = width - beats * pitchPx
            val cy = height / 2.0
            val lit = if (mood == Mood.COUNT_IN) Theme.warn else Theme.rec
            for (i in 0 until beats) {
                val cx = x0 + i * pitchPx + pitchPx / 2
                val r = if (i == 0) 7.0 else 5.5
                if (i == active) {
                    g2.color = lit.alpha(45)
                    g2.fill(Ellipse2D.Double(cx - r - 6, cy - r - 6, 2 * (r + 6), 2 * (r + 6)))
                    g2.color = lit.alpha(110)
                    g2.fill(Ellipse2D.Double(cx - r - 3, cy - r - 3, 2 * (r + 3), 2 * (r + 3)))
                    g2.color = lit
                    g2.fill(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
                } else {
                    g2.color = Theme.field
                    g2.fill(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
                    g2.stroke = BasicStroke(1.5f)
                    g2.color = if (i == 0) Theme.accent.alpha(170) else Theme.faint.alpha(120)
                    g2.draw(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
                }
            }
        }
    }
}

/** Logo, wordmark, tagline and an "Esc" hint. */
internal class HeaderBar : JComponent() {
    init {
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension = Dimension(760, 62)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val logo = 46.0
            val y0 = (height - logo) / 2
            drawLogo(g2, 0.0, y0, logo)

            g2.font = Theme.font(26f, Font.BOLD)
            val fm = g2.fontMetrics
            val tx = logo + 14
            val baseline = y0 + fm.ascent - 3
            g2.color = Theme.text
            g2.drawString("lily", tx.toFloat(), baseline.toFloat())
            g2.color = Theme.accent2
            g2.drawString("tap", (tx + fm.stringWidth("lily")).toFloat(), baseline.toFloat())

            g2.font = Theme.font(13f)
            g2.color = Theme.dim
            g2.drawString("Tap a rhythm along to a metronome, get LilyPond.", tx.toFloat(), (baseline + 21).toFloat())

            // Esc hint, right-aligned
            val label = "aborts the take"
            g2.font = Theme.font(12f)
            val labelW = g2.fontMetrics.stringWidth(label)
            val labelX = width - labelW.toDouble()
            val cy = height / 2.0
            g2.color = Theme.dim
            g2.drawString(label, labelX.toFloat(), (cy + g2.fontMetrics.ascent / 2 - 1).toFloat())

            g2.font = Theme.mono(12f)
            val capW = g2.fontMetrics.stringWidth("Esc") + 16.0
            val capX = labelX - 8 - capW
            g2.color = Theme.field
            g2.fill(rr(capX, cy - 11, capW, 22.0, 6.0))
            g2.color = Theme.line
            g2.draw(rr(capX + 0.5, cy - 10.5, capW - 1, 21.0, 6.0))
            g2.color = Theme.text
            g2.drawString("Esc", (capX + 8).toFloat(), (cy + g2.fontMetrics.ascent / 2 - 1).toFloat())
        }
    }
}
// ---------------------------------------------------------------------------------------------
// Scroll bar
// ---------------------------------------------------------------------------------------------

internal class ThinScrollBar : BasicScrollBarUI() {
    private fun zeroButton(): JButton =
        JButton().apply {
            val none = Dimension(0, 0)
            preferredSize = none
            minimumSize = none
            maximumSize = none
        }

    override fun createDecreaseButton(orientation: Int): JButton = zeroButton()

    override fun createIncreaseButton(orientation: Int): JButton = zeroButton()

    override fun paintTrack(
        g: Graphics,
        c: JComponent,
        trackBounds: Rectangle,
    ) {
        // transparent track
    }

    override fun paintThumb(
        g: Graphics,
        c: JComponent,
        thumbBounds: Rectangle,
    ) {
        if (thumbBounds.isEmpty) return
        g.paint2d { g2 ->
            g2.color = Theme.faint.alpha(if (isThumbRollover) 210 else 140)
            val t = thumbBounds
            g2.fill(rr(t.x + 2.0, t.y + 2.0, t.width - 4.0, t.height - 4.0, minOf(t.width, t.height) / 2.0 - 2))
        }
    }
}

/** Text area with a ghost hint while empty. */
internal class CodeArea : JTextArea(4, 40) {
    init {
        font = Theme.mono(14f)
        margin = Insets(10, 12, 10, 12)
        refreshTheme()
    }

    /** Re-reads the palette (call after switching theme). */
    fun refreshTheme() {
        background = Theme.code
        foreground = Theme.text
        caretColor = Theme.accent
        selectionColor = Theme.accent.alpha(110)
        selectedTextColor = Theme.text
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (text.isEmpty()) {
            g.paint2d { g2 ->
                g2.font = font
                g2.color = Theme.faint
                g2.drawString("% your transcription will appear here", margin.left, margin.top + g2.fontMetrics.ascent)
            }
        }
    }
}
