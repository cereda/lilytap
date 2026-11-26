// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The tooltips of the app: the lines of text a tooltip says.
 */
class ToolTipTest {
    @Test
    fun `the lines of a tooltip are joined with a break`() {
        val tip = tooltip("first", "second")

        assertTrue(tip.startsWith("<html>"), "the tooltip is HTML: $tip")
        assertTrue(tip.contains("first<br>second"), "the lines are one under the other: $tip")
    }

    @Test
    fun `the width of a tooltip is fixed`() {
        val tip = tooltip("a line")

        assertTrue(tip.contains("width='"), "the tooltip has a width: $tip")
    }

    @Test
    fun `the text of a tooltip is escaped`() {
        val tip = tooltip("a & b < c > d")

        assertTrue(!tip.contains("a & b"), "the ampersand is escaped: $tip")
        assertTrue(!tip.contains("< c"), "the bracket is escaped: $tip")
        assertTrue(tip.contains("&"), "the ampersand is an entity: $tip")
        assertTrue(tip.contains("<"), "the bracket is an entity: $tip")
        assertTrue(tip.contains(">"), "the bracket is an entity: $tip")
    }

    @Test
    fun `a tooltip of a list of lines says the same`() {
        val lines = listOf("first", "second")

        assertTrue(tooltip(lines) == tooltip("first", "second"), "the two ways are the same")
    }

    @Test
    fun `the paragraphs of a message are a little apart`() {
        val text = htmlParagraphs(440, listOf("first", "second"))

        assertTrue(text.startsWith("<html>"), "the text is HTML: $text")
        assertTrue(text.contains("width='440'"), "the text has a width: $text")
        assertTrue(
            text.contains("<p style='margin:0 0 3px 0'>first</p>"),
            "the first paragraph leaves a gap below it: $text",
        )
        assertTrue(
            text.contains("<p style='margin:0'>second</p>"),
            "the last paragraph leaves no gap below it: $text",
        )
    }

    @Test
    fun `the paragraphs of a message say the same as the lines`() {
        val lines = listOf("first", "second")

        val text = htmlParagraphs(440, lines)

        for (line in lines) {
            assertTrue(text.contains(">$line<"), "the text says the line: $text")
        }
    }

    @Test
    fun `the text of a paragraph is escaped`() {
        val text = htmlParagraphs(440, listOf("a & b < c > d"))

        assertTrue(!text.contains("a & b"), "the ampersand is escaped: $text")
        assertTrue(!text.contains("< c"), "the bracket is escaped: $text")
    }

    @Test
    fun `a tooltip of paragraphs has the width of a tooltip`() {
        val tip = tooltipParagraphs(listOf("first", "second"))

        assertTrue(tip.contains("width='360'"), "the tooltip has the width of a tooltip: $tip")
        assertTrue(tip.contains("margin:0 0 3px 0"), "the paragraphs are apart: $tip")
    }
}
