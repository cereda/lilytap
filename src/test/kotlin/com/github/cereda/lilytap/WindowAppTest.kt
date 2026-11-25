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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowAppTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)

    private val score =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)),
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

        assertEquals("% absolute\nc'2 c'2 |", output(app).text)

        // The header says what happened, and the split button is off for a range.
        val captionField =
            WindowApp::class.java.declaredFields.find { it.name == "scoreCaption" }
                ?: error("scoreCaption field not found")
        captionField.isAccessible = true
        val caption = captionField.get(app) as CaptionLabel
        assertEquals("c'1 → c'2 c'2", caption.text)

        val splitField =
            WindowApp::class.java.declaredFields.find { it.name == "splitButton" }
                ?: error("splitButton field not found")
        splitField.isAccessible = true
        val split = splitField.get(app) as PillButton
        assertFalse(split.isEnabled, "a range of two blocks cannot be split again")
    }

    @Test
    fun `the LilyPond text cannot be typed over`() {
        val app = WindowApp()
        val field = output(app)
        assertFalse(field.isEditable, "the text is generated, so it cannot be edited by hand")
    }

    @Test
    fun `the copy and save buttons follow the score`() {
        val app = WindowApp()
        val card = lilypond(app)

        assertFalse(card.copy.isEnabled, "there is nothing to copy without a score")
        assertFalse(card.save.isEnabled, "there is nothing to save without a score")

        showScore(app, score)

        assertTrue(card.copy.isEnabled, "there is a text to copy")
        assertTrue(card.save.isEnabled, "there is a score to save")
    }

    @Test
    fun `the pitch field is off and empty when nothing is selected`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val field = styledField(app, "pitchField")
        assertFalse(field.isEnabled, "there is no note to give a pitch to")
        assertEquals("", field.text)
    }

    @Test
    fun `the pitch field shows the pitch of the selected note`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        assertTrue(field.isEnabled, "a note is selected, so it can be given a pitch")
        assertEquals("c'", field.text, "middle C is written the way LilyPond writes it")
    }

    @Test
    fun `the pitch field puts the name on the selected note`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        field.text = "cis'"
        applyPitch(app)

        assertEquals("% absolute\ncis'1 |", output(app).text, "the LilyPond text follows the new pitch")
        assertFalse(field.invalid, "the name was read")
    }

    @Test
    fun `the pitch field is empty after the pitch is written`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        field.text = " cis "
        applyPitch(app)

        assertEquals("% absolute\ncis1 |", output(app).text, "the name without a mark is the C below middle C")
        assertEquals("", field.text, "the field is empty: it waits for the next note of the melody")
    }

    @Test
    fun `a name that cannot be read turns the border of the field red`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        field.text = "h"
        applyPitch(app)

        assertTrue(field.invalid, "the name is not a note")
        assertEquals("% absolute\nc'1 |", output(app).text, "the score is left alone")
    }

    @Test
    fun `the pitch field takes a double accidental`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        field.text = "fisis'"
        applyPitch(app)

        assertEquals("% absolute\nfisis'1 |", output(app).text, "the LilyPond text follows the new pitch")
        assertFalse(field.invalid, "the name was read")
    }

    @Test
    fun `the pitch edit can be undone`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        val field = styledField(app, "pitchField")
        field.text = "cis'"
        applyPitch(app)
        assertEquals("% absolute\ncis'1 |", output(app).text)

        notation(app).undo()
        assertEquals("% absolute\nc'1 |", output(app).text, "the pitch comes back")
    }

    @Test
    fun `the octaves can be written relative to the note before`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        assertEquals("% absolute\nc'1 |", output(app).text, "the octaves are written in full to begin with")

        toggleRelativeOutput(app)

        assertEquals("% relative\n\\relative {\nc'1 |\n}", output(app).text, "the text is a relative block")
    }

    @Test
    fun `the relative button lights up when the mode is on`() {
        val app = WindowApp()

        val button = lilypond(app).relative
        assertNull(button.accentOverride, "the button is quiet to begin with")

        toggleRelativeOutput(app)

        assertNotNull(button.accentOverride, "the button says that the mode is on")
    }

    @Test
    fun `an edit follows the mode that is on`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        toggleRelativeOutput(app)

        selectTheOnlyBlock(app)
        val field = styledField(app, "pitchField")
        field.text = "cis'"
        applyPitch(app)

        assertEquals("% relative\n\\relative {\ncis'1 |\n}", output(app).text, "the text stays relative")
    }

    @Test
    fun `the key is declared in the LilyPond text`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "F major"
        applyKey(app)

        assertEquals("% absolute\n\\key f \\major\nc'1 |", output(app).text, "the text declares the key")
    }

    @Test
    fun `the text of C major says nothing about the key`() {
        val app = WindowApp()
        showScore(app, score)

        assertEquals("% absolute\nc'1 |", output(app).text, "there is no key to declare")
    }

    @Test
    fun `the notes are spelled in the key`() {
        val app = WindowApp()
        val sharp =
            Score(
                listOf(Event(TapKind.NOTE, 0, 64, Pitch(PitchStep.C, 1, 4))),
                config,
            )
        showScore(app, sharp)

        keyField(app).text = "F major"
        applyKey(app)

        assertEquals(
            "% absolute\n\\key f \\major\ndes'1 |",
            output(app).text,
            "the C sharp of the score is a D flat in F major",
        )
    }

    @Test
    fun `the relative text declares the key inside the block`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "Bb major"
        applyKey(app)
        toggleRelativeOutput(app)

        assertEquals(
            "% relative\n\\relative {\n\\key bes \\major\nc'1 |\n}",
            output(app).text,
            "the block says everything it needs on its own",
        )
    }

    @Test
    fun `the change of the key can be undone`() {
        val app = WindowApp()
        val sharp =
            Score(
                listOf(Event(TapKind.NOTE, 0, 64, Pitch(PitchStep.C, 1, 4))),
                config,
            )
        showScore(app, sharp)

        keyField(app).text = "F major"
        applyKey(app)
        notation(app).undo()

        assertEquals("% absolute\ncis'1 |", output(app).text, "the note comes back as it was")
    }

    @Test
    fun `a name that is not a key is refused`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "Bb dorian"
        applyKey(app)

        assertTrue(keyField(app).invalid, "the field says that the name is wrong")
        assertEquals("% absolute\nc'1 |", output(app).text, "the score did not change")
    }

    @Test
    fun `the key field says when the name is wrong while it is typed`() {
        val app = WindowApp()

        keyField(app).text = "Bb dorian"
        assertTrue(keyField(app).invalid, "the field says that the name is wrong")

        keyField(app).text = "Bb major"
        assertFalse(keyField(app).invalid, "the name is good")
    }

    @Test
    fun `the key can be written the way LilyPond writes it`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "cis major"
        applyKey(app)

        // The middle C of the score is the leading tone of C sharp major, and it is written
        // B sharp: the same note, spelled the way the key wants it.
        assertEquals(
            "% absolute\n\\key cis \\major\nbis1 |",
            output(app).text,
            "the text declares the key",
        )
        assertEquals("C♯ major", keyField(app).text, "the field writes the name back with its sign")
    }

    @Test
    fun `a key with too many accidentals says what the limit is`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "gis major"
        applyKey(app)

        val tip = keyField(app).toolTipText ?: ""
        assertTrue(tip.contains("seven accidentals"), "the field says what the limit is")
        assertEquals("% absolute\nc'1 |", output(app).text, "the score did not change")
    }

    @Test
    fun `the name of the key is written back in its own spelling`() {
        val app = WindowApp()
        showScore(app, score)

        keyField(app).text = "bb MAJOR"
        applyKey(app)

        assertEquals("B♭ major", keyField(app).text, "the field says the name of the key")
        assertFalse(keyField(app).invalid, "the name is good")
    }

    @Test
    fun `the take does not begin when the name of the key is wrong`() {
        val app = WindowApp()

        keyField(app).text = "Bb dorian"

        val problem = inputProblem(app)
        assertNotNull(problem, "the name of the key is not a key")
        assertTrue(problem.headline.contains("key"), "the problem says what is wrong")
    }

    @Test
    fun `the take does not begin when the time signature is wrong`() {
        val app = WindowApp()

        styledField(app, "time").text = "7/5"

        val problem = inputProblem(app)
        assertNotNull(problem, "the time signature is not a meter")
        assertTrue(problem.headline.contains("time signature"), "the problem says what is wrong")
    }

    @Test
    fun `the take does not begin when the pitch is wrong`() {
        val app = WindowApp()

        styledField(app, "pitchField").text = "h"

        val problem = inputProblem(app)
        assertNotNull(problem, "the pitch is not a note")
        assertTrue(problem.headline.contains("pitch"), "the problem says what is wrong")
    }

    @Test
    fun `the take begins when every input is good`() {
        val app = WindowApp()

        keyField(app).text = "Bb major"
        applyKey(app)

        assertNull(inputProblem(app), "every input can be used")
    }

    @Test
    fun `the playback clears the selection`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)
        assertNotNull(notation(app).selection, "a block is selected to begin with")

        val startPlayback = WindowApp::class.java.getDeclaredMethod("startPlayback")
        startPlayback.isAccessible = true
        startPlayback.invoke(app)

        assertNull(
            notation(app).selection,
            "the playhead sweeps the strip, so the selection goes away",
        )
        assertEquals(
            NotationPanel.NOTHING_SELECTED,
            caption(app).text,
            "the message of the header follows the selection",
        )

        val stopPlayback = WindowApp::class.java.getDeclaredMethod("stopPlayback")
        stopPlayback.isAccessible = true
        stopPlayback.invoke(app)
    }

    @Test
    fun `a click outside the editor clears the selection`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)
        assertNotNull(notation(app).selection, "a block is selected to begin with")

        // The timeline is a part of the window, but not a part of the editor.
        val timeline = timeline(app)
        timeline.dispatchEvent(MouseEvent(timeline, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false))

        assertNull(
            notation(app).selection,
            "the user has moved on to another component, so the selection goes away",
        )
        assertEquals(
            NotationPanel.NOTHING_SELECTED,
            caption(app).text,
            "the message of the header follows the selection",
        )
    }

    @Test
    fun `a click on the editor keeps the selection`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        selectTheOnlyBlock(app)

        // The pitch field acts on the selected note.
        val field = styledField(app, "pitchField")
        field.dispatchEvent(MouseEvent(field, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false))
        assertNotNull(notation(app).selection, "the pitch field keeps the selection")

        // The edit buttons act on the selected blocks.
        val split = button(app, "splitButton")
        split.dispatchEvent(MouseEvent(split, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false))
        assertNotNull(notation(app).selection, "the edit buttons keep the selection")
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
        assertEquals("% absolute\nc'2 c'2 |", output.text)

        val revertScore = WindowApp::class.java.getDeclaredMethod("revertScore")
        revertScore.isAccessible = true
        revertScore.invoke(app)
        assertEquals("% absolute\nc'1 |", output.text, "the score is back the way the take ended")

        notation.undo()
        assertEquals("% absolute\nc'2 c'2 |", output.text, "the split comes back")
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
    fun `the time signature menu offers the common meters in two groups`() {
        val app = WindowApp()

        val field =
            WindowApp::class.java.declaredFields.find { it.name == "timeMenu" }
                ?: error("timeMenu field not found")
        field.isAccessible = true
        val menu = field.get(app) as MenuButton

        assertEquals(
            listOf(
                MenuGroup("Simple", listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8")),
                MenuGroup("Compound", listOf("6/8", "9/8", "12/8", "6/4")),
            ),
            menu.groups,
        )
        assertEquals(
            listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8", "6/8", "9/8", "12/8", "6/4"),
            menu.options,
        )
    }

    @Test
    fun `the groups of the time menu are the simple and the compound meters`() {
        val app = WindowApp()

        val field =
            WindowApp::class.java.declaredFields.find { it.name == "timeMenu" }
                ?: error("timeMenu field not found")
        field.isAccessible = true
        val menu = field.get(app) as MenuButton

        menu.groups.forEach { group ->
            group.options.forEach { meter ->
                val signature = runCatching { TimeSignature.parse(meter) }.getOrNull()
                assertNotNull(signature, "$meter is a time signature")
                val compound = signature.isCompound
                assertEquals(
                    compound,
                    group.name == "Compound",
                    "$meter belongs to the ${group.name} group",
                )
            }
        }
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
    fun `the helper message fits the header`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val caption = caption(app)
        val header = header(app)

        layOutStage(app)

        assertEquals(
            CAPTION_WIDTH,
            caption.width,
            "the message should have the whole room of the header",
        )

        // The widest messages the header can show: a long take can have hundreds of blocks.
        for (
        text in
        listOf(
            "512 blocks selected",
            "12 blocks → c'1.",
            "Nothing selected",
            "Reverted to the take",
        )
        ) {
            caption.text = text
            layOutStage(app)
            val fm = caption.getFontMetrics(caption.font)
            assertTrue(
                fm.stringWidth(caption.fitted(caption.width)) <= caption.width,
                "the message \"$text\" does not fit the header",
            )
            assertEquals(
                text,
                caption.fitted(caption.width),
                "the message \"$text\" should be shown in full",
            )
        }
    }

    @Test
    fun `a message that is too long is shortened and keeps the header in place`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val caption = caption(app)
        val header = header(app)

        caption.text = "ces'4 → ces'8 ces'8 → ces'16 ces'16 → ces'32 ces'32 and more"
        layOutStage(app)

        val fitted = caption.fitted(caption.width)
        assertTrue(fitted.endsWith("…"), "the message should be marked as shortened: $fitted")
        assertTrue(
            fitted.length < caption.text.length,
            "the message should be shorter: $fitted",
        )
        assertTrue(
            caption.preferredSize.width <= CAPTION_WIDTH,
            "the message should not make the label ask for more than $CAPTION_WIDTH pixels",
        )
        assertTrue(
            header.preferredSize.width <= STAGE_WIDTH,
            "the header should not ask for more than the stage has: " +
                "${header.preferredSize.width} > $STAGE_WIDTH",
        )
    }

    @Test
    fun `the message of a split is shown in full`() {
        val app = WindowApp()

        // A note with a flat: its name is long, so the message of a split is long too.
        val flat = Pitch(PitchStep.C, -1, 4)
        val long =
            Score(
                listOf(
                    Event(TapKind.NOTE, 0, 32, flat),
                    Event(TapKind.NOTE, 32, 32, Pitch.DEFAULT),
                ),
                config,
            )

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, long)

        selectTheFirstBlock(app)
        notation(app).splitSelected()
        layOutStage(app)

        val caption = caption(app)
        assertEquals("ces'2 → ces'4 ces'4", caption.text)
        assertEquals(
            caption.text,
            caption.fitted(caption.width),
            "the message of a split should be shown in full",
        )
    }

    @Test
    fun `the score header never asks for more width than the stage has`() {
        val app = WindowApp()

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, score)

        val header = header(app)
        layOutStage(app)

        assertTrue(
            header.preferredSize.width <= STAGE_WIDTH,
            "the header should not ask for more than the stage has: " +
                "${header.preferredSize.width} > $STAGE_WIDTH",
        )
    }

    @Test
    fun `the score header has no title of its own`() {
        val app = WindowApp()

        val header = header(app)
        layOutStage(app)

        // The strip below says what it is, so the header does not need a title of its own.
        val titles = glyphLabels(header).filter { it.text.isNotEmpty() }
        assertTrue(
            titles.isEmpty(),
            "the header should have no title: $titles",
        )
    }

    @Test
    fun `the revert button is a word in red`() {
        val app = WindowApp()

        val revertField =
            WindowApp::class.java.declaredFields.find { it.name == "revert" }
                ?: error("revert field not found")
        revertField.isAccessible = true
        val revert = revertField.get(app) as PillButton

        assertEquals("Revert", revert.text)
        assertNull(revert.glyph, "the button is the word alone: the reset arrow means something else")
        assertEquals(ButtonKind.SEVERE, revert.kind)
    }

    @Test
    fun `a long message does not push the score strip out of the window`() {
        val app = WindowApp()

        // A note with a flat: its name is long, so the message of a split is long too.
        val flat = Pitch(PitchStep.C, -1, 4)
        val long =
            Score(
                listOf(
                    Event(TapKind.NOTE, 0, 32, flat),
                    Event(TapKind.NOTE, 32, 32, Pitch.DEFAULT),
                ),
                config,
            )

        val showScore = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        showScore.isAccessible = true
        showScore.invoke(app, long)

        selectTheFirstBlock(app)
        notation(app).splitSelected()

        // The message is now "ces'4 → ces'8 ces'8", the longest one the editor writes.
        layOutStage(app)

        assertEquals(68, notation(app).height, "the score strip keeps its height")
        assertEquals(130, timeline(app).height, "the timeline keeps its height")
        assertEquals(
            timeline(app).width,
            notation(app).width,
            "the timeline and the score strip are the same width",
        )
        assertEquals(
            STAGE_WIDTH,
            notation(app).width,
            "the score strip is as wide as the stage",
        )
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

    /** The card that holds the LilyPond text. */
    private fun lilypond(app: WindowApp): LilyPondOutput {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "lilypond" }
                ?: error("lilypond field not found")
        field.isAccessible = true
        return field.get(app) as LilyPondOutput
    }

    /** Clicks the only block of the score on the strip, so that it is selected. */
    private fun selectTheOnlyBlock(app: WindowApp) {
        val notation = notation(app)
        val x = grid(app).xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
    }

    /** Clicks the first block of the score on the strip, so that it is selected. */
    private fun selectTheFirstBlock(app: WindowApp) {
        val notation = notation(app)
        val x = grid(app).xOfCell(0.5).toInt()
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 1, false))
        notation.dispatchEvent(MouseEvent(notation, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 1, false))
    }

    /** The message in the header of the score strip. */
    private fun caption(app: WindowApp): CaptionLabel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "scoreCaption" }
                ?: error("scoreCaption field not found")
        field.isAccessible = true
        return field.get(app) as CaptionLabel
    }

    /** The header of the score strip. */
    private fun header(app: WindowApp): JComponent {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "scoreHeader" }
                ?: error("scoreHeader field not found")
        field.isAccessible = true
        return field.get(app) as JComponent
    }

    /** Every label with a glyph in a container, and in the containers inside it. */
    private fun glyphLabels(container: Container): List<GlyphLabel> {
        val labels = mutableListOf<GlyphLabel>()
        for (child in container.components) {
            if (child is GlyphLabel) labels += child
            if (child is Container) labels += glyphLabels(child)
        }
        return labels
    }

    /** The timeline of the window. */
    private fun timeline(app: WindowApp): TimelinePanel {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "timeline" }
                ?: error("timeline field not found")
        field.isAccessible = true
        return field.get(app) as TimelinePanel
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
        layOut(body)
    }

    /** Puts what is in the pitch field on the selected note. */
    private fun applyPitch(app: WindowApp) {
        val apply = WindowApp::class.java.getDeclaredMethod("applyPitchInput")
        apply.isAccessible = true
        apply.invoke(app)
    }

    /** Switches the LilyPond text between the octaves in full and the octaves relative. */
    private fun toggleRelativeOutput(app: WindowApp) {
        val toggle = WindowApp::class.java.getDeclaredMethod("toggleRelativeOutput")
        toggle.isAccessible = true
        toggle.invoke(app)
    }

    /** The key setting of the window: the field with the name of the key and the menu. */
    private fun keySetting(app: WindowApp): KeySetting {
        val field =
            WindowApp::class.java.declaredFields.find { it.name == "keySetting" }
                ?: error("keySetting field not found")
        field.isAccessible = true
        return field.get(app) as KeySetting
    }

    /** The field of the key of the piece. */
    private fun keyField(app: WindowApp): StyledField = keySetting(app).field

    /** Puts the key that is written in the field on the score. */
    private fun applyKey(app: WindowApp) {
        val apply = KeySetting::class.java.getDeclaredMethod("apply")
        apply.isAccessible = true
        apply.invoke(keySetting(app))
    }

    /** What is wrong with the inputs of the window, when something is wrong with them. */
    private fun inputProblem(app: WindowApp): Message? {
        val problem = WindowApp::class.java.getDeclaredMethod("inputProblem")
        problem.isAccessible = true
        return problem.invoke(app) as Message?
    }

    /** Shows a score in the strip, the way a finished take does. */
    private fun showScore(
        app: WindowApp,
        score: Score,
    ) {
        val show = WindowApp::class.java.getDeclaredMethod("showScore", Score::class.java)
        show.isAccessible = true
        show.invoke(app, score)
    }
}

/** Lays a container out, and every container inside it, so the bounds are the real ones. */
private fun layOut(container: Container) {
    container.doLayout()
    for (child in container.components) {
        if (child is Container) layOut(child)
    }
}
