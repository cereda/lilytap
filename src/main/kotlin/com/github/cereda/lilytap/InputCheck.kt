// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The check that every input of the window can be used before the app acts.
 */

/** The short name of the problem of the pitch field. */
internal const val PITCH_ERROR_TITLE = "Not a note name"

/**
 * The lines the time signature field says when the meter in it cannot be read.
 *
 * The lines say what was written, what a meter looks like and what to do about it.
 *
 * @param text the time signature that was written.
 * @return the lines of the words.
 */
internal fun meterErrorLines(text: String): List<String> =
    listOf(
        if (text.isBlank()) {
            "The time signature field is empty."
        } else {
            "The time signature field says “$text”."
        },
        meterReason(text),
        "Fix it and try again.",
    )

/**
 * The line that says what a meter must be.
 *
 * The reason names the number that is wrong, so the user knows which half of the meter to
 * change: the beats in the bar or the note value of the beat.
 *
 * @param text the time signature that was written.
 * @return the line of the words.
 */
private fun meterReason(text: String): String {
    val parts = text.trim().split("/")
    if (parts.size != 2) return "A meter is two numbers, like 4/4 or 6/8."
    val beats = parts[0].trim().toIntOrNull()
    val unit = parts[1].trim().toIntOrNull()
    if (beats == null || unit == null) return "A meter is two numbers, like 4/4 or 6/8."
    if (beats <= 0) return "The beats in the bar must be a positive number."
    if (unit !in TimeSignature.BEAT_UNITS) {
        return "The note value of the beat must be 1, 2, 4, 8, 16 or 32."
    }
    return "The time signature is not a meter."
}

/**
 * The lines the pitch field says when the name in it cannot be read.
 *
 * @param text the name that was written.
 * @return the lines of the words.
 */
internal fun pitchErrorLines(text: String): List<String> =
    listOf(
        "The pitch field says “$text”.",
        "Write it the way LilyPond does:",
        "c' (middle C), cis' (sharp), ces' (flat), c (lower), c'' (higher).",
        "Fix it and try again.",
    )

/**
 * The check that every input of the window can be used before the app acts.
 *
 * The app reads every input before a take begins: a name that cannot be read is a name that
 * is still being typed, or a typo, and a take should not run on a setting the app does not
 * understand. The check says what is wrong instead of carrying on with the last good value.
 */
internal object InputCheck {
    /**
     * The message that says what is wrong with the inputs, when something is wrong.
     *
     * The time signature comes first: it is the setting the take is built on. The key comes
     * next, and the pitch of the selected note last.
     *
     * @param time the text of the time signature field.
     * @param key the fault of the name of the key, when it cannot be read.
     * @param pitch the text of the pitch field.
     * @return the message, or null when every input can be used.
     */
    fun message(
        time: String,
        key: KeyFault?,
        pitch: String,
    ): Message? {
        val meter = runCatching { TimeSignature.parse(time) }.exceptionOrNull()
        if (meter != null) {
            return Message(
                "Invalid settings",
                "Bad meter",
                meterErrorLines(time),
                Glyph.BOLT,
                Theme.rec,
            )
        }
        if (key != null) {
            return Message(
                "Invalid settings",
                key.title,
                key.lines,
                Glyph.BOLT,
                Theme.rec,
            )
        }
        val name = pitch.trim()
        if (name.isNotEmpty() && Pitch.parse(name) == null) {
            return Message(
                "Invalid pitch",
                PITCH_ERROR_TITLE,
                pitchErrorLines(name),
                Glyph.BOLT,
                Theme.rec,
            )
        }
        return null
    }
}
