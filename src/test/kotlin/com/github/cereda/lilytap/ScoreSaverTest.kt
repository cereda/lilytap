// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.io.File
import java.nio.file.Files
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The save of the LilyPond text: the name of the file and the word when it cannot be written.
 */
class ScoreSaverTest {
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    /** A score with one note: a C4 that fills the bar. */
    private val score = Score(listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)), cfg())

    @Test
    fun `a name without an extension gets the extension of a LilyPond file`() {
        val file = withExtension(File("/tmp/lilytap", "myscore"), LILYPOND_FORMAT.extension)

        assertEquals("myscore.ly", file.name, "the file is a LilyPond file")
        assertEquals("/tmp/lilytap", file.parent, "the file is in the folder the user chose")
    }

    @Test
    fun `a name with an extension is left alone`() {
        val file = withExtension(File("/tmp/lilytap", "myscore.txt"), LILYPOND_FORMAT.extension)

        assertEquals("myscore.txt", file.name, "the user asked for that name")
    }

    @Test
    fun `a name that is only an extension gets the extension of a LilyPond file`() {
        val file = withExtension(File("/tmp/lilytap", "myscore."), LILYPOND_FORMAT.extension)

        assertEquals("myscore..ly", file.name, "the name is complete")
    }

    @Test
    fun `the refusal says that the file was not written`() {
        val message = saveRefused("the disk is full")

        assertEquals("Cannot write the file", message.title)
        assertEquals("Not written", message.headline)
        assertEquals("the disk is full", message.lines.first(), "the reason is in the message")
        assertTrue(
            message.lines.any { it.contains("try again") },
            "the message says what to do: ${message.lines}",
        )
    }

    @Test
    fun `the file the user chose is written`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val saver = ScoreSaver(JFrame(), chooseFile = { File(folder, "chosen.ly") })

        val result = saver.save(score, relative = false)

        assertIs<SaveResult.Saved>(result, "the file was written")
        val written = File(folder, "chosen.ly")
        assertTrue(written.exists(), "the file is on the disk")
        assertEquals(
            lilyPondFile(score, relative = false),
            written.readText(),
            "the file says what the app writes",
        )
        folder.deleteRecursively()
    }

    @Test
    fun `a name without an extension is written with one`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val saver = ScoreSaver(JFrame(), chooseFile = { File(folder, "chosen") })

        saver.save(score, relative = false)

        assertTrue(File(folder, "chosen.ly").exists(), "the file is a LilyPond file")
        folder.deleteRecursively()
    }

    @Test
    fun `the file is written in the mode the card is in`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val saver = ScoreSaver(JFrame(), chooseFile = { File(folder, "chosen.ly") })

        saver.save(score, relative = true)

        assertEquals(
            lilyPondFile(score, relative = true),
            File(folder, "chosen.ly").readText(),
            "the bars are in a relative block",
        )
        folder.deleteRecursively()
    }

    @Test
    fun `the dialog is given up and nothing is written`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val saver = ScoreSaver(JFrame(), chooseFile = { null })

        val result = saver.save(score, relative = false)

        assertIs<SaveResult.Cancelled>(result, "the user gave the dialog up")
        assertEquals(0, folder.listFiles()?.size, "nothing was written")
        folder.deleteRecursively()
    }

    @Test
    fun `the dialog opens in the folder of the last save`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val suggested = mutableListOf<File>()
        var count = 0
        val saver =
            ScoreSaver(
                JFrame(),
                chooseFile = { hint ->
                    suggested += hint
                    count++
                    File(folder, "score$count.ly")
                },
            )

        saver.save(score, relative = false)
        saver.save(score, relative = false)

        assertEquals("lilytap.ly", suggested[0].name, "the dialog suggests a name")
        assertEquals(
            System.getProperty("user.home"),
            suggested[0].parentFile.absolutePath,
            "the first save opens in the home directory",
        )
        assertEquals(
            folder.absolutePath,
            suggested[1].parentFile.absolutePath,
            "the second save opens where the first one was",
        )
        folder.deleteRecursively()
    }
}
