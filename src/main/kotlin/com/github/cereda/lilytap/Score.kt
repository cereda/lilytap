// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The score: what a take becomes, and what the window version lets you change.
 *
 * A score is pure data - a list of events that covers every unit of the recording
 * window - so it can be edited, rendered and played without touching the recorder.
 *
 * Pipeline:  taps (ms) -> quantize to grid cells -> events (note/rest + length)
 *            -> score -> LilyPond text
 *
 * A unit is a sixty-fourth of a whole note ([UNITS_PER_WHOLE]): the recording grid says
 * where the taps fell, but the score itself is free to hold any duration LilyPond can
 * write - a note can be split in the middle of a cell, or a border can sit between two
 * notes that are only a sixteenth apart.
 */

import kotlin.math.abs

/**
 * One piece of a score: a note or a rest, [lengthUnits] internal units long.
 *
 * A unit is a sixty-fourth of a whole note ([UNITS_PER_WHOLE]), so an event can be shorter
 * than a grid cell: the recording grid says where the taps fell, but the score can hold
 * any duration LilyPond can write.
 */
data class Event(
    val kind: TapKind,
    val startUnits: Int,
    val lengthUnits: Int,
) {
    /** The first unit after this event. */
    val endUnits: Int get() = startUnits + lengthUnits
}

/** One written note or rest in LilyPond output. [tieToNext] means "tie this note into the following one". */
data class Atom(
    val kind: TapKind,
    val units: Int,
    val tieToNext: Boolean,
)

/**
 * A score: the notes and rests of a take, ready to be edited or played.
 *
 * The score covers every unit of the recording window exactly once: the events are
 * contiguous and add up to [Config.totalUnits]. That is what keeps a bar full when a
 * border is dragged around.
 *
 * @property events the notes and rests, in grid order. Two rests never follow each
 * other: a silence is one event, however long it lasts.
 * @property config the session settings the score belongs to.
 */
