// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The message in the header of the score strip.
 *
 * The message must never make the window wider, and it must never be cut in half: what does
 * not fit is shortened with an ellipsis, and the tooltip carries the whole message.
 */
class CaptionLabelTest {
    @Test
    fun `a short message is shown as it is`() {
        val label = CaptionLabel("c'4 selected")
        label.setSize(200, 20)
        assertEquals("c'4 selected", label.fitted(200))
    }

    @Test
    fun `a long message is shortened with an ellipsis`() {
        val label = CaptionLabel("512 blocks selected")
        label.setSize(80, 20)
        val fitted = label.fitted(80)
        assertTrue(fitted.endsWith("…"), "the message should be marked as shortened: $fitted")
        assertTrue(fitted.length < "512 blocks selected".length, "the message should be shorter: $fitted")
    }

    @Test
    fun `the shortened message fits the room it has`() {
        val label = CaptionLabel("ces'4 → ces'8 ces'8")
        label.setSize(60, 20)
        val fm = label.getFontMetrics(label.font)
        assertTrue(fm.stringWidth(label.fitted(60)) <= 60, "the message should fit in 60 pixels")
    }

    @Test
    fun `the shortened message does not break a word in half`() {
        val label = CaptionLabel("ces'4 → ces'8 ces'8")
        label.setSize(90, 20)
        val fitted = label.fitted(90)
        assertTrue(
            fitted == "ces'4 → ces'8…" || fitted.endsWith(" ces'8…"),
            "the cut should be at a space: $fitted",
        )
    }

    @Test
    fun `the label never asks for more than it may have`() {
        val label = CaptionLabel("512 blocks selected", maxWidth = 96)
        assertTrue(label.preferredSize.width <= 96, "the label should not ask for more than 96 pixels")
    }

    @Test
    fun `the label asks for the room the message needs`() {
        val label = CaptionLabel("c'4")
        val fm = label.getFontMetrics(label.font)
        assertTrue(label.preferredSize.width >= fm.stringWidth("c'4"), "the label should be wide enough")
    }

    @Test
    fun `the message can be changed`() {
        val label = CaptionLabel("Nothing selected")
        label.text = "c'4 selected"
        assertEquals("c'4 selected", label.text)
        label.setSize(200, 20)
        assertEquals("c'4 selected", label.fitted(200))
    }

    @Test
    fun `a message as wide as the room is shown in full`() {
        val label = CaptionLabel("ces'4 → ces'8 ces'8", maxWidth = CAPTION_WIDTH)
        label.setSize(CAPTION_WIDTH, 20)
        val fm = label.getFontMetrics(label.font)
        assertTrue(
            fm.stringWidth(label.text) <= CAPTION_WIDTH,
            "the message of the test should fit in $CAPTION_WIDTH pixels",
        )
        assertEquals(label.text, label.fitted(CAPTION_WIDTH))
    }

    @Test
    fun `the message never grows past the width the label may have`() {
        val label = CaptionLabel("a message that is much wider than the label may ever be", maxWidth = 80)
        label.setSize(400, 20)
        val fm = label.getFontMetrics(label.font)
        assertTrue(
            fm.stringWidth(label.fitted(400)) <= 80,
            "the message should be cut at the width of the label, even when there is more room",
        )
    }
}
