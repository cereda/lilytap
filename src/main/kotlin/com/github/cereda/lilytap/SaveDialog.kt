// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The dialog that asks where the LilyPond file goes: a field with the path and a save button.
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
 * What the text of the dialog means: the file to write, or why there is none.
 */
internal sealed interface SavePath {
    /** The file the text is written to. */
    data class Ready(
        val file: File,
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
 * without an extension gets the extension of a LilyPond file.
 *
 * @param text what the user typed.
 * @param defaultName the name the file takes when the text names a folder.
 * @return the file to write, or the reason the text cannot be used.
 */
internal fun resolveSavePath(
    text: String,
    defaultName: String,
): SavePath {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return SavePath.Invalid("The file name is empty.")
    val file = File(expandHome(trimmed))
    val namesAFolder =
        trimmed.endsWith("/") ||
            trimmed.endsWith(File.separator) ||
            (file.exists() && file.isDirectory)
    val target = if (namesAFolder) File(file, defaultName) else file
    val absolute = target.absoluteFile
    val folder = absolute.parentFile ?: return SavePath.Invalid("The path has no folder.")
    if (!folder.exists()) return SavePath.Invalid("The folder ${folder.path} does not exist.")
    if (!folder.isDirectory) return SavePath.Invalid("${folder.path} is not a folder.")
    if (!folder.canWrite()) return SavePath.Invalid("The folder ${folder.path} cannot be written to.")
    if (absolute.isDirectory) return SavePath.Invalid("${absolute.name} is a folder.")
    return SavePath.Ready(withExtension(absolute))
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
 * The dialog that asks where the LilyPond file goes.
 *
 * The dialog is drawn like the rest of the app: it is a card with a header, a field with the
 * path and two buttons. The field is checked as it is typed: the border turns red and the
 * save button goes off when the path cannot be used.
 *
 * @param frame the window the dialog belongs to.
 * @param suggested the path the field starts with.
 */
internal class SaveDialog(
    private val frame: JFrame,
    private val suggested: File,
) {
    /** The file the dialog asks for, or null when the user gave it up. */
    fun choose(): File? {
        var chosen: File? = null
        val dialog = JDialog(frame, null, Dialog.ModalityType.APPLICATION_MODAL)
        dialog.isUndecorated = true
        dialog.isResizable = false

        val field =
            StyledField(suggested.path, 40, heightPx = 38, hint = "The file of the text").apply {
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
        val header = GlyphLabel(Glyph.SAVE, "Save the LilyPond text", 16f, { Theme.text }, Font.BOLD)

        fun check(): SavePath {
            val path = resolveSavePath(field.text, DEFAULT_NAME)
            when (path) {
                is SavePath.Ready -> {
                    field.invalid = false
                    hint.text = "The text is written to ${path.file.path}."
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
                    JLabel("The file the LilyPond text is written to.").apply {
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
        /** The name the file takes when the path names a folder. */
        const val DEFAULT_NAME = "lilytap.ly"

        /** How wide the field of the path is, in pixels. */
        private const val FIELD_WIDTH = 430
    }
}
