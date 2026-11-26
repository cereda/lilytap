// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The option of the smallest rest that says that no silence is ever written.
 *
 * It is the last one of the control, after the three lengths.
 */
private const val NO_RESTS = 3

/**
 * The time signatures the app offers in the menu next to the field.
 *
 * The meters come in two groups: the simple ones, whose beat divides in two, and the compound
 * ones, whose beat divides in three. The compound group is where the 3-feel lives.
 *
 * The field takes any other one, so this is a shortcut and not a limit.
 */
private val METER_GROUPS =
    listOf(
        MenuGroup(
            "Simple",
            listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8"),
        ),
        MenuGroup("Compound", listOf("6/8", "9/8", "12/8", "6/4")),
    )

/**
 * The names of the note values a time signature can name.
 *
 * Used by the caption of the grid setting: in a compound meter the cells cut the note value
 * (the eighth in 6/8), not the beat, so the caption says which note they cut.
 */
private val NOTE_NAMES =
    mapOf(1 to "whole", 2 to "half", 4 to "quarter", 8 to "eighth", 16 to "16th", 32 to "32nd")

/**
 * The settings card: the controls that describe the next take.
 *
 * The card owns the widgets, the rows they sit in and the state that belongs to them: whether
 * the 3-feel is on, whether the meter in the time field is a compound one, and whether the
 * settings can be changed at all. The window reads the values and applies what they say.
 *
 * @param onKey called when a key is chosen: the score is put in it.
 */
