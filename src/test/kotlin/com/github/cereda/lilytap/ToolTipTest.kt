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
}
