// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The count-in in the window: the status counts the beats of the lead-in down, and the beat
 * dots keep marking the bar while the lead-in lasts.
 *
 * 4/4 at 180 BPM is the case where the count-in grows: a bar lasts 1.33 s, so the count-in
 * takes two bars - eight beats - to give the player time to settle in.
 */
class WindowAppCountInTest {
    private val config = Config(180.0, TimeSignature(4, 4), 1, 1)

    @Test
    fun `the status counts the beats of the count-in down`() {
        val app = WindowApp()
        val session = session(app, beat = 2)

        updateBeatDisplay(app, session)

        assertEquals("Count-in: 6", status(app).text)
    }

    @Test
    fun `the beat dots keep marking the bar`() {
        val app = WindowApp()
        val session = session(app, beat = 5)

        updateBeatDisplay(app, session)

        assertEquals(1, beatDots(app).active, "the second beat of the bar")
    }

    @Test
    fun `the take starts after the count-in`() {
        val app = WindowApp()
        val session = session(app, beat = 8)

        updateBeatDisplay(app, session)

        assertEquals("Recording · bar 1/1, beat 1", status(app).text)
    }

    @Test
    fun `a key pressed during the count-in is not recorded`() {
        val app = WindowApp()
        val session = session(app, beat = 0)

        press(app, KeyEvent.VK_A)
        release(app, KeyEvent.VK_A)

        assertTrue(session.held.isEmpty(), "the key should not be held")
        assertTrue(session.finished.isEmpty(), "the key should not be a note")
        assertTrue(session.rests.isEmpty(), "the key should not be a rest")
    }

    @Test
    fun `a key pressed after the count-in is recorded`() {
        val app = WindowApp()
        val session = session(app, beat = 8)

        press(app, KeyEvent.VK_A)
        assertEquals(1, session.held.size, "the key should be held")

        release(app, KeyEvent.VK_A)
        assertEquals(1, session.finished.size, "the key should be a note")
    }

    /**
     * A session whose clock stands at the given beat of the count-in: 0 is the first beat of
     * the count-in, and the count-in is over at [config.countInBeats].
     */
    private fun session(
        app: WindowApp,
        beat: Int,
    ): Session {
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        // Half a beat into the beat, so the clock cannot fall on the edge of it.
        val start = System.nanoTime() - beat * beatNanos - beatNanos / 2
        val origin = start + config.countInBeats * beatNanos
        val session = Session(config, 1, start, origin, origin + 4 * beatNanos, beatNanos)
        // The window takes the keys of the session it is running.
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "session" }
                ?: error("session field not found")
        field.isAccessible = true
        field.set(app, session)
        return session
    }

    /** A key press, as the window would receive it. */
    private fun press(
        app: WindowApp,
        keyCode: Int,
    ) {
        key(app, KeyEvent.KEY_PRESSED, keyCode, 1000)
    }

    /** A key release, as the window would receive it. */
    private fun release(
        app: WindowApp,
        keyCode: Int,
    ) {
        key(app, KeyEvent.KEY_RELEASED, keyCode, 1100)
    }

    /** Sends a key event to the window. */
    private fun key(
        app: WindowApp,
        id: Int,
        keyCode: Int,
        whenMillis: Long,
    ) {
        val method = WindowApp::class.java.getDeclaredMethod("handleKey", KeyEvent::class.java)
        method.isAccessible = true
        val frame = field(app, "frame")
        val event =
            KeyEvent(
                frame as java.awt.Component,
                id,
                whenMillis,
                0,
                keyCode,
                KeyEvent.CHAR_UNDEFINED,
            )
        method.invoke(app, event)
    }

    /** The badge in the header that says what the window is doing. */
    private fun status(app: WindowApp): StatusBadge {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "status" }
                ?: error("status field not found")
        field.isAccessible = true
        return field.get(app) as StatusBadge
    }

    /** A field of the window, by its name. */
    private fun field(
        app: WindowApp,
        name: String,
    ): Any {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app)!!
    }

    /** The dots in the header, one for each beat of the bar. */
    private fun beatDots(app: WindowApp): BeatDots {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "beatDots" }
                ?: error("beatDots field not found")
        field.isAccessible = true
        return field.get(app) as BeatDots
    }

    /** Tells the window what the clock says, as the frame timer does. */
    private fun updateBeatDisplay(
        app: WindowApp,
        session: Session,
    ) {
        val method = WindowApp::class.java.getDeclaredMethod("updateBeatDisplay", Session::class.java)
        method.isAccessible = true
        method.invoke(app, session)
    }
}
