// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The timing of a take: how far each note was from the beat, and what it adds up to.
 *
 * The tests are about the numbers and about the words: the window shows them, and what it
 * says has to mean what it says.
 */
class TimingReportTest {
    private fun cfg(
        bars: Int = 1,
        sub: Int = 1,
        latency: Double = 0.0,
    ) = Config(60.0, TimeSignature(4, 4), sub, bars, latency)

    /** A note pressed at [ms] and let go a moment later. */
    private fun held(vararg ms: Int) = ms.map { HeldNote(it.toDouble(), it.toDouble() + 100.0) }

    @Test
    fun `the report says how late the notes were`() {
        val take = transcribeHeld(held(40, 1040, 2040), cfg(), 1)
        val report = assertNotNull(take.timing)
        assertEquals(40.0, assertNotNull(report.meanMs), 1e-9)
        assertEquals(0.0, assertNotNull(report.spreadMs), 1e-9)
        assertEquals(3, report.late)
        assertEquals(0, report.early)
        assertEquals(0, report.onTime)
        assertEquals(3, report.notes)
    }

    @Test
    fun `a note within the tolerance is on time`() {
        val take = transcribeHeld(held(0, 1025, 1975), cfg(), 1)
        val report = assertNotNull(take.timing)
        assertEquals(3, report.onTime)
        assertEquals(0, report.late)
        assertEquals(0, report.early)
    }

    @Test
    fun `a note outside the tolerance is late or early`() {
        val take = transcribeHeld(held(0, 1026, 1974), cfg(), 1)
        val report = assertNotNull(take.timing)
        assertEquals(1, report.onTime)
        assertEquals(1, report.late)
        assertEquals(1, report.early)
    }

    @Test
    fun `the spread says how steady the timing was`() {
        val take = transcribeHeld(held(-30, 1000, 2030), cfg(), 1)
        val report = assertNotNull(take.timing)
        assertEquals(0.0, assertNotNull(report.meanMs), 1e-9)
        // The notes are 30 ms early, on the beat and 30 ms late: they are 30 ms from the mean.
        assertEquals(30.0, assertNotNull(report.spreadMs), 1e-9)
    }

    @Test
    fun `a silence is not judged`() {
        // A rest in the middle of the take: the silence is not played on time, so it does not
        // count towards the timing of the notes.
        val notes =
            listOf(
                HeldNote(40.0, 140.0),
                HeldNote(1000.0, 1100.0, TapKind.REST),
                HeldNote(2040.0, 2140.0),
                HeldNote(3040.0, 3140.0),
            )
        val report = assertNotNull(transcribeHeld(notes, cfg(), 1).timing)
        assertEquals(3, report.notes)
        assertEquals(40.0, assertNotNull(report.meanMs), 1e-9)
    }

    @Test
    fun `the report follows the events of the score`() {
        val notes =
            listOf(
                HeldNote(0.0, 100.0),
                HeldNote(1000.0, 1100.0, TapKind.REST),
                HeldNote(2000.0, 2100.0),
            )
        val take = transcribeHeld(notes, cfg(), 1)
        val report = assertNotNull(take.timing)
        // The score is a note, a rest, a note and the rest that closes the bar: the rests
        // have no error.
        assertEquals(4, report.eventOffsetsMs.size)
        assertEquals(0.0, assertNotNull(report.eventOffsetsMs[0]), 1e-9)
        assertNull(report.eventOffsetsMs[1])
        assertEquals(0.0, assertNotNull(report.eventOffsetsMs[2]), 1e-9)
        assertNull(report.eventOffsetsMs[3])
    }

    @Test
    fun `a take with no notes has no report`() {
        val notes = listOf(HeldNote(0.0, 100.0, TapKind.REST))
        assertNull(transcribeHeld(notes, cfg(), 1).timing)
    }

    @Test
    fun `the mean is the error that is left after the latency`() {
        // 40 ms of latency compensates the 40 ms of lateness exactly: nothing is left.
        val take = transcribeHeld(held(40, 1040, 2040), cfg(latency = 40.0), 1)
        assertEquals(0.0, assertNotNull(assertNotNull(take.timing).meanMs), 1e-9)
        assertEquals(0.0, assertNotNull(take.meanOffsetMs), 1e-9)
    }

    @Test
    fun `the headline says the bias of the take`() {
        val late = assertNotNull(transcribeHeld(held(40, 1040, 2040), cfg(), 1).timing)
        assertEquals("40 ms late", late.headline())
        val early = assertNotNull(transcribeHeld(held(-40, 960, 1960), cfg(), 1).timing)
        assertEquals("40 ms early", early.headline())
        // The notes were scattered around the beat: the take has no bias.
        val straight = assertNotNull(transcribeHeld(held(0, 1000, 2000), cfg(), 1).timing)
        assertEquals("no bias", straight.headline())
        val scattered = assertNotNull(transcribeHeld(held(60, 940, 2060, 2940), cfg(), 1).timing)
        assertEquals("no bias", scattered.headline())
    }

    @Test
    fun `the details carry the numbers of the take`() {
        val report = assertNotNull(transcribeHeld(held(40, 1040, 2040), cfg(), 1).timing)
        val details = report.details()
        assertTrue(details.contains("+40"), "the mean is in the details: $details")
        assertTrue(details.contains("3 late"), "the counts are in the details: $details")
        assertTrue(details.contains("spread"), "the spread is in the details: $details")
    }

    @Test
    fun `one or two notes are not statistics`() {
        val two = assertNotNull(transcribeHeld(held(0, 1000), cfg(), 1).timing)
        assertTrue(!two.hasStatistics, "two notes say nothing about a player")
        val three = assertNotNull(transcribeHeld(held(0, 1000, 2000), cfg(), 1).timing)
        assertTrue(three.hasStatistics, "three notes are a timing")
    }

    @Test
    fun `the error of one note reads in words`() {
        val report = assertNotNull(transcribeHeld(held(0, 1000, 2000), cfg(), 1).timing)
        assertEquals("on time", report.describe(10.0))
        assertEquals("30 ms late", report.describe(30.0))
        assertEquals("30 ms early", report.describe(-30.0))
    }
}
