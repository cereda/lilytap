// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The box with the LilyPond text: the hint it shows while it is empty.
 */
class CodeAreaTest {
    @Test
    fun `a text that fits stays on one line`() {
        assertEquals(
            listOf("Your score"),
            wrapText("Your score", 200) { it.length * 10 },
        )
    }

    @Test
    fun `a long text breaks at the spaces`() {
        assertEquals(
            listOf("Your transcription will", "appear here"),
            wrapText("Your transcription will appear here", 230) { it.length * 10 },
        )
    }

    @Test
    fun `an empty text has no lines`() {
        assertTrue(wrapText("", 200) { 0 }.isEmpty())
    }

    @Test
    fun `a word that is too long is not broken`() {
        assertEquals(
            listOf("transcription", "here"),
            wrapText("transcription here", 50) { it.length * 10 },
        )
    }

    @Test
    fun `the lines keep the words of the text`() {
        val text = "Your transcription will appear here"
        val lines = wrapText(text, 230) { it.length * 10 }
        assertEquals(text, lines.joinToString(" "))
    }
}