internal class SettingsCard(
    private val onKey: (KeySignature) -> Unit,
) {
    val bpm = Stepper(60.0, 20.0, 300.0, 1.0, width = 104) { it.roundToLong().toString() }
    val time = StyledField("4/4", 4)

    /** The time signatures the app offers; the field next to it takes any other one. */
    val timeMenu =
        MenuButton(METER_GROUPS) { meter ->
            time.text = meter
            time.invalid = false
        }

    /** The key of the piece: the field with its name and the circle of fifths beside it. */
    val keySetting = KeySetting(onKey)

    /**
     * The caption of the grid setting.
     *
     * In a simple meter the cells cut the beat; in a compound one they cut the note value
     * (the eighth in 6/8), because the beat there is the dotted quarter. The caption follows
     * the meter.
     */
    val gridCaption = GlyphLabel(Glyph.GRID, "Cells / beat")

    val bars = Stepper(4.0, 1.0, 64.0, 1.0, width = 124) { it.roundToLong().toString() }
    val subdivisions = SegmentedControl(listOf("1", "2", "4"), 1)

    /**
     * The smallest rest.
     *
     * The first three options say how short a silence may be before it becomes a rest; the
     * last one says that no rest is ever written, which used to be a separate checkbox.
     */
    val minRest = SegmentedControl(listOf("1 beat", "\u00BD beat", "\u00BC beat", "No rests"), 0)

    val latency =
        Stepper(0.0, -500.0, 500.0, 5.0) { v ->
            val n = v.roundToLong()
            if (n > 0) "+$n ms" else "$n ms"
        }

    val audioLatency =
        Stepper(0.0, -500.0, 500.0, 5.0) { v ->
            val n = v.roundToLong()
            if (n > 0) "+$n ms" else "$n ms"
        }

    val calibrateButton =
        PillButton("Calibrate", Glyph.METRONOME, ButtonKind.GHOST, minW = 168, heightPx = 38)

    val latencyReset = IconButton(Glyph.RESET, 38, 24)
    val audioLatencyReset = IconButton(Glyph.RESET, 38, 24)

    /** The button that sets the tempo by tapping. */
    val tapButton = IconButton(Glyph.TAP, 38, 24)

    /** The button that makes the click fall on every note value of a compound meter. */
    val threeFeelButton = IconButton(Glyph.BEATS, 38, 24)

    /** Whether the 3-feel is on: it only means something in a compound meter. */
    var threeFeel: Boolean = false
        private set

    /** Whether the meter in the time field is a compound one. */
    var compoundMeter: Boolean = false
        private set

    /** Whether the settings can be changed right now (a take or a playback owns the window). */
    private var live: Boolean = true

    init {
        minRest.onChange = { minRest.toolTipText = restToolTip() }
        wireTimeSignature()
        threeFeelButton.onClick = {
            threeFeel = !threeFeel
            updateThreeFeel()
        }
        updateThreeFeel()
    }

    /**
     * The rows of the settings card: three rows of cells, each with a caption above it.
     *
     * The rows are built apart from the card that holds them, so the layout can be looked at
     * without opening the window.
     *
     * @return the column of the settings.
     */
    fun rows(): JComponent {
        /**
         * Reserves the same caption strip as [labeled] without drawing one, so a control placed in
         * it lines up vertically with the fields in the other cells of the row.
         */
        fun slot(c: JComponent) =
            JPanel(BorderLayout(0, 7)).apply {
                isOpaque = false
                add(GlyphLabel(null, ""), BorderLayout.NORTH)
                add(c, BorderLayout.CENTER)
            }

        /** A stepper with a small reset button next to it. */
        fun withReset(
            stepper: Stepper,
            reset: JComponent,
        ) = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(stepper, BorderLayout.CENTER)
            add(reset, BorderLayout.EAST)
        }

        fun row(vararg items: JComponent) =
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                items.forEachIndexed { i, c ->
                    add(
                        c,
                        GridBagConstraints().apply {
                            gridx = i
                            gridy = 0
                            anchor = GridBagConstraints.NORTHWEST
                            insets = Insets(0, 0, 0, if (i == items.lastIndex) 0 else 18)
                        },
                    )
                }
                add(
                    JPanel().apply { isOpaque = false },
                    GridBagConstraints().apply {
                        gridx = items.size
                        weightx = 1.0
                    },
                )
            }

        return JPanel(GridLayout(3, 1, 0, 16)).apply {
            isOpaque = false
            add(
                row(
                    labeled(Glyph.METRONOME, "Tempo (BPM)", withReset(bpm, tapButton)),
                    labeled(Glyph.CLOCK, "Time", timeCell()),
                    labeled(Glyph.BARS, "Bars", bars),
                    captioned(gridCaption, subdivisions),
                ),
            )
            add(
                row(
                    labeled(Glyph.PAUSE, "Smallest rest", minRest),
                    labeled(Glyph.BOLT, "Latency", withReset(latency, latencyReset)),
                ),
            )
            add(
                row(
                    labeled(Glyph.BOLT, "Audio latency", withReset(audioLatency, audioLatencyReset)),
                    slot(calibrateButton),
                    labeled(Glyph.KEY, "Key", keySetting.cell()),
                ),
            )
        }
    }

    /**
     * The time signature field with the menu of common meters and the 3-feel button beside it.
     */
    fun timeCell(): JComponent =
        JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(time, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false
                    add(timeMenu, BorderLayout.CENTER)
                    add(threeFeelButton, BorderLayout.EAST)
                },
                BorderLayout.EAST,
            )
        }

    /**
     * A settings cell: a caption strip above the control.
     *
     * @param glyph the icon of the caption.
     * @param label the text of the caption.
     * @param c the control.
     */
    private fun labeled(
        glyph: Glyph,
        label: String,
        c: JComponent,
    ) = JPanel(BorderLayout(0, 7)).apply {
        isOpaque = false
        add(GlyphLabel(glyph, label), BorderLayout.NORTH)
        add(c, BorderLayout.CENTER)
    }

    /**
     * A settings cell whose caption can change while the window is open.
     *
     * @param caption the caption of the cell.
     * @param c the control.
     */
    private fun captioned(
        caption: GlyphLabel,
        c: JComponent,
    ) = JPanel(BorderLayout(0, 7)).apply {
        isOpaque = false
        add(caption, BorderLayout.NORTH)
        add(c, BorderLayout.CENTER)
    }

    /** The tooltips of the settings: what every control does and how it is used. */
    fun setToolTips() {
        bpm.toolTipText =
            "Beats per minute: the dotted quarter in a compound meter (6/8), the note value " +
            "otherwise. Click +/-, scroll, or drag the number."
        tapButton.toolTipText =
            "Set the tempo by tapping: click this button in time, or press any key. " +
            "The tempo follows the taps and settles on the median of the intervals."
        bars.toolTipText = "How many bars to record after the count-in."
        latency.toolTipText = "Delay subtracted from your taps (audio output + reaction time)."
        latencyReset.toolTipText = "Reset the tap latency to 0 ms."
        audioLatency.toolTipText = "Audio output latency: shifts the metronome click earlier so it sounds on time."
        audioLatencyReset.toolTipText = "Reset the audio latency to 0 ms."
        calibrateButton.toolTipText = "Tap along with the metronome clicks to measure your audio output latency automatically."
        minRest.toolTipText = restToolTip()
        subdivisions.toolTipText =
            "How many grid cells the note value is cut into: 1 = the note value itself " +
            "(eighths in 6/8), 2 = half of it, 4 = a quarter of it. The click falls on the " +
            "beat, not on the cells (unless the 3-feel is on)."
        time.toolTipText = "Time signature, e.g. 4/4 or 6/8."
    }

    /**
     * The tooltip of the smallest rest: it follows the option that is selected, because the
     * last one is not a length at all.
     */
    private fun restToolTip(): String =
        if (minRest.selectedIndex == NO_RESTS) {
            "Never write rests: every silence is absorbed into the previous note."
        } else {
            "Silences shorter than this are absorbed into the previous note."
        }

    /**
     * Turns the settings off and on.
     *
     * The settings describe the next take, and a take or a score that plays owns the window,
     * so they are off while either is running.
     *
     * @param on whether the settings can be changed.
     */
    fun setEnabled(on: Boolean) {
        live = on
        settingControls.forEach { it.isEnabled = on }
        keySetting.setEnabled(on)
        calibrateButton.isEnabled = on
        updateThreeFeel()
    }

    /** The settings that describe the next take. */
    private val settingControls =
        listOf<JComponent>(
            bpm,
            tapButton,
            time,
            timeMenu,
            threeFeelButton,
            bars,
            subdivisions,
            minRest,
            latency,
            latencyReset,
            audioLatency,
            audioLatencyReset,
        )

    /**
     * The settings of the next take.
     *
     * @return the configuration of the take.
     * @throws IllegalArgumentException when the time signature cannot be read.
     */
    fun config(): Config =
        Config(
            bpm = bpm.value,
            timeSignature = TimeSignature.parse(time.text),
            subdivisions = subdivisions.selectedText.toInt(),
            bars = bars.value.roundToInt(),
            latencyMs = latency.value,
            noRests = minRest.selectedIndex == NO_RESTS,
            threeFeel = threeFeel,
            key = keySetting.key,
        )

    /**
     * The smallest silence that becomes a rest of its own, in cells.
     *
     * The last option of the smallest rest is not a length: it says that no silence is ever
     * written, so the length does not matter there.
     *
     * @param config the configuration of the take.
     * @return the number of cells.
     */
    fun minRestCells(config: Config): Int {
        val fraction =
            when (minRest.selectedIndex) {
                0 -> 1
                1 -> 2
                else -> 4
            }
        return maxOf(1, config.cellsPerPulse / fraction)
    }

    /**
     * Wires the time signature field and the menu beside it: the menu offers the common
     * meters, and a text that is not a meter says so, right away, instead of only when the
     * take is refused.
     */
    private fun wireTimeSignature() {
        timeMenu.toolTipText = "Common time signatures"
        time.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = markTimeSignature()

                override fun removeUpdate(e: DocumentEvent) = markTimeSignature()

                override fun changedUpdate(e: DocumentEvent) = markTimeSignature()
            },
        )
    }

    /**
     * Says whether the text in the time signature field can be used, and names the grid cells.
     *
     * In a compound meter the cells cut the note value (the eighth in 6/8) and not the beat,
     * which is the dotted quarter there, so the caption of the grid setting says which note
     * the cells cut.
     */
    private fun markTimeSignature() {
        val parsed = runCatching { TimeSignature.parse(time.text) }.getOrNull()
        time.invalid = parsed == null
        compoundMeter = parsed != null && parsed.isCompound
        gridCaption.text =
            if (parsed != null && parsed.isCompound) {
                "Cells / ${NOTE_NAMES[parsed.beatUnit] ?: "note"}"
            } else {
                "Cells / beat"
            }
        updateThreeFeel()
    }

    /**
     * The 3-feel button: it works only in a compound meter, and it shows whether it is on.
     *
     * The button stays in place in every meter, so the row never moves; it is greyed out when
     * the meter is simple, and the setting is remembered when the meter changes back.
     */
    fun updateThreeFeel() {
        threeFeelButton.isEnabled = compoundMeter && live
        threeFeelButton.accentOverride = if (threeFeel && compoundMeter) Theme.accent else null
        threeFeelButton.toolTipText =
            if (!compoundMeter) {
                "3 feel: click every note value of a compound meter (the eighth in 6/8). " +
                    "It needs a compound meter."
            } else if (threeFeel) {
                "3 feel is on: the click falls on every note value of the bar. Click to turn it off."
            } else {
                "3 feel: click every note value of a compound meter (the eighth in 6/8). " +
                    "Click to turn it on."
            }
    }
}