data class Score(
    val events: List<Event>,
    val config: Config,
) {
    init {
        require(events.isNotEmpty()) { "A score needs at least one event" }
        var units = 0
        for ((i, event) in events.withIndex()) {
            require(event.lengthUnits >= 1) { "An event must last at least one unit" }
            require(event.startUnits == units) { "The events of a score must be contiguous" }
            require(i == 0 || events[i - 1].kind != TapKind.REST || event.kind != TapKind.REST) {
                "Two rests in a row are one rest"
            }
            units += event.lengthUnits
        }
        require(units == config.totalUnits) { "The score must cover all ${config.totalUnits} units" }
    }

    /** The number of internal units the score covers. */
    val totalUnits: Int get() = config.totalUnits

    /** The score as LilyPond text, one bar per line. */
    val lilypond: String get() = format(layout(events, config), config.pitch)

    /**
     * Turns the event at [index] into a rest, or a rest back into a note.
     *
     * Two rests in a row are one silence, so a note that becomes a rest is merged with
     * the rests next to it.
     *
     * @param index the index of the event in [events].
     * @return the edited score.
     */
    fun toggleRest(index: Int): Score {
        require(index in events.indices) { "There is no event $index" }
        val flipped = events.toMutableList()
        val event = flipped[index]
        flipped[index] =
            event.copy(kind = if (event.kind == TapKind.NOTE) TapKind.REST else TapKind.NOTE)
        return copy(events = mergeRests(flipped))
    }

    /**
     * Splits the event at [index] in two halves.
     *
     * The cut is at the middle of the event: the first half is the shorter one when the
     * event has an odd length. The first half keeps the kind of the event, and the second
     * half becomes a note - so a silence that is split in two becomes a silence followed by
     * a note, which is how a note is put inside a gap.
     *
     * @param index the index of the event in [events].
     * @return the edited score.
     */
    fun split(index: Int): Score {
        require(index in events.indices) { "There is no event $index" }
        val event = events[index]
        require(event.lengthUnits >= 2) { "An event of one unit cannot be split" }
        val leftLength = event.lengthUnits / 2
        val rightLength = event.lengthUnits - leftLength
        val split =
            events.toMutableList().apply {
                removeAt(index)
                add(index, event.copy(lengthUnits = leftLength))
                add(index + 1, Event(TapKind.NOTE, event.startUnits + leftLength, rightLength))
            }
        return copy(events = layOut(split))
    }

    /**
     * Swaps the events at [index] and [index + 1].
     *
     * Each block keeps its own length and moves in time: a note that is swapped with the
     * silence next to it keeps sounding for as long as it did, it just starts later.
     *
     * Two silences in a row are one silence, so a swap that leaves two silences touching
     * merges them into one.
     *
     * @param index the index of the first of the two events.
     * @return the edited score.
     */
    fun swap(index: Int): Score {
        require(index in 0 until events.size - 1) { "There is no pair of events at $index" }
        val first = events[index]
        val second = events[index + 1]
        val swapped =
            events.toMutableList().apply {
                this[index] = second.copy(startUnits = first.startUnits)
                this[index + 1] = first.copy(startUnits = first.startUnits + second.lengthUnits)
            }
        return copy(events = layOut(mergeRests(swapped)))
    }

    /**
     * Merges the events from [from] to [to] into a single one.
     *
     * The merged event lasts as long as the whole range. A note wins over a silence: the
     * silences inside the range are absorbed by the note, so a note followed by a silence
     * becomes one longer note.
     *
     * @param from the index of the first event of the range.
     * @param to the index of the last event of the range.
     * @return the edited score.
     */
    fun merge(
        from: Int,
        to: Int,
    ): Score {
        require(from in events.indices && to in events.indices) { "There is no event $from" }
        require(from <= to) { "A range runs from its first event to its last one" }
        val range = events.subList(from, to + 1)
        val length = range.sumOf { it.lengthUnits }
        val kind = if (range.any { it.kind == TapKind.NOTE }) TapKind.NOTE else TapKind.REST
        val merged = events.toMutableList()
        for (i in to downTo from) merged.removeAt(i)
        merged.add(from, Event(kind, events[from].startUnits, length))
        return copy(events = layOut(merged))
    }

    /**
     * Moves the border between event [index] and the next one by [deltaUnits] units.
     *
     * The event on the left grows by [deltaUnits] units and the one on the right shrinks by
     * the same amount, so the score keeps covering the same units. The drag stops when one
     * of the two would vanish: an event always keeps at least one unit, and an event that
     * is shorter than a cell (a split note) cannot be made shorter still.
     *
     * @param index the index of the event on the left of the border.
     * @param deltaUnits how many units to move the border to the right (negative moves left).
     * @return the edited score.
     */
    fun moveBoundary(
        index: Int,
        deltaUnits: Int,
    ): Score {
        require(index in 0 until events.size - 1) { "There is no border at $index" }
        val left = events[index]
        val right = events[index + 1]
        val cell = config.cellUnits
        val minLeft = minOf(cell, left.lengthUnits)
        val minRight = minOf(cell, right.lengthUnits)
        val shift = deltaUnits.coerceIn(-(left.lengthUnits - minLeft), right.lengthUnits - minRight)
        if (shift == 0) return this
        val moved = events.toMutableList()
        moved[index] = left.copy(lengthUnits = left.lengthUnits + shift)
        moved[index + 1] =
            right.copy(
                startUnits = right.startUnits + shift,
                lengthUnits = right.lengthUnits - shift,
            )
        return copy(events = moved)
    }

    /**
     * Snaps a fractional cell position to a border between two events.
     *
     * A bar line pulls harder than an ordinary cell: a border dragged close to one sticks
     * to it, so a note can be lined up exactly with the bar.
     *
     * The border is snapped to whole cells: the mouse cannot tell a sixty-fourth of a cell
     * from its neighbour, so a border that sits inside a cell stays where it is.
     *
     * @param position the position in cells, counted from the start of the score.
     * @param magnetCells how close to a bar line the border has to be to stick to it.
     * @return the cell the border ends up on.
     */
    fun snapBorder(
        position: Double,
        magnetCells: Double = BAR_LINE_MAGNET,
    ): Int {
        val barLine = nearestBarLine(position)
        val snapped =
            if (magnetCells > 0 && abs(position - barLine) <= magnetCells) {
                barLine
            } else {
                Math.round(position).toInt()
            }
        return snapped.coerceIn(0, config.totalCells)
    }

    /**
     * The bar line nearest to a fractional cell position.
     *
     * @param position the position in cells, counted from the start of the score.
     * @return the cell the bar line is on.
     */
    fun nearestBarLine(position: Double): Int {
        val perBar = config.cellsPerBar
        return Math.round(position / perBar).toInt() * perBar
    }

    companion object {
        /**
         * How close to a bar line (in cells) a dragged border has to be to stick to it.
         */
        const val BAR_LINE_MAGNET = 0.75
    }
}

