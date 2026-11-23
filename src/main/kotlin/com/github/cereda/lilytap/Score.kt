// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The score: what a take becomes, and what the window version lets you change.
 *
 * A score is pure data - a list of events that covers every unit of the recording
 * window - so it can be edited, rendered and played without touching the recorder.
 *
 * Pipeline:  taps (ms) -> quantize to grid cells -> events (note/rest + length + pitch)
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
 *
 * A note carries the pitch it sounds; a rest carries none.
 *
 * @property kind whether the event is a note or a rest.
 * @property startUnits where the event starts, counted from the start of the score.
 * @property lengthUnits how long the event lasts.
 * @property pitch the pitch of the note, or null for a rest.
 */
data class Event(
    val kind: TapKind,
    val startUnits: Int,
    val lengthUnits: Int,
    val pitch: Pitch? = null,
) {
    init {
        require((kind == TapKind.NOTE) == (pitch != null)) {
            "A note carries a pitch and a rest carries none"
        }
    }

    /** The first unit after this event. */
    val endUnits: Int get() = startUnits + lengthUnits
}

/**
 * One written note or rest in LilyPond output.
 *
 * @property kind whether the atom is a note or a rest.
 * @property units how long the atom lasts.
 * @property tieToNext whether the note is tied into the following one.
 * @property pitch the pitch of the note, or null for a rest.
 */
