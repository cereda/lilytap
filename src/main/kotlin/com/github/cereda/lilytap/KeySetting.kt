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
 * The key of the piece: the field with its name, the circle of fifths beside it, and the key
 * itself.
 *
 * The name is read when it is finished - with **Enter** or when the field loses the focus -
 * and not while it is typed: every letter of a name would spell the score again. A name that
 * cannot be read turns the border of the field red.
 *
 * The circle beside the field holds the keys of the piece: the major keys outside the ring
 * and their relative minors inside it. It holds the keys with ten accidentals on either
 * side of C major; the keys past them are named in the field.
 *
 * @param onKey called when a key is chosen: the score is put in it.
 */
internal class KeySetting(
    private val onKey: (KeySignature) -> Unit,
) {
    /** The name of the key, as it is typed. */
    val field = StyledField("C major", 7, heightPx = 38, hint = "Key")

    /** The circle of fifths: every key of the piece, with its relative minor. */
    private val circle = CircleOfFifths {}

    /** The button that opens the circle of fifths. */
    val menu =
        CircleButton(circle) { key ->
            field.text = key.name
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
     * The cell of the settings card: the field with the name of the key and the circle beside
     * it.
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
     * Turns the control off and on.
     *
     * The key of a take is read when the take begins, and a score that plays is fixed, so the
     * control is off while either is running: the name cannot be typed and the circle cannot
     * be opened.
     *
     * @param on whether the control takes a key.
     */
    fun setEnabled(on: Boolean) {
        field.isEnabled = on
        menu.isEnabled = on
    }

    /**
     * The fault of the name in the field, when it cannot be read.
     *
     * The field is the truth here: the name in it is what the user wrote, and a name that
     * cannot be read is a problem the app has to say before it acts.
     */
    val fault: KeyFault? get() = KeySignature.fault(this.field.text)

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
            field.toolTipText = faultText(KeySignature.fault(field.text))
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
        field.toolTipText = if (parsed == null) faultText(KeySignature.fault(field.text)) else KEY_TOOLTIP
    }

    /**
     * What the field says when the name in it cannot be read.
     *
     * @param fault what is wrong with the name.
     * @return the words of the tooltip.
     */
    private fun faultText(fault: KeyFault?): String = fault?.message ?: KeyFault.Malformed.message

    /** Writes the name of the key in the field, unless it is being typed in. */
    private fun show() {
        circle.key = key
        if (field.isFocusOwner) return
        field.text = key.name
        field.invalid = false
        field.toolTipText = KEY_TOOLTIP
    }

    companion object {
        /**
         * What the key field says about itself.
         *
         * The field takes the name of the key the way it is written in English (`Bb major`)
         * or the way LilyPond writes it (`bes major`), and a key past seven accidentals
         * carries a double accidental: `F𝄪 major`, `B𝄫 minor`.
         */
        private val KEY_TOOLTIP =
            tooltip(
                "The key of the piece: the notes are spelled in it and the LilyPond text declares it.",
                "Write it as it is written in English (Bb major, F# minor) or the way LilyPond " +
                    "writes it (bes major, fis minor).",
                "A key past seven accidentals carries a double accidental: F𝄪 major, B𝄫 minor.",
            )
    }
}
