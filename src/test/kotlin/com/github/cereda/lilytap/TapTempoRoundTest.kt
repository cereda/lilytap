// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapTempoRoundTest {
    private val button = IconButton(Glyph.TAP, 38, 24)
    private val bpm = Stepper(60.0, 20.0, 300.0, 1.0, width = 104) { it.toInt().toString() }
    private var said: String? = null
    private var restored = 0

    private fun round() =
        TapTempoRound(
            button = button,
            bpm = bpm,
            say = { text -> said = text },
            restore = { restored++ },
        )

    @Test
    fun `the first tap only starts the round`() {
        val round = round()
        round.tap()
        assertEquals("Keep tapping - the tempo follows the taps", said)
        assertTrue(button.armed)
    }

    @Test
    fun `the tempo follows the taps`() {
        val round = round()
        round.tap()
        Thread.sleep(500)
        round.tap()
        assertEquals(120.0, bpm.value, 5.0)
    }

    @Test
    fun `the round listens while the taps are close together`() {
        val round = round()
        assertFalse(round.isListening(System.nanoTime()))
        round.tap()
        assertTrue(round.isListening(System.nanoTime()))
    }

    @Test
    fun `ending the round puts the status line back`() {
        val round = round()
        round.tap()
        round.end(restoreStatus = true)
        assertEquals(1, restored)
        assertFalse(button.armed)
        assertFalse(round.isListening(System.nanoTime()))
    }

    @Test
    fun `ending the round can leave the status line alone`() {
        val round = round()
        round.tap()
        round.end(restoreStatus = false)
        assertEquals(0, restored)
    }

    @Test
    fun `a key is a tap only while the round listens`() {
        val round = round()
        val guard = AutoRepeat()
        assertFalse(round.isTapKey(press(KeyEvent.VK_A), guard))
        round.tap()
        assertTrue(round.isTapKey(press(KeyEvent.VK_A), guard))
    }

    @Test
    fun `a key that repeats itself is not a tap`() {
        val round = round()
        val guard = AutoRepeat()
        round.tap()
        val event = press(KeyEvent.VK_A)
        guard.note(event)
        assertFalse(round.isTapKey(event, guard))
    }

    @Test
    fun `escape is not a tap`() {
        val round = round()
        round.tap()
        assertFalse(round.isTapKey(press(KeyEvent.VK_ESCAPE), AutoRepeat()))
    }

    /** A key press outside any text field. */
    private fun press(keyCode: Int) =
        KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            keyCode,
            KeyEvent.CHAR_UNDEFINED,
        )
}
