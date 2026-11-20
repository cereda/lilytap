// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowAppTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64)),
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
    fun `playback disables the settings until it stops`() {
        val app = WindowApp()

        val takeScoreField =
            WindowApp::class.java.declaredFields.find { it.name == "takeScore" }
                ?: error("takeScore field not found")
        takeScoreField.isAccessible = true
        takeScoreField.set(app, score)

        val bpmField =
            WindowApp::class.java.declaredFields.find { it.name == "bpm" }
                ?: error("bpm field not found")
        bpmField.isAccessible = true
        val bpm = bpmField.get(app) as JComponent

        val startPlayback = WindowApp::class.java.getDeclaredMethod("startPlayback")
        startPlayback.isAccessible = true
        startPlayback.invoke(app)

        assertFalse(
            bpm.isEnabled,
            "the settings describe the next take, so they are off while a score plays",
        )

        val stopPlayback = WindowApp::class.java.getDeclaredMethod("stopPlayback")
        stopPlayback.isAccessible = true
        stopPlayback.invoke(app)

        assertTrue(bpm.isEnabled, "the settings come back when the playback stops")
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

    @Test
    fun `the beat index follows the session clock`() {
        val start = 1_000_000_000L
        val beat = 500_000_000L
        assertEquals(-1, beatIndexAt(start - 1, start, beat))
        assertEquals(-1, beatIndexAt(start - beat, start, beat))
        assertEquals(0, beatIndexAt(start, start, beat))
        assertEquals(0, beatIndexAt(start + beat - 1, start, beat))
        assertEquals(1, beatIndexAt(start + beat, start, beat))
        assertEquals(4, beatIndexAt(start + 4 * beat, start, beat))
    }
}
