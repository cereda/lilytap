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

    /**
     * Whether the meter is compound: its beats come in groups of three.
     *
     * In 6/8 the beat is a dotted quarter - three eighths - and not the eighth the time
     * signature names. So 6/8, 9/8, 12/8, 6/4... are compound, while 3/4, 5/8 and 7/8
     * are not: a simple meter counts its beats one by one, and an irregular one has no
     * group the app could rely on.
     */
    val isCompound: Boolean get() = beats > 3 && beats % 3 == 0

    /**
     * Internal grid units in one group of three - the musical beat of a compound meter.
     *
     * A duration in a compound meter is written inside a group, so a piece of it never
     * crosses the group's border.
     */
    val groupUnits: Int get() = unitsPerBeat * 3

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
 * @property noRests when true, every silence is absorbed into the note before it, so the
 * transcription never contains a rest. A silence before the very first note has no note to join
 * and stays a rest.
 */
data class Config(
    val bpm: Double,
    val timeSignature: TimeSignature,
    val subdivisions: Int,
    val bars: Int,
    val latencyMs: Double = 0.0,
    val pitch: String = "c",
    val noRests: Boolean = false,
) {
    init {
        require(bpm.isFinite() && bpm > 0) { "BPM must be positive" }
        require(subdivisions >= 1) { "Subdivisions must be at least 1" }
        require(timeSignature.unitsPerBeat % subdivisions == 0) {
            "Subdivisions ($subdivisions) too fine for time signature $timeSignature"
        }
        require(bars >= 1) { "Need at least one bar" }
        require(latencyMs.isFinite()) { "Latency must be finite" }
        require(pitch.isNotBlank()) { "Pitch must not be blank" }
    }

    /** Internal grid units in a single cell. */
    val cellUnits: Int get() = timeSignature.unitsPerBeat / subdivisions

    /** Grid cells in a single bar. */
    val cellsPerBar: Int get() = timeSignature.beats * subdivisions

    /** Total grid cells across all recorded bars. */
    val totalCells: Int get() = cellsPerBar * bars

    /** Internal grid units in a single bar. */
    val unitsPerBar: Int get() = timeSignature.unitsPerBar

    /** Total internal grid units across all recorded bars. */
    val totalUnits: Int get() = unitsPerBar * bars

    /** Milliseconds per grid cell. */
    val cellMs: Double get() = 60_000.0 / bpm / subdivisions

    /**
     * Milliseconds per internal grid unit.
     *
     * A cell is always a power of two in units, so dividing by it is exact: a cell-aligned
     * event keeps exactly the same time it had when the score was measured in cells.
     */
    val unitMs: Double get() = cellMs / cellUnits
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

/**
 * A key held down: pressed at [downMs], released at [upMs] (relative to the start of bar 1).
 *
 * @property kind [TapKind.NOTE] for a sounding note, [TapKind.REST] for a silence. A rest is
 * instantaneous: it lasts until the next press.
 */
data class HeldNote(
    val downMs: Double,
    val upMs: Double,
    val kind: TapKind = TapKind.NOTE,
)

/**
 * The result of transcribing a take: the score, the timing error, and the raw notes.
 *
 * @property score the notes and rests of the take.
 * @property meanOffsetMs the timing error that is left after the configured latency has been
 * applied; add it to that latency to correct the timing. Null if nothing was played.
 * @property snapped where each held note landed on the grid (empty for plain taps).
 */
data class Transcription(
    val score: Score,
    val meanOffsetMs: Double?,
    val snapped: List<SnappedNote> = emptyList(),
) {
    /** The score as LilyPond text. */
    val lilypond: String get() = score.lilypond
}

// ---------------------------------------------------------------------------
// Quantization
// ---------------------------------------------------------------------------

/**
 * The indices of the onsets that survive the "no rests" mode.
 *
 * Every rest is dropped, except a leading one: a silence before the first note has no note to
 * join, so it stays a rest.
 */
private fun keptOnsets(kinds: List<TapKind>): List<Int> {
    val kept = mutableListOf<Int>()
    var seenNote = false
    for ((i, kind) in kinds.withIndex()) {
        if (kind == TapKind.NOTE) {
            seenNote = true
            kept += i
        } else if (!seenNote) {
            kept += i
        }
    }
    return kept
}

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
    val cells = byCell.keys.toList()
    val kinds = byCell.values.toList()
    val kept = if (config.noRests) keptOnsets(kinds) else kinds.indices.toList()
    return QuantizeResult(
        onsets = kept.map { Onset(cells[it], kinds[it]) },
        meanOffsetMs = offsets.takeIf { it.isNotEmpty() }?.average(),
    )
}

/**
 * Builds [Event]s from onsets. Each onset lasts until the next onset (or the end).
 * If the first tap isn't on the very first cell, the gap before it becomes a rest.
 *
 * Two rests in a row are written as a single, longer rest.
 *
 * @param onsets the snapped onsets, in grid order.
 * @param config the session configuration: it says how long the score is and how many
 * internal units a grid cell holds.
 */
fun buildEvents(
    onsets: List<Onset>,
    config: Config,
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
    val cell = config.cellUnits
    return all.mapIndexed { i, onset ->
        val end = all.getOrNull(i + 1)?.cell ?: config.totalCells
        Event(onset.kind, onset.cell * cell, (end - onset.cell) * cell)
    }
}

/**
 * Transcribes raw taps into LilyPond notation.
 *
 * @param taps the raw taps, in any order.
 * @param config the session configuration.
 * @return a [Transcription] with the score and timing stats.
 */
fun transcribe(
    taps: List<Tap>,
    config: Config,
): Transcription {
    val q = quantize(taps, config)
    val score = Score(buildEvents(q.onsets, config), config)
    return Transcription(score, q.meanOffsetMs)
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
 * press.
 *
 * When [Config.noRests] is set, no silence is ever written: every rest is absorbed into the note
 * before it, and a leading silence (one with no note before it) stays a rest.
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
    val kinds = starts.map { rawByStart.getValue(it).kind }
    // In the "no rests" mode a rest press is not an event of its own: the silence belongs to the
    // note before it, so the press is dropped and the note runs on to the next one.
    val kept = if (config.noRests) keptOnsets(kinds) else kinds.indices.toList()
    val keptStarts = kept.map { starts[it] }

    val onsets = mutableListOf<Onset>()
    val snapped = mutableListOf<SnappedNote>()
    for ((i, start) in keptStarts.withIndex()) {
        val raw = rawByStart.getValue(start)
        val nextStart = keptStarts.getOrNull(i + 1) ?: config.totalCells
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
            val becomesRest = !config.noRests && nextStart - end >= minRestCells
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
    val score = Score(buildEvents(q.onsets, config), config)
    return Transcription(score, q.meanOffsetMs, q.snapped)
}
