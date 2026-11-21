// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The size of the stage: the column that holds the timeline, the score header and the strip.
 *
 * The window is packed around these numbers. The stage is the widest thing in the window
 * (the settings card is 835 px), so the stage sets the width of the window: 863 px of stage
 * + 32 px of card border + 40 px of window border = 935 px.
 *
 * The score header has to fit in this width. A header that asks for more would widen the
 * column of the stage, and the layout would then squeeze the score strip out of the window.
 */

/** How wide the stage is inside the window, in pixels. */
internal const val STAGE_WIDTH = 863

/** How tall the stage is inside the window, in pixels. */
internal const val STAGE_HEIGHT = 250

/** How wide the message in the score header may be, in pixels. */
internal const val CAPTION_WIDTH = 235

/*
 * The bottom row of the window: the pitches panel and the LilyPond card.
 *
 * The row is as wide as the content pane (the stage sets the width of the window), and the
 * pitches panel takes two thirds of it: the piano needs the room, and the LilyPond text is
 * read in a few lines.
 */

/** How wide the content pane of the window is, in pixels. */
internal const val CONTENT_WIDTH = 895

/** The gap between the pitches panel and the LilyPond card, in pixels. */
internal const val BOTTOM_GAP = 14

/** How wide the pitches panel is, in pixels: two thirds of the bottom row. */
internal const val PITCHES_WIDTH = 587

/** How wide the LilyPond card is, in pixels: one third of the bottom row. */
internal const val OUTPUT_WIDTH = 294

/** How tall the bottom row is, in pixels. */
internal const val BOTTOM_HEIGHT = 188
