// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pitches panel and the legend of the top column. */
class PitchesPanelTest {
    /** Paints a component and returns the image it drew on. */
    private fun paint(
        component: javax.swing.JComponent,
        width: Int = component.preferredSize.width,
        height: Int = component.preferredSize.height,
    ): BufferedImage {
        component.setSize(maxOf(width, 1), maxOf(height, 1))
        val image = BufferedImage(maxOf(width, 1), maxOf(height, 1), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        component.paint(graphics)
        graphics.dispose()
        return image
    }

    /** Whether the image has a pixel of the given colour. */
    private fun hasColour(
        image: BufferedImage,
        colour: java.awt.Color,
    ): Boolean {
        val wanted = colour.rgb
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == wanted) return true
            }
        }
        return false
    }

    @Test
    fun `the panel has a piano and a button`() {
        val panel = PitchesPanel()
        assertTrue(panel.components.any { it is PianoPanel }, "the panel has the piano")
        assertEquals("Fill the melody", panel.fill.text, "the button starts the walk")
    }

    @Test
    fun `the panel says which octave the tracker plays`() {
        val panel = PitchesPanel()
        assertEquals("C4", panel.octaveText, "the tracker starts in the middle")
        panel.setOctave(5, 4)
        assertEquals("C5", panel.octaveText, "the panel says where the tracker is")
        assertEquals(5, panel.piano.liveOctave, "the piano marks the same octave")
    }

    @Test
    fun `the octave control is a stepper that moves the tracker`() {
        val panel = PitchesPanel()
        assertEquals(4.0, panel.octave.value, "the stepper starts in the middle")
        panel.octave.value = 6.0
        assertEquals(6.0, panel.octave.value, "the stepper takes the value it is given")
        assertEquals("C6", panel.octaveText, "the stepper writes the octave")
    }

    @Test
    fun `the octave control stays inside the range of the keyboard`() {
        val panel = PitchesPanel()
        panel.setOctave(Pitch.MAX_OCTAVE + 2, null)
        assertEquals(
            Pitch.MAX_OCTAVE.toDouble(),
            panel.octave.value,
            "the stepper cannot go above the top of the keyboard",
        )
        panel.setOctave(Pitch.MIN_OCTAVE - 2, null)
        assertEquals(
            Pitch.MIN_OCTAVE.toDouble(),
            panel.octave.value,
            "the stepper cannot go below the bottom of the keyboard",
        )
    }

    @Test
    fun `the octave control says what it goes back to`() {
        val panel = PitchesPanel()
        panel.setOctave(5, 4)
        val tip = panel.octave.toolTipText ?: ""
        assertTrue(tip.contains("C5"), "the tooltip says where the tracker is")
        assertTrue(tip.contains("C4"), "the tooltip says which note it goes back to")
    }

    @Test
    fun `the octave control says when there is no note to go back to`() {
        val panel = PitchesPanel()
        panel.setOctave(5, null)
        val tip = panel.octave.toolTipText ?: ""
        assertTrue(tip.contains("C5"), "the tooltip says where the tracker is")
        assertTrue(!tip.contains("C4"), "there is no note to go back to")
    }

    @Test
    fun `the counter says where the cursor is`() {
        val panel = PitchesPanel()
        assertEquals("", panel.counterText)
        panel.setCounter("3 / 12")
        assertEquals("3 / 12", panel.counterText)
    }

    @Test
    fun `the legend fits the column of the settings card`() {
        val legend = LegendPanel()
        assertTrue(
            legend.preferredSize.width <= 168,
            "the legend fits the column: ${legend.preferredSize.width}",
        )
        assertTrue(
            legend.preferredSize.height <= 85,
            "the legend fits above the Start button: ${legend.preferredSize.height}",
        )
    }

    @Test
    fun `the legend shows the colours of the raw taps`() {
        val legend = LegendPanel()
        val image = paint(legend)
        assertTrue(hasColour(image, Theme.warn), "the legend shows the colour of a late tap")
        assertTrue(hasColour(image, Theme.accent2), "the legend shows the colour of an early tap")
        assertTrue(hasColour(image, Theme.dim), "the legend writes its words")
    }

    @Test
    fun `the lines of the legend fit in its width`() {
        val legend = LegendPanel()
        val fm = legend.getFontMetrics(Theme.font(11f))
        for (line in legend.trackerLines) {
            assertTrue(
                fm.stringWidth(line) <= legend.preferredSize.width,
                "the line '$line' fits in the column",
            )
        }
    }

    @Test
    fun `the legend says what the tracker keys do`() {
        val legend = LegendPanel()
        assertTrue(
            legend.trackerLines.any { it.contains("1–7") },
            "the legend says what the number row plays",
        )
        assertTrue(
            legend.trackerLines.any { it.contains("Q W E R T") },
            "the legend says what the top row plays",
        )
        assertTrue(
            legend.trackerLines.any { it.contains("8 / 9 = octave down / up") },
            "the legend says that the smaller key goes down",
        )
        assertTrue(
            legend.trackerLines.any { it.contains("0") },
            "the legend says what the zero key does",
        )
    }

    @Test
    fun `the legend says whether the tracker plays sharps or flats`() {
        val legend = LegendPanel()
        assertTrue(
            legend.trackerLines.any { it.contains("sharps") },
            "a sharp key plays the sharps",
        )
        legend.key = KeySignature.parse("F major")!!
        assertTrue(
            legend.trackerLines.any { it.contains("flats") },
            "a flat key plays the flats",
        )
        legend.key = KeySignature.parse("A minor")!!
        assertTrue(
            legend.trackerLines.any { it.contains("sharps") },
            "a minor key with no accidentals plays the sharps of the scale",
        )
    }
}
