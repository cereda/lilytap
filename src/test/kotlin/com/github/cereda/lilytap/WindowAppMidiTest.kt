// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The MIDI export in the window: the button that writes the file and where it sits.
 *
 * The button is with the other buttons of the timeline, and it follows the score: with no
 * score there is nothing to write, so the button is off.
 */
class WindowAppMidiTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)),
            config,
        )

    @Test
    fun `the MIDI button follows the score`() {
        val app = WindowApp()
        val button = midi(app).button

        assertFalse(button.isEnabled, "there is nothing to write without a score")

        showScore(app, score)

        assertTrue(button.isEnabled, "there is a score to write")
    }

    @Test
    fun `the MIDI button is with the other buttons of the timeline`() {
        val app = WindowApp()
        layOutStage(app)

        val right = stageRight(app)

        assertTrue(
            right.components.contains(midi(app).button),
            "the MIDI button is with the other buttons of the timeline",
        )
    }

    @Test
    fun `the buttons of the timeline fit in the stage`() {
        val app = WindowApp()
        layOutStage(app)

        val right = stageRight(app)

        assertTrue(
            right.preferredSize.width <= STAGE_WIDTH,
            "the buttons of the timeline should not ask for more than the stage has: " +
                "${right.preferredSize.width} > $STAGE_WIDTH",
        )
    }

    /** The export of the MIDI file. */
    private fun midi(app: WindowApp): MidiExport {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "midi" }
                ?: error("midi field not found")
        field.isAccessible = true
        return field.get(app) as MidiExport
    }

    /** The buttons of the timeline, on the right of its header. */
    private fun stageRight(app: WindowApp): JComponent {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "stageRight" }
                ?: error("stageRight field not found")
        field.isAccessible = true
        return field.get(app) as JComponent
    }

    /** Shows a score in the window, as a finished take does. */
    private fun showScore(
        app: WindowApp,
        score: Score,
    ) {
        val show = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        show.isAccessible = true
        show.invoke(app, score)
    }

    /** Lays the stage out, so the panels have the sizes they have in the window. */
    private fun layOutStage(app: WindowApp) {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "stageBody" }
                ?: error("stageBody field not found")
        field.isAccessible = true
        val body = field.get(app) as JPanel

        // The stage is built when the window opens; the test builds it by itself, so no
        // window has to be opened to lay it out. It is built only once, or the stage would
        // hold every panel twice.
        if (body.componentCount == 0) {
            val build = WindowApp::class.java.getDeclaredMethod("buildStage")
            build.isAccessible = true
            build.invoke(app)
        }

        body.setSize(STAGE_WIDTH, STAGE_HEIGHT)
        body.doLayout()
    }
}
