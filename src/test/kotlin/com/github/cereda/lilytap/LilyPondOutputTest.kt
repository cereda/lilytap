// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The card that holds the LilyPond text: the text itself, the way the octaves are written, and
 * the buttons that go with them.
 */
class LilyPondOutputTest {
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    /** A score with one note: a C4 that fills the bar. */
    private val score = Score(listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)), cfg())

    @Test
    fun `the text says in a comment that the octaves are absolute`() {
        val output = LilyPondOutput()

        output.show(score)

        assertEquals("% absolute\nc'1 |", output.area.text)
    }

    @Test
    fun `the text says in a comment that the octaves are relative`() {
        val output = LilyPondOutput()

        output.toggleRelative(score)

        assertEquals("% relative\n\\relative {\nc'1 |\n}", output.area.text)
    }

    @Test
    fun `the mode can be switched back and forth`() {
        val output = LilyPondOutput()

        output.show(score)
        output.toggleRelative(score)
        assertEquals("% relative\n\\relative {\nc'1 |\n}", output.area.text, "the text is relative")

        output.toggleRelative(score)
        assertEquals("% absolute\nc'1 |", output.area.text, "the text is absolute again")
    }

    @Test
    fun `the button says whether the mode is on`() {
        val output = LilyPondOutput()

        assertNull(output.relative.accentOverride, "the button is quiet to begin with")

        output.toggleRelative(score)

        assertNotNull(output.relative.accentOverride, "the button says that the mode is on")
    }

    @Test
    fun `the theme change keeps the button lit`() {
        val output = LilyPondOutput()
        output.toggleRelative(score)

        output.refreshTheme()

        assertNotNull(output.relative.accentOverride, "the button still says that the mode is on")
    }

    @Test
    fun `there is no text when there is no score`() {
        val output = LilyPondOutput()

        output.show(score)
        output.show(null)

        assertEquals("", output.area.text, "the text is gone")
    }

    @Test
    fun `the text is gone when the card is cleared`() {
        val output = LilyPondOutput()
        output.show(score)

        output.clear()

        assertEquals("", output.area.text, "the text is gone")
    }

    @Test
    fun `copying an empty text does nothing`() {
        val output = LilyPondOutput()

        output.copyText() // must not throw, and must not touch the button

        assertEquals("Copy", output.copy.text, "the button still says Copy")
        assertNull(output.copy.accentOverride, "the button is quiet")
    }

    @Test
    fun `the card holds the header and the text`() {
        val output = LilyPondOutput()

        val parts = output.card.components.filterIsInstance<JComponent>()
        assertEquals(2, parts.size, "the card holds the header and the box of the text")

        val buttons =
            parts[0]
                .components
                .filterIsInstance<JComponent>()
                .flatMap { it.components.toList() }
                .filterIsInstance<PillButton>()
        assertTrue(buttons.contains(output.relative), "the header carries the Rel button")
        assertTrue(buttons.contains(output.copy), "the header carries the Copy button")
    }
}
