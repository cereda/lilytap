// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The dialog that asks where the LilyPond file goes: what the path of the field means.
 */
class SaveDialogTest {
    /** The name the file takes when the path names a folder. */
    private val default = SaveDialog.DEFAULT_NAME

    @Test
    fun `an empty path cannot be used`() {
        val path = resolveSavePath("   ", default)

        val invalid = assertIs<SavePath.Invalid>(path, "an empty path is refused")
        assertTrue(invalid.reason.isNotBlank(), "the reason says something")
    }

    @Test
    fun `a path in a folder that exists is the file to write`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = File(folder, "score.ly").path

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals("score.ly", ready.file.name, "the file is the one the path names")
        assertEquals(folder.absolutePath, ready.file.parentFile.absolutePath, "it is in the folder")
        folder.deleteRecursively()
    }

    @Test
    fun `a name without an extension gets the extension of a LilyPond file`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = File(folder, "score").path

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals("score.ly", ready.file.name, "the file is a LilyPond file")
        folder.deleteRecursively()
    }

    @Test
    fun `a name with an extension keeps it`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = File(folder, "score.txt").path

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals("score.txt", ready.file.name, "the user asked for that name")
        folder.deleteRecursively()
    }

    @Test
    fun `a path that ends with a separator takes the default name`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = folder.path + File.separator

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals(default, ready.file.name, "the file takes the default name")
        assertEquals(folder.absolutePath, ready.file.parentFile.absolutePath, "it is in the folder")
        folder.deleteRecursively()
    }

    @Test
    fun `a folder takes the default name inside it`() {
        val folder = Files.createTempDirectory("lilytap").toFile()

        val path = resolveSavePath(folder.path, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals(default, ready.file.name, "the file takes the default name")
        assertEquals(folder.absolutePath, ready.file.parentFile.absolutePath, "it is in the folder")
        folder.deleteRecursively()
    }

    @Test
    fun `a folder that does not exist is refused`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = File(folder, "nowhere/score.ly").path

        val path = resolveSavePath(text, default)

        val invalid = assertIs<SavePath.Invalid>(path, "the folder does not exist")
        assertTrue(invalid.reason.contains("nowhere"), "the reason names the folder: ${invalid.reason}")
        folder.deleteRecursively()
    }

    @Test
    fun `a folder that is a file is refused`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val file = File(folder, "afile.txt").apply { writeText("x") }
        val text = File(file, "score.ly").path

        val path = resolveSavePath(text, default)

        assertIs<SavePath.Invalid>(path, "the folder is not a folder")
        folder.deleteRecursively()
    }

    @Test
    fun `a file that is already there is said to be replaced`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val file = File(folder, "score.ly").apply { writeText("old") }
        val text = file.path

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertTrue(ready.replacing, "the file is already there")
        folder.deleteRecursively()
    }

    @Test
    fun `a file that is not there yet is not replaced`() {
        val folder = Files.createTempDirectory("lilytap").toFile()
        val text = File(folder, "score.ly").path

        val path = resolveSavePath(text, default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertTrue(!ready.replacing, "there is no file to replace")
        folder.deleteRecursively()
    }

    @Test
    fun `a path that starts with the mark of the home directory is the home directory`() {
        val path = resolveSavePath("~/score.ly", default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals(
            File(System.getProperty("user.home"), "score.ly").absolutePath,
            ready.file.absolutePath,
            "the mark of the home directory is written out",
        )
    }

    @Test
    fun `a relative path is a path of the working folder`() {
        val path = resolveSavePath("score.ly", default)

        val ready = assertIs<SavePath.Ready>(path, "the path can be used")
        assertEquals(
            File(File("").absoluteFile, "score.ly").absolutePath,
            ready.file.absolutePath,
            "the path is read from the working folder",
        )
    }
}
