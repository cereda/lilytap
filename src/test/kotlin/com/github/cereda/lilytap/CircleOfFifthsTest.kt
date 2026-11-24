// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The circle of fifths: the keys of the piece around a ring. */
class CircleOfFifthsTest {
    @Test
    fun `the circle holds every key of the piece`() {
        val circle = CircleOfFifths {}
        val found = mutableSetOf<KeySignature>()
        for (index in 0 until 15) {
            val angle = Math.toRadians(-90.0 + index * 24.0)
            val major =
                circle.pickAt(
                    (CENTER + 120 * cos(angle)).toInt(),
                    (CENTER + 120 * sin(angle)).toInt(),
                )
            val minor =
                circle.pickAt(
                    (CENTER + 85 * cos(angle)).toInt(),
                    (CENTER + 85 * sin(angle)).toInt(),
                )
            assertNotNull(major, "the position $index has a major key")
            assertNotNull(minor, "the position $index has a minor key")
            assertEquals(true, major.major, "the outer half of the position is a major key")
            assertEquals(false, minor.major, "the inner half of the position is a minor key")
            found += major
            found += minor
        }
        assertEquals(30, found.size, "the circle holds every key of the piece")
    }

    @Test
    fun `the circle starts at C major and climbs to the right`() {
        val circle = CircleOfFifths {}
        assertEquals(KeySignature.C_MAJOR, circle.pickAt(CENTER, CENTER - 120), "C major is at the top")
        assertEquals(
            KeySignature(PitchStep.G, 0, true),
            circle.pickAt((CENTER + 120 * cos(Math.toRadians(-66.0))).toInt(), (CENTER + 120 * sin(Math.toRadians(-66.0))).toInt()),
            "G major is a step to the right",
        )
        assertEquals(
            KeySignature(PitchStep.F, 0, true),
            circle.pickAt((CENTER + 120 * cos(Math.toRadians(102.0))).toInt(), (CENTER + 120 * sin(Math.toRadians(102.0))).toInt()),
            "F major is a step to the left",
        )
    }

    @Test
    fun `the inner half of a position is the relative minor of the outer one`() {
        val circle = CircleOfFifths {}
        val major = KeySignature(PitchStep.B, -1, true)
        val minor = KeySignature(PitchStep.G, 0, false)
        // B flat major is the ninth position of the circle, at 126 degrees from the top.
        val angle = Math.toRadians(126.0)
        assertEquals(
            major,
            circle.pickAt((CENTER + 120 * cos(angle)).toInt(), (CENTER + 120 * sin(angle)).toInt()),
            "the outer half is the major key",
        )
        assertEquals(
            minor,
            circle.pickAt((CENTER + 85 * cos(angle)).toInt(), (CENTER + 85 * sin(angle)).toInt()),
            "the inner half is its relative minor",
        )
    }

    @Test
    fun `the middle of the circle keeps the key of the piece`() {
        val circle = CircleOfFifths {}
        val key = KeySignature.parse("E♭ major")!!
        circle.key = key

        assertEquals(key, circle.pickAt(CENTER, CENTER), "the middle picks the key of the piece")
        assertEquals(key, circle.pickAt(CENTER + 20, CENTER - 20), "the middle picks the key of the piece")
    }

    @Test
    fun `nothing is picked outside the circle`() {
        val circle = CircleOfFifths {}
        assertNull(circle.pickAt(CENTER + 160, CENTER), "the outside of the circle picks nothing")
        assertNull(circle.pickAt(0, 0), "the corner of the circle picks nothing")
    }

    @Test
    fun `the labels of the circle do not crowd each other`() {
        val circle = CircleOfFifths {}
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        val major = g.getFontMetrics(Theme.font(CircleOfFifths.MAJOR_FONT, Font.BOLD))
        val minor = g.getFontMetrics(Theme.font(CircleOfFifths.MINOR_FONT))
        g.dispose()
        val majorRoom = room(CircleOfFifths.R_MAJOR)
        val minorRoom = room(CircleOfFifths.R_MINOR)
        circle.labels().forEach { (outside, inside) ->
            assertTrue(
                major.stringWidth(outside) < majorRoom,
                "the label $outside has room: ${major.stringWidth(outside)} < $majorRoom",
            )
            assertTrue(
                minor.stringWidth(inside) < minorRoom,
                "the label $inside has room: ${minor.stringWidth(inside)} < $minorRoom",
            )
        }
    }

