// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * Pure rhythm logic: no I/O, no clocks. Everything here is deterministic so it
 * can be unit tested.
 *
 * Pipeline:  taps (ms) -> quantize to grid cells -> events (note/rest + length)
 *            -> split at barlines (ties) -> LilyPond text
 */

/** Resolution used internally: a whole note is 64 units (down to 64th notes). */
const val UNITS_PER_WHOLE = 64

/** The kind of a tap: a [NOTE] begins a sounding note, a [REST] begins a silence. */
enum class TapKind { NOTE, REST }

/** A key press, timestamped in milliseconds relative to the start of bar 1. */
data class Tap(
    val timeMs: Double,
    val kind: TapKind,
)

/**
 * A time signature.
 *
 * @property beats the number of beats per bar.
 * @property beatUnit the note value that gets one beat (1 = whole, 2 = half, 4 = quarter, ...).
 */
data class TimeSignature(
    val beats: Int,
    val beatUnit: Int,
) {
    init {
        require(beats > 0) { "Beats per bar must be positive" }
        require(beatUnit in setOf(1, 2, 4, 8, 16, 32)) { "Beat unit must be 1, 2, 4, 8, 16 or 32" }
    }

    /** Internal grid units in a single beat (a whole note is [UNITS_PER_WHOLE] units). */
    val unitsPerBeat: Int get() = UNITS_PER_WHOLE / beatUnit

    /** Internal grid units in a full bar. */
    val unitsPerBar: Int get() = unitsPerBeat * beats

    override fun toString() = "$beats/$beatUnit"

    companion object {
        /**
         * Parses a time signature from text like "4/4" or "6/8".
         * @throws IllegalArgumentException if the text is not a valid time signature.
         */
        fun parse(text: String): TimeSignature {
            val parts = text.split("/")
            require(parts.size == 2) { "Time signature must look like 4/4 or 6/8, got '$text'" }
            val beats = parts[0].trim().toIntOrNull()
            val unit = parts[1].trim().toIntOrNull()
            require(beats != null && unit != null) { "Time signature must look like 4/4 or 6/8, got '$text'" }
            return TimeSignature(beats, unit)
        }
    }
}

/**
 * Configuration for a recording session.
 *
 * @property bpm beats per minute, where "beat" is the time signature's denominator
 * (in 6/8, a beat is an eighth note).
 * @property timeSignature the time signature of the piece.
 * @property subdivisions grid cells per beat. 1 = beats only, 2 = halves of a beat
 * (eighths in 4/4), 4 = sixteenths in 4/4.
 * @property bars number of bars to record after the one-bar count-in.
 * @property latencyMs constant delay (audio output + reaction) subtracted from every tap.
 * @property pitch placeholder pitch printed in the LilyPond output.
 */
data class Config(
    val bpm: Double,
    val timeSignature: TimeSignature,
    val subdivisions: Int,
    val bars: Int,
    val latencyMs: Double = 0.0,
    val pitch: String = "c",
) {
    init {
        require(bpm > 0) { "BPM must be positive" }
        require(subdivisions >= 1) { "Subdivisions must be at least 1" }
        require(timeSignature.unitsPerBeat % subdivisions == 0) {
            "Subdivisions ($subdivisions) too fine for time signature $timeSignature"
        }
        require(bars >= 1) { "Need at least one bar" }
        require(pitch.isNotEmpty()) { "Pitch must not be empty" }
    }

    /** Internal grid units in a single cell. */
    val cellUnits: Int get() = timeSignature.unitsPerBeat / subdivisions

    /** Grid cells in a single bar. */
    val cellsPerBar: Int get() = timeSignature.beats * subdivisions

    /** Total grid cells across all recorded bars. */
    val totalCells: Int get() = cellsPerBar * bars

    /** Milliseconds per grid cell. */
    val cellMs: Double get() = 60_000.0 / bpm / subdivisions
}

/** A grid-cell onset: which cell an event starts on, and whether it is a note or rest. */
data class Onset(
    val cell: Int,
    val kind: TapKind,
)

/**
 * The result of quantizing taps to the grid.
 *
 * @property onsets the snapped note/rest onsets, in grid order.
 * @property meanOffsetMs average timing error *after* the configured latency has been applied
 * (compensated tap time - grid time). Positive = you tend to be late. Null if no taps landed.
 * @property snapped for the window's overlay: where each held note landed on the grid
 * (empty for plain taps).
 */
