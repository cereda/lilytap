// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `onFrame advances the timeline playhead during playback`() {
        val app = WindowApp()

        val takeScoreField =
            WindowApp::class.java.declaredFields.find { it.name == "takeScore" }
                ?: error("takeScore field not found")
        takeScoreField.isAccessible = true
        takeScoreField.set(app, score)

        val isPlayingField =
            WindowApp::class.java.declaredFields.find { it.name == "isPlaying" }
                ?: error("isPlaying field not found")
        isPlayingField.isAccessible = true
        isPlayingField.set(app, true)

        val startField =
            WindowApp::class.java.declaredFields.find { it.name == "playbackStartNanos" }
                ?: error("playbackStartNanos field not found")
        startField.isAccessible = true
        startField.set(app, System.nanoTime() - 1_000_000_000L)

        val onFrame = WindowApp::class.java.getDeclaredMethod("onFrame")
        onFrame.isAccessible = true
        onFrame.invoke(app)

        val timelineField =
            WindowApp::class.java.declaredFields.find { it.name == "timeline" }
                ?: error("timeline field not found")
        timelineField.isAccessible = true
        val timeline = timelineField.get(app) as TimelinePanel
        assertTrue(
            timeline.playbackPositionMs > 0.0,
            "the timeline should receive the playback position",
        )

        isPlayingField.set(app, false)
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

    @Test
    fun `playback starts from the edited score when there is no take score`() {
        val app = WindowApp()

        val notationField =
            WindowApp::class.java.declaredFields.find { it.name == "notation" }
                ?: error("notation field not found")
        notationField.isAccessible = true
        (notationField.get(app) as NotationPanel).score = score

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
    }

    @Test
    fun `editing the score updates the timeline`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val edited = score.toggleRest(0)
        val scoreChanged = WindowApp::class.java.getDeclaredMethod("scoreChanged", Score::class.java)
        scoreChanged.isAccessible = true
        scoreChanged.invoke(app, edited)

        val timelineField =
            WindowApp::class.java.declaredFields.find { it.name == "timeline" }
                ?: error("timeline field not found")
        timelineField.isAccessible = true
        val timeline = timelineField.get(app) as TimelinePanel
        assertEquals(edited, timeline.score)
    }

    @Test
    fun `calibration includes the first bar`() {
        assertEquals(0.0, calibrationOffsetMs(0.0, 1000.0, 4))
        assertEquals(40.0, calibrationOffsetMs(1040.0, 1000.0, 4))
        assertEquals(-20.0, calibrationOffsetMs(2980.0, 1000.0, 4))
        assertNull(calibrationOffsetMs(4100.0, 1000.0, 4))
        assertNull(calibrationOffsetMs(-10.0, 1000.0, 4))
    }
}
