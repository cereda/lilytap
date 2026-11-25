// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The MIDI file of a score: a standard MIDI file, written by hand.
 *
 * A MIDI file is a handful of chunks: a header that says how many tracks there are and how
 * many ticks a quarter note lasts, and then the tracks. The app writes the same shape
 * LilyPond writes - one track for the tempo and the meter, one for the notes - so that the
 * two can be compared with each other note by note.
 */

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * The MIDI file of a score.
 *
 * The file holds the notes of the score, the tempo, the meter and the key. A rest is
 * silence: it is not written, and the note after it starts later, which is what a rest is.
 *
 * @param score the score to write.
 * @return the bytes of the file.
 */
internal fun midiFile(score: Score): ByteArray {
    val file = ByteArrayOutputStream()
    file.write(ascii("MThd"))
    writeInt(file, 6)
    writeShort(file, FORMAT)
    writeShort(file, TRACKS)
    writeShort(file, TICKS_PER_QUARTER)
    file.write(trackChunk(conductorTrack(score)))
    file.write(trackChunk(noteTrack(score)))
    return file.toByteArray()
}

/**
 * The track that says how the piece goes: its name, its meter, its key and its tempo.
 *
 * @param score the score to write.
 * @return the bytes of the track.
 */
private fun conductorTrack(score: Score): ByteArray {
    val track = ByteArrayOutputStream()
    meta(track, META_NAME, ascii(TRACK_NAME))
    timeSignature(track, score.config.timeSignature)
    keySignature(track, score.config.key)
    tempo(track, score.config)
    endOfTrack(track)
    return track.toByteArray()
}

/**
 * The track that holds the notes.
 *
 * The notes are written in the order of the score, one after the other: a note starts when
 * the one before it ends, and a rest is the gap between them.
 *
 * @param score the score to write.
 * @return the bytes of the track.
 */
private fun noteTrack(score: Score): ByteArray {
    val track = ByteArrayOutputStream()
    var at = 0
    for (event in score.events) {
        val pitch = event.pitch ?: continue
        val onset = event.startUnits * TICKS_PER_UNIT
        val duration = event.lengthUnits * TICKS_PER_UNIT
        variable(track, onset - at)
        track.write(NOTE_ON)
        track.write(pitch.midi)
        track.write(VELOCITY)
        variable(track, duration)
        track.write(NOTE_ON)
        track.write(pitch.midi)
        track.write(0)
        at = onset + duration
    }
    endOfTrack(track)
    return track.toByteArray()
}

/**
 * The meter of the piece, as the MIDI file says it.
 *
 * @param track the track to write to.
 * @param signature the meter of the piece.
 */
private fun timeSignature(
    track: ByteArrayOutputStream,
    signature: TimeSignature,
) {
    val denominator = denominatorOf(signature.beatUnit)
    meta(
        track,
        META_TIME_SIGNATURE,
        byteArrayOf(
            signature.beats.toByte(),
            denominator.toByte(),
            CLOCKS_PER_CLICK.toByte(),
            THIRTY_SECONDS_PER_QUARTER.toByte(),
        ),
    )
}

/**
 * The denominator of a meter, as the MIDI file says it.
 *
 * The file counts the denominator as a power of two: a quarter note is 2, an eighth note
 * is 3.
 *
 * @param beatUnit the note value of the meter.
 * @return the power of two.
 */
private fun denominatorOf(beatUnit: Int): Int {
    var denominator = 0
    var unit = beatUnit
    while (unit > 1) {
        unit /= 2
        denominator++
    }
    return denominator
}

/**
 * The key of the piece, as the MIDI file says it.
 *
 * @param track the track to write to.
 * @param key the key of the piece.
 */
private fun keySignature(
    track: ByteArrayOutputStream,
    key: KeySignature,
) {
    meta(
        track,
        META_KEY_SIGNATURE,
        byteArrayOf(
            key.fifths.toByte(),
            if (key.major) 0 else 1,
        ),
    )
}

/**
 * The tempo of the piece, as the MIDI file says it.
 *
 * A MIDI file counts the tempo in microseconds per quarter note, and the app counts it in
 * musical beats a minute: the dotted quarter in a compound meter, the note value in a simple
 * one. The two are not the same, so the beat is written out: the beat of 6/8 lasts three
 * eighths, and a quarter note is two thirds of it.
 *
 * @param track the track to write to.
 * @param config the settings of the piece.
 */
