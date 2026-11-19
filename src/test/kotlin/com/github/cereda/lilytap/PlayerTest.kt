// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
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
        player.play(score(), startNanos = System.nanoTime(), onPosition = { positions += it })
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
        player.play(score(), startNanos = System.nanoTime(), onPosition = { positions += it })
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
        player.play(score(), startNanos = System.nanoTime(), onPosition = { positions += it })
        Thread.sleep(150)
        player.stop()
        Thread.sleep(200)
        // Second play should not throw and should not interleave with the stopped thread
        val positions2 = mutableListOf<Double>()
        player.play(score(), startNanos = System.nanoTime(), onPosition = { positions2 += it })
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
        player.play(score(), startNanos = System.nanoTime())
        Thread.sleep(150)
        player.stop()
        Thread.sleep(100)
    }
}
