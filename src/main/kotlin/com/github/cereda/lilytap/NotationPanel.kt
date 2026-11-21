// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The score strip: the notes and rests of the take, drawn on the grid the timeline uses.
 *
 * Clicking a block selects it, double-clicking turns it into a rest (and back again),
 * dragging the border between two blocks makes the first one longer and the second one
 * shorter. The score itself does the work - this panel only turns clicks, keys and drags
 * into edits.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.max

/**
 * A stretch of the score the user has selected: the units from [start] to [end].
 *
 * The selection is kept as a stretch of time and not as a pair of indices: an edit moves
 * the blocks around, but the music under the cursor stays where it was.
 */
internal data class ScoreSelection(
    val start: Int,
    val end: Int,
)

/**
 * A state of the strip: the score, the selection and the caption.
 *
 * The history keeps whole states and not differences: the score is a small value object, and a
 * state brings the selection and the caption back with it, so an undo puts the strip back
 * exactly as it was.
 *
 * @param score the score of the strip.
 * @param selection the blocks that were selected, or null when nothing was.
 * @param caption what the header said.
 */
private data class Snapshot(
    val score: Score,
    val selection: ScoreSelection?,
    val caption: String,
)

/** How far the ring of a selected block grows beyond the block, in pixels. */
internal const val RING_GROW = 1.25

/** How wide the stroke of the ring of a selected block is, in pixels. */
internal const val RING_WIDTH = 3.0

/** How tall the duration band at the foot of a block is, in pixels. */
internal const val BAND_H = 12.0

/**
 * How tall the duration band of a block of the given height is.
 *
 * The band cannot take more than two fifths of the block: the label of the pitch sits in what
 * is left, and the two must not touch.
 *
 * @param blockHeight the height of the block, in pixels.
 * @return the height of the band, in pixels.
 */
internal fun bandHeight(blockHeight: Double): Double = minOf(BAND_H, blockHeight * 0.4)

/**
 * The corner radius of a block.
 *
 * A note is rounder than a rest, and a narrow block is a pill: its radius cannot be more than
 * half of its width.
 *
 * @param event the event the block shows.
 * @param width the width of the block.
 * @param height the height of the block.
 * @return the radius of the corners of the block.
 */
internal fun cornerRadius(
    event: Event,
    width: Double,
    height: Double,
): Double = if (event.kind == TapKind.NOTE) minOf(6.0, width / 2, height / 2) else 4.0

/**
 * The path of the ring drawn around a selected block.
 *
 * The ring sits outside the block and touches it: the path is the shape of the block grown
 * by [RING_GROW] pixels on every side, and its corner grows by the same, so the two share
 * the centre of their corner. The stroke is [RING_WIDTH] pixels wide, so the ring reaches a
 * little into the block - no sliver of the strip can show between the two - and reaches over
 * the little notch of the block's rounded corner.
 *
 * @param event the event the block shows.
 * @param x1 the left edge of the block.
 * @param x2 the right edge of the block.
 * @param top the top edge of the block.
 * @param height the height of the block.
 * @return the path the ring is drawn along.
 */
internal fun ringShape(
    event: Event,
    x1: Double,
    x2: Double,
    top: Double,
    height: Double,
): Shape {
    val r = cornerRadius(event, x2 - x1, height) + RING_GROW
    return rr(
        x1 - RING_GROW,
        top - RING_GROW,
        x2 - x1 + 2 * RING_GROW,
        height + 2 * RING_GROW,
        minOf(r, (x2 - x1 + 2 * RING_GROW) / 2, (height + 2 * RING_GROW) / 2),
    )
}

/**
 * The score strip: the notes and rests of the take, editable in place.
 *
 * The strip shares its grid with the recording timeline, so a block sits exactly under the
 * beat it belongs to. Clicking a block selects it, double-clicking turns it into a rest,
 * dragging the border between two blocks resizes them.
 */
