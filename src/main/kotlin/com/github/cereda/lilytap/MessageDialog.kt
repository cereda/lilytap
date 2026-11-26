// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The message dialog: a small themed window that says what happened.
 */

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog
import java.awt.Font
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * A themed modal dialog: undecorated, draggable by its header, closed with OK, Enter or Esc.
 *
 * @param frame the window the dialog belongs to.
 * @param message what the dialog says.
 */
internal fun showMessage(
    frame: JFrame,
    message: Message,
) {
    val dialog = JDialog(frame, null, Dialog.ModalityType.APPLICATION_MODAL)
    dialog.isUndecorated = true
    dialog.isResizable = false

    val ok = PillButton("OK", Glyph.CHECK, ButtonKind.PRIMARY, minW = 120, heightPx = 40)
    val header = GlyphLabel(message.glyph, message.title, 16f, { Theme.text }, Font.BOLD)
    val value =
        JLabel(message.headline).apply {
            font = Theme.font(22f, Font.BOLD)
            foreground = message.accent
        }
    val body =
        JLabel("<html><body style='width:320px'>${escapeHtml(message.description)}</body></html>").apply {
            font = Theme.font(13f)
            foreground = Theme.dim
        }
    val card =
        Card().apply {
            layout = BorderLayout(0, 12)
            border = BorderFactory.createEmptyBorder(18, 24, 18, 24)
            add(header, BorderLayout.NORTH)
            add(
                JPanel(BorderLayout(0, 6)).apply {
                    isOpaque = false
                    add(value, BorderLayout.NORTH)
                    add(body, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(ok, BorderLayout.EAST)
                },
                BorderLayout.SOUTH,
            )
        }
    val content = dialog.contentPane as JComponent
    content.background = Theme.bg
    content.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    content.add(card, BorderLayout.CENTER)

    ok.onClick = { dialog.dispose() }
    dialog.rootPane.registerKeyboardAction(
        { dialog.dispose() },
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW,
    )
    dialog.rootPane.registerKeyboardAction(
        { dialog.dispose() },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW,
    )

    // the header doubles as the drag handle
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

    dialog.pack()
    dialog.setLocationRelativeTo(frame)
    dialog.isVisible = true
}
