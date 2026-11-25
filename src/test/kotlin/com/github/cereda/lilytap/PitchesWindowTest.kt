// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Container
import java.awt.Font
import java.awt.FontMetrics
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
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
    fun `the settings fit the card`() {
        val app = WindowApp()

        val rows = settingsRows(app)
        val column = startColumn(app)
        val room = CONTENT_WIDTH - 40 // the borders of the card
        val asked = rows.preferredSize.width + column.preferredSize.width
        assertTrue(
            asked <= room,
            "the settings ask for $asked px, the card has $room",
        )
    }

    @Test
    fun `every row of the settings fits the column`() {
        val app = WindowApp()

        val rows = settingsRows(app)
        val column = startColumn(app)
        val room = CONTENT_WIDTH - 40 - column.preferredSize.width
        for (row in rows.components.filterIsInstance<JComponent>()) {
            assertTrue(
                row.preferredSize.width <= room,
                "a row of the settings asks for ${row.preferredSize.width} px, it has $room",
            )
        }
    }

    @Test
    fun `the LilyPond text wraps in the narrow card`() {
        val app = WindowApp()

        val area = output(app)
        assertTrue(area.lineWrap, "the text wraps at the width of the card")
        assertTrue(area.wrapStyleWord, "the text breaks at a space")
    }

    @Test
    fun `the header of the LilyPond card fits the card`() {
        val app = WindowApp()

        val bottom = bottom(app)
        bottom.setSize(CONTENT_WIDTH, BOTTOM_HEIGHT)
        layOut(bottom)

        val cards = bottom.components.filterIsInstance<JComponent>()
        val outputCard = cards[1]
        val header = outputCard.components.filterIsInstance<JComponent>().first()
        val room = outputCard.width - 36
        assertTrue(
            header.preferredSize.width <= room,
            "the header asks for ${header.preferredSize.width} px, the card has $room",
        )
    }

    @Test
    fun `the LilyPond card shows five lines of transcription`() {
        val app = WindowApp()

        val bottom = bottom(app)
        bottom.setSize(CONTENT_WIDTH, BOTTOM_HEIGHT)
        layOut(bottom)

        val area = output(app)
        val fm = fontMetrics(area.font)
        val lines = (area.height - area.margin.top - area.margin.bottom) / fm.height
        assertTrue(lines >= 5, "the card shows $lines lines")
    }

    @Test
    fun `the hint of the LilyPond card is not cut off`() {
        val app = WindowApp()

        val bottom = bottom(app)
        bottom.setSize(CONTENT_WIDTH, BOTTOM_HEIGHT)
        layOut(bottom)

        val area = output(app)
        // The scroll bar takes its room from the text when it is there, so the hint is
        // measured in the narrowest box the text can have.
        val bar = scroll(app).verticalScrollBar.preferredSize.width
        val room = area.width - area.margin.left - area.margin.right - bar
        val fm = fontMetrics(area.font)
        val lines = wrapText(CodeArea.HINT, room) { fm.stringWidth(it) }

        assertTrue(lines.size > 1, "the hint does not fit on one line, so it is wrapped")
        for (line in lines) {
            assertTrue(fm.stringWidth(line) <= room, "the line '$line' is cut off")
        }
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
        assertTrue(tip.contains("Q W E R T the accidentals"), "the legend says what the top row plays")
        assertTrue(tip.contains("8 moves the octave down"), "the legend says how the octave moves")
        assertTrue(tip.contains("9 moves it up"), "the legend says how the octave moves")
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
        press(app, KeyEvent.VK_9)
        assertEquals("C5", pitches(app).octaveText)

        pitches(app).fill.onClick?.invoke()

        assertEquals(
            "C4",
            pitches(app).octaveText,
            "the tracker follows the note the cursor is on",
        )
    }

    @Test
    fun `the transpose button sits between the fill button and the octave`() {
        val app = WindowApp()

        val panel = pitches(app)
        panel.setSize(PITCHES_WIDTH, BOTTOM_HEIGHT)
        layOut(panel)

        val fill = placeIn(panel, panel.fill)
        val transpose = placeIn(panel, panel.transpose)
        val octave = placeIn(panel, panel.octave)
        assertTrue(
            fill.x + panel.fill.width <= transpose.x,
            "the transpose button is after the fill button",
        )
        assertTrue(
            transpose.x + panel.transpose.width <= octave.x,
            "the transpose button is before the octave",
        )
        assertTrue(
            fill.y < transpose.y + panel.transpose.height && transpose.y < fill.y + panel.fill.height,
            "the three of them are on the same line",
        )
    }

    @Test
    fun `the header of the pitches panel fits the panel`() {
        val app = WindowApp()

        val panel = pitches(app)
        assertTrue(
            panel.preferredSize.width <= PITCHES_WIDTH,
            "the panel asks for ${panel.preferredSize.width} px, it has $PITCHES_WIDTH",
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
        press(app, KeyEvent.VK_9)
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
        assertEquals("C3", pitches(app).octaveText, "the tracker moved down")

        press(app, KeyEvent.VK_9)
        press(app, KeyEvent.VK_9)
        assertEquals("C5", pitches(app).octaveText, "the tracker moved up twice")
    }

    @Test
    fun `the smaller octave key goes down and the bigger one goes up`() {
        val app = WindowApp()

        showScore(app, score)

        press(app, KeyEvent.VK_8)
        assertEquals("C3", pitches(app).octaveText, "8 is the lower octave")

        press(app, KeyEvent.VK_9)
        assertEquals("C4", pitches(app).octaveText, "9 is the higher octave")
    }

    @Test
    fun `a tracker key plays in the live octave`() {
        val app = WindowApp()

        showScore(app, score)
        press(app, KeyEvent.VK_9)
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
        press(app, KeyEvent.VK_9)
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
        press(app, KeyEvent.VK_9)
        assertEquals("C6", pitches(app).octaveText, "the tracker moved up")

        press(app, KeyEvent.VK_0)
        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker is back on the octave of the note",
        )
    }

    @Test
    fun `the octave control goes back to the note that is selected`() {
        val app = WindowApp()

        showScore(app, twoNotes)
        selectTheBlock(app, 1)
        press(app, KeyEvent.VK_9)

        clickTheOctaveValue(app)

        assertEquals(
            "C5",
            pitches(app).octaveText,
            "the tracker is back on the octave of the note",
        )
    }

    @Test
    fun `the octave control moves the tracker`() {
        val app = WindowApp()

        showScore(app, score)

        val octave = pitches(app).octave
        octave.setSize(116, 38)
        octave.dispatchEvent(
            MouseEvent(octave, MouseEvent.MOUSE_PRESSED, 0, 0, 105, 10, 1, false),
        )
        octave.dispatchEvent(
            MouseEvent(octave, MouseEvent.MOUSE_RELEASED, 0, 0, 105, 10, 1, false),
        )

        assertEquals("C5", pitches(app).octaveText, "the stepper took the tracker up")
        assertEquals(5, pitches(app).piano.liveOctave, "the piano marks the same octave")
    }

    @Test
    fun `the piano follows the hand that clicks it`() {
        val app = WindowApp()

        showScore(app, score)
        selectTheOnlyBlock(app)
        press(app, KeyEvent.VK_9)
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
        press(app, KeyEvent.VK_9)
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

    /**
     * Clicks the number of the octave control, without moving it.
     *
     * The click on the value puts the tracker back on the note that is selected.
     */
    private fun clickTheOctaveValue(app: WindowApp) {
        val octave = pitches(app).octave
        octave.setSize(116, 38)
        octave.dispatchEvent(
            MouseEvent(octave, MouseEvent.MOUSE_PRESSED, 0, 0, 58, 10, 1, false),
        )
        octave.dispatchEvent(
            MouseEvent(octave, MouseEvent.MOUSE_RELEASED, 0, 0, 58, 10, 1, false),
        )
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

    /** The rows of the settings card. */
    private fun settingsRows(app: WindowApp): JComponent {
        val build = WindowApp::class.java.getDeclaredMethod("buildSettingsRows")
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
    private fun output(app: WindowApp): CodeArea = lilypond(app).area

    /** The scroll pane that holds the LilyPond text. */
    private fun scroll(app: WindowApp): JScrollPane = lilypond(app).scroll

    /** The card that holds the LilyPond text. */
    private fun lilypond(app: WindowApp): LilyPondOutput {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "lilypond" }
                ?: error("lilypond field not found")
        field.isAccessible = true
        return field.get(app) as LilyPondOutput
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

/**
 * The place of a component in the coordinates of the panel that holds it.
 *
 * The children of the panel sit in panels of their own, so their own places say nothing
 * about where they are in the panel.
 *
 * @param panel the panel the place is in.
 * @param child the component whose place is wanted.
 * @return the place of the top left corner of the child.
 */
private fun placeIn(
    panel: JComponent,
    child: java.awt.Component,
): java.awt.Point {
    var x = 0
    var y = 0
    var current: java.awt.Component? = child
    while (current != null && current !== panel) {
        x += current.x
        y += current.y
        current = current.parent
    }
    return java.awt.Point(x, y)
}

/** The metrics of a font, measured without a screen. */
private fun fontMetrics(font: Font): FontMetrics {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    val metrics = g.getFontMetrics(font)
    g.dispose()
    return metrics
}
