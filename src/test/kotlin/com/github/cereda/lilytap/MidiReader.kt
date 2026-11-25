// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * A reader for MIDI files, for the tests: it reads back what the app writes.
 */

/**
 * A note of a MIDI file: when it starts, how long it lasts and what it sounds.
 *
 * @property onset the tick the note starts at.
 * @property duration how many ticks the note lasts.
 * @property pitch the key of the note.
 */
internal data class MidiNote(
    val onset: Int,
    val duration: Int,
    val pitch: Int,
)

/**
 * What a MIDI file says.
 *
 * @property format the format of the file.
 * @property division how many ticks a quarter note lasts.
 * @property notes the notes of the file, in the order they are played.
 * @property microsPerQuarter the tempo of the file, in microseconds per quarter note.
 * @property beats the beats per bar of the meter, or null when the file has no meter.
 * @property beatUnit the note value of the meter, or null when the file has no meter.
 * @property fifths the accidentals of the key, or null when the file has no key.
 * @property minor whether the key is a minor one, or null when the file has no key.
 * @property trackNames the names of the tracks.
 */
internal data class MidiFile(
    val format: Int,
    val division: Int,
    val notes: List<MidiNote>,
    val microsPerQuarter: Int?,
    val beats: Int?,
    val beatUnit: Int?,
    val fifths: Int?,
    val minor: Boolean?,
    val trackNames: List<String>,
)

/**
 * Reads a MIDI file.
 *
 * The reader is only here for the tests: it reads what the app writes back, so that the
 * file can be checked note by note.
 *
 * @param bytes the bytes of the file.
 * @return what the file says.
 */
internal fun readMidiFile(bytes: ByteArray): MidiFile {
    var at = 0

    fun word(): String {
        val text = String(bytes, at, 4, Charsets.US_ASCII)
        at += 4
        return text
    }

    fun number(): Int {
        var value = 0
        for (index in 0 until 4) {
            value = (value shl 8) or (bytes[at + index].toInt() and 0xFF)
        }
        at += 4
        return value
    }

    fun short(): Int {
        val value = (bytes[at].toInt() shl 8) or (bytes[at + 1].toInt() and 0xFF)
        at += 2
        return value
    }

    require(word() == "MThd") { "The file does not start with a header" }
    require(number() == 6) { "The header is not six bytes long" }
    val format = short()
    val tracks = short()
    val division = short()

    val notes = mutableListOf<MidiNote>()
    val names = mutableListOf<String>()
    var micros: Int? = null
    var beats: Int? = null
    var beatUnit: Int? = null
    var fifths: Int? = null
    var minor: Boolean? = null

    repeat(tracks) {
        require(word() == "MTrk") { "A track does not start with its name" }
        val length = number()
        val end = at + length
        var tick = 0
        var playing: MutableMap<Int, Int>? = null
        while (at < end) {
            var delta = 0
            var byte = bytes[at].toInt() and 0xFF
            at++
            while (byte and 0x80 != 0) {
                delta = (delta shl 7) or (byte and 0x7F)
                byte = bytes[at].toInt() and 0xFF
                at++
            }
            delta = (delta shl 7) or (byte and 0x7F)
            tick += delta
            val status = bytes[at].toInt() and 0xFF
            if (status == 0xFF) {
                at++
                val kind = bytes[at].toInt() and 0xFF
                at++
                var size = 0
                byte = bytes[at].toInt() and 0xFF
                at++
                while (byte and 0x80 != 0) {
                    size = (size shl 7) or (byte and 0x7F)
                    byte = bytes[at].toInt() and 0xFF
                    at++
                }
                size = (size shl 7) or (byte and 0x7F)
                val body = bytes.copyOfRange(at, at + size)
                at += size
                when (kind) {
                    0x03 -> {
                        names += String(body, Charsets.US_ASCII)
                    }

                    0x51 -> {
                        micros =
                            ((body[0].toInt() and 0xFF) shl 16) or
                            ((body[1].toInt() and 0xFF) shl 8) or
                            (body[2].toInt() and 0xFF)
                    }

                    0x58 -> {
                        beats = body[0].toInt() and 0xFF
                        beatUnit = 1 shl (body[1].toInt() and 0xFF)
                    }

                    0x59 -> {
                        fifths = body[0].toInt().toByte().toInt()
                        minor = body[1].toInt() == 1
                    }
                }
            } else if (status and 0xF0 == 0x90 || status and 0xF0 == 0x80) {
                at++
                val key = bytes[at].toInt() and 0xFF
                at++
                val velocity = bytes[at].toInt() and 0xFF
                at++
                if (status and 0xF0 == 0x80 || velocity == 0) {
                    val started = playing?.remove(key)
                    if (started != null) notes += MidiNote(started, tick - started, key)
                } else {
                    if (playing == null) playing = mutableMapOf()
                    playing[key] = tick
                }
            } else {
                at += 2
            }
        }
        at = end
    }
    return MidiFile(
        format = format,
        division = division,
        notes = notes.sortedBy { it.onset },
        microsPerQuarter = micros,
        beats = beats,
        beatUnit = beatUnit,
        fifths = fifths,
        minor = minor,
        trackNames = names,
    )
}
