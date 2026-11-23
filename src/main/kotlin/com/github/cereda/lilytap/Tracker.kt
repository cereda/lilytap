// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The tracker: the keys of the keyboard that play the piano of the pitches panel.
 *
 * The keys name the notes of the scale instead of fixed pitches: the number row carries the
 * white keys (1 is C, 2 is D, and so on up to 7, which is B) and the top row the black ones
 * (Q, W, E, R and T). The black keys are spelled the way the key of the piece wants them:
 * they are the sharps in a sharp key and the flats in a flat one. The octave they sound is
 * the live one: 8 moves it down, 9 moves it up and 0 puts it back on the note that is
 * selected.
 *
 * The number row climbs to the right, so the smaller of the two numbers is the lower octave:
 * the keyboard reads like the piano, where the low keys are on the left.
 *
 * The keys of the score editor (S, X and M) keep their meaning, so the tracker lives on the
 * rows above them.
 */

import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.text.JTextComponent

/**
 * The note every tracker key names, by its key code: the pitch class it sounds.
 *
 * The keys are read from the key code, so they work with the caps lock on and whatever the
 * keyboard layout says: the key that is physically in the same place names the same note.
 */
internal val TRACKER_KEYS: Map<Int, Int> =
    mapOf(
        KeyEvent.VK_1 to 0,
        KeyEvent.VK_2 to 2,
        KeyEvent.VK_3 to 4,
        KeyEvent.VK_4 to 5,
        KeyEvent.VK_5 to 7,
        KeyEvent.VK_6 to 9,
        KeyEvent.VK_7 to 11,
        KeyEvent.VK_Q to 1,
        KeyEvent.VK_W to 3,
        KeyEvent.VK_E to 6,
        KeyEvent.VK_R to 8,
        KeyEvent.VK_T to 10,
    )

/** The letter of every tracker key, by the note it names. */
internal val TRACKER_LABELS: Map<Int, String> =
    TRACKER_KEYS.entries.associate { (key, pitchClass) -> pitchClass to KeyEvent.getKeyText(key) }

/** The key that moves the tracker an octave down. */
internal const val OCTAVE_DOWN = KeyEvent.VK_8

/** The key that moves the tracker an octave up. */
internal const val OCTAVE_UP = KeyEvent.VK_9

/** The key that puts the tracker back on the octave of the selected note. */
internal const val OCTAVE_RESET = KeyEvent.VK_0

/**
 * What a tracker key does.
 *
 * A note is written on the selected note; the other keys move the octave the tracker plays.
 */
internal sealed interface TrackerAction {
    /** A note to write. */
    data class Note(
        /** The pitch the note sounds. */
        val pitch: Pitch,
    ) : TrackerAction

    /** The tracker moves an octave up. */
    data object Up : TrackerAction

    /** The tracker moves an octave down. */
    data object Down : TrackerAction

    /** The tracker goes back to the octave of the selected note. */
    data object Reset : TrackerAction
}

/**
 * The action a key press asks for, or null when the key is not one.
 *
 * A key that is typed in a text field is text, not a pitch, and a key with a modifier on it
 * belongs to something else (Ctrl+Z undoes, for one).
 *
 * @param e the key event.
 * @param focusOwner the component that has the keyboard, if any.
 * @param octave the octave the tracker plays.
 * @param key the key of the piece: it says how the notes are spelled.
 * @return the action of the key, or null when the key is not a tracker key.
 */
internal fun trackerAction(
    e: KeyEvent,
    focusOwner: Component?,
    octave: Int,
    key: KeySignature = KeySignature.C_MAJOR,
): TrackerAction? {
    if (e.id != KeyEvent.KEY_PRESSED) return null
    if (e.isControlDown || e.isMetaDown || e.isAltDown) return null
    if (focusOwner is JTextComponent) return null
    val move =
        when (e.keyCode) {
            OCTAVE_UP -> TrackerAction.Up
            OCTAVE_DOWN -> TrackerAction.Down
            OCTAVE_RESET -> TrackerAction.Reset
            else -> null
        }
    if (move != null) return move
    val pitchClass = TRACKER_KEYS[e.keyCode] ?: return null
    if (octave !in Pitch.MIN_OCTAVE..Pitch.MAX_OCTAVE) return null
    return TrackerAction.Note(keyPitch(12 * (octave + 1) + pitchClass, key))
}

