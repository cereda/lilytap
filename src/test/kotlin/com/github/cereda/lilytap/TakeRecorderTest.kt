// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TakeRecorderTest {
    private fun recorder(calibrationStatus: () -> String = { "Calibrating · 0 taps" }) =
        TakeRecorder(autoRepeat = AutoRepeat(), calibrationStatus = calibrationStatus)

    /** A session of one bar in 4/4 at 60 BPM, with a count-in of one bar. */
    private fun session(
        config: Config = Config(60.0, TimeSignature(4, 4), 1, 1),
        minRestCells: Int = 1,
    ): Session {
        val beat = 1_000_000_000L
        val origin = config.countInBeats * beat
        return Session(
            config,
            minRestCells,
            0L,
            origin,
            origin + config.bars.toLong() * config.timeSignature.pulsesPerBar * beat,
            beat,
        )
    }

    @Test
    fun `before the count-in the guide says to get ready`() {
        val guide = recorder().guide(session(), -1L, calibrating = false)
        assertEquals(-1, guide.index)
        assertEquals("Get ready...", guide.text)
        assertEquals(Mood.COUNT_IN, guide.mood)
        assertEquals(-1, guide.activeBeat)
    }

    @Test
    fun `the count-in counts the beats that are left`() {
        val recorder = recorder()
        val session = session()
        assertEquals("Count-in: 4", recorder.guide(session, 0L, false).text)
        assertEquals("Count-in: 1", recorder.guide(session, 3_000_000_000L, false).text)
        assertEquals(Mood.COUNT_IN, recorder.guide(session, 0L, false).mood)
        assertEquals(0, recorder.guide(session, 0L, false).activeBeat)
    }

    @Test
    fun `the dots mark the beat of the bar during the count-in`() {
        val recorder = recorder()
        val session = session()
        assertEquals(3, recorder.guide(session, 3_000_000_000L, false).activeBeat)
    }

    @Test
    fun `the take says where it is`() {
        val recorder = recorder()
        val session = session()
        val guide = recorder.guide(session, 4_000_000_000L, false)
        assertEquals("Recording · bar 1/1, beat 1", guide.text)
        assertEquals(Mood.REC, guide.mood)
        assertEquals(0, guide.activeBeat)
        assertEquals("Recording · bar 1/1, beat 2", recorder.guide(session, 5_000_000_000L, false).text)
        assertEquals(1, recorder.guide(session, 5_000_000_000L, false).activeBeat)
    }

    @Test
    fun `the calibration says no beat number`() {
        val recorder = recorder()
        val session = session()
        assertEquals("Get ready...", recorder.guide(session, 0L, true).text)
        assertEquals("Calibrating · 0 taps", recorder.guide(session, 4_000_000_000L, true).text)
    }

    @Test
    fun `escape gives up the take`() {
        val recorder = recorder()
        val session = session()
        assertEquals(KeyOutcome.ABORT, recorder.key(session, event(KeyEvent.VK_ESCAPE), 4_000_000_000L))
        assertEquals(
            KeyOutcome.CONSUMED,
            recorder.key(session, event(KeyEvent.VK_ESCAPE, KeyEvent.KEY_RELEASED), 4_000_000_000L),
        )
    }

    @Test
    fun `a key after the end of the take ends it`() {
        val recorder = recorder()
        val session = session()
        assertEquals(
            KeyOutcome.FINISH,
            recorder.key(session, event(KeyEvent.VK_A), session.endNanos),
        )
    }

    @Test
    fun `a modifier is swallowed and never a note`() {
        val recorder = recorder()
        val session = session()
        assertEquals(
            KeyOutcome.CONSUMED,
            recorder.key(session, event(KeyEvent.VK_SHIFT), 4_000_000_000L),
        )
        assertTrue(session.held.isEmpty())
    }

    @Test
    fun `a press starts a note and a release ends it`() {
        val recorder = recorder()
        val session = session()
        recorder.key(session, event(KeyEvent.VK_A, whenMillis = 1L), 4_000_000_000L)
        assertEquals(1, session.held.size)
        recorder.key(
            session,
            event(KeyEvent.VK_A, KeyEvent.KEY_RELEASED, whenMillis = 2L),
            4_500_000_000L,
        )
        assertTrue(session.held.isEmpty())
        assertEquals(1, session.finished.size)
    }

    @Test
    fun `a key before the downbeat is not part of the take`() {
        val recorder = recorder()
        val session = session()
        // A hair before the downbeat: it is a tap on the beat, and it is kept.
        recorder.key(session, event(KeyEvent.VK_A), 3_999_000_000L)
        assertEquals(1, session.held.size)
        // Further back in the count-in: that is not part of the take.
        val other = session()
        recorder.key(other, event(KeyEvent.VK_A), 2_000_000_000L)
        assertTrue(other.held.isEmpty())
    }

    @Test
    fun `the key r is a silence`() {
        val recorder = recorder()
        val session = session()
        recorder.key(session, event(KeyEvent.VK_R), 4_000_000_000L)
        assertEquals(1, session.rests.size)
        assertTrue(session.held.isEmpty())
    }

    @Test
    fun `the score of a take holds the notes that were played`() {
        val recorder = recorder()
        val session = session()
        recorder.key(session, event(KeyEvent.VK_A, whenMillis = 1L), 4_000_000_000L)
        recorder.key(
            session,
            event(KeyEvent.VK_A, KeyEvent.KEY_RELEASED, whenMillis = 2L),
            4_500_000_000L,
        )
        recorder.key(session, event(KeyEvent.VK_B, whenMillis = 3L), 5_000_000_000L)
        recorder.key(
            session,
            event(KeyEvent.VK_B, KeyEvent.KEY_RELEASED, whenMillis = 4L),
            5_500_000_000L,
        )
        val take = recorder.transcription(session)
        assertEquals(2, take.notes)
        // The two quarter notes, and the silence that follows them until the end of the bar.
        assertEquals("c'4 c'4 r2 |", take.transcription.score.lilypond)
    }

    @Test
    fun `a note that is still held lasts until the end of the take`() {
        val recorder = recorder()
        val session = session()
        recorder.key(session, event(KeyEvent.VK_A), 4_000_000_000L)
        val take = recorder.transcription(session)
        assertEquals(1, take.notes)
        assertTrue(session.held.isEmpty())
    }

    /** A key event of the take. */
    private fun event(
        keyCode: Int,
        id: Int = KeyEvent.KEY_PRESSED,
        whenMillis: Long = 0L,
    ) = KeyEvent(
        JPanel(),
        id,
        whenMillis,
        0,
        keyCode,
        KeyEvent.CHAR_UNDEFINED,
    )
}
