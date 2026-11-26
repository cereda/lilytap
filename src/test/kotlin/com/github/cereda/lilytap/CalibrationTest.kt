// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationTest {
    @Test
    fun `a round is not running before it begins`() {
        val calibration = Calibration()
        assertFalse(calibration.running)
        assertEquals(0, calibration.taps)
    }

    @Test
    fun `a round remembers the taps it collects`() {
        val calibration = Calibration()
        calibration.begin()
        assertTrue(calibration.running)
        assertTrue(calibration.tap(0.0, 1000.0, 8))
        assertTrue(calibration.tap(1050.0, 1000.0, 8))
        assertEquals(2, calibration.taps)
    }

    @Test
    fun `a tap outside the bars is not measured`() {
        val calibration = Calibration()
        calibration.begin()
        assertFalse(calibration.tap(-1.0, 1000.0, 8))
        assertFalse(calibration.tap(8000.0, 1000.0, 8))
        assertEquals(0, calibration.taps)
    }

    @Test
    fun `a new round forgets the taps of the last one`() {
        val calibration = Calibration()
        calibration.begin()
        calibration.tap(0.0, 1000.0, 8)
        calibration.begin()
        assertEquals(0, calibration.taps)
    }

    @Test
    fun `giving up the round forgets the taps`() {
        val calibration = Calibration()
        calibration.begin()
        calibration.tap(0.0, 1000.0, 8)
        calibration.cancel()
        assertFalse(calibration.running)
        assertEquals(0, calibration.taps)
    }

    @Test
    fun `the status counts the taps of the round`() {
        val calibration = Calibration()
        assertEquals("Calibrating · tap with the beeps", calibration.status())
        calibration.begin()
        calibration.tap(0.0, 1000.0, 8)
        assertEquals("Calibrating · 1 taps", calibration.status())
    }

    @Test
    fun `the latency grows by the median of the taps`() {
        val calibration = Calibration()
        calibration.begin()
        calibration.tap(100.0, 1000.0, 8)
        calibration.tap(120.0, 1000.0, 8)
        calibration.tap(110.0, 1000.0, 8)
        assertEquals(110.0, calibration.latencyAfter(0.0))
    }

    @Test
    fun `a round without taps leaves the latency alone`() {
        val calibration = Calibration()
        calibration.begin()
        assertEquals(50.0, calibration.latencyAfter(50.0))
    }

    @Test
    fun `calibration includes the first bar`() {
        assertEquals(0.0, calibrationOffsetMs(0.0, 1000.0, 4))
        assertEquals(40.0, calibrationOffsetMs(1040.0, 1000.0, 4))
        assertEquals(-20.0, calibrationOffsetMs(2980.0, 1000.0, 4))
        assertNull(calibrationOffsetMs(4100.0, 1000.0, 4))
        assertNull(calibrationOffsetMs(-10.0, 1000.0, 4))
    }

    @Test
    fun `calibration adds the median tap offset to the latency`() {
        // A tap 50 ms after the beat it followed: the latency was 50 ms too low.
        assertEquals(250.0, calibratedLatency(200.0, listOf(50.0)))
        // An odd number of taps: the middle one decides.
        assertEquals(260.0, calibratedLatency(0.0, listOf(250.0, 260.0, 300.0)))
        // An even number of taps: the middle of the two.
        assertEquals(275.0, calibratedLatency(0.0, listOf(250.0, 300.0)))
        // One stray tap does not move the result.
        assertEquals(265.0, calibratedLatency(0.0, listOf(250.0, 260.0, 270.0, 900.0)))
    }

    @Test
    fun `a measurement outside the range keeps the old latency`() {
        // A tap before the beat it followed would take the latency below zero.
        assertEquals(200.0, calibratedLatency(200.0, listOf(-450.0)))
        // A tap exactly one latency early brings it to zero, which is still a valid value.
        assertEquals(0.0, calibratedLatency(200.0, listOf(-200.0)))
        // A tap that would push it past the maximum is dropped as well.
        assertEquals(400.0, calibratedLatency(400.0, listOf(400.0)))
    }

    @Test
    fun `the calibration runs on a fixed slow grid`() {
        // One beat per second: a tap that is late by up to half a beat still belongs to the
        // beat it followed, whatever the tempo of the take is.
        assertEquals(60.0, CALIBRATION_BPM)
        assertEquals(1000.0, 60_000.0 / CALIBRATION_BPM)
        // Eight beats to tap along with: enough for a median, short enough to stay a round.
        assertEquals(8, CALIBRATION_BARS * CALIBRATION_SIGNATURE.beats)
    }

    @Test
    fun `the calibration grid leaves room for a late tap`() {
        // A tap half a beat late still belongs to the beat it followed.
        assertEquals(450.0, calibrationOffsetMs(450.0, 1000.0, 8))
        assertEquals(450.0, calibrationOffsetMs(1450.0, 1000.0, 8))
    }
}
