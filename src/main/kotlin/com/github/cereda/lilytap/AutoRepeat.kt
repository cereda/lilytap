// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent

/**
 * The guard against the keys that repeat themselves.
 *
 * On X11/Linux a held key produces release+press pairs with identical timestamps. Such a
 * press (or its matching release) repeats the previous one and is not a new press.
 *
 * The check is local to the window, so key events of other applications cannot be mistaken
 * for a repeat of ours.
 */
internal class AutoRepeat {
    /** The last key press that was seen. */
    private var lastPress: KeyEvent? = null

    /** Forgets the last press: the next one is never a repeat. */
    fun reset() {
        lastPress = null
    }

    /** Remembers a press, so that the event after it can be compared with it. */
    fun note(event: KeyEvent) {
        lastPress = event
    }

    /**
     * Whether an event repeats the previous press.
     *
     * @param event the key event.
     * @return true when the event repeats the last press.
     */
    fun isRepeat(event: KeyEvent): Boolean {
        val previous = lastPress ?: return false
        return previous.keyCode == event.keyCode && previous.`when` == event.`when`
    }
}
