// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Container
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `the score editor changes the output of the take`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val notationField =
            WindowApp::class.java.declaredFields.find { it.name == "notation" }
                ?: error("notation field not found")
        notationField.isAccessible = true
        val notation = notationField.get(app) as NotationPanel

        val gridField =
            WindowApp::class.java.declaredFields.find { it.name == "grid" }
                ?: error("grid field not found")
        gridField.isAccessible = true
        val grid = gridField.get(app) as GridView

        // Click the only block of the score: it is selected, and the split key splits it.
        val x = grid.xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
        assertEquals(1, notation.selectedBlocks)

        notation.handleKey(KeyEvent.VK_S)

        val outputField =
            WindowApp::class.java.declaredFields.find { it.name == "output" }
                ?: error("output field not found")
        outputField.isAccessible = true
        val output = outputField.get(app) as CodeArea
        assertEquals("c2 c2 |", output.text)

        // The header says what happened, and the split button is off for a range.
        val captionField =
            WindowApp::class.java.declaredFields.find { it.name == "scoreCaption" }
                ?: error("scoreCaption field not found")
        captionField.isAccessible = true
        val caption = captionField.get(app) as JLabel
        assertEquals("c1 → c2 c2", caption.text)

        val splitField =
            WindowApp::class.java.declaredFields.find { it.name == "splitButton" }
                ?: error("splitButton field not found")
        splitField.isAccessible = true
        val split = splitField.get(app) as PillButton
        assertFalse(split.isEnabled, "a range of two blocks cannot be split again")
    }

    @Test
    fun `the undo buttons follow the history of the strip`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val undoButton = button(app, "undoButton")
        val redoButton = button(app, "redoButton")
        assertFalse(undoButton.isEnabled, "there is nothing to undo yet")
        assertFalse(redoButton.isEnabled, "there is nothing to redo yet")

        val notation = notation(app)
        val grid = grid(app)
        val x = grid.xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
        notation.handleKey(KeyEvent.VK_S)

        assertTrue(undoButton.isEnabled, "the split can be undone")
        assertFalse(redoButton.isEnabled, "there is still nothing to redo")

        notation.undo()
        assertFalse(undoButton.isEnabled, "the split was the only edit")
        assertTrue(redoButton.isEnabled, "the split can be done again")

        notation.redo()
        assertTrue(undoButton.isEnabled)
        assertFalse(redoButton.isEnabled)
    }

    @Test
    fun `the revert button can be undone`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val notation = notation(app)
        val grid = grid(app)
        val x = grid.xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
        notation.handleKey(KeyEvent.VK_S)

        val output = output(app)
        assertEquals("c2 c2 |", output.text)

        val revertScore = WindowApp::class.java.getDeclaredMethod("revertScore")
        revertScore.isAccessible = true
        revertScore.invoke(app)
        assertEquals("c1 |", output.text, "the score is back the way the take ended")

        notation.undo()
        assertEquals("c2 c2 |", output.text, "the split comes back")
    }

    @Test
    fun `the tap button carries its icon`() {
        val app = WindowApp()
        val tap = iconButton(app, "tapButton")
        assertEquals(Glyph.TAP, tap.glyph)
    }

    @Test
    fun `the tap button is off with the other settings while a take runs`() {
        val app = WindowApp()
        val tap = iconButton(app, "tapButton")

        val setControls =
            WindowApp::class.java.getDeclaredMethod("setControlsEnabled", Boolean::class.javaPrimitiveType)
        setControls.isAccessible = true
        setControls.invoke(app, false)
        assertFalse(tap.isEnabled, "the settings describe the next take, so they are off")

        setControls.invoke(app, true)
        assertTrue(tap.isEnabled, "the settings come back when the take is over")
    }

    @Test
    fun `the taps of the button set the tempo`() {
        val app = WindowApp()
        val bpm = stepper(app, "bpm")

        tap(app)
        Thread.sleep(500)
        tap(app)

        // Half a second between the taps: 120 BPM.
        assertTrue(
            bpm.value in 115.0..125.0,
            "the tempo should follow the taps, but it was ${bpm.value}",
        )
    }

    @Test
    fun `the tap button is armed while the round listens`() {
        val app = WindowApp()
        val tap = iconButton(app, "tapButton")

        assertFalse(tap.armed, "nothing is listening yet")
        tap(app)
        assertTrue(tap.armed, "the round listens for the taps that come next")
    }

    @Test
    fun `a key press is a tap while the round listens`() {
        val app = WindowApp()
        val bpm = stepper(app, "bpm")

        tap(app)
        Thread.sleep(500)
        press(app, KeyEvent.VK_SPACE)

        assertTrue(
            bpm.value in 115.0..125.0,
            "the key press should set the tempo, but it was ${bpm.value}",
        )
    }

    @Test
    fun `a key press alone does not set the tempo`() {
        val app = WindowApp()
        val bpm = stepper(app, "bpm")

        // The round never started, so the keys belong to the app.
        press(app, KeyEvent.VK_SPACE)
        assertEquals(60.0, bpm.value, "the tempo only follows a tap round")
    }

    @Test
    fun `escape gives up the tap round`() {
        val app = WindowApp()
        val bpm = stepper(app, "bpm")
        val tap = iconButton(app, "tapButton")

        tap(app)
        Thread.sleep(500)
        tap(app)
        val tempo = bpm.value

        press(app, KeyEvent.VK_ESCAPE)
        assertFalse(tap.armed, "the round is over")

        // The next key press belongs to the app again, so the tempo stays where it was.
        Thread.sleep(500)
        press(app, KeyEvent.VK_SPACE)
        assertEquals(tempo, bpm.value, "the tempo of the cancelled round stays")
    }

    @Test
    fun `the score buttons carry their icons`() {
        val app = WindowApp()

        val expected =
            mapOf(
                "splitButton" to Glyph.SPLIT,
                "swapButton" to Glyph.SWAP,
                "mergeButton" to Glyph.MERGE,
                "undoButton" to Glyph.UNDO,
                "redoButton" to Glyph.REDO,
            )
        for ((name, glyph) in expected) {
            val field =
                WindowApp::class.java.declaredFields.find { it.name == name }
                    ?: error("$name field not found")
            field.isAccessible = true
            val button = field.get(app) as PillButton
            assertEquals(glyph, button.glyph, "$name should carry the ${glyph.name.lowercase()} icon")
        }
    }

    @Test
    fun `the time signature menu offers the common meters`() {
        val app = WindowApp()

        val field =
            WindowApp::class.java.declaredFields.find { it.name == "timeMenu" }
                ?: error("timeMenu field not found")
        field.isAccessible = true
        val menu = field.get(app) as MenuButton

        assertEquals(
            listOf("4/4", "3/4", "2/4", "2/2", "3/8", "6/8", "9/8", "12/8", "5/4", "6/4", "7/8", "5/8"),
            menu.options,
        )
    }

    @Test
    fun `picking a meter in the menu fills the field`() {
        val app = WindowApp()

        val menuField =
            WindowApp::class.java.declaredFields.find { it.name == "timeMenu" }
                ?: error("timeMenu field not found")
        menuField.isAccessible = true
        val menu = menuField.get(app) as MenuButton

        val timeField =
            WindowApp::class.java.declaredFields.find { it.name == "time" }
                ?: error("time field not found")
        timeField.isAccessible = true
        val time = timeField.get(app) as StyledField

        menu.pick("6/8")
        assertEquals("6/8", time.text)
        assertFalse(time.invalid)
    }

    @Test
    fun `a time signature that cannot be used is marked`() {
        val app = WindowApp()

        val timeField =
            WindowApp::class.java.declaredFields.find { it.name == "time" }
                ?: error("time field not found")
        timeField.isAccessible = true
        val time = timeField.get(app) as StyledField

        time.text = "6/8"
        assertFalse(time.invalid, "6/8 is a meter")

        time.text = "11/16"
        assertFalse(time.invalid, "the field takes any meter, not only the common ones")

        time.text = "6/6"
        assertTrue(time.invalid, "6/6 is not a meter")

        time.text = ""
        assertTrue(time.invalid, "an empty field is not a meter")
    }

    @Test
    fun `the grid caption names the note the cells cut`() {
        val app = WindowApp()

        val captionField =
            WindowApp::class.java.declaredFields.find { it.name == "gridCaption" }
                ?: error("gridCaption field not found")
        captionField.isAccessible = true
        val caption = captionField.get(app) as GlyphLabel

        val timeField =
            WindowApp::class.java.declaredFields.find { it.name == "time" }
                ?: error("time field not found")
        timeField.isAccessible = true
        val time = timeField.get(app) as StyledField

        assertEquals("Cells / beat", caption.text, "in a simple meter the cells cut the beat")

        time.text = "6/8"
        assertEquals("Cells / eighth", caption.text, "in 6/8 they cut the eighth")

        time.text = "6/4"
        assertEquals("Cells / quarter", caption.text, "in 6/4 they cut the quarter")

        time.text = "4/4"
        assertEquals("Cells / beat", caption.text)
    }

    @Test
    fun `the 3-feel button waits for a compound meter`() {
        val app = WindowApp()
        val button = iconButton(app, "threeFeelButton")
        val time = styledField(app, "time")

        assertFalse(button.isEnabled, "a simple meter has no 3-feel")

        time.text = "6/8"
        assertTrue(button.isEnabled, "6/8 is a compound meter")

        time.text = "7/8"
        assertFalse(button.isEnabled, "an irregular meter is not a compound one")

        time.text = "6/4"
        assertTrue(button.isEnabled, "6/4 is a compound meter too")
    }

    @Test
    fun `the 3-feel button keeps its setting when the meter changes`() {
        val app = WindowApp()
        val button = iconButton(app, "threeFeelButton")
        val time = styledField(app, "time")

        time.text = "6/8"
        button.onClick?.invoke()
        assertNotNull(button.accentOverride, "the button shows that the 3-feel is on")

        time.text = "4/4"
        assertFalse(button.isEnabled, "a simple meter has no 3-feel")
        assertNull(button.accentOverride, "the button is greyed out in a simple meter")

        time.text = "6/8"
        assertTrue(button.isEnabled)
        assertNotNull(button.accentOverride, "the setting is remembered when the meter comes back")
    }

    @Test
    fun `the smallest rest control holds the no-rest option`() {
        val app = WindowApp()
        val control = segmentedControl(app, "minRest")

        control.selectedIndex = 3
        assertEquals("No rests", control.selectedText)
        assertTrue(
            control.toolTipText!!.contains("Never write rests"),
            "the tooltip follows the option that is selected",
        )

        control.selectedIndex = 0
        assertEquals("1 beat", control.selectedText)
        assertTrue(
            control.toolTipText!!.contains("Silences shorter than this"),
            "the tooltip follows the option that is selected",
        )
    }

    @Test
    fun `the helper message is never cropped`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val captionField =
            WindowApp::class.java.declaredFields.find { it.name == "scoreCaption" }
                ?: error("scoreCaption field not found")
        captionField.isAccessible = true
        val caption = captionField.get(app) as JLabel

        val headerField =
            WindowApp::class.java.declaredFields.find { it.name == "scoreHeader" }
                ?: error("scoreHeader field not found")
        headerField.isAccessible = true
        val header = headerField.get(app) as JComponent

        // The widest message the header can show: a long take can have hundreds of blocks.
        for (
        text in
        listOf(
            "512 blocks selected",
            "12 blocks → c1.",
            "Nothing selected",
            "Reverted to the take",
        )
        ) {
            caption.text = text
            header.setSize(header.preferredSize)
            layOut(header)
            val fm = caption.getFontMetrics(caption.font)
            assertTrue(
                caption.width >= fm.stringWidth(text),
                "the message \"$text\" is cropped: it needs ${fm.stringWidth(text)} pixels " +
                    "but the header gives it ${caption.width}",
            )
        }
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

    /** A stepper of the window, by the name of the field that holds it. */
    private fun stepper(
        app: WindowApp,
        name: String,
    ): Stepper {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app) as Stepper
    }

    /** A round icon button of the window, by the name of the field that holds it. */
    private fun iconButton(
        app: WindowApp,
        name: String,
    ): IconButton {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app) as IconButton
    }

    /** A text field of the window, by the name of the field that holds it. */
    private fun styledField(
        app: WindowApp,
        name: String,
    ): StyledField {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app) as StyledField
    }

    /** An option picker of the window, by the name of the field that holds it. */
    private fun segmentedControl(
        app: WindowApp,
        name: String,
    ): SegmentedControl {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == name }
                ?: error("$name field not found")
        field.isAccessible = true
        return field.get(app) as SegmentedControl
    }

    /** A tap on the tap button. */
    private fun tap(app: WindowApp) {
        val tapNow = WindowApp::class.java.getDeclaredMethod("tapTempoNow")
        tapNow.isAccessible = true
        tapNow.invoke(app)
    }

    /** A key press in the window. */
    private fun press(
        app: WindowApp,
        keyCode: Int,
    ) {
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
        handleKey.invoke(app, event)
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
}

/** Lays a container out, and every container inside it, so the bounds are the real ones. */
private fun layOut(container: Container) {
    container.doLayout()
    for (child in container.components) {
        if (child is Container) layOut(child)
    }
}
