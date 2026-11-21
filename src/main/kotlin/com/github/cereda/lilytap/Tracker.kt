// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The tracker keys: the keys of the keyboard that play the piano of the pitches panel.
 *
 * The number row carries the white keys and the top row the black ones, so the keys of the
 * score editor (S, X and M) keep their meaning.
 */

import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.text.JTextComponent

/**
 * The tracker keys: the number row carries the white keys and the top row the black ones.
 *
 * The keys are absolute: 1 is middle C, 2 is D, and so on up to 0, which is the E above. The
 * black keys follow: Q is C♯, W is D♯, E is F♯, R is G♯ and T is A♯, and the next octave
 * starts again at Y.
 *
 * The letters are read from the key code, so they work with the caps lock on and whatever the
 * keyboard layout says: the key that is physically in the same place plays the same note.
 */
internal val TRACKER_KEYS: Map<Int, Int> =
    mapOf(
        KeyEvent.VK_1 to 60,
        KeyEvent.VK_2 to 62,
        KeyEvent.VK_3 to 64,
        KeyEvent.VK_4 to 65,
        KeyEvent.VK_5 to 67,
        KeyEvent.VK_6 to 69,
        KeyEvent.VK_7 to 71,
        KeyEvent.VK_8 to 72,
        KeyEvent.VK_9 to 74,
        KeyEvent.VK_0 to 76,
        KeyEvent.VK_Q to 61,
        KeyEvent.VK_W to 63,
        KeyEvent.VK_E to 66,
        KeyEvent.VK_R to 68,
        KeyEvent.VK_T to 70,
        KeyEvent.VK_Y to 73,
        KeyEvent.VK_I to 75,
        KeyEvent.VK_O to 78,
        KeyEvent.VK_P to 80,
    )

/** The letter of every tracker key, by the note it plays. */
internal val TRACKER_LABELS: Map<Int, String> =
    TRACKER_KEYS.entries.associate { (key, midi) -> midi to KeyEvent.getKeyText(key) }

/**
 * The pitch of a tracker key press, or null when the key is not one.
 *
 * A key that is typed in a text field is text, not a pitch, and a key with a modifier on it
 * belongs to something else (Ctrl+Z undoes, for one).
 *
 * @param e the key event.
 * @param focusOwner the component that has the keyboard, if any.
 * @return the pitch the key plays, or null when the key is not a tracker key.
 */
internal fun trackerPitch(
    e: KeyEvent,
    focusOwner: Component?,
): Pitch? {
    if (e.id != KeyEvent.KEY_PRESSED) return null
    if (e.isControlDown || e.isMetaDown || e.isAltDown) return null
    if (focusOwner is JTextComponent) return null
    val midi = TRACKER_KEYS[e.keyCode] ?: return null
    return keyPitch(midi)
}
