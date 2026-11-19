// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowAppTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 8)),
            config,
        )

    @Test
    fun `playback start and stop transitions`() {
        val app = WindowApp()

        val takeScoreField =
            WindowApp::class.java.declaredFields.find { it.name == "takeScore" }
                ?: error("takeScore field not found")
        takeScoreField.isAccessible = true
        takeScoreField.set(app, score)

        val playButtonField =
            WindowApp::class.java.declaredFields.find { it.name == "playButton" }
                ?: error("playButton field not found")
        playButtonField.isAccessible = true
        val playButton = playButtonField.get(app) as PillButton
        assertTrue(playButton.isEnabled)

        val startPlayback = WindowApp::class.java.getDeclaredMethod("startPlayback")
        startPlayback.isAccessible = true
        startPlayback.invoke(app)

        val isPlayingField =
            WindowApp::class.java.declaredFields.find { it.name == "isPlaying" }
                ?: error("isPlaying field not found")
        isPlayingField.isAccessible = true
        assertTrue(isPlayingField.get(app) as Boolean)

        val stopPlayback = WindowApp::class.java.getDeclaredMethod("stopPlayback")
        stopPlayback.isAccessible = true
        stopPlayback.invoke(app)

        assertFalse(isPlayingField.get(app) as Boolean)
    }

    @Test
    fun `clearScore stops playback and disables play button`() {
        val app = WindowApp()

        val takeScoreField =
            WindowApp::class.java.declaredFields.find { it.name == "takeScore" }
                ?: error("takeScore field not found")
        takeScoreField.isAccessible = true
        takeScoreField.set(app, score)

        val clearScore = WindowApp::class.java.getDeclaredMethod("clearScore")
        clearScore.isAccessible = true
        clearScore.invoke(app)

        val isPlayingField =
            WindowApp::class.java.declaredFields.find { it.name == "isPlaying" }
                ?: error("isPlaying field not found")
        isPlayingField.isAccessible = true
        assertFalse(isPlayingField.get(app) as Boolean)

        val playButtonField =
            WindowApp::class.java.declaredFields.find { it.name == "playButton" }
                ?: error("playButton field not found")
        playButtonField.isAccessible = true
        val playButton = playButtonField.get(app) as PillButton
        assertFalse(playButton.isEnabled)
    }
}