private fun tempo(
    track: ByteArrayOutputStream,
    config: Config,
) {
    val pulseMicros = 60_000_000.0 / config.bpm
    val micros = (pulseMicros * UNITS_PER_QUARTER / config.timeSignature.unitsPerPulse).roundToInt()
    meta(
        track,
        META_TEMPO,
        byteArrayOf(
            ((micros shr 16) and 0xFF).toByte(),
            ((micros shr 8) and 0xFF).toByte(),
            (micros and 0xFF).toByte(),
        ),
    )
}

/**
 * A meta event of the track.
 *
 * Every event of a track is written after the time that has passed since the one before
 * it; the events of the conductor track all happen at the beginning, so the time is zero.
 *
 * @param track the track to write to.
 * @param kind the kind of the event.
 * @param body what the event says.
 */
private fun meta(
    track: ByteArrayOutputStream,
    kind: Int,
    body: ByteArray,
) {
    variable(track, 0)
    track.write(0xFF)
    track.write(kind)
    track.write(body.size)
    track.write(body)
}

/**
 * The end of the track: nothing follows it.
 *
 * @param track the track to write to.
 */
private fun endOfTrack(track: ByteArrayOutputStream) {
    meta(track, META_END_OF_TRACK, ByteArray(0))
}

/**
 * A chunk of the file: the name of the chunk, its length and what it holds.
 *
 * @param track the bytes of the track.
 * @return the bytes of the chunk.
 */
private fun trackChunk(track: ByteArray): ByteArray {
    val chunk = ByteArrayOutputStream()
    chunk.write(ascii("MTrk"))
    writeInt(chunk, track.size)
    chunk.write(track)
    return chunk.toByteArray()
}

/**
 * A number of the file, as the four bytes it takes.
 *
 * @param out where the bytes go.
 * @param value the number to write.
 */
private fun writeInt(
    out: ByteArrayOutputStream,
    value: Int,
) {
    out.write((value shr 24) and 0xFF)
    out.write((value shr 16) and 0xFF)
    out.write((value shr 8) and 0xFF)
    out.write(value and 0xFF)
}

/**
 * A number of the file, as the two bytes it takes.
 *
 * @param out where the bytes go.
 * @param value the number to write.
 */
private fun writeShort(
    out: ByteArrayOutputStream,
    value: Int,
) {
    out.write((value shr 8) and 0xFF)
    out.write(value and 0xFF)
}

/**
 * A number of the file, as the variable number of bytes it takes.
 *
 * Every byte holds seven bits of the number and says whether another byte follows; the
 * last one does not.
 *
 * @param out where the bytes go.
 * @param value the number to write.
 */
private fun variable(
    out: ByteArrayOutputStream,
    value: Int,
) {
    val bytes = mutableListOf<Int>()
    var rest = value
    do {
        bytes += rest and 0x7F
        rest = rest shr 7
    } while (rest > 0)
    for (index in bytes.indices.reversed()) {
        out.write(if (index == 0) bytes[index] else bytes[index] or 0x80)
    }
}

/**
 * A word of the file, as the bytes it takes.
 *
 * @param word the word to write.
 * @return the bytes of the word.
 */
private fun ascii(word: String): ByteArray = word.toByteArray(Charsets.US_ASCII)

/** The format of the files the app writes: one track for the music, one for the tempo. */
private const val FORMAT = 1

/** How many tracks the files the app writes have. */
private const val TRACKS = 2

/** How many ticks a quarter note lasts in the files the app writes. */
private const val TICKS_PER_QUARTER = 384

/** How many units a quarter note lasts in the model. */
private const val UNITS_PER_QUARTER = UNITS_PER_WHOLE / 4

/** How many ticks a unit of the model lasts in the files the app writes. */
private const val TICKS_PER_UNIT = TICKS_PER_QUARTER / UNITS_PER_QUARTER

/** The velocity of a note: the same one LilyPond writes. */
private const val VELOCITY = 90

/** The status byte of a note that starts. A note that ends has the same byte and no velocity. */
private const val NOTE_ON = 0x90

/** The kind of the meta event that names the track. */
private const val META_NAME = 0x03

/** The kind of the meta event that carries the meter. */
private const val META_TIME_SIGNATURE = 0x58

/** The kind of the meta event that carries the key. */
private const val META_KEY_SIGNATURE = 0x59

/** The kind of the meta event that carries the tempo. */
private const val META_TEMPO = 0x51

/** The kind of the meta event that ends the track. */
private const val META_END_OF_TRACK = 0x2F

/** How many MIDI clocks a metronome click lasts. */
private const val CLOCKS_PER_CLICK = 24

/** How many thirty-seconds of a note a quarter note holds. */
private const val THIRTY_SECONDS_PER_QUARTER = 8

/** The name the track of the app carries. */
private const val TRACK_NAME = "lilytap"
