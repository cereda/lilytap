// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RhythmTest {
    private fun cfg(
        bars: Int = 1,
        ts: TimeSignature = TimeSignature(4, 4),
        sub: Int = 2,
        bpm: Double = 60.0,
        latency: Double = 0.0,
    ) = Config(bpm, ts, sub, bars, latency)

    private fun notes(vararg ms: Int) = ms.map { Tap(it.toDouble(), TapKind.NOTE) }

    private fun lily(
        taps: List<Tap>,
        config: Config,
    ) = transcribe(taps, config).lilypond

    @Test
    fun `a cell is a whole number of units and keeps its time`() {
        val config = cfg(bpm = 63.0)
        assertEquals(8, config.cellUnits)
        assertEquals(64, config.totalUnits)
        // Splitting a cell into units does not move it: the cell still ends at the same time.
        assertEquals(config.cellMs, config.cellUnits * config.unitMs)
        assertEquals(config.totalCells * config.cellMs, config.totalUnits * config.unitMs)
    }

    @Test
    fun `four taps on the beat give four quarter notes`() {
        assertEquals("c'4 c'4 c'4 c'4 |", lily(notes(0, 1000, 2000, 3000), cfg()))
    }

    @Test
    fun `skipping a beat extends the previous note`() {
        // tap, (hold), tap, tap  ->  half note, quarter, quarter
        assertEquals("c'2 c'4 c'4 |", lily(notes(0, 2000, 3000), cfg()))
    }

    @Test
    fun `sloppy timing within half a cell is snapped to the grid`() {
        // cell = 500 ms at 60 BPM with 2 subdivisions, so +-249 ms is tolerated
        assertEquals("c'4 c'4 c'4 c'4 |", lily(notes(-80, 1120, 1990, 3200), cfg()))
    }

    @Test
    fun `half-beat taps produce eighth notes`() {
        assertEquals("c'8 c'8 c'4 c'2 |", lily(notes(0, 500, 1000, 2000), cfg()))
    }

    @Test
    fun `sixteenths with 4 subdivisions`() {
        assertEquals("c'16 c'16 c'8 c'4 c'2 |", lily(notes(0, 250, 500, 1000, 2000), cfg(sub = 4)))
    }

    @Test
    fun `rest key marks a rest that lasts until the next tap`() {
        val taps =
            listOf(
                Tap(0.0, TapKind.NOTE),
                Tap(1000.0, TapKind.REST),
                Tap(2000.0, TapKind.NOTE),
            )
        assertEquals("c'4 r4 c'2 |", lily(taps, cfg()))
    }

    @Test
    fun `a late first tap becomes a leading rest`() {
        assertEquals("r4 c'2. |", lily(notes(1000), cfg()))
    }

    @Test
    fun `no taps is a full bar of rest`() {
        assertEquals("r1 |", lily(emptyList(), cfg()))
    }

    @Test
    fun `notes crossing a barline are split and tied`() {
        assertEquals("r2. c'4 ~ |\nc'1 |", lily(notes(3000), cfg(bars = 2)))
    }

    @Test
    fun `dotted notes in three four`() {
        assertEquals("c'2. |", lily(notes(0), cfg(ts = TimeSignature(3, 4))))
    }

    @Test
    fun `six eight counts the dotted quarter beats`() {
        // The beat in 6/8 is the dotted quarter: at 60 BPM a bar lasts two seconds, and the
        // two beats of the bar are a second apart.
        assertEquals(
            "c'4. c'4. |",
            lily(notes(0, 1000), cfg(ts = TimeSignature(6, 8), sub = 1)),
        )
    }

    @Test
    fun `second tap in the same cell replaces the first, regardless of input order`() {
        val taps = listOf(Tap(1000.0, TapKind.NOTE), Tap(1100.0, TapKind.REST), Tap(0.0, TapKind.NOTE))
        assertEquals("c'4 r2. |", lily(taps, cfg()))
    }

    @Test
    fun `taps during count-in or after the end are ignored`() {
        assertEquals("c'4 c'4 c'4 c'4 |", lily(notes(-3000, -2000, 0, 1000, 2000, 3000, 4000, 9000), cfg()))
    }

    @Test
    fun `latency compensation shifts taps back`() {
        // User consistently presses 400 ms after the click: without compensation the
        // taps land half a cell away, with latency=400 they snap to the right place.
        val late = notes(400, 1400, 2400, 3400)
        assertEquals("c'4 c'4 c'4 c'4 |", lily(late, cfg(latency = 400.0)))
    }

    @Test
    fun `mean offset reports how late the taps were`() {
        val result = transcribe(notes(40, 1040, 2040, 3040), cfg())
        assertEquals(40.0, assertNotNull(result.meanOffsetMs), 1e-9)
    }

    @Test
    fun `the mean offset is the error that is left after the latency`() {
        val late = notes(40, 1040, 2040, 3040)
        // 40 ms of latency compensates the 40 ms of lateness exactly: nothing is left.
        assertEquals(0.0, assertNotNull(transcribe(late, cfg(latency = 40.0)).meanOffsetMs), 1e-9)
        // 20 ms of latency leaves 20 ms of lateness: that is the correction to add.
        assertEquals(20.0, assertNotNull(transcribe(late, cfg(latency = 20.0)).meanOffsetMs), 1e-9)
    }

    @Test
    fun `two rests in a row are written as a single rest`() {
        val taps = listOf(Tap(0.0, TapKind.REST), Tap(1000.0, TapKind.REST))
        assertEquals("r1 |", lily(taps, cfg()))
    }

    @Test
    fun `a note that fills a 4 slash 2 bar is tied inside the bar`() {
        // One sustained sound: the two tokens of the bar are tied to each other.
        val config = Config(60.0, TimeSignature(4, 2), 1, 1, 0.0)
        assertEquals("c'1. ~ c'2 |", transcribe(listOf(Tap(0.0, TapKind.NOTE)), config).lilypond)
    }

    @Test
    fun `decompose splits into single-token durations`() {
        assertEquals(listOf(4, 1), decompose(5))
        assertEquals(listOf(6, 1), decompose(7))
        assertEquals(listOf(64), decompose(64))
        assertEquals(listOf(96, 16), decompose(112))
    }

    @Test
    fun `time signature parsing`() {
        assertEquals(TimeSignature(6, 8), TimeSignature.parse("6/8"))
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("4-4") }
        assertFailsWith<IllegalArgumentException> { TimeSignature.parse("4/5") }
    }

    @Test
    fun `config rejects grids finer than the notation resolution`() {
        assertFailsWith<IllegalArgumentException> { cfg(ts = TimeSignature(4, 32), sub = 4) }
    }
}