data class QuantizeResult(
    val onsets: List<Onset>,
    /**
     * Average timing error after the configured latency has been applied (compensated tap time -
     * grid time). Positive = you tend to be late. Null if no taps landed.
     *
     * Add it to the configured latency to correct the timing: it is the *remaining* error, not a
     * second compensation on top of the one already in use.
     */
    val meanOffsetMs: Double?,
    /** For the window's overlay: where each held note landed on the grid (empty for plain taps). */
    val snapped: List<SnappedNote> = emptyList(),
)

/** A held note after snapping: it occupies grid cells [startCell, endCell); [raw] is what was played. */
data class SnappedNote(
    val startCell: Int,
    val endCell: Int,
    val raw: HeldNote,
)

/** One written note or rest. [tieToNext] means "tie this note into the following one". */
data class Atom(
    val kind: TapKind,
    val units: Int,
    val tieToNext: Boolean,
)

/**
 * A key held down: pressed at [downMs], released at [upMs] (relative to the start of bar 1).
 *
 * @property kind [TapKind.NOTE] for a sounding note, [TapKind.REST] for a silence. A rest is
 * instantaneous: it lasts until the next press, like a rest tap in the terminal version.
 */
data class HeldNote(
    val downMs: Double,
    val upMs: Double,
    val kind: TapKind = TapKind.NOTE,
)

/**
 * One written note or rest in LilyPond output: [lilypond] text, [meanOffsetMs], and [snapped] notes.
 *
 * @property meanOffsetMs the timing error that is left after the configured latency has been
 * applied; add it to that latency to correct the timing. Null if nothing was played.
 */
data class Transcription(
    val lilypond: String,
    val meanOffsetMs: Double?,
    val snapped: List<SnappedNote> = emptyList(),
)

// ---------------------------------------------------------------------------
// Quantization
// ---------------------------------------------------------------------------

/**
 * Snaps tap times to the nearest grid cell. Anything within half a cell is "on the beat".
 *
 * @param taps the raw taps, in any order.
 * @param config the session configuration.
 * @return a [QuantizeResult] with the snapped onsets and mean timing offset.
 */
fun quantize(
    taps: List<Tap>,
    config: Config,
): QuantizeResult {
    val byCell = sortedMapOf<Int, TapKind>()
    val offsets = mutableListOf<Double>()
    for (tap in taps.sortedBy { it.timeMs }) {
        val adjusted = tap.timeMs - config.latencyMs
        val cell = Math.round(adjusted / config.cellMs).toInt()
        if (cell < 0 || cell >= config.totalCells) continue // count-in noise or after the end
        byCell[cell] = tap.kind // two taps in one cell: the later one wins
        // Measured against the *compensated* time, so this is the error that is left after the
        // configured latency has been applied. It is therefore the correction to add to that
        // latency, not a second compensation on top of it.
        offsets += adjusted - cell * config.cellMs
    }
    return QuantizeResult(
        onsets = byCell.map { (cell, kind) -> Onset(cell, kind) },
        meanOffsetMs = offsets.takeIf { it.isNotEmpty() }?.average(),
    )
}

/**
 * Builds [Event]s from onsets. Each onset lasts until the next onset (or the end).
 * If the first tap isn't on the very first cell, the gap before it becomes a rest.
 *
 * Two rests in a row are written as a single, longer rest.
 */
fun buildEvents(
    onsets: List<Onset>,
    totalCells: Int,
): List<Event> {
    val all = mutableListOf<Onset>()
    for (onset in onsets) {
        val last = all.lastOrNull()
        if (last != null && last.kind == TapKind.REST && onset.kind == TapKind.REST) continue
        all += onset
    }
    val first = all.firstOrNull()
    when {
        first == null -> all += Onset(0, TapKind.REST)

        first.cell > 0 && first.kind == TapKind.REST -> all[0] = Onset(0, TapKind.REST)

        // the gap joins the rest
        first.cell > 0 -> all.add(0, Onset(0, TapKind.REST))
    }
    return all.mapIndexed { i, onset ->
        val end = all.getOrNull(i + 1)?.cell ?: totalCells
        Event(onset.kind, onset.cell, end - onset.cell)
    }
}

