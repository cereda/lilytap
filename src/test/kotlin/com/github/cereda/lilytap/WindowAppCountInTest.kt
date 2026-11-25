// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val session = session(beat = 2)

        updateBeatDisplay(app, session)

        assertEquals("Count-in: 6", status(app).text)
    }

    @Test
    fun `the beat dots keep marking the bar`() {
        val app = WindowApp()
        val session = session(beat = 5)

        updateBeatDisplay(app, session)

        assertEquals(1, beatDots(app).active, "the second beat of the bar")
    }

    @Test
    fun `the take starts after the count-in`() {
        val app = WindowApp()
        val session = session(beat = 8)

        updateBeatDisplay(app, session)

        assertEquals("Recording · bar 1/1, beat 1", status(app).text)
    }

    /**
     * A session whose clock stands at the given beat of the count-in: 0 is the first beat of
     * the count-in, and the count-in is over at [config.countInBeats].
     */
    private fun session(beat: Int): Session {
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        // Half a beat into the beat, so the clock cannot fall on the edge of it.
        val start = System.nanoTime() - beat * beatNanos - beatNanos / 2
        val origin = start + config.countInBeats * beatNanos
        return Session(config, 1, start, origin, origin + 4 * beatNanos, beatNanos)
    }

    /** The badge in the header that says what the window is doing. */
    private fun status(app: WindowApp): StatusBadge {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "status" }
                ?: error("status field not found")
        field.isAccessible = true
        return field.get(app) as StatusBadge
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
