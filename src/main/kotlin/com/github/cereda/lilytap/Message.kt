// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The message of the dialog: what it says and how it looks.
 */

import java.awt.Color

/**
 * A message for the dialog: what it says and how it looks.
 *
 * @property title the headline of the dialog.
 * @property headline the big line of the dialog.
 * @property description what the dialog says about it.
 * @property glyph the icon of the dialog.
 * @property accent the colour of the headline.
 */
internal data class Message(
    val title: String,
    val headline: String,
    val description: String,
    val glyph: Glyph,
    val accent: Color = Theme.accent,
)
