// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoRepeatTest {
    @Test
    fun `the first press is never a repeat`() {
        val guard = AutoRepeat()
        assertFalse(guard.isRepeat(press(KeyEvent.VK_A)))
    }

    @Test
    fun `the same press again is a repeat`() {
        val guard = AutoRepeat()
        val event = press(KeyEvent.VK_A)
        guard.note(event)
        assertTrue(guard.isRepeat(event))
    }

    @Test
    fun `another key is not a repeat of the last one`() {
        val guard = AutoRepeat()
        guard.note(press(KeyEvent.VK_A))
        assertFalse(guard.isRepeat(press(KeyEvent.VK_B)))
    }

    @Test
    fun `forgetting the press makes the next one a first press`() {
        val guard = AutoRepeat()
        val event = press(KeyEvent.VK_A)
        guard.note(event)
        guard.reset()
        assertFalse(guard.isRepeat(event))
    }

    /** A key press with a fixed time, so two of them can be the same event. */
    private fun press(
        keyCode: Int,
        whenMillis: Long = 1_000L,
    ) = KeyEvent(
        JPanel(),
        KeyEvent.KEY_PRESSED,
        whenMillis,
        0,
        keyCode,
        KeyEvent.CHAR_UNDEFINED,
    )
}
