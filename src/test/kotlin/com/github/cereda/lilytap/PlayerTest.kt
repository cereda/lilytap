// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerTest {
    private fun cfg() = Config(600.0, TimeSignature(4, 4), 2, 1, 0.0, "c")

    private fun score() =
        Score(
            listOf(
                Event(TapKind.NOTE, 0, 2),
                Event(TapKind.REST, 2, 2),
                Event(TapKind.NOTE, 4, 4),
            ),
            cfg(),
        )

    @Test
    fun `playing a score advances the reported position`() {
        val player = Player(0.0)
        val positions = mutableListOf<Double>()
        player.play(score(), onPosition = { positions += it })
        Thread.sleep(300)
        player.stop()
        Thread.sleep(100)
        assertTrue(positions.size > 1, "onPosition should be invoked multiple times")
        assertEquals(0.0, positions.first())
        assertTrue(positions.dropLastWhile { it == 0.0 }.any { it > 0.0 }, "position should advance past 0")
    }

    @Test
    fun `stop halts position updates`() {
        val player = Player(0.0)
        val positions = mutableListOf<Double>()
        player.play(score(), onPosition = { positions += it })
        Thread.sleep(150)
        val advancingBefore = positions.count { it > 0.0 }
        player.stop()
        Thread.sleep(200)
        val advancingAfter = positions.count { it > 0.0 }
        assertEquals(advancingBefore, advancingAfter, "position should stop advancing after stop()")
    }

    @Test
    fun `hasAudio is a boolean`() {
        assertNotNull(Player(0.0).hasAudio)
    }

    @Test
    fun `play after stop does not start two threads`() {
        val player = Player(0.0)
        val positions = mutableListOf<Double>()
        player.play(score(), onPosition = { positions += it })
        Thread.sleep(150)
        player.stop()
        Thread.sleep(200)
        // Second play should not throw and should not interleave with the stopped thread
        val positions2 = mutableListOf<Double>()
        player.play(score(), onPosition = { positions2 += it })
        Thread.sleep(150)
        player.stop()
        Thread.sleep(100)
        assertTrue(positions2.size > 1, "second play should produce positions")
        assertTrue(positions2.dropLastWhile { it == 0.0 }.any { it > 0.0 }, "second position should advance")
    }

    @Test
    fun `play without onPosition callback`() {
        val player = Player(0.0)
        // Should not throw when onPosition is omitted
        player.play(score())
        Thread.sleep(150)
        player.stop()
        Thread.sleep(100)
    }

    @Test
    fun `beat playback still advances the position`() {
        val player = Player(0.0, PlaybackSound.BEAT)
        val positions = mutableListOf<Double>()
        player.play(score(), onPosition = { positions += it })
        Thread.sleep(300)
        player.stop()
        Thread.sleep(100)
        assertTrue(positions.any { it > 0.0 }, "beat playback should advance past the first event")
    }

    @Test
    fun `play after close is a no-op`() {
        val player = Player(0.0)
        player.close()
        var positions = 0
        val origin = player.play(score(), onPosition = { positions++ })
        Thread.sleep(100)
        assertEquals(0, positions, "a closed player must not start playback")
        assertEquals(0L, origin, "a closed player has no playback origin")
    }

    @Test
    fun `play returns an origin after the player is ready`() {
        val player = Player(0.0)
        val before = System.nanoTime()
        val origin = player.play(score())
        assertTrue(
            origin > before,
            "the origin must be sampled after the audio line is open, with a small lead-in",
        )
        player.close()
    }

    @Test
    fun `event timing rounds fractional cell durations`() {
        val cellMs = 60_000.0 / 63.0 / 2.0
        val start = 1_000_000_000L
        val expected = start + (512 * cellMs * 1_000_000.0).roundToLong()
        assertEquals(expected, eventStartNanos(start, 512, cellMs))
        val truncated = start + 512L * cellMs.toLong() * 1_000_000L
        assertTrue(
            expected - truncated > 90_000_000L,
            "rounding should avoid the ~97 ms drift of truncating cellMs",
        )
    }

    @Test
    fun `playback clicks have their own timbre`() {
        val normal = clickWave(600.0, 0.035, 120.0)
        val accent = clickWave(900.0, 0.035, 120.0)
        assertTrue(normal.isNotEmpty())
        assertEquals(normal.size, accent.size)
        assertFalse(normal.contentEquals(accent), "the downbeat accent should differ from a normal click")
    }
}