internal class NotationPanel(
    private val grid: GridView,
) : JComponent() {
    /** The score the strip shows. */
    private var currentScore: Score? = null

    /**
     * The score to show.
     *
     * Setting it from the outside means a new score arrived, not an edit: the selection and
     * the history start over.
     */
    var score: Score?
        get() = currentScore
        set(value) {
            currentScore = value
            undoStack.clear()
            redoStack.clear()
            clearSelection()
            repaint()
        }

    /** The states before each edit: undo walks back through them. */
    private val undoStack = ArrayDeque<Snapshot>()

    /** The states that were undone: redo walks forward through them. */
    private val redoStack = ArrayDeque<Snapshot>()

    /** Called whenever the score changes, so the output follows. */
    var onEdit: ((Score) -> Unit)? = null

    /** Called whenever the selection changes, so the header can follow it. */
    var onSelectionChange: (() -> Unit)? = null

    /** What the header says about the selection: what is selected, or what just happened. */
    var caption: String = NOTHING_SELECTED
        private set

    /** The stretch of the score the user has selected. */
    internal var selection: ScoreSelection? = null
        private set

    /** The block the selection started from: a shift-click extends the selection from here. */
    private var anchor: ScoreSelection? = null

    private var dragIndex = -1 // the border being dragged
    private var dragOrigin: Score? = null // the score when the drag started
    private var dragCell = 0 // the cell the dragged border was on
    private var dragRecorded = false // whether the drag already made a step in the history
    private var pressX = 0.0
    private var pressIndex = -1 // the block that was pressed
    private var hoverBorder = -1
    private var hoverBlock = -1

    /**
     * The glow around a selected block: the width of every stroke and how much of it shows.
     *
     * The strokes are drawn from the narrowest to the widest, and each one is fainter than
     * the last, so the light falls off as it leaves the ring. They are wider than the ring
     * itself, so the halo shows around it and not under it.
     */
    private val glows = listOf(6f to 26, 8f to 16, 10f to 10)

    /** Current playback position in ms (-1 when not playing). */
    var playbackPositionMs: Double = -1.0
        set(v) {
            field = v
            repaint()
        }

    /** Removes this panel's grid-change listener, so GridView doesn't hold a stale reference. */
    private val removeGridListener = grid.onChange { repaint() }

    /** Detaches this panel from the shared grid. Call when the panel is no longer needed. */
    fun detach() {
        removeGridListener()
    }

    init {
        isOpaque = false
        isFocusable = true
        // The strip keeps its height, whatever else happens in the stage: a layout that
        // squeezes it would push the music out of the window.
        minimumSize = Dimension(0, 68)
        // The arrow keys belong to the selection, not to the focus traversal.
        focusTraversalKeysEnabled = false
        preferredSize = Dimension(STAGE_WIDTH, 68)
        toolTipText =
            "Click a block to select it, double-click to turn it into a rest, " +
            "drag a border to change its length. Keys: S splits, X swaps, M merges, Ctrl+Z undoes."
        // The strip shares the grid with the timeline: when it scrolls, the strip follows.
        // The cell-to-pixel mapping depends on the panel's width; keep it in sync on resize
        // instead of mutating shared state during paint.
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    grid.viewWidth = e.component.width
                }
            },
        )
        val mouse =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    // The click belongs to the strip: it must not reach the window, which
                    // parks the focus on the background when it sees one.
                    e.consume()
                    requestFocusInWindow()
                    val border = borderAt(e.x.toDouble())
                    if (border >= 0) {
                        startDrag(border)
                        return
                    }
                    pressX = e.x.toDouble()
                    pressIndex = blockAt(e.x.toDouble())
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (dragIndex >= 0) dragTo(e.x.toDouble())
                }

                override fun mouseReleased(e: MouseEvent) {
                    e.consume()
                    if (dragIndex >= 0) {
                        dragIndex = -1
                        dragOrigin = null
                        dragRecorded = false
                        return
                    }
                    // A click is a press and a release that barely moved.
                    if (pressIndex >= 0 && abs(e.x - pressX) <= 3.0) {
                        if (e.clickCount >= 2) {
                            flip(pressIndex)
                        } else {
                            select(pressIndex, e.isShiftDown)
                        }
                    }
                    pressIndex = -1
                }

                override fun mouseMoved(e: MouseEvent) {
                    val border = borderAt(e.x.toDouble())
                    if (border != hoverBorder) {
                        hoverBorder = border
                        repaint()
                    }
                    val block = if (border >= 0) -1 else blockAt(e.x.toDouble())
                    if (block != hoverBlock) {
                        hoverBlock = block
                        repaint()
                    }
                    cursor =
                        Cursor.getPredefinedCursor(
                            if (border >= 0) Cursor.E_RESIZE_CURSOR else Cursor.DEFAULT_CURSOR,
                        )
                }

                override fun mouseExited(e: MouseEvent) {
                    hoverBorder = -1
                    hoverBlock = -1
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    handleKey(e)
                }
            },
        )
        // The wheel scrolls the grid, and with it the timeline.
        // grid.scrollTo() notifies listeners (repaint), so no extra repaint needed here.
        addMouseWheelListener { e ->
            if (grid.scrollable) {
                grid.scrollTo(grid.scrollX + e.preciseWheelRotation * 60)
            }
        }
    }

    /**
     * The tooltip of the strip: what a block says, or the hint when the mouse is not on one.
     *
     * The band of the block says how long the note is, in four steps; the tooltip says it
     * exactly, so the colour only has to carry the rough idea.
     *
     * @param event the mouse event that asks for the tooltip.
     * @return the text of the tooltip.
     */
    override fun getToolTipText(event: MouseEvent): String? {
        val s = score ?: return super.getToolTipText(event)
        val index = blockAt(event.x.toDouble())
        if (index < 0) return super.getToolTipText(event)
        val block = s.events[index]
        val what = block.pitch?.scientific ?: "Rest"
        return "$what · ${durationName(block.lengthUnits)}"
    }

    // -------------------------------------------------------------------------------------------
    // The selection
    // -------------------------------------------------------------------------------------------

    /** The number of blocks the selection covers. */
    val selectedBlocks: Int
        get() {
            val s = score ?: return 0
            val range = selectedRange(s) ?: return 0
            return range.last - range.first + 1
        }

    /** Whether the selected block can be split in half. */
    val canSplit: Boolean
        get() {
            val s = score ?: return false
            val range = selectedRange(s) ?: return false
            if (range.first != range.last) return false
            return s.events[range.first].lengthUnits >= 2
        }

    /** Whether the two selected blocks can be swapped. */
    val canSwap: Boolean get() = selectedBlocks == 2

    /** Whether the selected blocks can be merged into one. */
    val canMerge: Boolean get() = selectedBlocks >= 2

    /**
     * The pitch of the selected block, when a single note is selected.
     *
     * A rest has no pitch, and a range of blocks has more than one, so both give null.
     */
    val selectedPitch: Pitch?
        get() {
            val s = score ?: return null
            val range = selectedRange(s) ?: return null
            if (range.first != range.last) return null
            return s.events[range.first].pitch
        }

    /** The number of notes in the take: the rests carry no pitch. */
    val noteCount: Int
        get() = score?.events?.count { it.kind == TapKind.NOTE } ?: 0

    /**
     * The place of the selected note among the notes of the take, counting from one.
     *
     * A rest, a range of blocks and an empty selection have no place among the notes, so
     * they give null.
     */
    val selectedNoteNumber: Int?
        get() {
            val s = score ?: return null
            val range = selectedRange(s) ?: return null
            if (range.first != range.last) return null
            val index = range.first
            if (s.events[index].kind != TapKind.NOTE) return null
            return s.events.take(index + 1).count { it.kind == TapKind.NOTE }
        }

    /**
     * Where the cursor is among the notes of the take: "3 / 12".
     *
     * A dash means that the cursor is not on a note: it is on a rest, on a range of blocks,
     * or on nothing at all.
     */
    val cursorText: String
        get() {
            val notes = noteCount
            if (notes == 0) return ""
            val place = selectedNoteNumber
            return if (place == null) "— / $notes" else "$place / $notes"
        }

    /** The indices of the blocks the selection covers. */
    private fun selectedRange(s: Score): IntRange? {
        val sel = selection ?: return null
        val indices = s.events.indices.filter { i -> covers(s.events[i], sel) }
        if (indices.isEmpty()) return null
        return indices.first()..indices.last()
    }

    /** Whether an event lies inside a stretch of the score. */
    private fun covers(
        event: Event,
        range: ScoreSelection,
    ): Boolean = event.startUnits < range.end && event.endUnits > range.start

    /** Selects a block, or extends the selection to it. */
    private fun select(
        index: Int,
        extend: Boolean,
    ) {
        val s = score ?: return
        val event = s.events.getOrNull(index) ?: return
        val clicked = ScoreSelection(event.startUnits, event.endUnits)
        val base = anchor
        if (extend && base != null) {
            selection = ScoreSelection(minOf(base.start, clicked.start), maxOf(base.end, clicked.end))
        } else {
            anchor = clicked
            selection = clicked
        }
        caption = describe(s, selection!!)
        repaint()
        onSelectionChange?.invoke()
    }

    /** Forgets the selection. */
    fun clearSelection() {
        selection = null
        anchor = null
        caption = NOTHING_SELECTED
        repaint()
        onSelectionChange?.invoke()
    }

    /**
     * Puts the selection on a block without changing the message.
     *
     * The cursor moves on its own after a pitch is written, and the message of the edit that
     * came before it says more than the name of the block the cursor is on.
     *
     * @param index the index of the block.
     */
    private fun selectQuietly(index: Int) {
        val s = score ?: return
        val event = s.events.getOrNull(index) ?: return
        val moved = ScoreSelection(event.startUnits, event.endUnits)
        selection = moved
        anchor = moved
        repaint()
        onSelectionChange?.invoke()
    }

    /**
     * Takes the cursor to the first note of the take.
     *
     * The melody is filled in from the top: this button starts the walk, and every pitch
     * that is written moves the cursor on to the next note.
     */
    fun fillMelody() {
        val s = score ?: return
        val first = s.events.indices.firstOrNull { s.events[it].kind == TapKind.NOTE } ?: return
        select(first, false)
    }

    /**
     * Moves the cursor to the next note of the take.
     *
     * The rests are skipped: a rest has no pitch to write. When the take has no note left,
     * the cursor stays where it is.
     */
    private fun advanceCursor() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        val next =
            (range.first + 1 until s.events.size).firstOrNull { s.events[it].kind == TapKind.NOTE }
                ?: return
        selectQuietly(next)
    }

    /** Moves the selection one block to the left or to the right. */
    private fun moveSelection(direction: Int) {
        val s = score ?: return
        val range = selectedRange(s)
        if (range == null) {
            // Nothing is selected: the first arrow key picks the block at the edge.
            select(if (direction > 0) 0 else s.events.size - 1, false)
            return
        }
        val count = range.last - range.first + 1
        val first = (range.first + direction).coerceIn(0, s.events.size - count)
        val last = first + count - 1
        val moved = ScoreSelection(s.events[first].startUnits, s.events[last].endUnits)
        selection = moved
        anchor = moved
        caption = describe(s, moved)
        repaint()
        onSelectionChange?.invoke()
    }

    /** A short description of what is selected: "c'2 selected", "3 blocks selected". */
    private fun describe(
        s: Score,
        range: ScoreSelection,
    ): String {
        val indices = s.events.indices.filter { i -> covers(s.events[i], range) }
        if (indices.isEmpty()) return NOTHING_SELECTED
        if (indices.size == 1) return "${eventText(s.events[indices.first()])} selected"
        return "${indices.size} blocks selected"
    }

    // -------------------------------------------------------------------------------------------
    // The history
    // -------------------------------------------------------------------------------------------

    /** Whether there is an edit to undo. */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Whether there is an edit to redo. */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Remembers the state the strip is in now, so the edit that comes next can be undone.
     *
     * A new edit throws away the edits that were undone: the history is a line, not a tree.
     */
    private fun pushHistory() {
        val snapshot = currentSnapshot() ?: return
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    /** The state the strip is in now, or null when there is no score. */
    private fun currentSnapshot(): Snapshot? {
        val s = currentScore ?: return null
        return Snapshot(s, selection, caption)
    }

    /**
     * Takes the score back one edit.
     *
     * The selection and the caption come back with it, so the header says what it said before
     * the edit that was undone.
     */
    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        currentSnapshot()?.let { redoStack.addLast(it) }
        restore(snapshot)
    }

    /** Does the edit that was undone again. */
    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        currentSnapshot()?.let { undoStack.addLast(it) }
        restore(snapshot)
    }

    /**
     * Puts a state back on the strip: the score, the selection and the caption.
     *
     * @param snapshot the state to put back.
     */
    private fun restore(snapshot: Snapshot) {
        currentScore = snapshot.score
        selection = snapshot.selection
        anchor = snapshot.selection
        caption = snapshot.caption
        repaint()
        onEdit?.invoke(snapshot.score)
        onSelectionChange?.invoke()
    }

    /**
     * Puts the score of the take back on the strip.
     *
     * The revert is an edit like any other, so it can be undone: a mis-click does not throw
     * the work away.
     *
     * @param original the score the take ended with.
     */
    fun revertTo(original: Score) {
        if (currentScore == original) return
        pushHistory()
        currentScore = original
        selection = null
        anchor = null
        caption = REVERTED
        repaint()
        onEdit?.invoke(original)
        onSelectionChange?.invoke()
    }

    // -------------------------------------------------------------------------------------------
    // The edits
    // -------------------------------------------------------------------------------------------

    /** Splits the selected block in half: the second half becomes a note. */
    fun splitSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.first != range.last) return
        val index = range.first
        if (s.events[index].lengthUnits < 2) return
        val before = eventText(s.events[index])
        val edited = s.split(index)
        applyEdit(edited, before)
    }

    /** Swaps the two selected blocks: each keeps its length and moves in time. */
    fun swapSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.last - range.first + 1 != 2) return
        val before =
            eventText(s.events[range.first]) + " " + eventText(s.events[range.last])
        val edited = s.swap(range.first)
        applyEdit(edited, before)
    }

    /** Merges the selected blocks into one: a note wins over a silence. */
    fun mergeSelected() {
        val s = score ?: return
        val range = selectedRange(s) ?: return
        if (range.last == range.first) return
        val before = mergedBefore(s, range)
        val edited = s.merge(range.first, range.last)
        applyEdit(edited, before)
    }

    /** Turns the selected block into a rest, or a rest back into a note. */
    private fun flip(index: Int) {
        val s = score ?: return
        val event = s.events.getOrNull(index) ?: return
        val before = eventText(event)
        val edited = s.toggleRest(index)
        applyEdit(edited, before)
    }

    /**
     * Puts a pitch on the selected block and moves the cursor to the next note.
     *
     * A rest has no pitch, but a pitch can be given to it: the silence becomes a note, which
     * is how a gap in the melody is filled in place. Every pitch that is accepted moves the
     * cursor on, so a whole melody can be written in one go.
     *
     * @param pitch the pitch the note sounds.
     * @return true when the pitch was accepted.
     */
    fun setSelectedPitch(pitch: Pitch): Boolean {
        val s = score ?: return false
        val range = selectedRange(s) ?: return false
        if (range.first != range.last) return false
        val index = range.first
        val event = s.events[index]
        val before = event.pitch?.scientific ?: REST_NAME
        val edited =
            if (event.kind == TapKind.REST) {
                s.fillRest(index, pitch)
            } else {
                s.setPitch(index, pitch)
            }
        if (edited != s) {
            applyEdit(edited, before, pitch.scientific)
        }
        advanceCursor()
        return true
    }

    /**
     * Puts an edited score on the strip and keeps the selection on the same stretch of music.
     *
     * The caption says what the edit did: the blocks before the edit, an arrow, and the blocks
     * that are there now.
     *
     * @param edited the edited score.
     * @param before what the selected blocks were before the edit.
     * @param after what the selection is now, when the edit does not change the blocks.
     */
    private fun applyEdit(
        edited: Score,
        before: String,
        after: String? = null,
    ) {
        val range = selection
        pushHistory()
        currentScore = edited
        // The edit moved the blocks around; the selection follows the music, not the indices.
        range?.let { restoreSelection(it) }
        val now = after ?: range?.let { afterText(edited, it) } ?: before
        caption = "$before → $now"
        repaint()
        onEdit?.invoke(edited)
        onSelectionChange?.invoke()
    }

    /**
     * The blocks that are there now over a stretch of the score.
     *
     * @param s the score to read.
     * @param range the stretch of the score.
     * @return the LilyPond text of the blocks, separated by spaces.
     */
    private fun afterText(
        s: Score,
        range: ScoreSelection,
    ): String = s.events.filter { covers(it, range) }.joinToString(" ") { eventText(it) }

    /**
     * The blocks that are about to be merged, as "c'2 + r2".
     *
     * A long range is shortened to "12 blocks", so the caption stays short enough to be read.
     *
     * @param s the score to read.
     * @param range the indices of the blocks to merge.
     * @return the text of the blocks before the merge.
     */
    private fun mergedBefore(
        s: Score,
        range: IntRange,
    ): String {
        val parts = range.map { eventText(s.events[it]) }
        val joined = parts.joinToString(" + ")
        return if (joined.length <= MAX_CAPTION_PART) joined else "${parts.size} blocks"
    }

    /** Selects the blocks that cover a stretch of the score. */
    private fun restoreSelection(range: ScoreSelection) {
        val s = score ?: return
        val indices = s.events.indices.filter { i -> covers(s.events[i], range) }
        if (indices.isEmpty()) {
            selection = null
            anchor = null
            return
        }
        val restored = ScoreSelection(s.events[indices.first()].startUnits, s.events[indices.last()].endUnits)
        selection = restored
        anchor = restored
    }

    /**
     * Handles a key press in the strip, with its modifiers.
     *
     * Ctrl (or Cmd) with Z undoes the last edit, and with Shift it does it again; Ctrl with Y
     * redoes it as well, the way Windows does.
     *
     * @param e the key event.
     */
    internal fun handleKey(e: KeyEvent) {
        if (e.isControlDown || e.isMetaDown) {
            when (e.keyCode) {
                KeyEvent.VK_Z -> {
                    if (e.isShiftDown) redo() else undo()
                    return
                }

                KeyEvent.VK_Y -> {
                    redo()
                    return
                }
            }
        }
        handleKey(e.keyCode)
    }

    /**
     * Handles a key press in the strip.
     *
     * The keys are read from the key code, so they work with or without shift and whatever
     * the caps lock says.
     *
     * @param keyCode the key that was pressed.
     */
    internal fun handleKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.VK_S -> splitSelected()
            KeyEvent.VK_X -> swapSelected()
            KeyEvent.VK_M -> mergeSelected()
            KeyEvent.VK_ESCAPE -> clearSelection()
            KeyEvent.VK_LEFT -> moveSelection(-1)
            KeyEvent.VK_RIGHT -> moveSelection(1)
        }
    }

    // -------------------------------------------------------------------------------------------
    // The borders
    // -------------------------------------------------------------------------------------------

    /** The border closest to the x pixel, or -1 when the mouse is not near one. */
    private fun borderAt(x: Double): Int {
        val s = score ?: return -1
        for (i in 0 until s.events.size - 1) {
            // Only a border that sits on a cell can be dragged: the mouse cannot tell a
            // sixty-fourth of a cell from its neighbour.
            if (s.events[i].endUnits % s.config.cellUnits != 0) continue
            val bx = grid.xOfCell(s.events[i].endUnits.toDouble() / s.config.cellUnits)
            if (abs(x - bx) <= GRAB_PX) return i
        }
        return -1
    }

    /** The block under the x pixel, or -1. */
    private fun blockAt(x: Double): Int {
        val s = score ?: return -1
        val units = grid.cellOfX(x) * s.config.cellUnits
        for ((i, event) in s.events.withIndex()) {
            if (units >= event.startUnits && units < event.endUnits) return i
        }
        return -1
    }

    private fun startDrag(index: Int) {
        val s = score ?: return
        dragIndex = index
        dragOrigin = s
        dragCell = s.events[index].endUnits / s.config.cellUnits
        dragRecorded = false
    }

    /**
     * Moves the dragged border to the cell under the mouse. The border snaps to the nearest
     * cell, and bar lines pull it harder, so a note can be lined up exactly with the bar.
     */
    private fun dragTo(x: Double) {
        val origin = dragOrigin ?: return
        val index = dragIndex
        if (index < 0) return
        val target = origin.snapBorder(grid.cellOfX(x))
        val edited = origin.moveBoundary(index, (target - dragCell) * origin.config.cellUnits)
        if (edited == score) return
        // The whole drag is one edit: the history gets a step on the first move only.
        if (!dragRecorded) {
            pushHistory()
            dragRecorded = true
        }
        currentScore = edited
        clearSelection()
        onEdit?.invoke(edited)
        repaint()
    }

    // -------------------------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------------------------

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val w = width.toDouble()
            val h = height.toDouble()
            g2.color = Theme.code
            g2.fill(rr(0.0, 0.0, w, h, 14.0))
            g2.color = Theme.line
            g2.draw(rr(0.5, 0.5, w - 1, h - 1, 14.0))

            val s = score
            if (s == null || grid.span <= 0) {
                drawHint(g2, w, h)
                return@paint2d
            }
            g2.clip = rr(1.0, 1.0, w - 2, h - 2, 13.0)

            val top = 13.0
            val blockH = h - 26.0
            drawBarLines(g2, h)
            drawSelectionBand(g2, s, top, blockH)
            for (event in s.events) {
                val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
                val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
                drawBlock(g2, event, BlockBox(x1, x2, top, top + blockH))
            }
            drawHoveredBlock(g2, s, top, blockH)
            drawSelection(g2, s, top, blockH)
            drawHoveredBorder(g2, s, top, blockH)

            // Playback playhead: a thin line moving across the score blocks.
            if (playbackPositionMs >= 0) {
                val cell = playbackPositionMs / s.config.cellMs
                val px = grid.xOfCell(cell)
                if (px >= -10 && px <= w + 10) {
                    g2.color = Theme.ok
                    g2.stroke = BasicStroke(2f)
                    g2.draw(Line2D.Double(px, top - 6, px, top + blockH + 6))
                    val head = Path2D.Double()
                    head.moveTo(px - 4, top - 6)
                    head.lineTo(px + 4, top - 6)
                    head.lineTo(px, top + 1)
                    head.closePath()
                    g2.fill(head)
                }
            }
            if (hasFocus()) drawFocus(g2, w, h)
        }
    }

    /** The bar lines, so the strip reads as the same grid as the timeline. */
    private fun drawBarLines(
        g2: Graphics2D,
        h: Double,
    ) {
        g2.stroke = BasicStroke(1f)
        for (bar in 0..grid.bars) {
            val x = grid.xOfCell((bar * grid.cellsPerBar).toDouble())
            if (x < -2 || x > width + 2) continue
            g2.color = Theme.text.alpha(70)
            g2.drawLine(x.toInt(), 4, x.toInt(), (h - 4).toInt())
        }
    }

    /** One note or rest: a filled block for a note, an outlined one for a rest. */
    private fun drawBlock(
        g2: Graphics2D,
        event: Event,
        box: BlockBox,
    ) {
        if (box.width <= 1.0) return
        val note = event.kind == TapKind.NOTE
        val shape: Shape
        if (note) {
            val r = cornerRadius(event, box.width, box.height)
            g2.paint =
                GradientPaint(
                    box.x1.toFloat(),
                    0f,
                    Theme.accent,
                    (box.x1 + max(box.width, 40.0)).toFloat(),
                    0f,
                    Theme.accent2,
                )
            shape = rr(box.x1, box.top, box.width, box.height, r)
            g2.fill(shape)
        } else {
            g2.color = Theme.warn.alpha(35)
            shape = rr(box.x1, box.top, box.width, box.height, 4.0)
            g2.fill(shape)
        }
        val band = bandHeight(box.height)
        drawDurationBand(g2, event, shape, band)
        if (!note) {
            // The outline of a rest goes over the band, so the shape of the rest stays whole.
            g2.color = Theme.warn.alpha(150)
            g2.stroke = BasicStroke(1.2f)
            g2.draw(rr(box.x1 + 0.5, box.top + 0.5, box.width - 1, box.height - 1, 4.0))
        }
        drawLabel(g2, event, box, band, if (note) Theme.onAccent else Theme.warn)
    }

    /**
     * The duration band at the foot of a block.
     *
     * The block says what the note is (its pitch) and how long it is (its width); the band says
     * how long it is again, in a second channel: the four buckets have a colour of their own,
     * so the length of a note reads at a glance. The band is drawn inside the block, so it
     * follows its shape, and a line of the strip's own colour separates it from the gradient of
     * the block - without it, a band of the same hue as the block would disappear into it.
     *
     * @param g2 the graphics context.
     * @param event the event the block shows.
     * @param shape the shape of the block.
     * @param height how tall the band is.
     */
    private fun drawDurationBand(
        g2: Graphics2D,
        event: Event,
        shape: Shape,
        height: Double,
    ) {
        val level = durationLevel(event.lengthUnits)
        val bounds = shape.bounds2D
        val clip = g2.clip
        val area = Area(shape)
        if (clip != null) area.intersect(Area(clip))
        g2.clip = area
        // The separator: the strip's own colour, so the band never merges with the gradient.
        g2.color = Theme.code
        g2.fill(Rectangle2D.Double(bounds.x, bounds.maxY - height, bounds.width, 1.0))
        g2.color = Theme.duration[level]
        g2.fill(Rectangle2D.Double(bounds.x, bounds.maxY - height + 1, bounds.width, height - 1))
        g2.clip = clip
    }

    /** The name of the note, drawn in the block when there is room for it. */
    private fun drawLabel(
        g2: Graphics2D,
        event: Event,
        box: BlockBox,
        band: Double,
        color: Color,
    ) {
        val label = event.pitch?.scientific ?: REST_LABEL
        g2.font = Theme.font(11f, Font.BOLD)
        val fm = g2.fontMetrics
        if (fm.stringWidth(label) > box.width - 10) return
        g2.color = color
        val tx = (box.centerX - fm.stringWidth(label) / 2.0).toFloat()
        // The label sits in the block above the band, so the two never touch.
        val center = box.top + (box.height - band) / 2
        g2.drawString(label, tx, (center + fm.ascent / 2.0 - 1).toFloat())
    }

    /** A soft band behind a range of blocks, so it reads as one thing. */
    private fun drawSelectionBand(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val range = selectedRange(s) ?: return
        if (range.first == range.last) return
        val first = s.events[range.first]
        val last = s.events[range.last]
        val x1 = grid.xOfCell(first.startUnits.toDouble() / s.config.cellUnits)
        val x2 = grid.xOfCell(last.endUnits.toDouble() / s.config.cellUnits)
        g2.color = Theme.accent.alpha(28)
        g2.fill(Rectangle2D.Double(x1, top - 4, x2 - x1, hh + 8))
    }

    /**
     * A ring around every selected block.
     *
     * The ring is the text colour, not the accent: the blocks are painted in the accent
     * colours, and a ring in the accent would disappear into the block it surrounds. A glow
     * is drawn under the ring, so the block also stands out from the strip around it.
     */
    private fun drawSelection(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val range = selectedRange(s) ?: return
        for (index in range) {
            val event = s.events[index]
            val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
            val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
            val ring = ringShape(event, x1, x2, top, hh)
            // The glow: the same ring, wider and fainter, so the light falls off outwards.
            for ((width, a) in glows) {
                g2.color = Theme.text.alpha(a)
                g2.stroke = BasicStroke(width)
                g2.draw(ring)
            }
            g2.color = Theme.text
            g2.stroke = BasicStroke(RING_WIDTH.toFloat())
            g2.draw(ring)
        }
    }

    /** A thin outline around the block under the mouse. */
    private fun drawHoveredBlock(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val index = hoverBlock
        if (index < 0 || index >= s.events.size) return
        val event = s.events[index]
        val x1 = grid.xOfCell(event.startUnits.toDouble() / s.config.cellUnits) + 1.0
        val x2 = grid.xOfCell(event.endUnits.toDouble() / s.config.cellUnits) - 1.0
        g2.color = Theme.dim.alpha(140)
        g2.stroke = BasicStroke(1f)
        g2.draw(rr(x1, top, x2 - x1, hh, 6.0))
    }

    /** The border under the mouse, so it is clear what can be dragged. */
    private fun drawHoveredBorder(
        g2: Graphics2D,
        s: Score,
        top: Double,
        hh: Double,
    ) {
        val index = hoverBorder
        if (index < 0 || index >= s.events.size - 1) return
        val x = grid.xOfCell(s.events[index].endUnits.toDouble() / s.config.cellUnits)
        g2.color = Theme.accent.alpha(200)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(x.toInt(), (top - 4).toInt(), x.toInt(), (top + hh + 4).toInt())
    }

    /** A thin accent line, so it is clear the strip has the keyboard. */
    private fun drawFocus(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        g2.color = Theme.accent.alpha(170)
        g2.stroke = BasicStroke(2f)
        g2.drawLine(14, (h - 4).toInt(), (w - 14).toInt(), (h - 4).toInt())
    }

    /** What the strip says before there is a take to show. */
    private fun drawHint(
        g2: Graphics2D,
        w: Double,
        h: Double,
    ) {
        val title = "Your score will show up here"
        val hint = "Click a block to select it, double-click to turn it into a rest, drag a border to change its length."
        g2.font = Theme.font(13f, Font.BOLD)
        val titleFm = g2.fontMetrics
        g2.font = Theme.font(12f)
        val hintFm = g2.fontMetrics
        // The two lines are one block, and the block sits in the middle of the panel.
        val gap = 6.0
        val blockTop = (h - (titleFm.ascent + titleFm.descent + gap + hintFm.ascent + hintFm.descent)) / 2
        g2.font = Theme.font(13f, Font.BOLD)
        g2.color = Theme.dim
        g2.drawString(title, ((w - titleFm.stringWidth(title)) / 2).toFloat(), (blockTop + titleFm.ascent).toFloat())
        g2.font = Theme.font(12f)
        g2.color = Theme.faint
        g2.drawString(hint, ((w - hintFm.stringWidth(hint)) / 2).toFloat(), (blockTop + hintFm.height + gap + hintFm.ascent).toFloat())
    }

    companion object {
        /** How close to a border the mouse has to be to grab it, in pixels. */
        private const val GRAB_PX = 7.0

        /** How long the "before" part of a caption may be before it is shortened. */
        private const val MAX_CAPTION_PART = 24

        /** What a rest says in its block: it has no pitch. */
        private const val REST_LABEL = "R"

        /** What the header says about a rest that is given a pitch. */
        private const val REST_NAME = "Rest"

        /** What the header says when nothing is selected. */
        const val NOTHING_SELECTED = "Nothing selected"

        /** What the header says after the score was put back the way the take ended. */
        const val REVERTED = "Reverted to the take"
    }

    /** The box a block is drawn in. */
    private data class BlockBox(
        val x1: Double,
        val x2: Double,
        val top: Double,
        val bottom: Double,
    ) {
        val width: Double get() = x2 - x1
        val height: Double get() = bottom - top
        val centerX: Double get() = (x1 + x2) / 2
        val centerY: Double get() = (top + bottom) / 2
    }
}
