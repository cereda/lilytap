// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The score strip: selecting blocks, editing them, and dragging a border to resize it. */
class NotationPanelTest {
    private val config = Config(60.0, TimeSignature(4, 4), 2, 1, 0.0)
    private val wide = Config(60.0, TimeSignature(4, 4), 2, 2, 0.0)

    /** The internal units in a grid cell of these configurations. */
    private val cellUnits = 8

    /** An event that starts at a grid cell and lasts a number of grid cells. */
    private fun event(
        kind: TapKind,
        start: Int,
        length: Int,
        pitch: Pitch? = if (kind == TapKind.NOTE) Pitch.DEFAULT else null,
    ) = Event(kind, start * cellUnits, length * cellUnits, pitch)

    private fun panel(config: Config = this.config): Pair<NotationPanel, GridView> {
        val grid =
            GridView().apply {
                this.config = config
                viewWidth = 400
            }
        val panel = NotationPanel(grid)
        panel.setSize(400, 56)
        return panel to grid
    }

    private fun score() =
        Score(
            listOf(
                event(TapKind.NOTE, 0, 2),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            config,
        )

    private fun click(
        panel: NotationPanel,
        x: Int,
        shift: Boolean = false,
    ) {
        val modifiers = if (shift) InputEvent.SHIFT_DOWN_MASK else 0
        val press = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, modifiers, x, 28, 1, false)
        panel.dispatchEvent(press)
        val release = MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, modifiers, x, 28, 1, false)
        panel.dispatchEvent(release)
    }

    /** Two clicks in a row: the second one carries the click count. */
    private fun doubleClick(
        panel: NotationPanel,
        x: Int,
    ) {
        click(panel, x)
        val press = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, 28, 2, false)
        panel.dispatchEvent(press)
        val release = MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, 28, 2, false)
        panel.dispatchEvent(release)
    }

    private fun drag(
        panel: NotationPanel,
        from: Int,
        to: Int,
    ) {
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, from, 28, 1, false))
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, 0, to, 28, 1, false))
        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, to, 28, 1, false))
    }

    private fun press(
        panel: NotationPanel,
        keyCode: Int,
        modifiers: Int = 0,
    ) {
        // The window sends the key to the focused component; here the panel gets it directly.
        val event =
            KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)
        for (listener in panel.keyListeners) {
            listener.keyPressed(event)
        }
    }

    // -------------------------------------------------------------------------------------------
    // Selecting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `clicking a block selects it without changing the score`() {
        val (panel, grid) = panel()
        panel.score = score()
        var edited: Score? = null
        panel.onEdit = { edited = it }
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals(1, panel.selectedBlocks)
        assertEquals("c'4 selected", panel.caption)
        assertNull(edited, "a click only selects")
        assertEquals("c'4 c'4 c'2 |", panel.score?.lilypond)
    }

    @Test
    fun `shift-clicking extends the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(4.5).toInt(), shift = true)
        assertEquals(3, panel.selectedBlocks)
        assertEquals("3 blocks selected", panel.caption)
    }

    @Test
    fun `the escape key clears the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        press(panel, KeyEvent.VK_ESCAPE)
        assertEquals(0, panel.selectedBlocks)
        assertEquals(NotationPanel.NOTHING_SELECTED, panel.caption)
    }

    @Test
    fun `the arrow keys move the selection`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(5.0).toInt())
        assertEquals(4 * cellUnits, panel.selection?.start)
        press(panel, KeyEvent.VK_LEFT)
        assertEquals(2 * cellUnits, panel.selection?.start)
        press(panel, KeyEvent.VK_LEFT)
        assertEquals(0, panel.selection?.start)
    }

    @Test
    fun `the arrow keys pick a block when nothing is selected`() {
        val (panel, grid) = panel()
        panel.score = score()
        press(panel, KeyEvent.VK_RIGHT)
        assertEquals(1, panel.selectedBlocks)
        assertEquals("c'4 selected", panel.caption)
        press(panel, KeyEvent.VK_RIGHT)
        press(panel, KeyEvent.VK_RIGHT)
        assertEquals("c'2 selected", panel.caption)
    }

    // -------------------------------------------------------------------------------------------
    // Editing
    // -------------------------------------------------------------------------------------------

    @Test
    fun `double-clicking a note turns it into a rest`() {
        val (panel, grid) = panel()
        panel.score = score()
        doubleClick(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(
                event(TapKind.REST, 0, 2),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        assertEquals("r4 c'4 c'2 |", panel.score?.lilypond)
    }

    @Test
    fun `double-clicking a rest turns it back into a note`() {
        val (panel, grid) = panel()
        panel.score = Score(listOf(event(TapKind.REST, 0, 2), event(TapKind.NOTE, 2, 6)), config)
        doubleClick(panel, grid.xOfCell(1.0).toInt())
        assertEquals(
            listOf(event(TapKind.NOTE, 0, 2), event(TapKind.NOTE, 2, 6)),
            panel.score?.events,
        )
    }

    @Test
    fun `the split key splits the selected block`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 1),
                event(TapKind.NOTE, 1, 1),
                event(TapKind.NOTE, 2, 2),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        assertEquals("c'4 → c'8 c'8", panel.caption)
    }

    @Test
    fun `the swap key swaps the two selected blocks`() {
        val (panel, grid) = panel()
        panel.score = Score(listOf(event(TapKind.NOTE, 0, 2), event(TapKind.REST, 2, 6)), config)
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(3.0).toInt(), shift = true)
        press(panel, KeyEvent.VK_X)
        assertEquals(
            listOf(event(TapKind.REST, 0, 6), event(TapKind.NOTE, 6, 2)),
            panel.score?.events,
        )
        assertEquals("c'4 r2. → r2. c'4", panel.caption)
    }

    @Test
    fun `a swap that leaves two silences touching shows the merged silence`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 2),
                    event(TapKind.NOTE, 2, 2),
                    event(TapKind.REST, 4, 2),
                    event(TapKind.NOTE, 6, 2),
                ),
                config,
            )
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_X)
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 2),
                event(TapKind.REST, 2, 4),
                event(TapKind.NOTE, 6, 2),
            ),
            panel.score?.events,
        )
        assertEquals("r4 c'4 → c'4 r2", panel.caption)
    }

    @Test
    fun `the merge key merges the selected blocks`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_M)
        assertEquals(
            listOf(event(TapKind.NOTE, 0, 4), event(TapKind.NOTE, 4, 4)),
            panel.score?.events,
        )
        assertEquals("c'4 + c'4 → c'2", panel.caption)
    }

    @Test
    fun `a long merge says how many blocks it merged`() {
        val (panel, grid) = panel(wide)
        panel.score =
            Score(
                List(6) { event(TapKind.NOTE, it * 2, 2) } + event(TapKind.NOTE, 12, 4),
                wide,
            )
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(10.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_M)
        assertEquals("6 blocks → c'1.", panel.caption)
    }

    @Test
    fun `turning a block into a rest says what it became`() {
        val (panel, grid) = panel()
        panel.score = score()
        doubleClick(panel, grid.xOfCell(1.0).toInt())
        assertEquals("c'4 → r4", panel.caption)
    }

    @Test
    fun `the split key does nothing without a single block`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        press(panel, KeyEvent.VK_S)
        assertEquals(3, panel.score?.events?.size)
    }

    @Test
    fun `an edit keeps the selection on the same music`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals(2, panel.selectedBlocks)
        assertTrue(panel.canMerge)
    }

    // -------------------------------------------------------------------------------------------
    // The pitch of a block
    // -------------------------------------------------------------------------------------------

    /** A sharp C an octave up: C♯5. */
    private val sharp = Pitch(PitchStep.C, 1, 5)

    @Test
    fun `the pitch of the selected block is reported`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals(Pitch.DEFAULT, panel.selectedPitch)
    }

    @Test
    fun `a block that is not selected has no pitch to report`() {
        val (panel, _) = panel()
        panel.score = score()
        assertNull(panel.selectedPitch)
    }

    @Test
    fun `a rest has no pitch`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 4),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        click(panel, grid.xOfCell(1.0).toInt())
        assertNull(panel.selectedPitch)
    }

    @Test
    fun `a range of blocks has no single pitch`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        click(panel, grid.xOfCell(2.5).toInt(), shift = true)
        assertNull(panel.selectedPitch)
    }

    @Test
    fun `setting the pitch of the selected block changes the score`() {
        val (panel, grid) = panel()
        panel.score = score()
        var edited: Score? = null
        panel.onEdit = { edited = it }
        click(panel, grid.xOfCell(1.0).toInt())
        assertTrue(panel.setSelectedPitch(sharp))
        assertEquals(
            sharp,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
        )
        assertEquals(sharp, edited?.events?.get(0)?.pitch)
    }

    @Test
    fun `the caption says what the pitch edit did`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        panel.setSelectedPitch(sharp)
        assertEquals("C4 → C♯5", panel.caption)
    }

    @Test
    fun `the pitch of a block can be undone`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        panel.setSelectedPitch(sharp)
        panel.undo()
        assertEquals(
            Pitch.DEFAULT,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
        )
    }

    @Test
    fun `setting the pitch of nothing does nothing`() {
        val (panel, _) = panel()
        panel.score = score()
        assertFalse(panel.setSelectedPitch(sharp))
        assertEquals(
            Pitch.DEFAULT,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
        )
    }

    @Test
    fun `setting the pitch of a rest fills the silence`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 4),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        click(panel, grid.xOfCell(1.0).toInt())
        assertTrue(panel.setSelectedPitch(sharp), "a pitch can be given to a rest")
        assertEquals(
            TapKind.NOTE,
            panel.score
                ?.events
                ?.get(0)
                ?.kind,
            "the silence becomes a note",
        )
        assertEquals(
            sharp,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
            "the note sounds the pitch that was written",
        )
    }

    @Test
    fun `the cursor moves on even when the pitch is the same`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        assertTrue(panel.setSelectedPitch(Pitch.DEFAULT))
        assertEquals(
            Pitch.DEFAULT,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
            "the pitch is still the one it was",
        )
        assertFalse(panel.canUndo, "nothing changed, so there is nothing to undo")
        assertEquals(
            2,
            panel.selectedNoteNumber,
            "the cursor moved on to the next note",
        )
    }

    @Test
    fun `the cursor moves to the next note after a pitch is written`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        panel.setSelectedPitch(sharp)
        assertEquals(2, panel.selectedNoteNumber, "the cursor is on the second note")
        assertEquals(1, panel.selectedBlocks, "the cursor is on one block")
    }

    @Test
    fun `the cursor skips the rests`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.NOTE, 0, 2),
                    event(TapKind.REST, 2, 2),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        click(panel, grid.xOfCell(1.0).toInt())
        panel.setSelectedPitch(sharp)
        assertEquals(
            2,
            panel.selectedNoteNumber,
            "the rest has no pitch: the cursor steps over it",
        )
    }

    @Test
    fun `the cursor stops at the end of the take`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(5.0).toInt())
        panel.setSelectedPitch(sharp)
        assertEquals(3, panel.selectedNoteNumber, "the cursor stays on the last note")
    }

    @Test
    fun `the fill takes the cursor to the first note`() {
        val (panel, _) = panel()
        panel.score = score()
        panel.fillMelody()
        assertEquals(1, panel.selectedNoteNumber)
        assertEquals("c'4 selected", panel.caption)
    }

    @Test
    fun `the fill walks over the rests at the top of the take`() {
        val (panel, _) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 2),
                    event(TapKind.NOTE, 2, 2),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        panel.fillMelody()
        assertEquals(1, panel.selectedNoteNumber, "the first note is the first one with a pitch")
    }

    @Test
    fun `the cursor moves on when a silence is filled`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 2),
                    event(TapKind.NOTE, 2, 2),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        click(panel, grid.xOfCell(1.0).toInt())
        assertTrue(panel.setSelectedPitch(sharp))
        assertEquals(
            TapKind.NOTE,
            panel.score
                ?.events
                ?.get(0)
                ?.kind,
            "the silence becomes a note",
        )
        assertEquals(
            2,
            panel.selectedNoteNumber,
            "the cursor is on the note that comes after the filled silence",
        )
    }

    @Test
    fun `the counter counts the notes and not the blocks`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.NOTE, 0, 2),
                    event(TapKind.REST, 2, 2),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        assertEquals("— / 2", panel.cursorText, "the take has two notes and three blocks")
        click(panel, grid.xOfCell(5.0).toInt())
        assertEquals("2 / 2", panel.cursorText, "the cursor is on the second note")
    }

    @Test
    fun `a take without notes has no counter`() {
        val (panel, _) = panel()
        panel.score = Score(listOf(event(TapKind.REST, 0, 8)), config)
        assertEquals("", panel.cursorText)
        panel.fillMelody()
        assertEquals(0, panel.selectedBlocks, "there is no note to walk to")
    }

    @Test
    fun `the counter says where the cursor is among the notes`() {
        val (panel, grid) = panel()
        panel.score = score()
        assertEquals("— / 3", panel.cursorText, "without a selection the cursor is nowhere")
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals("1 / 3", panel.cursorText)
        panel.setSelectedPitch(sharp)
        assertEquals("2 / 3", panel.cursorText, "the cursor moved on")
    }

    @Test
    fun `the counter has a dash when the cursor is not on a note`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 4),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        click(panel, grid.xOfCell(1.0).toInt())
        assertEquals("— / 1", panel.cursorText, "a rest is not one of the notes")
    }

    @Test
    fun `the undo puts the cursor back on the note that was filled`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        panel.setSelectedPitch(sharp)
        panel.undo()
        assertEquals(
            1,
            panel.selectedNoteNumber,
            "the cursor is back on the note that was filled",
        )
        assertEquals(
            Pitch.DEFAULT,
            panel.score
                ?.events
                ?.get(0)
                ?.pitch,
            "the pitch is undone as well",
        )
    }

    // -------------------------------------------------------------------------------------------
    // Dragging a border
    // -------------------------------------------------------------------------------------------

    @Test
    fun `dragging a border resizes the two notes`() {
        val (panel, grid) = panel()
        panel.score = score()
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(3.0).toInt())
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 3),
                event(TapKind.NOTE, 3, 1),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
    }

    @Test
    fun `a border cannot be dragged past the end of a note`() {
        val (panel, grid) = panel()
        panel.score = score()
        // Drag far to the right: the second note keeps one cell.
        drag(panel, grid.xOfCell(2.0).toInt(), 390)
        assertEquals(
            cellUnits,
            panel.score
                ?.events
                ?.get(1)
                ?.lengthUnits,
        )
    }

    @Test
    fun `a drag near a bar line sticks to it`() {
        val (panel, grid) = panel(wide)
        panel.score = Score(listOf(event(TapKind.NOTE, 0, 2), event(TapKind.NOTE, 2, 14)), wide)
        // Cell 7 is close to the bar line at cell 8: the bar line wins.
        drag(panel, grid.xOfCell(2.0).toInt(), grid.xOfCell(7.4).toInt())
        assertEquals(
            8 * cellUnits,
            panel.score
                ?.events
                ?.get(0)
                ?.lengthUnits,
        )
    }

    // -------------------------------------------------------------------------------------------
    // The history
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the undo key takes the last edit back`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals(4, panel.score?.events?.size)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        assertEquals(3, panel.score?.events?.size)
        assertEquals("c'4 c'4 c'2 |", panel.score?.lilypond)
    }

    @Test
    fun `the undo key brings the selection and the caption back`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertEquals("c'4 → c'8 c'8", panel.caption)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        assertEquals("c'4 selected", panel.caption)
        assertEquals(1, panel.selectedBlocks)
    }

    @Test
    fun `the redo key does the edit again`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
        assertEquals(4, panel.score?.events?.size)
        assertEquals("c'4 → c'8 c'8", panel.caption)
    }

    @Test
    fun `ctrl and y redoes the edit as well`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        press(panel, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK)
        assertEquals(4, panel.score?.events?.size)
    }

    @Test
    fun `a new edit throws away the edits that were undone`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        assertTrue(panel.canRedo)
        press(panel, KeyEvent.VK_S)
        assertFalse(panel.canRedo, "the undone edit is gone for good")
    }

    @Test
    fun `undo at the beginning and redo at the end do nothing`() {
        val (panel, grid) = panel()
        val original = score()
        panel.score = original
        assertFalse(panel.canUndo)
        assertFalse(panel.canRedo)
        panel.undo()
        panel.redo()
        assertEquals(original.events, panel.score?.events)
    }

    @Test
    fun `a drag is one step in the history`() {
        val (panel, grid) = panel()
        val original = score()
        panel.score = original
        panel.dispatchEvent(
            MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, grid.xOfCell(2.0).toInt(), 28, 1, false),
        )
        panel.dispatchEvent(
            MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, 0, grid.xOfCell(2.5).toInt(), 28, 1, false),
        )
        panel.dispatchEvent(
            MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, 0, grid.xOfCell(3.0).toInt(), 28, 1, false),
        )
        panel.dispatchEvent(
            MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, grid.xOfCell(3.0).toInt(), 28, 1, false),
        )
        assertEquals(
            listOf(
                event(TapKind.NOTE, 0, 3),
                event(TapKind.NOTE, 3, 1),
                event(TapKind.NOTE, 4, 4),
            ),
            panel.score?.events,
        )
        panel.undo()
        assertEquals(original.events, panel.score?.events, "the whole drag is one step")
        assertFalse(panel.canUndo, "there was only one edit to undo")
    }

    @Test
    fun `a new score starts the history over`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        assertTrue(panel.canUndo)
        panel.score = score()
        assertFalse(panel.canUndo)
        assertFalse(panel.canRedo)
    }

    @Test
    fun `the revert can be undone`() {
        val (panel, grid) = panel()
        val original = score()
        panel.score = original
        click(panel, grid.xOfCell(0.5).toInt())
        press(panel, KeyEvent.VK_S)
        panel.revertTo(original)
        assertEquals(original.events, panel.score?.events)
        assertEquals(NotationPanel.REVERTED, panel.caption)
        panel.undo()
        assertEquals(4, panel.score?.events?.size, "the split comes back")
    }

    // -------------------------------------------------------------------------------------------
    // Transposing the score
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the transposition moves the notes and the key`() {
        val (panel, _) = panel()
        panel.score = score()

        val result = panel.transpose(Transposition(Interval.THIRD, true))

        assertTrue(result is TransposeResult.Moved, "the score moved")
        assertEquals("\\key e \\major\ne'4 e'4 e'2 |", panel.score?.lilypond)
        assertEquals(
            "E major",
            panel.score
                ?.config
                ?.key
                ?.name,
        )
    }

    @Test
    fun `the caption says what the transposition did`() {
        val (panel, _) = panel()
        panel.score = score()

        panel.transpose(Transposition(Interval.THIRD, true))

        assertEquals("C major → E major", panel.caption)
    }

    @Test
    fun `the transposition can be undone`() {
        val (panel, _) = panel()
        val original = score()
        panel.score = original

        panel.transpose(Transposition(Interval.THIRD, true))
        press(panel, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)

        assertEquals(original.events, panel.score?.events, "the notes are back")
        assertEquals(
            "C major",
            panel.score
                ?.config
                ?.key
                ?.name,
            "the key is back",
        )
    }

    @Test
    fun `the revert puts a transposed score back the way the take was`() {
        val (panel, _) = panel()
        val original = score()
        panel.score = original

        panel.transpose(Transposition(Interval.THIRD, true))
        panel.revertTo(original)

        assertEquals(original.events, panel.score?.events, "the notes are back")
        assertEquals(
            "C major",
            panel.score
                ?.config
                ?.key
                ?.name,
            "the key is back",
        )
    }

    @Test
    fun `a transposition that cannot be done changes nothing`() {
        val (panel, _) = panel()
        // The note is at the top of the keyboard: an octave up would leave it.
        val high = Score(listOf(event(TapKind.NOTE, 0, 8, Pitch(PitchStep.C, 0, 8))), config)
        panel.score = high

        val result = panel.transpose(Transposition(Interval.OCTAVE, up = true))

        assertTrue(result is TransposeResult.Refused, "the transposition was refused")
        assertEquals(high.events, panel.score?.events, "the score did not move")
        assertFalse(panel.canUndo, "there is nothing to undo")
    }

    // -------------------------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the strip paints the score without complaining`() {
        val (panel, _) = panel()
        panel.score = score()
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertNotNull(image.colorModel)
    }

    @Test
    fun `the strip paints a selection without complaining`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(1.0).toInt())
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        assertNotNull(image.colorModel)
    }

    @Test
    fun `the ring around a selected block does not look like the block`() {
        val (panel, grid) = panel()
        panel.score = score()

        val without = paint(panel)
        click(panel, grid.xOfCell(1.0).toInt())
        val with = paint(panel)

        val ink = differingPixels(without, with)
        assertTrue(ink.isNotEmpty(), "the selection should draw something")
        assertTrue(
            ink.any { it == Theme.text.rgb },
            "the ring should be drawn in the text colour, so it stands out from the block",
        )
        assertTrue(
            ink.none { it == Theme.accent.rgb },
            "the ring should not be the colour of the block it surrounds",
        )
    }

    @Test
    fun `the ring follows the shape of the block`() {
        val x1 = 1.0
        val x2 = 49.0
        val top = 13.0
        val height = 30.0
        val note = event(TapKind.NOTE, 0, 2)
        val block =
            rr(x1, top, x2 - x1, height, cornerRadius(note, x2 - x1, height)) as RoundRectangle2D
        val ring = ringShape(note, x1, x2, top, height) as RoundRectangle2D

        // The ring and the block share the centre of their corner: the ring follows the shape
        // of the block instead of floating over it.
        assertEquals(block.x + block.arcWidth / 2, ring.x + ring.arcWidth / 2, 0.001)
        assertEquals(block.y + block.arcHeight / 2, ring.y + ring.arcHeight / 2, 0.001)
    }

    @Test
    fun `the ring leaves no sliver of strip between the block and itself`() {
        val x1 = 1.0
        val x2 = 49.0
        val top = 13.0
        val height = 30.0
        val note = event(TapKind.NOTE, 0, 2)
        val ring = ringShape(note, x1, x2, top, height) as RoundRectangle2D

        // The inner edge of the ring is inside the block, so the strip cannot show between
        // the two, not even in the corner.
        val inner = ring.arcWidth / 2 - RING_WIDTH / 2
        val edge = cornerRadius(note, x2 - x1, height)
        assertTrue(
            inner < edge,
            "the ring should reach into the block: $inner against $edge",
        )
    }

    @Test
    fun `the ring covers the corner of the block`() {
        // A block with rounded corners: the corners of its box are not part of the block, and
        // the ring has to reach over that notch.
        val x1 = 1.0
        val x2 = 49.0
        val top = 13.0
        val height = 30.0
        val note = event(TapKind.NOTE, 0, 2)
        val ring = ringShape(note, x1, x2, top, height) as RoundRectangle2D

        // The tip of the corner of the block: the point where its edges would meet.
        val tip = Point2D.Double(x1, top)
        // The centre of the corner of the ring: the arc sits in the corner of its box.
        val centre =
            Point2D.Double(
                ring.x + ring.arcWidth / 2,
                ring.y + ring.arcHeight / 2,
            )
        // The tip is as far from the centre as the radius of the corner times the square root
        // of two: that is how far the notch of the corner reaches.
        val notch = cornerRadius(note, x2 - x1, height) * sqrt(2.0)
        val reach = ring.arcWidth / 2 + RING_WIDTH / 2
        assertTrue(
            tip.distance(centre) <= reach,
            "the ring should reach the corner of the block",
        )
        assertTrue(
            reach >= notch,
            "the ring should reach over the notch of the corner: $reach against $notch",
        )
    }

    @Test
    fun `the ring around a selected block has a glow`() {
        val (panel, grid) = panel()
        panel.score = score()

        val without = paint(panel)
        click(panel, grid.xOfCell(1.0).toInt())
        val with = paint(panel)

        // The block sits at y=13 and the ring runs along its top edge, so a few pixels above
        // it are lit by the glow alone.
        assertTrue(
            with.getRGB(20, 9) != without.getRGB(20, 9),
            "the glow should reach outside the ring",
        )
        // The glow does not spread over the whole strip.
        assertEquals(
            without.getRGB(20, 5),
            with.getRGB(20, 5),
            "the glow should not spread too far",
        )
    }

    @Test
    fun `the empty hint is vertically centred in the strip`() {
        val (panel, _) = panel()
        panel.setSize(400, 68)
        panel.score = null
        val image = BufferedImage(400, 68, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        // Background is the card colour; rows that contain text ink differ from it.
        val background = image.getRGB(200, 2)
        val rowInk = IntArray(image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != background) rowInk[y]++
            }
        }
        var total = 0
        var weighted = 0L
        for (y in 0 until image.height) {
            total += rowInk[y]
            weighted += rowInk[y].toLong() * y
        }
        val centreY = weighted.toDouble() / total
        // The hint block sits between the border rows; its centre should be near the panel centre.
        assertTrue(
            abs(centreY - (image.height / 2.0)) <= 4.0,
            "hint centre $centreY is far from panel centre ${image.height / 2}",
        )
    }

    @Test
    fun `the duration band is drawn at the foot of a block`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.NOTE, 0, 4),
                    event(TapKind.NOTE, 4, 4),
                ),
                config,
            )
        val image = paint(panel)

        val x = grid.xOfCell(0.0).toInt() + BAND_X
        val band = Color(image.getRGB(x, BAND_Y))
        val fill = Color(image.getRGB(x, BAND_Y - 11))
        assertNotEquals(fill, band, "the band is drawn over the fill of the block")
        assertEquals(Theme.duration[durationLevel(4 * cellUnits)], band)
    }

    @Test
    fun `the band of a long note has a different colour than the band of a short one`() {
        val (panel, grid) = panel()
        val x = grid.xOfCell(0.0).toInt() + BAND_X

        panel.score =
            Score(
                listOf(
                    event(TapKind.NOTE, 0, 1),
                    event(TapKind.NOTE, 1, 7),
                ),
                config,
            )
        val short = Color(paint(panel).getRGB(x, BAND_Y))

        panel.score =
            Score(
                listOf(
                    event(TapKind.NOTE, 0, 7),
                    event(TapKind.NOTE, 7, 1),
                ),
                config,
            )
        val long = Color(paint(panel).getRGB(x, BAND_Y))

        assertNotEquals(short, long, "the two bands should be told apart by their colour")
        assertEquals(Theme.duration[durationLevel(cellUnits)], short)
        assertEquals(Theme.duration[durationLevel(7 * cellUnits)], long)
    }

    @Test
    fun `the band is tall enough to be read`() {
        val (panel, grid) = panel()
        panel.score = score()
        val image = paint(panel)
        val x = grid.xOfCell(0.0).toInt() + BAND_X
        val colour = Theme.duration[durationLevel(2 * cellUnits)].rgb

        // How many rows of the block are the band?
        var rows = 0
        for (y in 0 until image.height) {
            if (image.getRGB(x, y) == colour) rows++
        }
        assertTrue(rows >= 8, "the band should be at least eight pixels tall, not $rows")
    }

    @Test
    fun `the band does not touch the pitch label`() {
        // The label is drawn in the block above the band: the two must not meet.
        val blockTop = 13.0
        val blockHeight = 30.0
        val band = bandHeight(blockHeight)
        val fm = labelMetrics()
        val center = blockTop + (blockHeight - band) / 2
        val baseline = center + fm.ascent / 2.0 - 1
        val bottom = baseline + fm.descent
        assertTrue(
            bottom <= blockTop + blockHeight - band,
            "the label ends at $bottom and the band starts at ${blockTop + blockHeight - band}",
        )
    }

    /** The metrics of the font the pitch label is drawn with. */
    private fun labelMetrics(): FontMetrics {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.font = Theme.font(11f, Font.BOLD)
        val metrics = graphics.fontMetrics
        graphics.dispose()
        return metrics
    }

    @Test
    fun `the tooltip of a block names the pitch and the duration`() {
        val (panel, grid) = panel()
        panel.score = score()
        val x = grid.xOfCell(0.5).toInt()
        val event = MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, 28, 0, false)
        assertEquals("C4 · quarter", panel.getToolTipText(event))
    }

    @Test
    fun `the tooltip of a rest says so`() {
        val (panel, grid) = panel()
        panel.score =
            Score(
                listOf(
                    event(TapKind.REST, 0, 2),
                    event(TapKind.NOTE, 2, 6),
                ),
                config,
            )
        val x = grid.xOfCell(0.5).toInt()
        val event = MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, x, 28, 0, false)
        assertEquals("Rest · quarter", panel.getToolTipText(event))
    }

    @Test
    fun `the tooltip away from a block is the hint of the strip`() {
        val (panel, grid) = panel()
        panel.score = score()
        val event = MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 399, 28, 0, false)
        assertTrue(
            panel.getToolTipText(event)?.contains("Click a block") == true,
            "away from a block the strip shows its hint",
        )
    }

    // -------------------------------------------------------------------------------------------
    // The moves of the selection
    // -------------------------------------------------------------------------------------------

    @Test
    fun `clicking a block is a move of the user`() {
        val (panel, grid) = panel()
        panel.score = score()
        val moves = mutableListOf<SelectionMove>()
        panel.onSelectionChange = { moves += it }

        click(panel, grid.xOfCell(0.5).toInt())

        assertEquals(
            listOf(SelectionMove.USER),
            moves,
            "the click is the user moving the selection",
        )
    }

    @Test
    fun `the arrow keys are a move of the user`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        val moves = mutableListOf<SelectionMove>()
        panel.onSelectionChange = { moves += it }

        press(panel, KeyEvent.VK_RIGHT)

        assertEquals(
            listOf(SelectionMove.USER),
            moves,
            "the arrow key is the user moving the selection",
        )
    }

    @Test
    fun `the walk after a pitch is not a move of the user`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        val moves = mutableListOf<SelectionMove>()
        panel.onSelectionChange = { moves += it }

        panel.setSelectedPitch(Pitch(PitchStep.G, 0, 4))

        assertEquals(
            listOf(SelectionMove.EDIT, SelectionMove.WALK),
            moves,
            "the edit and the walk are not the user moving the selection",
        )
    }

    @Test
    fun `the fill is a move of the user`() {
        val (panel, grid) = panel()
        panel.score = score()
        val moves = mutableListOf<SelectionMove>()
        panel.onSelectionChange = { moves += it }

        panel.fillMelody()

        assertEquals(
            listOf(SelectionMove.USER),
            moves,
            "the fill takes the cursor to the first note",
        )
    }

    @Test
    fun `the undo is a move of the user`() {
        val (panel, grid) = panel()
        panel.score = score()
        click(panel, grid.xOfCell(0.5).toInt())
        panel.setSelectedPitch(Pitch(PitchStep.G, 0, 4))
        val moves = mutableListOf<SelectionMove>()
        panel.onSelectionChange = { moves += it }

        panel.undo()

        assertEquals(
            listOf(SelectionMove.USER),
            moves,
            "the undo puts the selection back where it was",
        )
    }

    /** Paints the strip and returns the image it drew on. */
    private fun paint(panel: NotationPanel): BufferedImage {
        val image = BufferedImage(400, 56, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        return image
    }

    /** The colours of the pixels where the two images are different, as the second one has them. */
    private fun differingPixels(
        a: BufferedImage,
        b: BufferedImage,
    ): List<Int> {
        val ink = mutableListOf<Int>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val first = a.getRGB(x, y)
                val second = b.getRGB(x, y)
                if (first != second) ink += second
            }
        }
        return ink
    }

    companion object {
        /** The row of the duration band in a strip that is 56 pixels high. */
        private const val BAND_Y = 41

        /** How far from the left edge of a block the band is read. */
        private const val BAND_X = 13
    }
}
