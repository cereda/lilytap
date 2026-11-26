// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The timing of a take in the window: what the status line says, what the tooltip carries and
 * the marks the strip gets.
 */
class WindowAppTimingTest {
    private val config = Config(60.0, TimeSignature(4, 4), 1, 1)

    @Test
    fun `the status line says how the take was played`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)

        finish(app)

        assertEquals("Done. 40 ms late · spread 0 ms · try latency 40", status(app).text)
    }

    @Test
    fun `the tooltip carries the numbers of the take`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)

        finish(app)

        val tooltip = assertNotNull(status(app).toolTipText, "the badge has a tooltip")
        assertTrue(tooltip.contains("4 late"), "the counts are there: $tooltip")
        assertTrue(tooltip.contains("spread"), "the spread is there: $tooltip")
        assertTrue(tooltip.contains("+40"), "the error of every note is there: $tooltip")
    }

    @Test
    fun `a take with no bias says so`() {
        val app = WindowApp()
        take(app, 60.0, -60.0, 60.0, -60.0)

        finish(app)

        assertEquals("Done. no bias · spread 60 ms", status(app).text)
    }

    @Test
    fun `the report goes when the score goes`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)
        finish(app)
        assertNotNull(status(app).toolTipText)

        val clear = WindowApp::class.java.getDeclaredMethod("clearScore")
        clear.isAccessible = true
        clear.invoke(app)

        assertNull(status(app).toolTipText, "the report is about the score of the take")
    }

    @Test
    fun `a take of two notes says nothing about the timing`() {
        val app = WindowApp()
        take(app, 40.0, 40.0)

        finish(app)

        assertEquals("Done.", status(app).text)
    }

    @Test
    fun `the strip carries the marks of the take`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)

        finish(app)

        val timing = assertNotNull(notation(app).timing, "the strip carries the timing of the take")
        assertEquals(4, timing.notes)
    }

    @Test
    fun `a take with no notes has no marks`() {
        val app = WindowApp()
        take(app)

        finish(app)

        assertNull(notation(app).timing, "there is nothing to mark")
    }

    @Test
    fun `an edit of the score takes the marks with it`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)
        finish(app)
        assertNotNull(notation(app).timing)

        // A real edit: a pitch on the first note.
        selectTheFirstBlock(app)
        notation(app).setSelectedPitch(Pitch(PitchStep.C, 0, 5))

        assertNull(notation(app).timing, "the marks belong to the take of the score")
    }

    @Test
    fun `a revert brings the marks back`() {
        val app = WindowApp()
        take(app, 40.0, 40.0, 40.0, 40.0)
        finish(app)

        // An edit takes the marks away...
        selectTheFirstBlock(app)
        notation(app).setSelectedPitch(Pitch(PitchStep.C, 0, 5))
        assertNull(notation(app).timing)

        // ...and the Revert, which puts the take back, brings them back.
        val revert = WindowApp::class.java.getDeclaredMethod("revertScore")
        revert.isAccessible = true
        revert.invoke(app)

        assertNotNull(notation(app).timing, "the score of the take carries the marks again")
    }

    /** Clicks the first block of the score on the strip, so that it is selected. */
    private fun selectTheFirstBlock(app: WindowApp) {
        val notation = notation(app)
        val x = grid(app).xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
    }

    /** The grid the strip and the timeline share. */
    private fun grid(app: WindowApp): GridView {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "grid" }
                ?: error("grid field not found")
        field.isAccessible = true
        return field.get(app) as GridView
    }

    /**
     * A take of the window, with its notes the given number of milliseconds after the beat.
     *
     * @param app the window.
     * @param offsets the error of every note of the take.
     * @return the session of the take.
     */
    private fun take(
        app: WindowApp,
        vararg offsets: Double,
    ): Session {
        val beatNanos = 1_000_000_000L
        val origin = System.nanoTime()
        val session =
            Session(
                config,
                1,
                origin - config.countInBeats * beatNanos,
                origin,
                origin + config.totalCells * beatNanos,
                beatNanos,
            )
        // The metronome of the take: a thread that is already over.
        session.ticker = Thread {}
        for ((index, offset) in offsets.withIndex()) {
            val down = index * 1000.0 + offset
            session.finished += HeldNote(down, down + 100.0)
        }
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "session" }
                ?: error("session field not found")
        field.isAccessible = true
        field.set(app, session)
        return session
    }

    /** Ends the take of the window, as the clock does when the bar is over. */
    private fun finish(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("finish")
        method.isAccessible = true
        method.invoke(app)
    }

    /** The badge in the header that says what the window is doing. */
    private fun status(app: WindowApp): StatusBadge {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "status" }
                ?: error("status field not found")
        field.isAccessible = true
        return field.get(app) as StatusBadge
    }

    /** The score strip of the window. */
    private fun notation(app: WindowApp): NotationPanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "notation" }
                ?: error("notation field not found")
        field.isAccessible = true
        return field.get(app) as NotationPanel
    }
}
