// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The piano of the pitches panel: the keys, the tracker letters and what a click does. */
class PianoPanelTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)

    /** A score of one event per pitch, every event a cell long, and a silence at the end. */
    private fun scoreOf(vararg pitches: Pitch): Score {
        val events = mutableListOf<Event>()
        var units = 0
        for (pitch in pitches) {
            events += Event(TapKind.NOTE, units, config.cellUnits, pitch)
            units += config.cellUnits
        }
        if (units < config.totalUnits) {
            events += Event(TapKind.REST, units, config.totalUnits - units, null)
        }
        return Score(events, config)
    }

    /** A piano of the given size. */
    private fun piano(
        width: Int = 300,
        height: Int = 100,
    ): PianoPanel =
        PianoPanel().apply {
            setSize(width, height)
        }

    /** Clicks the piano at a point. */
    private fun click(
        panel: PianoPanel,
        x: Int,
        y: Int,
    ) {
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false))
    }

    // -------------------------------------------------------------------------------------------
    // The tracker keys
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the number row names the notes of the scale`() {
        val expected =
            mapOf(
                KeyEvent.VK_1 to 0,
                KeyEvent.VK_2 to 2,
                KeyEvent.VK_3 to 4,
                KeyEvent.VK_4 to 5,
                KeyEvent.VK_5 to 7,
                KeyEvent.VK_6 to 9,
                KeyEvent.VK_7 to 11,
            )
        for ((key, pitchClass) in expected) {
            assertEquals(pitchClass, TRACKER_KEYS[key], "the key $key names the wrong note")
            assertFalse(isBlackKey(pitchClass), "the note $pitchClass is a white key")
        }
    }

    @Test
    fun `the top row names the sharps`() {
        val expected =
            mapOf(
                KeyEvent.VK_Q to 1,
                KeyEvent.VK_W to 3,
                KeyEvent.VK_E to 6,
                KeyEvent.VK_R to 8,
                KeyEvent.VK_T to 10,
            )
        for ((key, pitchClass) in expected) {
            assertEquals(pitchClass, TRACKER_KEYS[key], "the key $key names the wrong note")
            assertTrue(isBlackKey(pitchClass), "the note $pitchClass is a black key")
        }
    }

    @Test
    fun `the tracker keys leave the keys of the editor alone`() {
        for (key in listOf(KeyEvent.VK_S, KeyEvent.VK_X, KeyEvent.VK_M, KeyEvent.VK_ESCAPE)) {
            assertNull(TRACKER_KEYS[key], "the key $key belongs to the editor")
        }
    }

    @Test
    fun `the octave keys name no note`() {
        for (key in listOf(KeyEvent.VK_8, KeyEvent.VK_9, KeyEvent.VK_0)) {
            assertNull(TRACKER_KEYS[key], "the key $key moves the octave")
        }
    }

    @Test
    fun `every tracker key has a letter on its key`() {
        for ((key, pitchClass) in TRACKER_KEYS) {
            assertEquals(
                KeyEvent.getKeyText(key),
                TRACKER_LABELS[pitchClass],
                "the key $key should show its letter on the piano",
            )
        }
        assertEquals(TRACKER_KEYS.size, TRACKER_LABELS.size, "every key has a letter of its own")
    }

    @Test
    fun `a tracker key plays the pitch of its key`() {
        assertEquals(Pitch(PitchStep.C, 0, 4), keyPitch(12 * 5 + TRACKER_KEYS[KeyEvent.VK_1]!!))
        assertEquals(Pitch(PitchStep.C, 1, 4), keyPitch(12 * 5 + TRACKER_KEYS[KeyEvent.VK_Q]!!))
        assertEquals(Pitch(PitchStep.A, 1, 4), keyPitch(12 * 5 + TRACKER_KEYS[KeyEvent.VK_T]!!))
        assertEquals(Pitch(PitchStep.G, 0, 4), keyPitch(12 * 5 + TRACKER_KEYS[KeyEvent.VK_5]!!))
        assertEquals(Pitch(PitchStep.E, 0, 5), keyPitch(12 * 6 + TRACKER_KEYS[KeyEvent.VK_3]!!))
    }

    // -------------------------------------------------------------------------------------------
    // The keys of the piano
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a black key is the sharp of the note below it`() {
        assertEquals(Pitch(PitchStep.C, 0, 4), keyPitch(60))
        assertEquals(Pitch(PitchStep.C, 1, 4), keyPitch(61))
        assertEquals(Pitch(PitchStep.D, 0, 4), keyPitch(62))
        assertEquals(Pitch(PitchStep.D, 1, 4), keyPitch(63))
        assertEquals(Pitch(PitchStep.E, 0, 4), keyPitch(64))
        assertEquals(Pitch(PitchStep.F, 0, 4), keyPitch(65))
        assertEquals(Pitch(PitchStep.F, 1, 4), keyPitch(66))
        assertEquals(Pitch(PitchStep.G, 0, 4), keyPitch(67))
        assertEquals(Pitch(PitchStep.G, 1, 4), keyPitch(68))
        assertEquals(Pitch(PitchStep.A, 0, 4), keyPitch(69))
        assertEquals(Pitch(PitchStep.A, 1, 4), keyPitch(70))
        assertEquals(Pitch(PitchStep.B, 0, 4), keyPitch(71))
        assertEquals(Pitch(PitchStep.C, 0, 5), keyPitch(72))
    }

    @Test
    fun `the keys of the piano follow the key of the piece`() {
        val flat = KeySignature.parse("F major")!!
        assertEquals(Pitch(PitchStep.D, -1, 4), keyPitch(61, flat), "the C sharp is a D flat")
        assertEquals(Pitch(PitchStep.B, -1, 4), keyPitch(70, flat), "the A sharp is a B flat")
        assertEquals(Pitch(PitchStep.B, 0, 4), keyPitch(71, flat), "the white key is a B natural")

        val sharp = KeySignature.parse("D major")!!
        assertEquals(Pitch(PitchStep.C, 1, 4), keyPitch(61, sharp), "the C sharp is a C sharp")
        assertEquals(Pitch(PitchStep.F, 1, 4), keyPitch(66, sharp), "the F sharp is a F sharp")
    }

    @Test
    fun `clicking a key gives the note of the key`() {
        val panel = piano()
        panel.key = KeySignature.parse("F major")!!
        var heard: Pitch? = null
        panel.onPitch = { heard = it }
        val black = panel.keys().first { it.black }
        click(panel, (black.x + black.width / 2).toInt(), 10)
        assertEquals(Pitch(PitchStep.D, -1, 4), heard, "the first black key is a D flat in F major")
    }

    @Test
    fun `the piano starts at two octaves`() {
        assertEquals(60, lowestKey(null, 4))
        assertEquals(84, highestKey(null, 4))
    }

    @Test
    fun `the piano grows to cover the take`() {
        val low = scoreOf(Pitch(PitchStep.C, 0, 3))
        assertEquals(48, lowestKey(low, 4), "the keyboard reaches down to the lowest note")

        val high = scoreOf(Pitch(PitchStep.C, 0, 6))
        assertEquals(96, highestKey(high, 4), "the keyboard reaches up to the highest note")
    }

    @Test
    fun `the piano keeps two octaves when the take is small`() {
        val small = scoreOf(Pitch(PitchStep.C, 0, 4))
        assertEquals(60, lowestKey(small, 4))
        assertEquals(84, highestKey(small, 4))
    }

    @Test
    fun `the piano follows the octave of the tracker`() {
        assertEquals(48, lowestKey(null, 3), "the keyboard reaches down to the live octave")
        assertEquals(96, highestKey(null, 6), "the keyboard reaches up to the live octave")
    }

    @Test
    fun `the piano keeps the octave of the tracker in sight`() {
        for (octave in Pitch.MIN_OCTAVE..Pitch.MAX_OCTAVE) {
            val low = lowestKey(null, octave)
            val high = highestKey(null, octave)
            val first = 12 * (octave + 1)
            val last = 12 * (octave + 2) - 1
            assertTrue(
                first in low until high,
                "the C of the octave $octave is not on the keyboard",
            )
            assertTrue(
                last in low until high,
                "the B of the octave $octave is not on the keyboard",
            )
        }
    }

    @Test
    fun `the white keys fill the width of the piano`() {
        val keys = pianoKeys(60, 84, 300.0, 100.0)
        val whites = keys.filter { !it.black }
        assertEquals(14, whites.size, "two octaves have fourteen white keys")
        assertEquals(0.0, whites.first().x)
        assertEquals(300.0, whites.last().x + whites.last().width, "the keys fill the width")
        for (key in whites) {
            assertEquals(300.0 / 14, key.width, 0.0001)
            assertEquals(100.0, key.height, "the white keys are as tall as the piano")
        }
    }

    @Test
    fun `a black key sits on the seam between two white keys`() {
        val keys = pianoKeys(60, 84, 300.0, 100.0)
        val black = keys.filter { it.black }
        assertEquals(10, black.size, "two octaves have ten black keys")
        val whiteWidth = 300.0 / 14
        for (key in black) {
            val centre = key.x + key.width / 2
            val seam = centre % whiteWidth
            val distance = minOf(seam, whiteWidth - seam)
            assertTrue(
                distance < 0.001,
                "the black key at $centre should sit on a seam",
            )
        }
    }

    @Test
    fun `the black key wins over the white key below it`() {
        val keys = pianoKeys(60, 84, 300.0, 100.0)
        val black = keys.first { it.black }
        val x = (black.x + black.width / 2).toInt()
        assertEquals(keyPitch(black.midi), pitchAt(keys, x.toDouble(), 10.0))
        // Below the black key the white one is there.
        val white = keys.first { !it.black && it.midi == 60 }
        assertEquals(keyPitch(white.midi), pitchAt(keys, x.toDouble(), 90.0))
    }

    @Test
    fun `a point away from the keys has no pitch`() {
        val keys = pianoKeys(60, 84, 300.0, 100.0)
        assertNull(pitchAt(keys, 400.0, 10.0))
        assertNull(pitchAt(keys, -5.0, 10.0))
    }

    // -------------------------------------------------------------------------------------------
    // The panel
    // -------------------------------------------------------------------------------------------

    @Test
    fun `clicking a key gives its pitch`() {
        val panel = piano()
        var heard: Pitch? = null
        panel.onPitch = { heard = it }
        click(panel, 10, 90)
        assertEquals(keyPitch(60), heard, "the first key is middle C")
    }

    @Test
    fun `clicking a black key gives the sharp`() {
        val panel = piano()
        var heard: Pitch? = null
        panel.onPitch = { heard = it }
        val black = panel.keys().first { it.black }
        click(panel, (black.x + black.width / 2).toInt(), 10)
        assertEquals(keyPitch(black.midi), heard)
    }

    @Test
    fun `the piano says nothing while it is not interactive`() {
        val panel = piano()
        panel.interactive = false
        var heard: Pitch? = null
        panel.onPitch = { heard = it }
        click(panel, 10, 90)
        assertNull(heard, "a quiet piano does not answer")
    }

    @Test
    fun `the piano does not take the keyboard`() {
        assertFalse(piano().isFocusable, "the keys of the tracker belong to the score")
    }

    @Test
    fun `the piano shows the note that is sounding`() {
        val panel = piano()
        panel.score = scoreOf(Pitch(PitchStep.C, 0, 4))
        val quiet = paint(panel)
        panel.playbackPositionMs = 10.0
        val playing = paint(panel)
        val ink = differingPixels(quiet, playing)
        assertTrue(
            ink.contains(Theme.accent2.rgb),
            "the key that is sounding should light up",
        )
    }

    @Test
    fun `the keys of the take are lit`() {
        val panel = piano()
        val empty = paint(panel)
        panel.score = scoreOf(Pitch(PitchStep.C, 0, 4))
        val withNote = paint(panel)
        assertNotNull(withNote)
        assertTrue(
            differingPixels(empty, withNote).isNotEmpty(),
            "the notes of the take should show on the keys",
        )
    }

    @Test
    fun `the letters of the live octave stand out`() {
        val panel = piano()
        val first = paint(panel)
        panel.liveOctave = 5
        val second = paint(panel)
        assertTrue(
            differingPixels(first, second).isNotEmpty(),
            "the keys of the live octave should carry the letters",
        )
    }

    /** Paints the piano and returns the image it drew on. */
    private fun paint(panel: PianoPanel): BufferedImage {
        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        return image
    }

    /** The colours of the pixels where the two images are different, as the second one has them. */
    private fun differingPixels(
        a: BufferedImage,
        b: BufferedImage,
    ): List<Int> {
        val ink = mutableListOf<Int>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val first = a.getRGB(x, y)
                val second = b.getRGB(x, y)
                if (first != second) ink += second
            }
        }
        return ink
    }
}
