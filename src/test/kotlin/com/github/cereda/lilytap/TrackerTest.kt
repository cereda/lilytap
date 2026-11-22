// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tracker: the keys of the keyboard that play the piano. */
class TrackerTest {
    /** A key event of the given key. */
    private fun event(
        keyCode: Int,
        id: Int = KeyEvent.KEY_PRESSED,
        modifiers: Int = 0,
    ): KeyEvent =
        KeyEvent(
            JPanel(),
            id,
            System.currentTimeMillis(),
            modifiers,
            keyCode,
            KeyEvent.CHAR_UNDEFINED,
        )

    /** The action of a key press in the given octave. */
    private fun action(
        keyCode: Int,
        octave: Int = 4,
        focusOwner: javax.swing.JComponent? = null,
    ): TrackerAction? = trackerAction(event(keyCode), focusOwner, octave)

    /** The pitch a key plays in the given octave. */
    private fun pitch(
        keyCode: Int,
        octave: Int = 4,
    ): Pitch? = (action(keyCode, octave) as? TrackerAction.Note)?.pitch

    // -------------------------------------------------------------------------------------------
    // The keys
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the number row names the notes of the scale`() {
        val expected =
            mapOf(
                KeyEvent.VK_1 to PitchStep.C,
                KeyEvent.VK_2 to PitchStep.D,
                KeyEvent.VK_3 to PitchStep.E,
                KeyEvent.VK_4 to PitchStep.F,
                KeyEvent.VK_5 to PitchStep.G,
                KeyEvent.VK_6 to PitchStep.A,
                KeyEvent.VK_7 to PitchStep.B,
            )
        for ((key, step) in expected) {
            assertEquals(step, pitch(key)?.step, "the key $key names the wrong note")
            assertEquals(0, pitch(key)?.alter, "the key $key names a natural note")
        }
    }

    @Test
    fun `the top row names the sharps`() {
        val expected =
            mapOf(
                KeyEvent.VK_Q to PitchStep.C,
                KeyEvent.VK_W to PitchStep.D,
                KeyEvent.VK_E to PitchStep.F,
                KeyEvent.VK_R to PitchStep.G,
                KeyEvent.VK_T to PitchStep.A,
            )
        for ((key, step) in expected) {
            assertEquals(step, pitch(key)?.step, "the key $key names the wrong note")
            assertEquals(1, pitch(key)?.alter, "the key $key names a sharp")
        }
    }

    @Test
    fun `a tracker key plays its note in the live octave`() {
        assertEquals(Pitch(PitchStep.C, 0, 4), pitch(KeyEvent.VK_1, 4))
        assertEquals(Pitch(PitchStep.G, 0, 4), pitch(KeyEvent.VK_5, 4))
        assertEquals(Pitch(PitchStep.B, 0, 4), pitch(KeyEvent.VK_7, 4))
        assertEquals(Pitch(PitchStep.C, 1, 4), pitch(KeyEvent.VK_Q, 4))
        assertEquals(Pitch(PitchStep.A, 1, 4), pitch(KeyEvent.VK_T, 4))
    }

    @Test
    fun `the same key plays another note in another octave`() {
        assertEquals(Pitch(PitchStep.C, 0, 3), pitch(KeyEvent.VK_1, 3))
        assertEquals(Pitch(PitchStep.C, 0, 5), pitch(KeyEvent.VK_1, 5))
        assertEquals(Pitch(PitchStep.C, 0, 6), pitch(KeyEvent.VK_1, 6))
    }

    @Test
    fun `the octave keys move the tracker`() {
        assertEquals(TrackerAction.Down, action(KeyEvent.VK_8))
        assertEquals(TrackerAction.Up, action(KeyEvent.VK_9))
        assertEquals(TrackerAction.Reset, action(KeyEvent.VK_0))
    }

    @Test
    fun `the smaller octave key goes down and the bigger one goes up`() {
        assertTrue(OCTAVE_DOWN < OCTAVE_UP, "the number row climbs to the right")
        assertEquals(KeyEvent.VK_8, OCTAVE_DOWN)
        assertEquals(KeyEvent.VK_9, OCTAVE_UP)
    }

    @Test
    fun `a key that is not a tracker key plays nothing`() {
        assertNull(action(KeyEvent.VK_S), "S belongs to the strip")
        assertNull(action(KeyEvent.VK_X), "X belongs to the strip")
        assertNull(action(KeyEvent.VK_M), "M belongs to the strip")
        assertNull(action(KeyEvent.VK_ESCAPE))
        assertNull(action(KeyEvent.VK_A))
        assertNull(action(KeyEvent.VK_Y), "Y names no note")
        assertNull(action(KeyEvent.VK_P), "P names no note")
    }

    @Test
    fun `a key with a modifier on it plays nothing`() {
        assertNull(
            trackerAction(event(KeyEvent.VK_1, modifiers = KeyEvent.CTRL_DOWN_MASK), null, 4),
            "Ctrl+1 is something else",
        )
        assertNull(
            trackerAction(event(KeyEvent.VK_5, modifiers = KeyEvent.ALT_DOWN_MASK), null, 4),
            "Alt+5 is something else",
        )
        assertNull(
            trackerAction(event(KeyEvent.VK_5, modifiers = KeyEvent.META_DOWN_MASK), null, 4),
            "Cmd+5 is something else",
        )
    }

    @Test
    fun `a released key plays nothing`() {
        assertNull(
            trackerAction(event(KeyEvent.VK_5, id = KeyEvent.KEY_RELEASED), null, 4),
            "only a press plays a note",
        )
    }

    @Test
    fun `a key that is typed in a text field is text`() {
        assertNull(action(KeyEvent.VK_5, focusOwner = JTextField()))
        assertNull(action(KeyEvent.VK_8, focusOwner = JTextField()), "the octave keys are text too")
    }

    @Test
    fun `every tracker key has a letter of its own`() {
        val labels = TRACKER_KEYS.entries.associate { (key, pitchClass) -> pitchClass to KeyEvent.getKeyText(key) }
        assertEquals(labels, TRACKER_LABELS)
        assertEquals(TRACKER_KEYS.size, TRACKER_LABELS.size, "no two keys share a letter")
    }

    @Test
    fun `the keys of the strip are not tracker keys`() {
        for (key in listOf(KeyEvent.VK_S, KeyEvent.VK_X, KeyEvent.VK_M)) {
            assertNull(TRACKER_KEYS[key], "the key $key belongs to the strip")
        }
    }

    // -------------------------------------------------------------------------------------------
    // The state of the tracker
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the tracker starts on middle C`() {
        val tracker = TrackerState()
        assertEquals(4, tracker.octave)
        assertEquals(4, tracker.anchor)
    }