/**
 * The state of the tracker: the octave it plays, and the octave it goes back to.
 *
 * The anchor is the octave of the note that is selected: clicking a block puts the tracker in
 * that octave, and the reset key brings it back there. The octave moves on its own when the
 * user asks for it, and it stays where it is while the melody is filled in.
 *
 * The anchor is empty when there is no note to point at: the reset key then does nothing,
 * because there is nowhere to go back to.
 */
internal class TrackerState {
    /** The octave the tracker plays. */
    var octave: Int = Pitch.DEFAULT.octave
        private set

    /** The octave the tracker goes back to when it is reset, when there is one. */
    var anchor: Int? = Pitch.DEFAULT.octave
        private set

    /** Moves the tracker an octave up. */
    fun up() {
        moveTo(octave + 1)
    }

    /** Moves the tracker an octave down. */
    fun down() {
        moveTo(octave - 1)
    }

    /**
     * Puts the tracker on an octave, leaving the anchor alone.
     *
     * @param octave the octave the tracker plays.
     */
    fun moveTo(octave: Int) {
        this.octave = octave.coerceIn(Pitch.MIN_OCTAVE, Pitch.MAX_OCTAVE)
    }

    /** Puts the tracker back on the anchored octave, when there is one. */
    fun reset() {
        anchor?.let { octave = it }
    }

    /**
     * Puts the tracker on the octave of a note: the anchor and the live octave.
     *
     * @param octave the octave of the note.
     */
    fun anchorTo(octave: Int) {
        val bounded = octave.coerceIn(Pitch.MIN_OCTAVE, Pitch.MAX_OCTAVE)
        anchor = bounded
        this.octave = bounded
    }

    /**
     * Forgets the anchor and goes back to the middle of the keyboard.
     *
     * A new take is a fresh score: the note the tracker was anchored on is gone.
     */
    fun clear() {
        anchor = null
        octave = Pitch.DEFAULT.octave
    }
}

/**
 * The tracker of the window: the keys of the keyboard that play the piano of the pitches
 * panel.
 *
 * The tracker owns the octave the keys play and the octave they go back to. The window tells
 * it when the selection moves to another note and when a new score arrives; the tracker says
 * which octave it is in on the panel.
 *
 * The stepper of the panel moves the tracker, and the tracker moves the stepper: the two are
 * the same octave seen from two sides.
 *
 * @param panel the panel that shows the octave.
 * @param onPitch called with the pitch a tracker key plays.
 */
internal class Tracker(
    private val panel: PitchesPanel,
    private val onPitch: (Pitch) -> Unit,
) {
    /** The state of the tracker: the octave it plays and the one it goes back to. */
    private val state = TrackerState()

    /** The key of the piece: it says how the notes of the tracker are spelled. */
    var key: KeySignature = KeySignature.C_MAJOR

    init {
        // The stepper of the panel is the octave of the tracker, seen from the mouse.
        panel.octave.onChange = { value -> moveTo(value.toInt()) }
        panel.octave.onMiddleClick = { reset() }
    }

    /** The octave the tracker plays. */
    val octave: Int get() = state.octave

    /** The octave the tracker goes back to, when there is a note to go back to. */
    val anchor: Int? get() = state.anchor

    /**
     * Handles a key press: it writes a pitch, or it moves the octave the keys play.
     *
     * @param e the key event.
     * @return true when the key was a tracker key and was used.
     */
    fun handleKey(e: KeyEvent): Boolean {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return when (val action = trackerAction(e, owner, state.octave, key)) {
            null -> {
                false
            }

            is TrackerAction.Note -> {
                onPitch(action.pitch)
                true
            }

            TrackerAction.Up -> {
                state.up()
                sayOctave()
                true
            }

            TrackerAction.Down -> {
                state.down()
                sayOctave()
                true
            }

            TrackerAction.Reset -> {
                state.reset()
                sayOctave()
                true
            }
        }
    }

    /**
     * Puts the tracker in the octave of a note: the anchor and the live octave.
     *
     * @param octave the octave of the note.
     */
    fun anchorTo(octave: Int) {
        state.anchorTo(octave)
        sayOctave()
    }

    /**
     * Puts the tracker on an octave, leaving the anchor alone.
     *
     * @param octave the octave the tracker plays.
     */
    fun moveTo(octave: Int) {
        state.moveTo(octave)
        sayOctave()
    }

    /** Puts the tracker back on the anchored octave. */
    fun reset() {
        state.reset()
        sayOctave()
    }

    /** Forgets the anchor and goes back to the middle of the keyboard. */
    fun clear() {
        state.clear()
        sayOctave()
    }

    /** Says which octave the tracker plays on the panel. */
    fun sayOctave() {
        panel.setOctave(state.octave, state.anchor)
    }
}
