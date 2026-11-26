// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.Timer
import javax.swing.text.JTextComponent

/**
 * How close two presses of the same key have to be to count as one tap.
 *
 * A held key repeats itself, and the repeats come faster than any tempo the app can hold
 * (300 BPM is a fifth of a second), so two presses that close together are one tap.
 */
private const val REPEAT_GUARD_NANOS = 100_000_000L

/** What the status line says while a tap round is listening. */
private const val TAP_STATUS = "Keep tapping - the tempo follows the taps"

/**
 * A round of taps that sets the tempo.
 *
 * The user taps in time, on the button or on any key, and the tempo follows the taps. The
 * round owns the taps themselves and the two clocks that surround them: the flash of the
 * button, so a tap is seen even when the tempo does not move yet, and the end of the round,
 * so the keys belong to the app again when the taps stop.
 *
 * The class holds the logic of the round; the window tells it what the status line says.
 */
internal class TapTempoRound(
    /** The button that shows that the round is listening. */
    private val button: IconButton,
    /** The stepper that holds the tempo the taps settle on. */
    private val bpm: Stepper,
    /** What the status line says while the round listens. */
    private val say: (String) -> Unit,
    /** Puts the status line back where it belongs when the round is over. */
    private val restore: () -> Unit,
) {
    /** The taps of the round and the tempo they make. */
    private val tempo = TapTempo()

    /** The flash of the button, so a tap is seen even when the tempo does not move. */
    private val flash = Timer(220) { button.accentOverride = null }.apply { isRepeats = false }

    /**
     * The end of a round: the button is no longer armed and the status line goes back.
     *
     * The round ends when the taps stop, so the keys belong to the app again.
     */
    private val idle =
        Timer(TapTempo.IDLE_MS) {
            button.armed = false
            restore()
        }.apply { isRepeats = false }

    /** The key of the last tap, so a key that repeats itself is not counted twice. */
    private var lastKey = 0

    /** When the last tap happened, in nanoseconds. */
    private var lastNanos = 0L

    /**
     * A tap on the tap button or on a key: the tempo follows the taps.
     *
     * The button flashes on every tap, so a tap is seen even when it does not move the tempo
     * yet - the first tap of a round never does. The button stays armed while the round
     * listens, so the user sees that the key presses count as taps.
     */
    fun tap() {
        val now = System.nanoTime()
        button.accentOverride = Theme.accent
        flash.restart()
        button.armed = true
        idle.restart()
        val tempo = tempo.tap(now)
        if (tempo == null) {
            // The first tap of a round: it only starts the round.
            say(TAP_STATUS)
            return
        }
        bpm.value = tempo.toDouble()
    }

    /**
     * Ends the round: the taps are forgotten and the keys belong to the app again.
     *
     * @param restoreStatus true when the status line goes back to the idle message.
     */
    fun end(restoreStatus: Boolean) {
        tempo.reset()
        button.armed = false
        button.accentOverride = null
        flash.stop()
        idle.stop()
        if (restoreStatus) restore()
    }

    /**
     * Whether the round is still listening.
     *
     * @param nowNanos the clock now.
     * @return true when the round listens for more taps.
     */
    fun isListening(nowNanos: Long): Boolean = tempo.isListening(nowNanos)

    /**
     * Whether a key press is a tap of the tempo.
     *
     * Only while the round is listening - the button was clicked and the last tap is not too
     * long ago - and never while a text field has the focus: there the keys are text, not
     * taps. A key that repeats itself is not a tap either.
     *
     * @param event the key event.
     * @param autoRepeat the guard against the keys that repeat themselves.
     * @return true when the key press counts as a tap.
     */
    fun isTapKey(
        event: KeyEvent,
        autoRepeat: AutoRepeat,
    ): Boolean {
        if (!canTap(event)) return false
        val now = System.nanoTime()
        if (!tempo.isListening(now)) return false
        if (autoRepeat.isRepeat(event)) return false
        // A held key repeats itself: two presses of the same key that close together are one tap.
        if (event.keyCode == lastKey && now - lastNanos < REPEAT_GUARD_NANOS) return false
        lastKey = event.keyCode
        lastNanos = now
        autoRepeat.note(event)
        return true
    }

    /**
     * Whether a key press can be a tap at all: a plain key, pressed outside a text field.
     *
     * @param event the key event.
     * @return true when the key could be a tap.
     */
    private fun canTap(event: KeyEvent): Boolean {
        if (event.id != KeyEvent.KEY_PRESSED) return false
        if (event.isControlDown || event.isMetaDown || event.isAltDown) return false
        if (event.keyCode in IGNORED_KEYS) return false
        if (event.keyCode == KeyEvent.VK_ESCAPE) return false
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return owner !is JTextComponent
    }
}
