// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The circle of fifths: the keys of the piece around a ring, with their relative minors.
 */

import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import javax.swing.JComponent
import javax.swing.JPopupMenu
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The button that opens the circle of fifths.
 *
 * @param circle the circle the button opens.
 * @param onPick called with the key the user picks.
 */
internal class CircleButton(
    private val circle: CircleOfFifths,
    private val onPick: (KeySignature) -> Unit,
) : PopupButton() {
    /**
     * Builds the circle of the button.
     *
     * The circle hangs under the button, with its right edge on the button's right edge: the
     * button sits at the right end of the key cell, and a circle that starts there would
     * fall off the edge of the window.
     */
    override fun buildPopup(): JPopupMenu {
        val made = menuPopup()
        circle.onPick = { key ->
            made.isVisible = false
            onPick(key)
        }
        made.add(circle)
        return made
    }
}

/**
 * The circle of fifths: every key of the piece around a ring, with its relative minor.
 *
 * The ring is the circle of fifths itself: the keys stand on it the way they do in a circle
 * of fifths chart, C major at the top, the sharps climbing to the right and the flats coming
 * back on the left. Outside the ring stands the major key of the position, inside it the minor
 * key that has the same signature - the relative minor.
 *
 * The outer half of a position picks the major key and the inner half the minor one, the part
 * under the mouse is lit, and the middle of the circle says which key the piece is in.
 *
 * The ring holds twenty-one positions: the keys with ten accidentals on either side. The
 * keys past them are named in the field beside the circle, and the circle marks the key of
 * the piece whenever it stands on it.
 *
 * @param pick called with the key the user picks.
 */
