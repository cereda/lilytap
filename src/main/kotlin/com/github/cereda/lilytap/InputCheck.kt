// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The check that every input of the window can be used before the app acts.
 */

/** What the pitch field says when the name in it cannot be read. */
internal val PITCH_ERROR_LINES =
    listOf(
        "Not a LilyPond note name.",
        "Write it the way LilyPond does: c' (middle C), cis' (sharp), ces' (flat), c (lower), c'' (higher).",
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
                "The time signature is not a meter",
                "${meter.message}. Fix it and try again.",
                Glyph.BOLT,
                Theme.rec,
            )
        }
        if (key != null) {
            val headline =
                when (key) {
                    is KeyFault.TooManyAccidentals -> "The key carries ${key.fifths} accidentals"
                    KeyFault.Malformed -> "The key is not the name of a key"
                }
            return Message(
                "Invalid settings",
                headline,
                "${key.message} Fix it and try again.",
                Glyph.BOLT,
                Theme.rec,
            )
        }
        val name = pitch.trim()
        if (name.isNotEmpty() && Pitch.parse(name) == null) {
            return Message(
                "Invalid pitch",
                "The pitch is not a note name",
                "${PITCH_ERROR_LINES.joinToString(" ")} Fix it and try again.",
                Glyph.BOLT,
                Theme.rec,
            )
        }
        return null
    }
}
