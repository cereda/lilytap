// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tracker keys: the keys of the keyboard that play the piano. */
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

    @Test
    fun `a tracker key plays its pitch`() {
        assertEquals(keyPitch(60), trackerPitch(event(KeyEvent.VK_1), null))
        assertEquals(keyPitch(61), trackerPitch(event(KeyEvent.VK_Q), null))
        assertEquals(keyPitch(80), trackerPitch(event(KeyEvent.VK_P), null))
    }

    @Test
    fun `a key that is not a tracker key plays nothing`() {
        assertNull(trackerPitch(event(KeyEvent.VK_S), null), "S belongs to the strip")
        assertNull(trackerPitch(event(KeyEvent.VK_X), null), "X belongs to the strip")
        assertNull(trackerPitch(event(KeyEvent.VK_M), null), "M belongs to the strip")
        assertNull(trackerPitch(event(KeyEvent.VK_ESCAPE), null))
        assertNull(trackerPitch(event(KeyEvent.VK_A), null))
    }

    @Test
    fun `a key with a modifier on it plays nothing`() {
        assertNull(
            trackerPitch(event(KeyEvent.VK_1, modifiers = KeyEvent.CTRL_DOWN_MASK), null),
            "Ctrl+1 is something else",
        )
        assertNull(
            trackerPitch(event(KeyEvent.VK_5, modifiers = KeyEvent.ALT_DOWN_MASK), null),
            "Alt+5 is something else",
        )
        assertNull(
            trackerPitch(event(KeyEvent.VK_5, modifiers = KeyEvent.META_DOWN_MASK), null),
            "Cmd+5 is something else",
        )
    }

    @Test
    fun `a released key plays nothing`() {
        assertNull(
            trackerPitch(event(KeyEvent.VK_5, id = KeyEvent.KEY_RELEASED), null),
            "only a press plays a note",
        )
    }

    @Test
    fun `a key that is typed in a text field is text`() {
        assertNull(trackerPitch(event(KeyEvent.VK_5), JTextField()))
    }

    @Test
    fun `every tracker key has a letter of its own`() {
        val labels = TRACKER_KEYS.entries.associate { (key, midi) -> midi to KeyEvent.getKeyText(key) }
        assertEquals(labels, TRACKER_LABELS)
        assertEquals(TRACKER_KEYS.size, TRACKER_LABELS.size, "no two keys share a letter")
    }
}
