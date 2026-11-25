// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.io.File
import java.nio.file.Files
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The export of the MIDI file: the file that is written and the button that asks for it.
 */
class MidiExportTest {
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    /** A score with one note: a C4 that fills the bar. */
    private val score = Score(listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)), cfg())

    @Test
    fun `the file the user chose is written`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val export =
            MidiExport(
                JFrame(),
                score = { score },
                chooseFile = { File(folder, "chosen") },
            )

        export.export()

        val written = File(folder, "chosen.mid")
        assertTrue(written.exists(), "the file is on the disk")
        assertContentEquals(midiFile(score), written.readBytes(), "the file is the one the app builds")
        folder.deleteRecursively()
    }

    @Test
    fun `a name with an extension keeps it`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val export =
            MidiExport(
                JFrame(),
                score = { score },
                chooseFile = { File(folder, "chosen.txt") },
            )

        export.export()

        assertTrue(File(folder, "chosen.txt").exists(), "the name the user gave is kept")
        folder.deleteRecursively()
    }

    @Test
    fun `nothing is written when the dialog is given up`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val export =
            MidiExport(
                JFrame(),
                score = { score },
                chooseFile = { null },
            )

        export.export()

        assertEquals(0, folder.listFiles()?.size, "nothing was written")
        folder.deleteRecursively()
    }

    @Test
    fun `nothing is written when there is no score`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        var asked = false
        val export =
            MidiExport(
                JFrame(),
                score = { null },
                chooseFile = {
                    asked = true
                    File(folder, "chosen.mid")
                },
            )

        export.export()

        assertTrue(!asked, "the dialog was not opened")
        assertEquals(0, folder.listFiles()?.size, "nothing was written")
        folder.deleteRecursively()
    }

    @Test
    fun `the button confirms the export`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val export =
            MidiExport(
                JFrame(),
                score = { score },
                chooseFile = { File(folder, "chosen.mid") },
            )

        export.export()

        assertEquals(Glyph.CHECK, export.button.glyph, "the button shows the check mark")
        folder.deleteRecursively()
    }

    @Test
    fun `the button has a word of its own`() {
        val export = MidiExport(JFrame(), score = { score })

        assertEquals("", export.button.text, "the button has no word")
        assertEquals(Glyph.MIDI, export.button.glyph, "the button shows its own mark")
        assertEquals(MidiExport.TOOLTIP, export.button.toolTipText, "the button says what it does")
    }
}