/**
 * Merges rests that follow each other: two rests in a row are one longer rest.
 */
private fun mergeRests(events: List<Event>): List<Event> {
    val merged = mutableListOf<Event>()
    for (event in events) {
        val last = merged.lastOrNull()
        if (last != null && last.kind == TapKind.REST && event.kind == TapKind.REST) {
            merged[merged.lastIndex] = last.copy(lengthUnits = last.lengthUnits + event.lengthUnits)
        } else {
            merged += event
        }
    }
    return merged
}

/**
 * Numbers the events so that they follow each other: the first one starts at 0, and every
 * one starts where the previous one ended. An edit that moves a block around leaves the
 * starts behind, so they are numbered again here.
 */
private fun layOut(events: List<Event>): List<Event> {
    var units = 0
    return events.map { event ->
        val placed = event.copy(startUnits = units)
        units += event.lengthUnits
        placed
    }
}

// ---------------------------------------------------------------------------
// LilyPond formatting
// ---------------------------------------------------------------------------

/** Every duration LilyPond can write as a single token (plain or single-dotted), largest first. */
private val DURATIONS =
    listOf(
        96 to "1.",
        64 to "1",
        48 to "2.",
        32 to "2",
        24 to "4.",
        16 to "4",
        12 to "8.",
        8 to "8",
        6 to "16.",
        4 to "16",
        3 to "32.",
        2 to "32",
        1 to "64",
    )

/** Splits a length into the fewest greedy single-token durations, e.g. 5 -> [4, 1]. */
fun decompose(units: Int): List<Int> {
    require(units > 0)
    val out = mutableListOf<Int>()
    var remaining = units
    while (remaining > 0) {
        val piece = DURATIONS.first { it.first <= remaining }.first
        out += piece
        remaining -= piece
    }
    return out
}

/** Returns the LilyPond duration token for the given internal grid units. */
fun lilyDuration(units: Int): String = DURATIONS.first { it.first == units }.second

/**
 * Cuts events at barlines (adding ties for notes) and returns the atoms of each bar.
 *
 * An event is one sustained sound, so every piece of it is tied to the next one - across barlines
 * and also within a bar, where a long value has to be written as several LilyPond tokens
 * (a full bar of 4/2, for instance, is a dotted whole tied to a half).
 *
 * @param events the events to lay out.
 * @param config the session configuration.
 * @return one list of [Atom] per bar.
 */
fun layout(
    events: List<Event>,
    config: Config,
): List<List<Atom>> {
    val barUnits = config.timeSignature.unitsPerBar
    val bars = List(config.bars) { mutableListOf<Atom>() }

    for (event in events) {
        var pos = event.startUnits
        var remaining = event.lengthUnits
        val pieces = mutableListOf<Pair<Int, Int>>() // (bar index, units)

        while (remaining > 0) {
            val bar = pos / barUnits
            val chunk = minOf(remaining, barUnits - pos % barUnits)
            for (u in decompose(chunk)) pieces += bar to u
            pos += chunk
            remaining -= chunk
        }
        pieces.forEachIndexed { i, (bar, units) ->
            bars[bar] += Atom(event.kind, units, tieToNext = event.kind == TapKind.NOTE && i < pieces.lastIndex)
        }
    }
    return bars
}

/**
 * Formats bars as LilyPond text, one bar per line, each terminated by a bar check `|`.
 *
 * @param bars the atoms of each bar.
 * @param pitch the placeholder pitch for notes (rests use "r").
 */
fun format(
    bars: List<List<Atom>>,
    pitch: String,
): String =
    bars.joinToString("\n") { bar ->
        bar.joinToString(" ") { atom ->
            val name = if (atom.kind == TapKind.NOTE) pitch else "r"
            name + lilyDuration(atom.units) + if (atom.tieToNext) " ~" else ""
        } + " |"
    }
