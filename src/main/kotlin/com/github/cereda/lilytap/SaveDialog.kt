// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The dialog that asks where the file goes: a field with the path and a save button.
 */

import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * What kind of file is written.
 *
 * @property title the headline of the window that asks for the file.
 * @property description the line that says what the window is for.
 * @property name the name the file takes when the path names a folder.
 * @property extension the extension the file takes when its name has none.
 * @property glyph the mark the window carries.
 */
internal data class FileFormat(
    val title: String,
    val description: String,
    val name: String,
    val extension: String,
    val glyph: Glyph,
)

/** The LilyPond file: the text of the score, as a document LilyPond reads. */
internal val LILYPOND_FORMAT =
    FileFormat(
        title = "Save the LilyPond text",
        description = "The file the LilyPond text is written to.",
        name = "lilytap.ly",
        extension = "ly",
        glyph = Glyph.SAVE,
    )

/** The MIDI file: the score, as sound. */
internal val MIDI_FORMAT =
    FileFormat(
        title = "Save the MIDI file",
        description = "The file the score is written to, as sound.",
        name = "lilytap.mid",
        extension = "mid",
        glyph = Glyph.MIDI,
    )

/**
 * What the text of the dialog means: the file to write, or why there is none.
 */
internal sealed interface SavePath {
    /**
     * The file the text is written to.
     *
     * @property file the file to write.
     * @property replacing whether the file is already there and would be replaced.
     */
    data class Ready(
        val file: File,
        val replacing: Boolean = false,
    ) : SavePath

    /**
     * The text cannot be used.
     *
     * @property reason what is wrong with it.
     */
    data class Invalid(
        val reason: String,
    ) : SavePath
}

/**
 * The file the text of the dialog points at.
 *
 * The text is a path, and the path may say more than the name of a file: a path that ends
 * with a separator, or that names a folder, takes the default name inside it. A name
 * without an extension gets the extension of the file that is written.
 *
 * @param text what the user typed.
 * @param format the kind of file that is written.
 * @return the file to write, or the reason the text cannot be used.
 */
internal fun resolveSavePath(
    text: String,
    format: FileFormat,
): SavePath {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return SavePath.Invalid("The file name is empty.")
    val file = File(expandHome(trimmed))
    val namesAFolder =
        trimmed.endsWith("/") ||
            trimmed.endsWith(File.separator) ||
            (file.exists() && file.isDirectory)
    val target = if (namesAFolder) File(file, format.name) else file
    val absolute = target.absoluteFile
    val folder = absolute.parentFile ?: return SavePath.Invalid("The path has no folder.")
    if (!folder.exists()) return SavePath.Invalid("The folder ${folder.path} does not exist.")
    if (!folder.isDirectory) return SavePath.Invalid("${folder.path} is not a folder.")
    if (!folder.canWrite()) return SavePath.Invalid("The folder ${folder.path} cannot be written to.")
    if (absolute.isDirectory) return SavePath.Invalid("${absolute.name} is a folder.")
    return SavePath.Ready(withExtension(absolute, format.extension), replacing = absolute.exists())
}

/**
 * A path that starts with the mark of the home directory.
 *
 * @param path the path the user typed.
 * @return the path with the home directory written out.
 */
private fun expandHome(path: String): String =
    if (path == "~") {
        System.getProperty("user.home")
    } else if (path.startsWith("~/")) {
        System.getProperty("user.home") + path.substring(1)
    } else {
        path
    }

/**
 * The dialog that asks where the file goes.
 *
 * The dialog is drawn like the rest of the app: it is a card with a header, a field with the
 * path and two buttons. The field is checked as it is typed: the border turns red and the
 * save button goes off when the path cannot be used.
 *
 * @param frame the window the dialog belongs to.
 * @param suggested the path the field starts with.
 * @param format the kind of file that is written.
 */