    @Test
    fun `every key sits in the middle of its band`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        val major = g.getFontMetrics(Theme.font(CircleOfFifths.MAJOR_FONT, Font.BOLD))
        val minor = g.getFontMetrics(Theme.font(CircleOfFifths.MINOR_FONT))
        g.dispose()
        // The band of a key is the ring of the circle on one side and the edge of the band on
        // the other: the label has the same room above it and below it.
        assertBalanced(
            "the major keys",
            CircleOfFifths.R_MAJOR,
            CircleOfFifths.R_RING,
            CircleOfFifths.R_OUTER,
            major.ascent / 2.0,
        )
        assertBalanced(
            "the minor keys",
            CircleOfFifths.R_MINOR,
            CircleOfFifths.R_HOLE,
            CircleOfFifths.R_RING,
            minor.ascent / 2.0,
        )
    }

    @Test
    fun `the circle is drawn`() {
        val circle = CircleOfFifths {}
        circle.key = KeySignature.parse("E♭ major")!!
        circle.size = circle.preferredSize
        val image = BufferedImage(CircleOfFifths.SIZE, CircleOfFifths.SIZE, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x18, 0x1A, 0x1F)
        g.fillRect(0, 0, image.width, image.height)
        circle.paint(g)
        g.dispose()
        assertEquals(true, image.width > 0, "the circle has a size")
    }

    @Test
    fun `the wedge of a position is under the label of the position`() {
        val circle = CircleOfFifths {}
        val wedge = circle.sector(0, CircleOfFifths.R_HOLE, CircleOfFifths.R_OUTER)
        val bounds = wedge.bounds2D
        assertEquals(true, bounds.centerY < CENTER, "the wedge of the first position is at the top")
        assertEquals(true, bounds.centerX > CENTER - 20, "the wedge of the first position is at the top")
        val last = circle.sector(7, CircleOfFifths.R_HOLE, CircleOfFifths.R_OUTER)
        val lastBounds = last.bounds2D
        assertEquals(true, lastBounds.centerY > CENTER, "the wedge of the eighth position is at the bottom")
    }

    companion object {
        /** The centre of the circle, in pixels. */
        private const val CENTER = CircleOfFifths.SIZE / 2

        /**
         * The room between two labels of the circle.
         *
         * @param radius the distance of the labels from the centre.
         * @return the distance between the centres of two neighbouring labels.
         */
        private fun room(radius: Double): Double = 2 * radius * sin(Math.toRadians(CircleOfFifths.STEP) / 2)

        /**
         * Says that a label has the same room above it and below it.
         *
         * @param what the labels, for the message.
         * @param radius the distance of the label from the centre.
         * @param inner the radius of the inner edge of the band.
         * @param outer the radius of the outer edge of the band.
         * @param halfHeight the half of the height of the label.
         */
        private fun assertBalanced(
            what: String,
            radius: Double,
            inner: Double,
            outer: Double,
            halfHeight: Double,
        ) {
            val above = radius - halfHeight - inner
            val below = outer - radius - halfHeight
            assertTrue(above >= 4.0, "$what have room above them: $above")
            assertTrue(below >= 4.0, "$what have room below them: $below")
            assertTrue(
                kotlin.math.abs(above - below) <= 1.0,
                "$what sit in the middle of their band: $above above, $below below",
            )
        }

        /** The cosine of an angle, in radians. */
        private fun cos(angle: Double): Double = kotlin.math.cos(angle)

        /** The sine of an angle, in radians. */
        private fun sin(angle: Double): Double = kotlin.math.sin(angle)
    }
}