internal class CircleOfFifths(
    pick: (KeySignature) -> Unit,
) : JComponent() {
    /** Called with the key the user picks. */
    var onPick: (KeySignature) -> Unit = pick

    /** The key the piece is in: the circle marks it. */
    var key: KeySignature = KeySignature.C_MAJOR
        set(value) {
            field = value
            repaint()
        }

    /** The position under the mouse, or -1. */
    private var hover = -1

    /** Whether the mouse is on the inner half of the position. */
    private var inner = false

    init {
        isOpaque = false
        // The circle is a picker, not a field: it never takes the keyboard away from the
        // field of the key.
        isFocusable = false
        font = Theme.font(12f)
        toolTipText = TOOLTIP
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
                    val found = positionAt(e.x, e.y)
                    val index = found?.first ?: -1
                    val half = found?.second ?: false
                    if (index == hover && half == inner) return
                    hover = index
                    inner = half
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hover = -1
                    repaint()
                }
            },
        )
    }

    /**
     * The key under a point of the circle.
     *
     * The middle of the circle is the key the piece is in: clicking it keeps the key.
     *
     * @param x the place of the point.
     * @param y the place of the point.
     * @return the key under the point, or null when the point is not on one.
     */
    fun pickAt(
        x: Int,
        y: Int,
    ): KeySignature? {
        val dx = x - CENTER
        val dy = y - CENTER
        val distance = hypot(dx.toDouble(), dy.toDouble())
        if (distance > R_OUTER) return null
        if (distance < R_HOLE) return key
        val (index, half) = positionAt(x, y) ?: return null
        val (major, minor) = KEYS[index]
        return if (half) minor else major
    }

    /**
     * The position under a point of the circle.
     *
     * @param x the place of the point.
     * @param y the place of the point.
     * @return the index of the position and whether the point is on its inner half, or null
     * when the point is not on the circle.
     */
    private fun positionAt(
        x: Int,
        y: Int,
    ): Pair<Int, Boolean>? {
        val dx = x - CENTER
        val dy = y - CENTER
        val distance = hypot(dx.toDouble(), dy.toDouble())
        if (distance > R_OUTER || distance < R_HOLE) return null
        val index = indexAt(dx, dy)
        return index to (distance <= R_RING)
    }

    /**
     * The position a point belongs to.
     *
     * @param dx the place of the point, from the centre.
     * @param dy the place of the point, from the centre.
     * @return the index of the position.
     */
    private fun indexAt(
        dx: Int,
        dy: Int,
    ): Int {
        // The angle of the point in the way Java2D counts them: zero at three o'clock, and
        // growing clockwise because the y axis points down.
        val angle = atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI
        return ((angle + 90.0) / STEP).roundToInt().mod(KEYS.size)
    }

    /** The key under the mouse, or null when the mouse is not on one. */
    private fun hovered(): KeySignature? {
        if (hover < 0) return null
        val (major, minor) = KEYS[hover]
        return if (inner) minor else major
    }

    /**
     * The labels of the circle: the major key of every position and its relative minor.
     *
     * @return the label of every position, outside the ring and inside it.
     */
    fun labels(): List<Pair<String, String>> = KEYS.map { (major, minor) -> major.tonicDisplay to (minor.tonicDisplay + MINOR_MARK) }

    override fun getPreferredSize(): Dimension = Dimension(SIZE, SIZE)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val fm = g2.fontMetrics
            // The panel of the circle: the border the other widgets have.
            g2.color = Theme.field
            g2.fill(rr(0.0, 0.0, width.toDouble(), height.toDouble(), 10.0))
            g2.color = Theme.line
            g2.draw(rr(1.0, 1.0, width - 2.0, height - 2.0, 10.0))
            // The part under the mouse, so the user sees what a click picks.
            if (hover >= 0) {
                g2.color = Theme.fieldHover
                g2.fill(
                    if (inner) {
                        sector(hover, R_HOLE, R_RING)
                    } else {
                        sector(hover, R_RING, R_OUTER)
                    },
                )
            }
            // The ring: the circle of fifths itself.
            g2.color = Theme.line
            g2.stroke = BasicStroke(1.0f)
            g2.draw(Ellipse2D.Double(CENTER - R_RING, CENTER - R_RING, 2 * R_RING, 2 * R_RING))
            KEYS.forEachIndexed { index, keys ->
                val major = keys.first
                val minor = keys.second
                val here = hover == index
                // The major key, outside the ring.
                g2.font = Theme.font(MAJOR_FONT, Font.BOLD)
                g2.color =
                    when {
                        key.major && key == major -> Theme.accent
                        here && !inner -> Theme.text
                        else -> Theme.dim
                    }
                val title = major.tonicDisplay
                drawAt(g2, fm, index, R_MAJOR, title)
                // The minor key, inside the ring.
                g2.font = Theme.font(MINOR_FONT)
                g2.color =
                    when {
                        !key.major && key == minor -> Theme.accent
                        here && inner -> Theme.text
                        else -> Theme.dim
                    }
                val name = minor.tonicDisplay + MINOR_MARK
                drawAt(g2, fm, index, R_MINOR, name)
            }
            // The middle of the circle: the key of the piece, or the one under the mouse.
            val shown = hovered() ?: key
            g2.font = Theme.font(16f, Font.BOLD)
            g2.color = if (shown == key) Theme.accent else Theme.text
            drawCentered(g2, fm, shown.name, CENTER - 6)
            val signature = signature(shown)
            if (signature.isNotEmpty()) {
                g2.font = Theme.font(14f)
                g2.color = Theme.dim
                drawCentered(g2, fm, signature, CENTER + 16)
            }
        }
    }

    /**
     * The signature of a key: the count of its sharps or its flats.
     *
     * @param key the key.
     * @return the signature, as it is written under the name of the key.
     */
    private fun signature(key: KeySignature): String {
        val count = kotlin.math.abs(key.fifths)
        if (count == 0) return ""
        val sign = if (key.fifths > 0) "♯" else "♭"
        return "$count$sign"
    }

    /**
     * Draws a label on the circle.
     *
     * @param g2 the graphics to draw on.
     * @param fm the metrics of the font of the label.
     * @param index the position of the label.
     * @param radius the distance of the label from the centre.
     * @param text the text of the label.
     */
    private fun drawAt(
        g2: Graphics2D,
        fm: FontMetrics,
        index: Int,
        radius: Double,
        text: String,
    ) {
        val angle = angleOf(index)
        val x = CENTER + radius * cos(angle) - fm.stringWidth(text) / 2.0
        val y = CENTER + radius * sin(angle) + (fm.ascent - fm.descent) / 2.0
        g2.drawString(text, x.toFloat(), y.toFloat())
    }

    /**
     * Draws a label in the middle of the circle.
     *
     * @param g2 the graphics to draw on.
     * @param fm the metrics of the font of the label.
     * @param text the text of the label.
     * @param y the place of the middle of the label.
     */
    private fun drawCentered(
        g2: Graphics2D,
        fm: FontMetrics,
        text: String,
        y: Int,
    ) {
        val x = CENTER - fm.stringWidth(text) / 2.0
        val baseline = y + (fm.ascent - fm.descent) / 2.0
        g2.drawString(text, x.toFloat(), baseline.toFloat())
    }

    /**
     * The wedge of a position: the part of the circle that a click picks.
     *
     * The angles of the shape are the ones Java2D counts, which run the other way round from
     * the angles of the circle: the wedge of a position is drawn with the start of the arc
     * where the position ends.
     *
     * @param index the index of the position.
     * @param inner the radius of the hole of the wedge.
     * @param outer the radius of the wedge.
     * @return the shape of the wedge.
     */
    fun sector(
        index: Int,
        inner: Double,
        outer: Double,
    ): Area {
        val start = -(Math.toDegrees(angleOf(index)) + STEP / 2) + 1
        val hole = Area(Arc2D.Double(CENTER - inner, CENTER - inner, 2 * inner, 2 * inner, start, STEP - 2, Arc2D.PIE))
        val whole = Arc2D.Double(CENTER - outer, CENTER - outer, 2 * outer, 2 * outer, start, STEP - 2, Arc2D.PIE)
        return Area(whole).apply { subtract(hole) }
    }

    /**
     * The angle of a position, in the way Java2D counts them.
     *
     * @param index the index of the position.
     * @return the angle in radians.
     */
    private fun angleOf(index: Int): Double = Math.toRadians(-90.0 + index * STEP)

    companion object {
        /** The size of the circle, in pixels. */
        const val SIZE = 420

        /** The centre of the circle, in pixels. */
        private const val CENTER = SIZE / 2

        /** The radius of the ring: the circle of fifths itself. */
        const val R_RING = 142.0

        /** The radius of the labels of the major keys, outside the ring. */
        const val R_MAJOR = 169.0

        /** The radius of the labels of the minor keys, inside the ring. */
        const val R_MINOR = 121.0

        /** The radius of the hole in the middle: it keeps the key of the piece. */
        const val R_HOLE = 100.0

        /** The radius of the outer edge of the circle: nothing is picked beyond it. */
        const val R_OUTER = 196.0

        /** The angle between two positions, in degrees. */
        const val STEP = 360.0 / 21

        /** The size of the letters of the major keys. */
        const val MAJOR_FONT = 17f

        /** The size of the letters of the minor keys. */
        const val MINOR_FONT = 13f

        /** The mark that says that a key is a minor one, as in a circle of fifths chart. */
        private const val MINOR_MARK = "m"

        /**
         * The keys of the circle, in the order they stand on it.
         *
         * C major is at the top, the sharps climb to the right and the flats come back on the
         * left. Every position holds the major key and the minor key that has the same
         * signature - the relative minor.
         *
         * The ring holds twenty-one positions: the keys with ten accidentals on either side
         * of C major. The keys past them are named in the field beside the circle.
         */
        private val KEYS =
            listOf(
                KeySignature(PitchStep.C, 0, true) to KeySignature(PitchStep.A, 0, false),
                KeySignature(PitchStep.G, 0, true) to KeySignature(PitchStep.E, 0, false),
                KeySignature(PitchStep.D, 0, true) to KeySignature(PitchStep.B, 0, false),
                KeySignature(PitchStep.A, 0, true) to KeySignature(PitchStep.F, 1, false),
                KeySignature(PitchStep.E, 0, true) to KeySignature(PitchStep.C, 1, false),
                KeySignature(PitchStep.B, 0, true) to KeySignature(PitchStep.G, 1, false),
                KeySignature(PitchStep.F, 1, true) to KeySignature(PitchStep.D, 1, false),
                KeySignature(PitchStep.C, 1, true) to KeySignature(PitchStep.A, 1, false),
                KeySignature(PitchStep.G, 1, true) to KeySignature(PitchStep.E, 1, false),
                KeySignature(PitchStep.D, 1, true) to KeySignature(PitchStep.B, 1, false),
                KeySignature(PitchStep.A, 1, true) to KeySignature(PitchStep.F, 2, false),
                KeySignature(PitchStep.E, -2, true) to KeySignature(PitchStep.C, -1, false),
                KeySignature(PitchStep.B, -2, true) to KeySignature(PitchStep.G, -1, false),
                KeySignature(PitchStep.F, -1, true) to KeySignature(PitchStep.D, -1, false),
                KeySignature(PitchStep.C, -1, true) to KeySignature(PitchStep.A, -1, false),
                KeySignature(PitchStep.G, -1, true) to KeySignature(PitchStep.E, -1, false),
                KeySignature(PitchStep.D, -1, true) to KeySignature(PitchStep.B, -1, false),
                KeySignature(PitchStep.A, -1, true) to KeySignature(PitchStep.F, 0, false),
                KeySignature(PitchStep.E, -1, true) to KeySignature(PitchStep.C, 0, false),
                KeySignature(PitchStep.B, -1, true) to KeySignature(PitchStep.G, 0, false),
                KeySignature(PitchStep.F, 0, true) to KeySignature(PitchStep.D, 0, false),
            )

        /** What the circle says about itself. */
        private const val TOOLTIP =
            "The circle of fifths: the keys of the piece and the minor key that has the same " +
                "signature. Outside the ring are the major keys (C), inside the minor ones " +
                "(Am). Click the key you want. The keys past ten accidentals are named in the " +
                "field beside the circle."
    }
}
