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
