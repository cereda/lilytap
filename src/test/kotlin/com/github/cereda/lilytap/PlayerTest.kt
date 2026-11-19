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
}
