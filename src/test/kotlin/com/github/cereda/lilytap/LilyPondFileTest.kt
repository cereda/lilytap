// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The LilyPond file of a score: the document that is written to the disk.
 */
class LilyPondFileTest {
    private fun cfg(bars: Int = 1) = Config(60.0, TimeSignature(4, 4), 2, bars, 0.0)

    /** A score with one note: a C4 that fills the bar. */
    private val score = Score(listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)), cfg())

    /** A score in a key that has to be declared. */
    private val inE =
        Score(
            listOf(Event(TapKind.NOTE, 0, 64, Pitch.DEFAULT)),
            cfg().copy(key = assertNotNull(KeySignature.parse("E major"))),
        )

    @Test
    fun `the file is a whole document`() {
        val file = lilyPondFile(score, relative = false)

        assertEquals(
            """
            \version "2.26.0"

            \score {
              \new Staff {
                c'1 |
              }
            }

            """.trimIndent(),
            file,
            "the file has the version, the score block and the staff",
        )
    }

    @Test
    fun `the file ends with a newline`() {
        assertTrue(lilyPondFile(score, relative = false).endsWith("}\n"), "the file ends with a newline")
    }

    @Test
    fun `the relative file puts the bars in a relative block`() {
        val file = lilyPondFile(score, relative = true)

        assertEquals(
            """
            \version "2.26.0"

            \score {
              \new Staff {
                \relative {
                  c'1 |
                }
              }
            }

            """.trimIndent(),
            file,
            "the bars are in a relative block, and they go deeper",
        )
    }

    @Test
    fun `the file declares the key when it is not C major`() {
        val file = lilyPondFile(inE, relative = false)

        assertTrue(file.contains("\\key e \\major"), "the file declares the key: $file")
    }

    @Test
    fun `the file says nothing about the key when it is C major`() {
        val file = lilyPondFile(score, relative = false)

        assertTrue(!file.contains("\\key"), "the file says nothing about the key: $file")
    }

    @Test
    fun `the file asks for a MIDI file when it is told to`() {
        val plain = lilyPondFile(score, relative = false)
        val withMidi = lilyPondFile(score, relative = false, midi = true)

        assertTrue(!plain.contains("\\midi"), "the file of the user asks for nothing else")
        assertTrue(withMidi.contains("\\midi { }"), "the file of the referee asks for a MIDI file")
    }

    @Test
    fun `the file lands on the disk`() {
        val folder = Files.createTempDirectory("lilytap")
        val path = folder.resolve("score.ly")

        val reason = writeLilyPondFile(path, score, relative = false)

        assertNull(reason, "the file was written")
        assertEquals(lilyPondFile(score, relative = false), Files.readString(path), "the file says what it should")
        folder.toFile().deleteRecursively()
    }

    @Test
    fun `a write that cannot happen says why`() {
        val folder = Files.createTempDirectory("lilytap")
        val path = folder.resolve("nowhere").resolve("score.ly")

        val reason = writeLilyPondFile(path, score, relative = false)

        assertNotNull(reason, "the write failed")
        assertTrue(reason.isNotBlank(), "the reason says something")
        folder.toFile().deleteRecursively()
    }
}
