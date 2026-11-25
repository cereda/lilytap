// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The controls that go off while a take runs or a score plays.
 *
 * A take is recorded with the settings the window shows, and a score that plays is fixed, so
 * the inputs that would change one of them are off while it is running.
 */
class WindowAppControlsTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)),
            config,
        )

    @Test
    fun `the key is off with the other settings while a take runs`() {
        val app = WindowApp()
        val setting = keySetting(app)

        setControlsEnabled(app, false)
        assertFalse(setting.field.isEnabled, "the key of a take is read when the take begins")
        assertFalse(setting.menu.isEnabled, "the circle cannot be opened while a take runs")

        setControlsEnabled(app, true)
        assertTrue(setting.field.isEnabled, "the key comes back when the take is over")
        assertTrue(setting.menu.isEnabled, "the circle comes back too")
    }

    @Test
    fun `the key is off while a score plays`() {
        val app = WindowApp()
        takeScoreField().set(app, score)

        val setting = keySetting(app)

        startPlayback(app)
        assertFalse(setting.field.isEnabled, "the score that plays is fixed")
        assertFalse(setting.menu.isEnabled, "the circle cannot be opened while a score plays")

        stopPlayback(app)
        assertTrue(setting.field.isEnabled, "the key comes back when the playback stops")
        assertTrue(setting.menu.isEnabled, "the circle comes back too")
    }

    @Test
    fun `the take is recorded in the key the field says`() {
        val app = WindowApp()

        // The name is typed, not applied: the field is the truth.
        keySetting(app).field.text = "F major"

        begin(app)
        assertEquals("F major", session(app).config.key.name, "the take is in the key the field says")
        abort(app)
    }

    @Test
    fun `the transpose button is off while a take runs`() {
        val app = WindowApp()
        val button = transposeButton(app)

        begin(app)
        assertFalse(button.isEnabled, "there is no score while the take runs")
        abort(app)
    }

    @Test
    fun `the transpose button comes on when the take is over`() {
        val app = WindowApp()
        val button = transposeButton(app)

        begin(app)
        finish(app)

        assertTrue(button.isEnabled, "the take is a score, and a score can be transposed")
    }

    @Test
    fun `the transpose button is off while a score plays`() {
        val app = WindowApp()
        takeScoreField().set(app, score)
        val button = transposeButton(app)

        startPlayback(app)
        assertFalse(button.isEnabled, "the score that plays is fixed")

        stopPlayback(app)
        assertTrue(button.isEnabled, "the button comes back when the playback stops")
    }

    @Test
    fun `the fill button follows the score`() {
        val app = WindowApp()
        val fill = fillButton(app)

        assertFalse(fill.isEnabled, "there is no score to fill yet")

        begin(app)
        assertFalse(fill.isEnabled, "there is no score while the take runs")
        finish(app)
        assertTrue(fill.isEnabled, "the take is a score, and it can be filled")

        startPlayback(app)
        assertFalse(fill.isEnabled, "the score that plays is fixed")

        stopPlayback(app)
        assertTrue(fill.isEnabled, "the fill button comes back when the playback stops")
    }

    @Test
    fun `the transpose button moves the score and the key field follows`() {
        val app = WindowApp()
        showScore(app, score)

        transposeButton(app).onPick?.invoke(Transposition(Interval.THIRD, true))

        assertEquals("E major", keySetting(app).field.text, "the field says the key the score is in")
        assertEquals("C major → E major", scoreCaption(app).text, "the header says what happened")
        assertEquals(
            Pitch(PitchStep.E, 0, 4),
            notation(app)
                .score
                ?.events
                ?.first()
                ?.pitch,
            "the note moved up a third",
        )
    }

    /** The transpose button of the pitches panel. */
    private fun transposeButton(app: WindowApp): TransposeButton {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "pitches" }
                ?: error("pitches field not found")
        field.isAccessible = true
        val pitches = field.get(app) as PitchesPanel
        return pitches.transpose
    }

    /** The button that takes the cursor to the first note of the take. */
    private fun fillButton(app: WindowApp): PillButton {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "pitches" }
                ?: error("pitches field not found")
        field.isAccessible = true
        val pitches = field.get(app) as PitchesPanel
        return pitches.fill
    }

    /** The score strip of the window. */
    private fun notation(app: WindowApp): NotationPanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "notation" }
                ?: error("notation field not found")
        field.isAccessible = true
        return field.get(app) as NotationPanel
    }

    /** The message in the header of the score strip. */
    private fun scoreCaption(app: WindowApp): CaptionLabel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "scoreCaption" }
                ?: error("scoreCaption field not found")
        field.isAccessible = true
        return field.get(app) as CaptionLabel
    }

    /** Shows a score in the window, as a finished take does. */
    private fun showScore(
        app: WindowApp,
        score: Score,
    ) {
        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)
    }

    /** The key setting of the window: the field with the name of the key and the menu. */
    private fun keySetting(app: WindowApp): KeySetting {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "keySetting" }
                ?: error("keySetting field not found")
        field.isAccessible = true
        return field.get(app) as KeySetting
    }

    /** The field of the window that holds the take it plays. */
    private fun takeScoreField(): Field {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "takeScore" }
                ?: error("takeScore field not found")
        field.isAccessible = true
        return field
    }

    /** The take the window plays, when there is one. */
    private fun session(app: WindowApp): Session {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "session" }
                ?: error("session field not found")
        field.isAccessible = true
        return field.get(app) as Session
    }

    /** Turns the settings of the next take off and on. */
    private fun setControlsEnabled(
        app: WindowApp,
        on: Boolean,
    ) {
        val method =
            WindowApp::class.java.getDeclaredMethod("setControlsEnabled", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(app, on)
    }

    /** Plays the score of the window. */
    private fun startPlayback(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("startPlayback")
        method.isAccessible = true
        method.invoke(app)
    }

    /** Stops the score that plays. */
    private fun stopPlayback(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("stopPlayback")
        method.isAccessible = true
        method.invoke(app)
    }

    /** Starts a take. */
    private fun begin(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("begin")
        method.isAccessible = true
        method.invoke(app)
    }

    /** Gives up the take that runs. */
    private fun abort(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("abort")
        method.isAccessible = true
        method.invoke(app)
    }

    /** Ends the take that runs, as the clock does when the take is over. */
    private fun finish(app: WindowApp) {
        val method = WindowApp::class.java.getDeclaredMethod("finish")
        method.isAccessible = true
        method.invoke(app)
    }
}
