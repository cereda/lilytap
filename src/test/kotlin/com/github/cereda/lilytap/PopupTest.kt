// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the menus of the app open. */
class PopupTest {
    @Test
    fun `a popup opens under the button with a space between them`() {
        val spot = popupSpot(26, 38, 100)

        assertEquals(38 + POPUP_GAP, spot.y, "the popup is below the button, with a space")
        assertEquals(26 - 100, spot.x, "the popup is lined up with the right edge of the button")
    }

    @Test
    fun `a popup opens with a space of its own`() {
        assertEquals(8, POPUP_GAP, "the space between the button and the popup")
    }
}