internal class SaveDialog(
    private val frame: JFrame,
    private val suggested: File,
    private val format: FileFormat = LILYPOND_FORMAT,
) {
    /** The file the dialog asks for, or null when the user gave it up. */
    fun choose(): File? {
        var chosen: File? = null
        val dialog = JDialog(frame, null, Dialog.ModalityType.APPLICATION_MODAL)
        dialog.isUndecorated = true
        dialog.isResizable = false

        val field =
            StyledField(suggested.path, 40, heightPx = 38, hint = "The file to write").apply {
                horizontalAlignment = SwingConstants.LEFT
                preferredSize = Dimension(FIELD_WIDTH, 38)
            }
        val hint =
            JLabel("").apply {
                font = Theme.font(12f)
                foreground = Theme.dim
            }
        val save = PillButton("Save", Glyph.SAVE, ButtonKind.PRIMARY, minW = 110, heightPx = 40)
        val cancel = PillButton("Cancel", null, ButtonKind.GHOST, minW = 100, heightPx = 40)
        val header = GlyphLabel(format.glyph, format.title, 16f, { Theme.text }, Font.BOLD)

        fun check(): SavePath {
            val path = resolveSavePath(field.text, format)
            when (path) {
                is SavePath.Ready -> {
                    field.invalid = false
                    hint.text =
                        if (path.replacing) {
                            "The file is already there: it will be replaced."
                        } else {
                            "The file is written to ${path.file.path}."
                        }
                    hint.foreground = Theme.dim
                    save.isEnabled = true
                }

                is SavePath.Invalid -> {
                    field.invalid = true
                    hint.text = path.reason
                    hint.foreground = Theme.rec
                    save.isEnabled = false
                }
            }
            return path
        }

        field.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    check()
                }

                override fun removeUpdate(e: DocumentEvent) {
                    check()
                }

                override fun changedUpdate(e: DocumentEvent) {
                    check()
                }
            },
        )
        save.onClick = {
            val path = check()
            if (path is SavePath.Ready) {
                chosen = path.file
                dialog.dispose()
            }
        }
        cancel.onClick = { dialog.dispose() }

        val body =
            JPanel(BorderLayout(0, 10)).apply {
                isOpaque = false
                add(
                    JLabel(format.description).apply {
                        font = Theme.font(13f)
                        foreground = Theme.dim
                    },
                    BorderLayout.NORTH,
                )
                add(
                    JPanel(BorderLayout()).apply {
                        isOpaque = false
                        add(field, BorderLayout.NORTH)
                    },
                    BorderLayout.CENTER,
                )
                add(hint, BorderLayout.SOUTH)
            }
        val card =
            Card().apply {
                layout = BorderLayout(0, 14)
                border = BorderFactory.createEmptyBorder(18, 24, 18, 24)
                add(header, BorderLayout.NORTH)
                add(body, BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                        isOpaque = false
                        add(cancel)
                        add(save)
                    },
                    BorderLayout.SOUTH,
                )
            }
        val content = dialog.contentPane as JComponent
        content.background = Theme.bg
        content.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        content.add(card, BorderLayout.CENTER)

        dialog.rootPane.registerKeyboardAction(
            { cancel.onClick?.invoke() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        dialog.rootPane.registerKeyboardAction(
            { save.onClick?.invoke() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )

        // The header doubles as the drag handle.
        var grab: Point? = null
        header.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val here = e.locationOnScreen
                    val there = dialog.location
                    grab = Point(here.x - there.x, here.y - there.y)
                }

                override fun mouseDragged(e: MouseEvent) {
                    val g = grab ?: return
                    val here = e.locationOnScreen
                    dialog.location = Point(here.x - g.x, here.y - g.y)
                }
            },
        )

        check()
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        // The name of the file is selected, so a name typed in takes its place; the folder
        // in front of it stays where it is.
        val nameStart = suggested.path.lastIndexOf(File.separatorChar) + 1
        SwingUtilities.invokeLater {
            field.requestFocusInWindow()
            field.select(nameStart, field.text.length)
        }
        dialog.isVisible = true
        return chosen
    }

    companion object {
        /** How wide the field of the path is, in pixels. */
        private const val FIELD_WIDTH = 430
    }
}
