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
}
