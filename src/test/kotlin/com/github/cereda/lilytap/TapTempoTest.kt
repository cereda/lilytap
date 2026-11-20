// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tap tempo: the taps of a round, and the tempo they make. */
class TapTempoTest {
    /** A tap at the given second of the round. */
    private fun at(seconds: Double): Long = (seconds * 1_000_000_000L).toLong()

    @Test
    fun `the first tap alone says nothing about the tempo`() {
        val tempo = TapTempo()
        assertNull(tempo.tap(at(0.0)))
    }

    @Test
    fun `two taps give the tempo of the interval between them`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        // Half a second between the taps: 120 BPM.
        assertEquals(120, tempo.tap(at(0.5)))
    }

    @Test
    fun `the tempo follows the taps as they come`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        assertEquals(120, tempo.tap(at(0.5)))
        assertEquals(120, tempo.tap(at(1.0)))
    }

    @Test
    fun `one slipped tap does not wreck the tempo`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        tempo.tap(at(0.5))
        tempo.tap(at(1.0))
        // A slip: the fourth tap comes early.
        tempo.tap(at(1.2))
        // The median of the intervals is the 500 ms one: 120 BPM. The average would say 150.
        assertEquals(120, tempo.tap(at(1.7)))
    }

    @Test
    fun `a pause starts a new sequence`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        tempo.tap(at(0.5))
        // A long pause: the tap after it says nothing about the old round.
        assertNull(tempo.tap(at(5.0)), "the first tap of a new sequence has no interval")
        assertEquals(60, tempo.tap(at(6.0)), "the new round runs at its own tempo")
    }

    @Test
    fun `the slowest tempo can still be tapped`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        // 20 BPM is three seconds between the taps: the window has to hold it.
        assertEquals(20, tempo.tap(at(3.0)))
        assertEquals(20, tempo.tap(at(6.0)))
    }

    @Test
    fun `the round listens while the taps are close together`() {
        val tempo = TapTempo()
        assertFalse(tempo.isListening(at(0.0)), "nothing was tapped yet")
        tempo.tap(at(0.0))
        assertTrue(tempo.isListening(at(1.0)))
        assertFalse(tempo.isListening(at(4.0)), "the round is over after three seconds of silence")
    }

    @Test
    fun `the tempo stays inside the range the app can hold`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        // Ten taps a second: far above the fastest tempo the app has.
        assertEquals(300, tempo.tap(at(0.1)))
        tempo.reset()
        tempo.tap(at(0.0))
        // One tap every three seconds: the slowest tempo the app has.
        assertEquals(20, tempo.tap(at(3.0)))
    }

    @Test
    fun `the tempo is a whole number`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        // 0.44 s between the taps: 136.36 BPM, so 136.
        assertEquals(136, tempo.tap(at(0.44)))
    }

    @Test
    fun `forgetting the taps starts a new round`() {
        val tempo = TapTempo()
        tempo.tap(at(0.0))
        tempo.tap(at(0.5))
        tempo.reset()
        assertFalse(tempo.isListening(at(0.6)))
        assertNull(tempo.tap(at(0.7)))
    }
}
