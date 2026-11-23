// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.BorderLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The key of the piece: the field with its name, the menu of the common keys, and the key
 * itself.
 *
 * The name is read when it is finished - with **Enter** or when the field loses the focus -
 * and not while it is typed: every letter of a name would spell the score again. A name that
 * cannot be read turns the border of the field red.
 *
 * @param onKey called when a key is chosen: the score is put in it.
 */
internal class KeySetting(
    private val onKey: (KeySignature) -> Unit,
) {
    /** The name of the key, as it is typed. */
    val field = StyledField("C major", 7, heightPx = 38, hint = "Key")

    /** The keys the app offers; the field next to it takes any other one. */
    private val menu =
        MenuButton(COMMON_KEYS) { name ->
            field.text = name
            apply()
        }

    /** The key the piece is in. */
    var key: KeySignature = KeySignature.C_MAJOR
        private set

    init {
        field.toolTipText = KEY_TOOLTIP
        field.addActionListener { apply() }
        field.addFocusListener(
            object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    apply()
                }
            },
        )
        field.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = mark()

                override fun removeUpdate(e: DocumentEvent) = mark()

                override fun changedUpdate(e: DocumentEvent) = mark()
            },
        )
    }

    /**
     * The cell of the settings card: the field with the name of the key and the menu beside it.
     *
     * @return the cell of the key.
     */
    fun cell(): JComponent =
        JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(field, BorderLayout.CENTER)
            add(menu, BorderLayout.EAST)
        }

    /**
     * Puts a key on the score: the notes are spelled in it.
     *
     * @param key the key the piece is in.
     */
    fun choose(key: KeySignature) {
        this.key = key
        show()
        onKey(key)
    }

    /**
     * Says the key the score is in, without changing it.
     *
     * The score knows its key better than the field does: an undo or a revert can put the
     * score back in another key.
     *
     * @param key the key the piece is in.
     */
    fun tell(key: KeySignature) {
        this.key = key
        show()
    }

    /**
     * Reads the name in the field and puts it on the score.
     *
     * A name that cannot be read turns the border of the field red and says what a name
     * looks like.
     */
    fun apply() {
        val parsed = KeySignature.parse(field.text)
        if (parsed == null) {
            field.invalid = true
            field.toolTipText = KEY_ERROR_TOOLTIP
            return
        }
        field.invalid = false
        field.toolTipText = KEY_TOOLTIP
        field.text = parsed.name
        choose(parsed)
    }

    /**
     * Marks the name in the field as good or bad, as it is typed.
     *
     * The name is not put on the score here: the score is spelled when the name is finished.
     */
    private fun mark() {
        val parsed = KeySignature.parse(field.text)
        field.invalid = parsed == null
        field.toolTipText = if (parsed == null) KEY_ERROR_TOOLTIP else KEY_TOOLTIP
    }

    /** Writes the name of the key in the field, unless it is being typed in. */
    private fun show() {
        if (field.isFocusOwner) return
        field.text = key.name
        field.invalid = false
        field.toolTipText = KEY_TOOLTIP
    }

    companion object {
        /**
         * The keys the app offers in the menu next to the field.
         *
         * The field takes any other one, so this is a shortcut and not a limit.
         */
        private val COMMON_KEYS =
            listOf(
                "C major",
                "G major",
                "F major",
                "D major",
                "A major",
                "E major",
                "Bb major",
                "Eb major",
                "Ab major",
                "A minor",
                "E minor",
                "D minor",
                "B minor",
                "G minor",
            )

        /**
         * What the key field says about itself.
         *
         * The field takes the name of the key the way it is written in English: a letter, an
         * optional accidental (`#` for a sharp, `b` for a flat) and the mode.
         */
        private const val KEY_TOOLTIP =
            "The key of the piece: the notes are spelled in it and the LilyPond text declares it. " +
                "Write it as it is written in English: Bb major, F# minor, C♭ major."

        /**
         * What the key field says when the name in it cannot be read.
         */
        private const val KEY_ERROR_TOOLTIP =
            "Not the name of a key. Write it as it is written in English: " +
                "Bb major, F# minor, C♭ major."
    }
}
