// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        assertEquals(Glyph.COPY, output.copy.glyph, "the button still shows the copy mark")
        assertNull(output.copy.accentOverride, "the button is quiet")
    }

    @Test
    fun `the copy button turns into a check when the text is copied`() {
        var copied = ""
        val output = LilyPondOutput(putOnClipboard = { copied = it })
        output.show(score)

        output.copyText()

        assertEquals("% absolute\nc'1 |", copied, "the text is on the clipboard")
        assertEquals(Glyph.CHECK, output.copy.glyph, "the button shows the check mark")
        assertNotNull(output.copy.accentOverride, "the button is lit")
    }

    @Test
    fun `the buttons of the card are icons`() {
        val output = LilyPondOutput()

        assertEquals("", output.relative.text, "the relative button has no word")
        assertEquals("", output.copy.text, "the copy button has no word")
        assertEquals("", output.save.text, "the save button has no word")
        assertEquals(Glyph.RELATIVE, output.relative.glyph, "the relative button shows its own mark")
        assertEquals(Glyph.COPY, output.copy.glyph, "the copy button shows the copy mark")
        assertEquals(Glyph.SAVE, output.save.glyph, "the save button shows the save mark")
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
        assertTrue(buttons.contains(output.relative), "the header carries the relative button")
        assertTrue(buttons.contains(output.copy), "the header carries the copy button")
        assertTrue(buttons.contains(output.save), "the header carries the save button")
    }

    @Test
    fun `the header of the card fits the card`() {
        val output = LilyPondOutput()

        val header =
            output.card.components
                .filterIsInstance<JComponent>()
                .first()
        val room = OUTPUT_WIDTH - 36
        assertTrue(
            header.preferredSize.width <= room,
            "the header asks for ${header.preferredSize.width} px, the card has $room",
        )
    }

    @Test
    fun `the buttons that read the text follow the score`() {
        val output = LilyPondOutput()

        assertFalse(output.copy.isEnabled, "there is nothing to copy without a score")
        assertFalse(output.save.isEnabled, "there is nothing to save without a score")

        output.show(score)
        assertTrue(output.copy.isEnabled, "there is a text to copy")
        assertTrue(output.save.isEnabled, "there is a score to save")

        output.show(null)
        assertFalse(output.copy.isEnabled, "the text is gone")
        assertFalse(output.save.isEnabled, "the score is gone")
    }

    @Test
    fun `the save button asks to save the score and the mode`() {
        var saved: Pair<Score, Boolean>? = null
        val output =
            LilyPondOutput(
                saveTo = { score, relative ->
                    saved = score to relative
                    SaveResult.Saved
                },
            )
        output.show(score)

        output.saveFile()

        assertEquals(score, saved?.first, "the score is the one the card shows")
        assertEquals(false, saved?.second, "the octaves are written in full")
    }

    @Test
    fun `the save button asks for the relative mode when it is on`() {
        var relative = false
        val output =
            LilyPondOutput(
                saveTo = { _, mode ->
                    relative = mode
                    SaveResult.Saved
                },
            )
        output.show(score)
        output.toggleRelative(score)

        output.saveFile()

        assertTrue(relative, "the file is written with the octaves relative")
    }

    @Test
    fun `the save button turns into a check when the file is written`() {
        val output = LilyPondOutput(saveTo = { _, _ -> SaveResult.Saved })
        output.show(score)

        output.saveFile()

        assertEquals(Glyph.CHECK, output.save.glyph, "the button shows the check mark")
        assertNotNull(output.save.accentOverride, "the button is lit")
    }

    @Test
    fun `the save button stays as it is when the file was not written`() {
        val output = LilyPondOutput(saveTo = { _, _ -> SaveResult.Failed("the disk is full") })
        output.show(score)

        output.saveFile()

        assertEquals(Glyph.SAVE, output.save.glyph, "the button shows the save mark")
        assertNull(output.save.accentOverride, "the button is quiet")
    }

    @Test
    fun `the save button does nothing when the user gives the dialog up`() {
        var asked = false
        val output =
            LilyPondOutput(
                saveTo = { _, _ ->
                    asked = true
                    SaveResult.Cancelled
                },
            )
        output.show(score)

        output.saveFile()

        assertTrue(asked, "the saver was asked")
        assertEquals(Glyph.SAVE, output.save.glyph, "the button shows the save mark")
    }
}