/** One written note or rest event: [kind], [startCell], and [lengthCells]. */
data class Event(
    val kind: TapKind,
    val startCell: Int,
    val lengthCells: Int,
)

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
        var pos = event.startCell * config.cellUnits
        var remaining = event.lengthCells * config.cellUnits
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

/**
 * Transcribes raw taps into LilyPond notation.
 *
 * @param taps the raw taps, in any order.
 * @param config the session configuration.
 * @return a [Transcription] with the LilyPond text and timing stats.
 */
fun transcribe(
    taps: List<Tap>,
    config: Config,
): Transcription {
    val q = quantize(taps, config)
    val events = buildEvents(q.onsets, config.totalCells)
    val bars = layout(events, config)
    return Transcription(format(bars, config.pitch), q.meanOffsetMs)
}

// ---------------------------------------------------------------------------
// Held notes (press + release), used by the window version.
// ---------------------------------------------------------------------------

/**
 * Like [quantize], but each note has its own end. After a release, the silence only
 * becomes a rest if it is at least [minRestCells] long; shorter gaps (staccato, a finger
 * lifting a bit early) are absorbed by extending the note up to the next press.
 * A note that overlaps the next one is cut where the next one starts.
 *
 * A note whose [HeldNote.kind] is [TapKind.REST] is a silence instead: it lasts until the next
 * press, like a rest tap in the terminal version.
 *
 * @param notes the held notes (press + release pairs).
 * @param config the session configuration.
 * @param minRestCells the minimum silence length (in grid cells) that counts as a rest.
 */
fun quantizeHeld(
    notes: List<HeldNote>,
    config: Config,
    minRestCells: Int,
): QuantizeResult {
    require(minRestCells >= 1) { "Smallest rest must be at least one cell" }
    val ends = sortedMapOf<Int, Int>() // start cell -> end cell
    val rawByStart = sortedMapOf<Int, HeldNote>() // start cell -> the note that won that cell
    val offsets = mutableListOf<Double>()

    for (note in notes.sortedBy { it.downMs }) {
        val start = Math.round((note.downMs - config.latencyMs) / config.cellMs).toInt()
        if (start < 0 || start >= config.totalCells) continue
        val rawEnd = Math.round((note.upMs - config.latencyMs) / config.cellMs).toInt()
        ends[start] = maxOf(rawEnd, start + 1) // at least one cell long
        rawByStart[start] = note
        offsets += (note.downMs - config.latencyMs) - start * config.cellMs
    }

    val starts = ends.keys.toList()
    val onsets = mutableListOf<Onset>()
    val snapped = mutableListOf<SnappedNote>()
    for ((i, start) in starts.withIndex()) {
        val raw = rawByStart.getValue(start)
        val nextStart = starts.getOrNull(i + 1) ?: config.totalCells
        if (raw.kind == TapKind.REST) {
            val last = onsets.lastOrNull()
            if (last != null && last.kind == TapKind.REST) {
                // The silence before this rest is already written: extend it instead of repeating it.
                snapped += SnappedNote(last.cell, nextStart, raw)
            } else {
                onsets += Onset(start, TapKind.REST)
                snapped += SnappedNote(start, nextStart, raw)
            }
        } else {
            onsets += Onset(start, TapKind.NOTE)
            val end = minOf(ends.getValue(start), nextStart)
            val becomesRest = nextStart - end >= minRestCells
            if (becomesRest) onsets += Onset(end, TapKind.REST)
            // A short gap is absorbed: the written note runs on up to the next press.
            snapped += SnappedNote(start, if (becomesRest) end else nextStart, raw)
        }
    }
    return QuantizeResult(onsets, offsets.takeIf { it.isNotEmpty() }?.average(), snapped)
}

/**
 * Transcribes held notes (press + release) into LilyPond notation.
 *
 * @param notes the held notes.
 * @param config the session configuration.
 * @param minRestCells the minimum silence length (in grid cells) that counts as a rest.
 */
fun transcribeHeld(
    notes: List<HeldNote>,
    config: Config,
    minRestCells: Int,
): Transcription {
    val q = quantizeHeld(notes, config, minRestCells)
    val events = buildEvents(q.onsets, config.totalCells)
    return Transcription(format(layout(events, config), config.pitch), q.meanOffsetMs, q.snapped)
}
