// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Container
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pitches panel in the window: the piano, the tracker keys, the legend and the bottom row.
 */
class PitchesWindowTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)),
            config,
        )

    /** A score of two notes: a C4 and a C5, a quarter of the bar each. */
    private val twoNotes =
        Score(
            listOf(
                Event(TapKind.NOTE, 0, 32, Pitch(PitchStep.C, 0, 4)),
                Event(TapKind.NOTE, 32, 32, Pitch(PitchStep.C, 0, 5)),
            ),
            config,
        )

    // -------------------------------------------------------------------------------------------
    // The bottom row
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the bottom row gives two thirds to the pitches panel`() {
        val app = WindowApp()

        val bottom = bottom(app)
        bottom.setSize(CONTENT_WIDTH, BOTTOM_HEIGHT)
        layOut(bottom)

        val cards = bottom.components.filterIsInstance<JComponent>()
        assertEquals(2, cards.size, "the bottom row has two cards")
        assertEquals(PITCHES_WIDTH, cards[0].width, "the pitches panel takes two thirds")
        assertEquals(OUTPUT_WIDTH, cards[1].width, "the LilyPond card takes the rest")
        assertEquals(
            PITCHES_WIDTH + BOTTOM_GAP,
            cards[1].x,
            "the two cards are apart",
        )
        assertEquals(
            CONTENT_WIDTH,
            cards[1].x + cards[1].width,
            "the row is as wide as the window",
        )
    }

    @Test
    fun `the bottom row does not make the window wider`() {
        val app = WindowApp()

        val bottom = bottom(app)
        assertEquals(
            CONTENT_WIDTH,
            bottom.preferredSize.width,
            "the row is as wide as the stage, so the window keeps its width",
        )
        assertEquals(BOTTOM_HEIGHT, bottom.preferredSize.height)
    }

    @Test
    fun `the LilyPond text wraps in the narrow card`() {
        val app = WindowApp()

        val area = output(app)
        assertTrue(area.lineWrap, "the text wraps at the width of the card")
        assertTrue(area.wrapStyleWord, "the text breaks at a space")
    }

    // -------------------------------------------------------------------------------------------
    // The legend
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the legend sits above the Start button`() {
        val app = WindowApp()

        val column = startColumn(app)
        column.setSize(188, 218)
        layOut(column)

        val legend = legend(app)
        val start = button(app, "start")
        assertTrue(
            legend.y + legend.height <= start.y,
            "the legend is above the Start button",
        )
        assertTrue(
            legend.preferredSize.width <= 168,
            "the legend fits the column: ${legend.preferredSize.width}",
        )
    }

    @Test
    fun `the legend says what the colours and the keys mean`() {
        val app = WindowApp()

        val tip = legend(app).toolTipText ?: ""
        assertTrue(tip.contains("amber"), "the legend says that amber is late")
        assertTrue(tip.contains("teal"), "the legend says that teal is early")
        assertTrue(tip.contains("1 to 7 are C to B"), "the legend says what the number row plays")
        assertTrue(tip.contains("Q W E R T the sharps"), "the legend says what the top row plays")
        assertTrue(tip.contains("8 and 9 move the octave"), "the legend says how the octave moves")
        assertTrue(tip.contains("0 puts it back"), "the legend says what the zero key does")
    }

    @Test
    fun `the legend says the same on the panel`() {
        val app = WindowApp()

        val legend = legend(app)
        val tip = legend.toolTipText ?: ""
        assertTrue(
            tip.contains("1 to 7 are C to B"),
            "the tooltip and the legend say the same thing",
        )
        assertEquals(
            168,
            legend.preferredSize.width,
            "the legend fits the column beside the settings",
        )
    }

    // -------------------------------------------------------------------------------------------
    // The panel and the counter
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the pitches panel follows the score`() {
        val app = WindowApp()

        showScore(app, score)

        val panel = pitches(app)
        assertEquals(score, panel.piano.score, "the piano shows the score of the take")
        assertEquals("— / 1", panel.counterText, "the cursor is nowhere yet")
    }

    @Test
    fun `the counter of the pitches panel follows the cursor`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)

        assertEquals("1 / 1", pitches(app).counterText)
    }

    @Test
    fun `the counter follows the tracker keys`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_5)

        assertEquals("1 / 1", pitches(app).counterText, "the cursor is on the only note")
    }

    @Test
    fun `the fill button takes the cursor to the first note`() {
        val app = WindowApp()

        showScore(app, score)
        pitches(app).fill.onClick?.invoke()

        assertEquals(1, notation(app).selectedNoteNumber, "the cursor is on the first note")
        assertEquals("1 / 1", pitches(app).counterText)
    }

    @Test
    fun `the fill button puts the tracker in the octave of the first note`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        press(app, KeyEvent.VK_8)
        assertEquals("C5", pitches(app).octaveText)

        pitches(app).fill.onClick?.invoke()

        assertEquals(
            "C4",
            pitches(app).octaveText,
            "the tracker follows the note the cursor is on",
        )
    }

    // -------------------------------------------------------------------------------------------
    // The piano
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the piano writes the pitch of the key that was clicked`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)

        val piano = pitches(app).piano
        piano.setSize(300, 100)
        piano.dispatchEvent(
            MouseEvent(piano, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 90, 1, false),
        )

        assertEquals(
            Pitch(PitchStep.C, 0, 4),
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the middle C key was clicked",
        )
    }

    @Test
    fun `the piano is quiet while the score plays`() {
        val app = WindowApp()

        val isPlaying = flag("isPlaying")
        isPlaying.set(app, true)
        updatePitchesPanel(app)

        assertFalse(
            pitches(app).piano.interactive,
            "the piano does not answer while the app is playing",
        )

        isPlaying.set(app, false)
    }

    @Test
    fun `the piano is quiet while a take is recorded`() {
        val app = WindowApp()

        val session = session()
        session.set(app, recordingSession())
        updatePitchesPanel(app)

        assertFalse(
            pitches(app).piano.interactive,
            "the piano does not answer while a take runs",
        )

        session.set(app, null)
    }

    // -------------------------------------------------------------------------------------------
    // The tracker keys
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a tracker key writes a pitch on the selected note`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)
        press(app, KeyEvent.VK_5)

        assertEquals(
            Pitch(PitchStep.G, 0, 4),
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the tracker key played G4",
        )
    }

    @Test
    fun `a tracker key starts the walk when nothing is selected`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_2)

        assertEquals(
            Pitch(PitchStep.D, 0, 4),
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the first note of the take took the pitch",
        )
        assertEquals(
            1,
            notation(app).selectedNoteNumber,
            "the cursor is on the note that was filled",
        )
    }

    @Test
    fun `a tracker key that starts the walk keeps the octave of the tracker`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_8)
        press(app, KeyEvent.VK_1)

        assertEquals(
            Pitch(PitchStep.C, 0, 5),
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the first note of the take took the pitch of the live octave",
        )
        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the walk did not put the tracker back",
        )
    }

    @Test
    fun `the tracker keys do nothing while the score plays`() {
        val app = WindowApp()

        showScore(app, score)
        val isPlaying = flag("isPlaying")
        isPlaying.set(app, true)

        press(app, KeyEvent.VK_5)

        assertEquals(
            Pitch.DEFAULT,
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the score is left alone while it plays",
        )

        isPlaying.set(app, false)
    }

    @Test
    fun `a tracker key does nothing while a take is recorded`() {
        val app = WindowApp()

        showScore(app, score)
        val session = session()
        session.set(app, recordingSession())

        press(app, KeyEvent.VK_5)

        assertEquals(
            Pitch.DEFAULT,
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the score is left alone while the take runs",
        )

        session.set(app, null)
    }

    @Test
    fun `a tracker key is text when a text field has the keyboard`() {
        val event =
            KeyEvent(
                JPanel(),
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_5,
                KeyEvent.CHAR_UNDEFINED,
            )

        assertNull(
            trackerAction(event, JTextField(), 4),
            "a key that is typed in a text field is text, not a pitch",
        )
        assertEquals(
            TrackerAction.Note(keyPitch(67)),
            trackerAction(event, null, 4),
            "without a text field the key plays the piano",
        )
    }

    @Test
    fun `the editor keys still work with the tracker around`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)
        assertFalse(
            press(app, KeyEvent.VK_S),
            "S belongs to the strip: the window must not swallow it",
        )
        assertFalse(
            press(app, KeyEvent.VK_M),
            "M belongs to the strip as well",
        )

        notation(app).handleKey(KeyEvent.VK_S)
        assertEquals(
            2,
            notation(app).score?.events?.size,
            "S still splits the selected block",
        )
    }

    // -------------------------------------------------------------------------------------------
    // The octave of the tracker
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the tracker starts in the middle of the keyboard`() {
        val app = WindowApp()

        showScore(app, score)

        assertEquals("C4", pitches(app).octaveText, "the tracker starts in the middle")
    }

    @Test
    fun `the octave keys move the tracker`() {
        val app = WindowApp()

        showScore(app, score)

        press(app, KeyEvent.VK_8)
        assertEquals("C5", pitches(app).octaveText, "the tracker moved up")

        press(app, KeyEvent.VK_9)
        press(app, KeyEvent.VK_9)
        assertEquals("C3", pitches(app).octaveText, "the tracker moved down twice")
    }

    @Test
    fun `a tracker key plays in the live octave`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_8)
        press(app, KeyEvent.VK_1)

        assertEquals(
            Pitch(PitchStep.C, 0, 5),
            notation(app)
                .score
                ?.events
                ?.get(0)
                ?.pitch,
            "the tracker played the C of the octave above",
        )
    }

    @Test
    fun `the tracker follows the note that is selected`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        selectTheBlock(app, 1)

        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker is in the octave of the note",
        )
    }

    @Test
    fun `the walk does not move the tracker`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        selectTheBlock(app, 0)
        press(app, KeyEvent.VK_8)
        press(app, KeyEvent.VK_1)

        assertEquals(
            2,
            notation(app).selectedNoteNumber,
            "the cursor walked to the next note",
        )
        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker stayed in the octave it was put in",
        )
    }

    @Test
    fun `the reset key goes back to the note that is selected`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        selectTheBlock(app, 1)
        press(app, KeyEvent.VK_8)
        assertEquals("C6", pitches(app).octaveText, "the tracker moved up")

        press(app, KeyEvent.VK_0)
        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker is back on the octave of the note",
        )
    }

    @Test
    fun `the octave button goes back to the note that is selected`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        selectTheBlock(app, 1)
        press(app, KeyEvent.VK_8)

        pitches(app).octave.onClick?.invoke()

        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker is back on the octave of the note",
        )
    }

    @Test
    fun `the piano follows the hand that clicks it`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)
        press(app, KeyEvent.VK_8)
        assertEquals("C5", pitches(app).octaveText)

        val piano = pitches(app).piano
        piano.setSize(300, 100)
        // The first white key of the piano is the lowest one it shows: a C4.
        piano.dispatchEvent(
            MouseEvent(piano, MouseEvent.MOUSE_PRESSED, 0, 0, 4, 90, 1, false),
        )

        assertEquals(
            "C4",
            pitches(app).octaveText,
            "the tracker followed the key that was clicked",
        )
    }

    @Test
    fun `a new take puts the tracker back in the middle`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_8)
        assertEquals("C5", pitches(app).octaveText)

        showScore(app, score)

        assertEquals(
            "C4",
            pitches(app).octaveText,
            "the tracker of a new take starts in the middle",
        )
    }

    // -------------------------------------------------------------------------------------------
    // The helpers
    // -------------------------------------------------------------------------------------------

    /** Shows a score in the window, as a finished take does. */
    private fun showScore(
        app: WindowApp,
        score: Score,
    ) {
        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)
    }

    /** Lets the pitches panel follow the strip. */
    private fun updatePitchesPanel(app: WindowApp) {
        val update = WindowApp::class.java.getDeclaredMethod("updatePitchesPanel")
        update.isAccessible = true
        update.invoke(app)
    }

    /**
     * A key press in the window.
     *
     * @return true when the window swallowed the key.
     */
    private fun press(
        app: WindowApp,
        keyCode: Int,
    ): Boolean {
        val handleKey = WindowApp::class.java.getDeclaredMethod("handleKey", KeyEvent::class.java)
        handleKey.isAccessible = true
        val event =
            KeyEvent(
                JPanel(),
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                KeyEvent.CHAR_UNDEFINED,
            )
        return handleKey.invoke(app, event) as Boolean
    }

    /** A field of the window, by the name it has. */
    private fun flag(name: String): java.lang.reflect.Field {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field
    }

    /** The recording session of the window. */
    private fun session(): java.lang.reflect.Field {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "session" }
                ?: error("session field not found")
        field.isAccessible = true
        return field
    }

    /** A session that is recording and will not end on its own. */
    private fun recordingSession(): Session =
        Session(
            config,
            1,
            0L,
            0L,
            Long.MAX_VALUE,
            1_000_000_000L,
        )

    /** The pitches panel of the window. */
    private fun pitches(app: WindowApp): PitchesPanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "pitches" }
                ?: error("pitches field not found")
        field.isAccessible = true
        return field.get(app) as PitchesPanel
    }

    /** The legend of the top column. */
    private fun legend(app: WindowApp): LegendPanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "legend" }
                ?: error("legend field not found")
        field.isAccessible = true
        return field.get(app) as LegendPanel
    }

    /** The bottom row of the window: the pitches panel and the LilyPond card. */
    private fun bottom(app: WindowApp): JComponent {
        val build = WindowApp::class.java.getDeclaredMethod("buildBottom")
        build.isAccessible = true
        return build.invoke(app) as JComponent
    }

    /** The column of the settings card that holds the legend and the Start button. */
    private fun startColumn(app: WindowApp): JComponent {
        val build = WindowApp::class.java.getDeclaredMethod("buildStartColumn")
        build.isAccessible = true
        return build.invoke(app) as JComponent
    }

    /** The score strip of the window. */
    private fun notation(app: WindowApp): NotationPanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "notation" }
                ?: error("notation field not found")
        field.isAccessible = true
        return field.get(app) as NotationPanel
    }

    /** The grid the strip and the timeline share. */
    private fun grid(app: WindowApp): GridView {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "grid" }
                ?: error("grid field not found")
        field.isAccessible = true
        return field.get(app) as GridView
    }

    /** The box with the LilyPond text. */
    private fun output(app: WindowApp): CodeArea {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "output" }
                ?: error("output field not found")
        field.isAccessible = true
        return field.get(app) as CodeArea
    }

    /** A button of the window, by the name of the field that holds it. */
    private fun button(
        app: WindowApp,
        name: String,
    ): PillButton {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app) as PillButton
    }

    /** Clicks the only block of the score on the strip, so that it is selected. */
    private fun selectTheOnlyBlock(app: WindowApp) {
        selectTheBlock(app, 0)
    }

    /**
     * Clicks a block of the score on the strip, so that it is selected.
     *
     * @param app the window.
     * @param index the index of the block in the score.
     */
    private fun selectTheBlock(
        app: WindowApp,
        index: Int,
    ) {
        val notation = notation(app)
        val s = notation.score ?: return
        val event = s.events[index]
        val cell = event.startUnits / s.config.cellUnits
        val x = grid(app).xOfCell(cell + 0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
    }
}

/** Lays a container out, and every container inside it, so the bounds are the real ones. */
private fun layOut(container: Container) {
    container.doLayout()
    for (child in container.components) {
        if (child is Container) layOut(child)
    }
}
