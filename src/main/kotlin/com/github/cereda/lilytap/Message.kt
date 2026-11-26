// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The message of the dialog: what it says and how it looks.
 */

import java.awt.Color

/**
 * A message for the dialog: what it says and how it looks.
 *
 * The title says what kind of thing happened, the headline names the problem in a few
 * words, and the lines say what happened, what is accepted and what to do about it.
 *
 * @property title the headline of the dialog.
 * @property headline the big line of the dialog.
 * @property lines the lines the dialog says about it.
 * @property glyph the icon of the dialog.
 * @property accent the colour of the headline.
 */
internal data class Message(
    val title: String,
    val headline: String,
    val lines: List<String>,
    val glyph: Glyph,
    val accent: Color = Theme.accent,
)