data class Atom(
    val kind: TapKind,
    val units: Int,
    val tieToNext: Boolean,
    val pitch: Pitch? = null,
) {
    init {
        require((kind == TapKind.NOTE) == (pitch != null)) {
            "A note carries a pitch and a rest carries none"
        }
    }
}

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
    val lilypond: String get() = format(layout(events, config), config.key)

    /**
     * The score as LilyPond text, one bar per line, with the octaves relative.
     *
     * The text is wrapped in a `\relative` block: inside it, LilyPond reads the octave of a
     * note from the note before it.
     */
    val lilypondRelative: String get() = formatRelative(layout(events, config), config.key)

    /**
     * The score in a key: every note is spelled the way the key wants it.
     *
     * The notes sound the same: only their names change, and a name that crosses the C or
     * the B takes its octave with it (a B♯4 is a C5).
     *
     * @param key the key the score is in.
     * @return the score in the key.
     */
    fun inKey(key: KeySignature): Score {
        val spelled =
            events.map { event ->
                val pitch = event.pitch ?: return@map event
                event.copy(pitch = key.spell(pitch.midi))
            }
        return copy(events = spelled, config = config.copy(key = key))
    }

    /**
     * Turns the event at [index] into a rest, or a rest back into a note.
     *
     * Two rests in a row are one silence, so a note that becomes a rest is merged with
     * the rests next to it.
     *
     * A rest that becomes a note takes the pitch of the note before it, so a melody that is
     * typed in does not change when a silence in the middle of it is filled in.
     *
     * @param index the index of the event in [events].
     * @return the edited score.
     */
    fun toggleRest(index: Int): Score {
        require(index in events.indices) { "There is no event $index" }
        val flipped = events.toMutableList()
        val event = flipped[index]
        flipped[index] =
            if (event.kind == TapKind.NOTE) {
                event.copy(kind = TapKind.REST, pitch = null)
            } else {
                event.copy(kind = TapKind.NOTE, pitch = previousPitch(index))
            }
        return copy(events = mergeRests(flipped))
    }

    /**
     * The pitch a new note takes: the pitch of the note before it, or middle C.
     *
     * @param index where the new note goes.
     * @return the pitch of the closest note before [index].
     */
    private fun previousPitch(index: Int): Pitch {
        for (i in index - 1 downTo 0) {
            val pitch = events[i].pitch
            if (pitch != null) return pitch
        }
        return Pitch.DEFAULT
    }

    /**
     * Sets the pitch of the note at [index].
     *
     * @param index the index of the note in [events].
     * @param pitch the pitch the note sounds.
     * @return the edited score.
     */
    fun setPitch(
        index: Int,
        pitch: Pitch,
    ): Score {
        require(index in events.indices) { "There is no event $index" }
        val event = events[index]
        require(event.kind == TapKind.NOTE) { "A rest has no pitch" }
        if (event.pitch == pitch) return this
        val changed = events.toMutableList()
        changed[index] = event.copy(pitch = pitch)
        return copy(events = changed)
    }

    /**
     * Fills the rest at [index] with a note of the given pitch.
     *
     * A silence in the middle of a melody can be filled in place: the rest becomes a note
     * that sounds the pitch, and it keeps the length it had.
     *
     * @param index the index of the rest in [events].
     * @param pitch the pitch the note sounds.
     * @return the edited score.
     */
    fun fillRest(
        index: Int,
        pitch: Pitch,
    ): Score {
        require(index in events.indices) { "There is no event $index" }
        val event = events[index]
        require(event.kind == TapKind.REST) { "Only a rest can be filled" }
        val changed = events.toMutableList()
        changed[index] = event.copy(kind = TapKind.NOTE, pitch = pitch)
        return copy(events = changed)
    }

    /**
     * Splits the event at [index] in two halves.
     *
     * The cut is at the middle of the event: the first half is the shorter one when the
     * event has an odd length. The first half keeps the kind of the event, and the second
     * half becomes a note - so a silence that is split in two becomes a silence followed by
     * a note, which is how a note is put inside a gap.
     *
     * The two halves of a note keep its pitch: it is one sound, cut in two. The note that
     * comes out of a split silence takes the pitch of the note before it.
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
        val rightPitch = event.pitch ?: previousPitch(index)
        val split =
            events.toMutableList().apply {
                removeAt(index)
                add(index, event.copy(lengthUnits = leftLength))
                add(index + 1, Event(TapKind.NOTE, event.startUnits + leftLength, rightLength, rightPitch))
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
     * becomes one longer note. The merged note keeps the pitch of the first note of the
     * range.
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
        val firstNote = range.firstOrNull { it.kind == TapKind.NOTE }
        val kind = if (firstNote != null) TapKind.NOTE else TapKind.REST
        val merged = events.toMutableList()
        for (i in to downTo from) merged.removeAt(i)
        merged.add(from, Event(kind, events[from].startUnits, length, firstNote?.pitch))
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

/** The name of every duration LilyPond can write as a single token. */
private val DURATION_NAMES =
    mapOf(
        96 to "dotted whole",
        64 to "whole",
        48 to "dotted half",
        32 to "half",
        24 to "dotted quarter",
        16 to "quarter",
        12 to "dotted eighth",
        8 to "eighth",
        6 to "dotted sixteenth",
        4 to "sixteenth",
        3 to "dotted thirty-second",
        2 to "thirty-second",
        1 to "sixty-fourth",
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
 * The bucket of the duration band of a block, from 0 (the shortest value) to 3 (the longest).
 *
 * The buckets follow the written durations: the sixteenths and the shorter values are one
 * bucket, the eighths another, the quarters another, and the halves and the longer values the
 * last one. Four buckets with four colours of their own are read at a glance; a ramp of
 * brightness over the thirteen values LilyPond can write is not, and it looks like a shadow
 * under the block.
 *
 * @param units the length of the block, in internal units.
 * @return the bucket of the band, between 0 and 3.
 */
fun durationLevel(units: Int): Int {
    require(units >= 1) { "A duration must be at least one unit" }
    return when {
        units <= 4 -> 0

        // 16th, 32nd, 64th
        units <= 12 -> 1

        // 8th
        units <= 24 -> 2

        // quarter
        else -> 3 // half and longer
    }
}

/**
 * The name of a duration, in words: "quarter", "dotted eighth".
 *
 * A length that LilyPond cannot write as a single token is named after its pieces.
 *
 * @param units the length, in internal units.
 * @return the name of the duration.
 */
fun durationName(units: Int): String {
    require(units >= 1) { "A duration must be at least one unit" }
    return decompose(units).joinToString(" + ") { DURATION_NAMES[it] ?: "?" }
}

/**
 * The LilyPond text of an event: the note or the rest with its duration, e.g. "c'4" or "r2".
 *
 * @param event the event to write.
 */
fun eventText(event: Event): String {
    val name = event.pitch?.lilypond ?: "r"
    return decompose(event.lengthUnits).joinToString(" ") { name + lilyDuration(it) }
}

/**
 * Cuts events at barlines (adding ties for notes) and returns the atoms of each bar.
 *
 * An event is one sustained sound, so every piece of it is tied to the next one - across barlines
 * and also within a bar, where a long value has to be written as several LilyPond tokens
 * (a full bar of 4/2, for instance, is a dotted whole tied to a half).
 *
 * In a compound meter the pieces are also cut at the groups of three: a duration is written
 * inside a group, so a note that crosses a group becomes two tied notes (c'4. ~ c'8) and a
 * silence that crosses one becomes two rests (r4. r8).
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
            for (piece in splitAtGroups(chunk, pos, config.timeSignature)) {
                for (u in decompose(piece)) pieces += bar to u
            }
            pos += chunk
            remaining -= chunk
        }
        pieces.forEachIndexed { i, (bar, units) ->
            bars[bar] +=
                Atom(
                    event.kind,
                    units,
                    tieToNext = event.kind == TapKind.NOTE && i < pieces.lastIndex,
                    pitch = event.pitch,
                )
        }
    }
    return bars
}

/**
 * Splits a chunk of a bar at the group borders of a compound meter.
 *
 * The groups of a compound meter are three beats long, and a bar holds a whole number of
 * them, so the borders fall on the same units everywhere in the score. A simple meter has
 * no groups, and the chunk comes back whole.
 *
 * @param chunk the length of the chunk, in units.
 * @param pos where the chunk starts, counted from the start of the score.
 * @param signature the time signature of the take.
 * @return the lengths of the pieces, in units.
 */
private fun splitAtGroups(
    chunk: Int,
    pos: Int,
    signature: TimeSignature,
): List<Int> {
    if (!signature.isCompound) return listOf(chunk)
    val group = signature.groupUnits
    val pieces = mutableListOf<Int>()
    var start = pos
    val end = pos + chunk
    while (start < end) {
        val border = (start / group + 1) * group
        val stop = minOf(end, border)
        pieces += stop - start
        start = stop
    }
    return pieces
}

/**
 * Formats bars as LilyPond text, one bar per line, each terminated by a bar check `|`.
 *
 * Every note is written with its own pitch; rests use "r". The key of the piece is declared
 * on the first line, unless it is C major: there the text says nothing, as it always did.
 *
 * @param bars the atoms of each bar.
 * @param key the key of the piece.
 */
fun format(
    bars: List<List<Atom>>,
    key: KeySignature = KeySignature.C_MAJOR,
): String {
    val text =
        bars.joinToString("\n") { bar ->
            bar.joinToString(" ") { atom ->
                val name = atom.pitch?.lilypond ?: "r"
                name + lilyDuration(atom.units) + if (atom.tieToNext) " ~" else ""
            } + " |"
        }
    return if (key.isCMajor) text else "${key.lilypond}\n$text"
}

/**
 * Formats bars as LilyPond text with the octaves relative, inside a `\relative` block.
 *
 * In a relative block LilyPond reads the octave of a note from the note before it, so the
 * notes are written with as few marks as they can be written with. The first note of the
 * block has no note before it and is written in full, and a rest carries no pitch and so
 * does not move the reference.
 *
 * The key of the piece is declared inside the block, so the block says everything it needs
 * on its own.
 *
 * @param bars the atoms of each bar.
 * @param key the key of the piece.
 * @return the LilyPond text of the bars.
 */
fun formatRelative(
    bars: List<List<Atom>>,
    key: KeySignature = KeySignature.C_MAJOR,
): String {
    val text = StringBuilder("\\relative {\n")
    if (!key.isCMajor) text.append(key.lilypond).append("\n")
    var previous: Pitch? = null
    for (bar in bars) {
        val pieces = mutableListOf<String>()
        for (atom in bar) {
            val pitch = atom.pitch
            val name = if (pitch == null) "r" else relativeName(pitch, previous)
            if (pitch != null) previous = pitch
            pieces += name + lilyDuration(atom.units) + if (atom.tieToNext) " ~" else ""
        }
        text.append(pieces.joinToString(" ")).append(" |\n")
    }
    return text.append("}").toString()
}

/**
 * The name of a note in relative mode.
 *
 * The octave of a note without marks is the one that puts it as close to the previous note
 * as the letters allow: the interval is measured in letter steps, seven to an octave, and
 * the accidentals do not count. The marks then lift or lower that pitch by whole octaves.
 *
 * The first note of a relative block has no note before it and is written in full.
 *
 * @param pitch the pitch of the note.
 * @param previous the pitch of the note before it, or null for the first note.
 * @return the LilyPond name of the note.
 */
fun relativeName(
    pitch: Pitch,
    previous: Pitch?,
): String {
    if (previous == null) return pitch.lilypond
    val delta = pitch.octave - relativeOctave(previous, pitch.step)
    val marks =
        when {
            delta > 0 -> "'".repeat(delta)
            delta < 0 -> ",".repeat(-delta)
            else -> ""
        }
    return pitch.lilypondName + marks
}

/**
 * The octave a note would have in a relative block if it carried no octave marks.
 *
 * LilyPond puts the note as close to the previous one as the letters allow: the interval
 * is measured in letter steps (seven to an octave, the accidentals do not count) and the
 * nearer direction wins.
 *
 * @param previous the pitch of the note before it.
 * @param step the letter of the note.
 * @return the octave of the note, in scientific notation.
 */
private fun relativeOctave(
    previous: Pitch,
    step: PitchStep,
): Int {
    val from = previous.step.ordinal
    val to = step.ordinal
    val up = (to - from + 7) % 7
    val down = (from - to + 7) % 7
    val upOctave = previous.octave + if (from > to) 1 else 0
    val downOctave = previous.octave - if (from < to) 1 else 0
    return if (up < down) upOctave else downOctave
}