    @Test
    fun `the tracker moves an octave up and down`() {
        val tracker = TrackerState()
        tracker.up()
        assertEquals(5, tracker.octave)
        tracker.down()
        tracker.down()
        assertEquals(3, tracker.octave)
    }

    @Test
    fun `the tracker goes to the octave it is told to`() {
        val tracker = TrackerState()
        tracker.moveTo(6)
        assertEquals(6, tracker.octave)
        tracker.moveTo(1)
        assertEquals(1, tracker.octave)
    }

    @Test
    fun `the tracker stays inside the range when it is told to move`() {
        val tracker = TrackerState()
        tracker.moveTo(Pitch.MAX_OCTAVE + 3)
        assertEquals(Pitch.MAX_OCTAVE, tracker.octave, "the tracker cannot go above the top")
        tracker.moveTo(Pitch.MIN_OCTAVE - 3)
        assertEquals(Pitch.MIN_OCTAVE, tracker.octave, "the tracker cannot go below the bottom")
    }

    @Test
    fun `the tracker keeps its anchor when it is told to move`() {
        val tracker = TrackerState()
        tracker.anchorTo(5)
        tracker.moveTo(2)
        assertEquals(2, tracker.octave, "the tracker is where it was told to go")
        assertEquals(5, tracker.anchor, "the anchor is where the note is")
    }

    @Test
    fun `the tracker goes back to the anchor`() {
        val tracker = TrackerState()
        tracker.anchorTo(2)
        tracker.up()
        assertEquals(3, tracker.octave)
        tracker.reset()
        assertEquals(2, tracker.octave, "the tracker is back on the anchor")
    }

    @Test
    fun `the tracker is anchored on the note`() {
        val tracker = TrackerState()
        tracker.anchorTo(5)
        assertEquals(5, tracker.octave, "the tracker is in the octave of the note")
        assertEquals(5, tracker.anchor)
    }

    @Test
    fun `the tracker stays inside the range of a keyboard`() {
        val tracker = TrackerState()
        repeat(20) { tracker.up() }
        assertEquals(Pitch.MAX_OCTAVE, tracker.octave, "the tracker cannot go above the top")
        repeat(20) { tracker.down() }
        assertEquals(Pitch.MIN_OCTAVE, tracker.octave, "the tracker cannot go below the bottom")
    }

    @Test
    fun `a tracker key in an octave the keyboard cannot play does nothing`() {
        assertNull(pitch(KeyEvent.VK_1, Pitch.MAX_OCTAVE + 1))
    }

    @Test
    fun `the tracker state is what the keys are played in`() {
        val tracker = TrackerState()
        tracker.up()
        assertTrue(tracker.octave > tracker.anchor!!, "the tracker is above the anchor")
    }

    @Test
    fun `the tracker forgets the anchor when the score is gone`() {
        val tracker = TrackerState()
        tracker.anchorTo(5)
        tracker.clear()

        assertEquals(Pitch.DEFAULT.octave, tracker.octave, "the tracker is back in the middle")
        assertNull(tracker.anchor, "there is no note to go back to")
    }

    @Test
    fun `the reset does nothing when there is no anchor`() {
        val tracker = TrackerState()
        tracker.up()
        tracker.clear()
        tracker.up()
        tracker.reset()

        assertEquals(5, tracker.octave, "the tracker stayed where it was")
    }
}
