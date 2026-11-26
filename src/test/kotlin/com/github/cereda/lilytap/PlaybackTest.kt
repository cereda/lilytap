// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackTest {
    private val config = Config(60.0, TimeSignature(4, 4), 1, 1)
    private val score = Score(listOf(Event(TapKind.NOTE, 0, config.totalUnits, Pitch.DEFAULT)), config)

    @Test
    fun `a playback that has not started is still`() {
        val playback = Playback()
        assertFalse(playback.isPlaying)
        assertEquals(-1.0, playback.positionMs)
    }

    @Test
    fun `the sound of the playback is the pitch of the score`() {
        val playback = Playback()
        assertEquals(PlaybackSound.NOTE, playback.sound)
    }

    @Test
    fun `the sound can be turned into a click`() {
        val playback = Playback()
        playback.toggleSound()
        assertEquals(PlaybackSound.BEAT, playback.sound)
        playback.toggleSound()
        assertEquals(PlaybackSound.NOTE, playback.sound)
    }

    @Test
    fun `a started playback plays and the playhead is at the beginning`() {
        val playback = Playback()
        try {
            assertTrue(playback.start(score, 0.0))
            assertTrue(playback.isPlaying)
            assertEquals(0.0, playback.positionMs)
            assertTrue(playback.startNanos > 0L)
        } finally {
            playback.close()
        }
    }

    @Test
    fun `a stopped playback is still and the playhead is gone`() {
        val playback = Playback()
        try {
            playback.start(score, 0.0)
            playback.stop()
            assertFalse(playback.isPlaying)
            assertEquals(-1.0, playback.positionMs)
        } finally {
            playback.close()
        }
    }
}
