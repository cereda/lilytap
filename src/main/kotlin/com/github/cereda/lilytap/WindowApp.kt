// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The tempo of a calibration round: one beat per second.
 *
 * The measurement does not depend on the tempo, and a slow beat leaves room around every beat
 * for the whole output latency: at the user's tempo (120 BPM is half a beat) a tap that is late
 * by more than half a beat lands closer to the next one and is read as early.
 */
internal const val CALIBRATION_BPM = 60.0

/** The time signature of a calibration round: four beats to the bar. */
internal val CALIBRATION_SIGNATURE = TimeSignature(4, 4)

/** The number of bars to tap along with in a calibration round. */
internal const val CALIBRATION_BARS = 2

/** The largest audio latency the setting can hold; an output delay is never negative. */
private const val MAX_AUDIO_LATENCY_MS = 500.0

/**
 * How close two presses of the same key have to be to count as one tap.
 *
 * A held key repeats itself, and the repeats come faster than any tempo the app can hold
 * (300 BPM is a fifth of a second), so two presses that close together are one tap.
 */
private const val REPEAT_GUARD_NANOS = 100_000_000L

/** What the status line says when the window waits for a take. */
private const val IDLE_STATUS = "Set the tempo, press Start, then play with any key"

/**
 * What the pitch field says about itself.
 *
 * The field takes the name of the note the way LilyPond writes it: a letter, an optional
 * accidental and the octave marks. LilyPond puts `c` in the octave below middle C, so
 * middle C is `c'` there.
 */
private const val PITCH_TOOLTIP =
    "The pitch of the selected note, written the way LilyPond writes it: " +
        "c' (middle C), cis' (sharp), ces' (flat), cisis' (double sharp), c (lower), c'' (higher). " +
        "Press Enter to apply it."

/** What the status line says while a tap round is listening. */
private const val TAP_STATUS = "Keep tapping - the tempo follows the taps"

/**
 * The option of the smallest rest that says that no rest is ever written.
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
 * Launches the Swing (window) version of lilytap.
 *
 * Sets text anti-aliasing system properties before AWT starts, applies the OS-preferred theme,
 * and shows the main window on the Swing event thread.
 */
fun launchWindow() {
    // Must be set before AWT starts: smoother text everywhere.
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")
    // Detecting the OS theme can spawn a process and wait for it; keep that off the EDT.
    val dark = osPrefersDark()
    SwingUtilities.invokeLater {
        Theme.set(dark)
        try {
            // Metal everywhere: our widgets are custom painted, so a native L&F only adds inconsistency.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
        } catch (_: Exception) {
            // keep the default look and feel
        }
        applyDialogTheme()
        WindowApp().show()
    }
}

private fun runCommand(vararg cmd: String): String? =
    try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            null
        } else if (p.exitValue() == 0) {
            p.inputStream.bufferedReader().readText()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

/**
 * The offset of a calibration tap from the nearest beat, in milliseconds, or null when the tap
 * falls outside the recorded bars. [ms] is measured from the first recorded beat, not from the
 * count-in, so the first bar is included.
 */
internal fun calibrationOffsetMs(
    ms: Double,
    beatMs: Double,
    totalBeats: Int,
): Double? {
    if (!ms.isFinite() || !beatMs.isFinite()) return null
    if (beatMs <= 0 || totalBeats <= 0 || ms < 0) return null
    val beatIndex = Math.round(ms / beatMs).toInt()
    if (beatIndex !in 0 until totalBeats) return null
    return ms - beatIndex * beatMs
}

/**
 * The audio latency after a calibration round.
 *
 * [offsetsMs] are the signed distances from the taps to the nearest beat. The median is used
 * instead of the average, so a single stray tap or one missed beat cannot move the result.
 *
 * A tap that answers a beep which reached the ears late lands after the beat, so the distance is
 * positive and the latency grows by it. A result outside the range of the setting is a sign that
 * the taps did not follow the beeps; such a measurement is dropped and the old value is kept,
 * which keeps a stray tap from moving the setting by a whole beat.
 */
internal fun calibratedLatency(
    currentMs: Double,
    offsetsMs: List<Double>,
): Double {
    if (offsetsMs.isEmpty()) return currentMs
    val sorted = offsetsMs.sorted()
    val middle = sorted.size / 2
    val median =
        if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    val corrected = currentMs + median
    return if (corrected in 0.0..MAX_AUDIO_LATENCY_MS) corrected else currentMs
}

/** Best-effort OS dark-mode check, used only on the very first launch. Falls back to dark. */
private fun osPrefersDark(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    return when {
        "mac" in os -> {
            runCommand("defaults", "read", "-g", "AppleInterfaceStyle")?.contains("dark", ignoreCase = true) ?: false
        }

        "win" in os -> {
            runCommand(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme",
            )?.trim()?.endsWith("0x0") ?: true
        }

        else -> {
            runCommand("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                ?.contains("dark", ignoreCase = true) ?: true
        } // GNOME: 'default' means light
    }
}

internal class WindowApp {
    companion object {
        /**
         * The windows that are watching the clicks.
         *
         * The listener belongs to the toolkit and not to a window: the mouse events of AWT
         * do not bubble up to the panels of a window, so watching the panels is not enough
         * to see every click. The windows are held weakly, so a closed one can go.
         */
        private val watchers = CopyOnWriteArrayList<WeakReference<WindowApp>>()

        init {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                AWTEventListener { event ->
                    if (event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
                    val press = event as MouseEvent
                    for (watcher in watchers) watcher.get()?.clickOutsideScore(press.component)
                },
                AWTEvent.MOUSE_EVENT_MASK,
            )
        }
    }

    private val frame = JFrame("lilytap")
    private val bpm = Stepper(60.0, 20.0, 300.0, 1.0, width = 104) { it.roundToLong().toString() }
    private val time = StyledField("4/4", 4)

    /** The time signatures the app offers; the field next to it takes any other one. */
    private val timeMenu =
        MenuButton(METER_GROUPS) { meter ->
            time.text = meter
            time.invalid = false
        }

    /** The key of the piece: the field with its name and the circle of fifths beside it. */
    private val keySetting = KeySetting { key -> setKey(key) }

    /**
     * The caption of the grid setting.
     *
     * In a simple meter the cells cut the beat; in a compound one they cut the note value
     * (the eighth in 6/8), because the beat there is the dotted quarter. The caption follows
     * the meter.
     */
    private val gridCaption = GlyphLabel(Glyph.GRID, "Cells / beat")
    private val bars = Stepper(4.0, 1.0, 64.0, 1.0, width = 124) { it.roundToLong().toString() }
    private val subdivisions = SegmentedControl(listOf("1", "2", "4"), 1)

    /**
     * The smallest rest.
     *
     * The first three options say how short a silence may be before it becomes a rest; the
     * last one says that no rest is ever written, which used to be a separate checkbox.
     */
    private val minRest = SegmentedControl(listOf("1 beat", "\u00BD beat", "\u00BC beat", "No rests"), 0)
    private val latency =
        Stepper(0.0, -500.0, 500.0, 5.0) { v ->
            val n = v.roundToLong()
            if (n > 0) "+$n ms" else "$n ms"
        }
    private val audioLatency =
        Stepper(0.0, -500.0, 500.0, 5.0) { v ->
            val n = v.roundToLong()
            if (n > 0) "+$n ms" else "$n ms"
        }
    private val calibrateButton =
        PillButton("Calibrate", Glyph.METRONOME, ButtonKind.GHOST, minW = 168, heightPx = 38)
    private val latencyReset = IconButton(Glyph.RESET, 38, 24)
    private val audioLatencyReset = IconButton(Glyph.RESET, 38, 24)

    /** The button that sets the tempo by tapping. */
    private val tapButton = IconButton(Glyph.TAP, 38, 24)

    /** The button that makes the click fall on every note value of a compound meter. */
    private val threeFeelButton = IconButton(Glyph.BEATS, 38, 24)

    /** The pitch of the selected note: a LilyPond name, typed in. */
    private val pitchField = StyledField("", 3, heightPx = 28, hint = "Pitch")

    /** The icon of the pitch field: it stands for the note it names. */
    private val pitchCaption = GlyphLabel(Glyph.NOTE, "")

    private val start = PillButton("Start", Glyph.PLAY, ButtonKind.PRIMARY, minW = 168, heightPx = 48)
    private val status = StatusBadge()
    private val beatDots = BeatDots()
    private val grid = GridView()
    private val timeline = TimelinePanel(grid)
    private val notation = NotationPanel(grid)

    /** The pitches panel: the piano that writes the pitch of the selected note. */
    private val pitches = PitchesPanel()

    /** The legend of the top column: the colours of the raw taps and the tracker keys. */
    private val legend = LegendPanel()

    /** The tracker: the keys of the keyboard that play the piano, and the octave they play. */
    private val tracker = Tracker(pitches) { pitch -> fillPitch(pitch) }

    /**
     * The way back to the score the take ended with.
     *
     * The button is a word in red: it throws the edits away, so it says so.
     */
    private val revert = PillButton("Revert", null, ButtonKind.SEVERE, heightPx = 32)

    /** The dialog that asks where the LilyPond file goes. */
    private val saver = ScoreSaver(frame)

    /** The export of the score as a MIDI file, with the button that asks for it. */
    private val midi = MidiExport(frame, score = { currentScore() })

    /** The card that holds the LilyPond text: the third of the bottom row. */
    private val lilypond =
        LilyPondOutput(saveTo = { score, relative -> saver.save(score, relative) })
    private val rawToggle = PillButton("Raw taps", Glyph.LAYERS, heightPx = 32)
    private val scoreToggle = PillButton("", Glyph.EYE, heightPx = 32)
    private val soundButton = PillButton("", Glyph.NOTE, heightPx = 32)
    private val playButton = PillButton("", Glyph.PLAY, heightPx = 32)
    private val themeButton = IconButton(Glyph.SUN)
    private val stageRight = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
    private val stageBody = JPanel(GridBagLayout()).apply { isOpaque = false }

    /** The edits the score strip can do to the selected blocks. */
    private val splitButton = PillButton("Split", Glyph.SPLIT, ButtonKind.GHOST, heightPx = 28)
    private val swapButton = PillButton("Swap", Glyph.SWAP, ButtonKind.GHOST, heightPx = 28)
    private val mergeButton = PillButton("Merge", Glyph.MERGE, ButtonKind.GHOST, heightPx = 28)

    /** The way back through the edits of the score strip. */
    private val undoButton = PillButton("", Glyph.UNDO, ButtonKind.GHOST, heightPx = 28)
    private val redoButton = PillButton("", Glyph.REDO, ButtonKind.GHOST, heightPx = 28)

    /** What the strip has selected, or what it just did. */
    private val scoreCaption = CaptionLabel(NotationPanel.NOTHING_SELECTED)

    /**
     * The header of the score strip: the pitch of the selected note, the message, and the
     * edits on the right.
     *
     * The strip says what it is, so the header does not need a title of its own. The gap
     * between the edits and Revert is 4: an empty centre asks for two gaps, so the two
     * halves of it make the 8 px the other parts of the header have.
     */
    private val scoreHeader =
        JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(pitchCell(), BorderLayout.WEST)
            add(scoreCaption, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(4, 0)).apply {
                    isOpaque = false
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                            isOpaque = false
                            add(undoButton)
                            add(redoButton)
                            add(Box.createHorizontalStrut(6))
                            add(splitButton)
                            add(swapButton)
                            add(mergeButton)
                        },
                        BorderLayout.WEST,
                    )
                    add(revert, BorderLayout.EAST)
                },
                BorderLayout.EAST,
            )
        }
    private var showRaw = false
    private var enterGuard = false
    private val metronome = Metronome()
    private val frameTimer = Timer(15) { onFrame() }
    private var session: Session? = null

    /** The beat the guide was last updated for, so it only changes when a new beat starts. */
    private var lastBeatIndex = Int.MIN_VALUE
    private var currentPlayer: Player? = null
    private var calibrating = false
    private val calibrationOffsets = mutableListOf<Double>()

    /** The score the take had right after it ended, so Revert can put it back. */
    private var takeScore: Score? = null

    /** The taps of the round that sets the tempo. */
    private val tapTempo = TapTempo()

    /** The flash of the tap button, so a tap is seen even when the tempo does not move. */
    private val tapFlash = Timer(220) { tapButton.accentOverride = null }.apply { isRepeats = false }

    /**
     * The end of a tap round: the button is no longer armed and the status line goes back.
     *
     * The round ends when the taps stop, so the keys belong to the app again.
     */
    private val tapIdle =
        Timer(TapTempo.IDLE_MS) {
            tapButton.armed = false
            if (session == null && !isPlaying) status.text = IDLE_STATUS
        }.apply { isRepeats = false }

    /** The key of the last tap, so a key that repeats itself is not counted twice. */
    private var lastTapKey = 0

    /** When the last tap happened, in nanoseconds. */
    private var lastTapNanos = 0L

    /** Whether the score strip is shown. */
    private var scoreVisible = true

    /** Whether the 3-feel is on: it only means something in a compound meter. */
    private var threeFeel = false

    /** Whether the meter in the time field is a compound one. */
    private var compoundMeter = false

    /** Whether the settings can be changed right now (a take or a playback owns the window). */
    private var settingsLive = true

    /** Current playback position in ms relative to the start of the score (-1 when not playing). */
    private var playbackPositionMs = -1.0
    private var playbackStartNanos = 0L
    private var isPlaying = false
    private var playbackSound = PlaybackSound.NOTE

    /** The last key press seen, used to recognise X11 auto-repeat. */
    private var lastPress: KeyEvent? = null

    private val ignoredKeys =
        setOf(
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_ALT,
            KeyEvent.VK_ALT_GRAPH,
            KeyEvent.VK_META,
            KeyEvent.VK_CAPS_LOCK,
        )

    init {
        watchClicks()
        wireScoreEditor()
        wireTimeSignature()
        wireThreeFeel()
        wirePitchField()
        wirePitchesPanel()
        minRest.onChange = { minRest.toolTipText = restToolTip() }
        updateThreeFeel()
        // There is no score yet, so the controls that work on one are off.
        updateScoreControls()
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
    private fun setToolTips() {
        bpm.toolTipText =
            "Beats per minute: the dotted quarter in a compound meter (6/8), the note value " +
            "otherwise. Click +/-, scroll, or drag the number."
        tapButton.toolTipText =
            "Set the tempo by tapping: click this button in time, or press any key. " +
            "The tempo follows the taps and settles on the median of the intervals."
        bars.toolTipText = "How many bars to record after the one-bar count-in."
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
     * The rows of the settings card: three rows of cells, each with a caption above it.
     *
     * The rows are built apart from the card that holds them, so the layout can be looked at
     * without opening the window.
     *
     * @return the column of the settings.
     */
    private fun buildSettingsRows(): JComponent {
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

    fun show() {
        status.text = IDLE_STATUS
        setToolTips()

        val rows = buildSettingsRows()
        val startColumn = buildStartColumn()
        val settings =
            Card().apply {
                layout = BorderLayout()
                border = BorderFactory.createEmptyBorder(18, 20, 18, 20)
                add(rows, BorderLayout.CENTER)
                add(startColumn, BorderLayout.EAST)
            }

        rawToggle.isEnabled = false
        rawToggle.toolTipText = "Show your raw key presses as ghosts under the snapped notes. Amber = pressed late, teal = early."
        soundButton.isEnabled = false
        beatDots.isVisible = false
        revert.isEnabled = false
        revert.toolTipText = "Put the score back the way it was when the take ended. Ctrl+Z undoes it."
        val stage = buildStage()
        val bottom = buildBottom()

        val headerRow =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(HeaderBar(), BorderLayout.CENTER)
                add(
                    JPanel(GridBagLayout()).apply {
                        isOpaque = false
                        border = BorderFactory.createEmptyBorder(0, 14, 0, 0)
                        add(themeButton)
                    },
                    BorderLayout.EAST,
                )
            }
        val top =
            JPanel(BorderLayout(0, 14)).apply {
                isOpaque = false
                add(headerRow, BorderLayout.NORTH)
                add(settings, BorderLayout.CENTER)
            }
        frame.contentPane =
            JPanel(BorderLayout(0, 14)).apply {
                background = Theme.bg
                border = BorderFactory.createEmptyBorder(16, 20, 20, 20)
                add(top, BorderLayout.NORTH)
                add(stage, BorderLayout.CENTER)
                add(bottom, BorderLayout.SOUTH)
            }

        start.onClick = { if (session == null) begin() else abort() }
        start.toolTipText = "Start (Ctrl+Enter, or Cmd+Enter on macOS). Esc aborts. Press r for a rest."
        lilypond.relative.onClick = { toggleRelativeOutput() }
        rawToggle.onClick = { toggleRaw() }
        scoreToggle.onClick = { toggleScoreStrip() }
        themeButton.onClick = { applyTheme(!Theme.isDark) }
        calibrateButton.onClick = { if (session == null) calibrate() }
        latencyReset.onClick = { latency.value = 0.0 }
        audioLatencyReset.onClick = { audioLatency.value = 0.0 }
        revert.onClick = { revertScore() }
        tapButton.onClick = {
            // A tap on the button is not a key, so it does not block the next key press.
            lastTapKey = 0
            tapTempoNow()
        }
        playButton.onClick = { togglePlayback() }
        soundButton.onClick = { togglePlaybackSound() }
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(KeyEventDispatcher { e -> handleKey(e) })
        registerShutdownHook()

        updateRawToggle()
        updateScoreToggle()
        updateSoundButton()
        updateScoreHeader()
        applyTheme(Theme.isDark)
        frame.iconImage = logoImage(64)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.pack()
        frame.minimumSize = Dimension(frame.width, frame.height)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        // Nothing should look selected on startup: park focus on the background, and let a click
        // on any empty area take focus away from a text field later on.
        parkFocusOutsideScore(settings, stage, bottom, lilypond.card)
    }

    /**
     * The time signature field with the menu of common meters and the 3-feel button beside it.
     */
    private fun timeCell(): JComponent =
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
     * The pitch of the selected note: an icon and the field the name is typed in.
     */
    private fun pitchCell(): JComponent =
        JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(pitchCaption, BorderLayout.WEST)
            add(pitchField, BorderLayout.CENTER)
        }

    /**
     * Wires the pitch field: it takes a LilyPond name and puts it on the selected note.
     */
    private fun wirePitchField() {
        pitchField.toolTipText = PITCH_TOOLTIP
        pitchField.addActionListener { applyPitchInput() }
    }

    /**
     * Wires the pitches panel: the piano writes a pitch, the button starts the walk and the
     * transpose button moves the whole score.
     *
     * The panel is an editor: it says nothing while a take runs, and while the score plays it
     * only shows what is sounding.
     */
    private fun wirePitchesPanel() {
        pitches.piano.onPitch = { pitch ->
            // The hand is in that octave now: the tracker follows it there.
            tracker.anchorTo(pitch.octave)
            fillPitch(pitch)
        }
        pitches.fill.onClick = { notation.fillMelody() }
        pitches.transpose.onPick = { transposition -> transposeScore(transposition) }
    }

    /**
     * Transposes the score, and says so when it cannot.
     *
     * The transposition is an edit like any other: the strip, the key field, the piano and
     * the LilyPond text all follow it, and Ctrl+Z undoes it. A transposition that would take
     * the piece to a key the app cannot hold changes nothing and says why.
     *
     * @param transposition the interval and the direction the notes move.
     */
    private fun transposeScore(transposition: Transposition) {
        val result = notation.transpose(transposition)
        if (result is TransposeResult.Refused) {
            showMessage(frame, transposeRefused(result.message))
        }
    }

    /**
     * Puts the tracker in the octave of the selected note.
     *
     * The tracker follows the cursor: a block the user picks takes the tracker to its octave.
     * The walk that follows a written pitch does not, or every note of the melody would put
     * the tracker back where it was.
     */
    private fun anchorTracker() {
        val pitch = notation.selectedPitch ?: return
        tracker.anchorTo(pitch.octave)
    }

    /**
     * Puts a pitch on the selected note.
     *
     * When nothing is selected, the walk starts at the first note of the take: a pitch that is
     * written always has a note to land on. The walk starts quietly, so it does not put the
     * tracker back in the octave it was put in.
     *
     * @param pitch the pitch the note sounds.
     */
    private fun fillPitch(pitch: Pitch) {
        if (notation.selectedBlocks == 0) notation.fillMelody(quiet = true)
        notation.setSelectedPitch(pitch)
    }

    /**
     * Puts the name in the pitch field on the selected note.
     *
     * The field takes a LilyPond name (`c'`, `cis'`, `ces'`); a name that cannot be read
     * turns the border of the field red and says what a name looks like. A name that was read
     * is written back in its own spelling, so the field always says the same thing as the
     * LilyPond text.
     *
     * The pitch is written and the cursor moves on to the next note, so the field is emptied:
     * it waits for the next note of the melody.
     */
    private fun applyPitchInput() {
        val text = pitchField.text.trim()
        if (text.isEmpty()) return
        val pitch = Pitch.parse(text)
        if (pitch == null) {
            pitchField.invalid = true
            pitchField.toolTipText = PITCH_ERROR
            return
        }
        pitchField.invalid = false
        pitchField.toolTipText = PITCH_TOOLTIP
        pitchField.text = pitch.lilypond
        if (notation.setSelectedPitch(pitch)) {
            pitchField.text = ""
        }
    }

    /**
     * The pitch field follows the selection: it shows the pitch of the selected note, and waits
     * for a new one.
     *
     * The text of the field is not touched while it has the focus, so a name that is being
     * typed does not jump away under the fingers.
     */
    private fun updatePitchField() {
        val selected = notation.selectedPitch
        pitchField.isEnabled = selected != null
        pitchField.invalid = false
        if (!pitchField.isFocusOwner) {
            pitchField.text = selected?.lilypond ?: ""
        }
    }

    /**
     * Wires the score strip and its header: the edits, the caption and the keys.
     */
    private fun wireScoreEditor() {
        notation.onEdit = { edited -> scoreChanged(edited) }
        notation.onSelectionChange = { move ->
            if (move == SelectionMove.USER) anchorTracker()
            updateScoreHeader()
        }
        splitButton.onClick = { notation.splitSelected() }
        swapButton.onClick = { notation.swapSelected() }
        mergeButton.onClick = { notation.mergeSelected() }
        splitButton.toolTipText = "Split the selected block in half (S)"
        swapButton.toolTipText = "Swap the two selected blocks (X)"
        mergeButton.toolTipText = "Merge the selected blocks into one (M)"
        undoButton.onClick = { notation.undo() }
        redoButton.onClick = { notation.redo() }
        undoButton.toolTipText = "Undo the last edit (Ctrl+Z)"
        redoButton.toolTipText = "Redo the last undone edit (Ctrl+Shift+Z)"
    }

    /**
     * The parts of the window that work on the score.
     *
     * The strip, the pitch field, the pitches panel and the edit buttons: they all act on the
     * selected blocks, so the selection has to survive a click on any of them.
     */
    private val editorParts: Set<Component>
        get() =
            setOf(
                notation,
                pitchField,
                pitches,
                pitches.piano,
                pitches.fill,
                pitches.transpose,
                splitButton,
                swapButton,
                mergeButton,
                undoButton,
                redoButton,
            )

    /**
     * Watches the clicks of the window.
     *
     * The mouse events of AWT do not bubble up to the panels of a window, so watching the
     * panels is not enough to see every click: the toolkit is watched instead.
     */
    private fun watchClicks() {
        watchers += WeakReference(this)
    }

    /**
     * The selection belongs to the editor: a click on anything else means the user has moved
     * on to something else, and a ring around a block would only be in the way.
     */
    private fun clickOutsideScore(component: Component?) {
        if (!belongsToTheApp(component)) return
        if (component !in editorParts) notation.clearSelection()
    }

    /**
     * Whether a component is a part of the app.
     *
     * A click in another window (a dialog) is not a click in the app: the dialog asks for
     * something else, and the score editor waits for the user to come back to it.
     *
     * @param component the component that was clicked, or null when there was none.
     * @return true when the component belongs to the app.
     */
    private fun belongsToTheApp(component: Component?): Boolean {
        val window = component?.let { SwingUtilities.getWindowAncestor(it) }
        return window == null || window === frame
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
     * Wires the 3-feel button: it turns the finer click on and off.
     */
    private fun wireThreeFeel() {
        threeFeelButton.onClick = {
            threeFeel = !threeFeel
            updateThreeFeel()
        }
    }

    /**
     * The 3-feel button: it works only in a compound meter, and it shows whether it is on.
     *
     * The button stays in place in every meter, so the row never moves; it is greyed out when
     * the meter is simple, and the setting is remembered when the meter changes back.
     */
    private fun updateThreeFeel() {
        threeFeelButton.isEnabled = compoundMeter && settingsLive
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
     * Parks the focus on the background, and lets a click on any empty area take the focus
     * away from a text field later on.
     *
     * The score strip keeps the keyboard while it is being edited: its keys split, swap and
     * merge the selected blocks. The piano keeps it too: it writes the pitch of the selected
     * note, and the keys of the tracker do the same.
     */
    private fun parkFocusOutsideScore(
        settings: JComponent,
        stage: JComponent,
        bottom: JComponent,
        outputCard: JComponent,
    ) {
        val background = frame.contentPane as JComponent
        val keepsFocus =
            setOf(
                notation,
                splitButton,
                swapButton,
                mergeButton,
                undoButton,
                redoButton,
                pitches.piano,
            )
        val release =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.component in keepsFocus) return
                    background.requestFocusInWindow()
                }
            }
        for (c in listOf(background, settings, stage, bottom, outputCard)) c.addMouseListener(release)
        SwingUtilities.invokeLater { background.requestFocusInWindow() }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                currentPlayer?.close()
                metronome.close()
                timeline.detach()
                notation.detach()
            },
        )
    }

    private fun applyTheme(dark: Boolean) {
        Theme.set(dark)
        applyDialogTheme()
        frame.contentPane.background = Theme.bg
        lilypond.refreshTheme()
        time.refreshTheme()
        pitchField.refreshTheme()
        updateRawToggle()
        updateScoreToggle()
        updateSoundButton()
        themeButton.glyph = if (dark) Glyph.SUN else Glyph.MOON
        themeButton.toolTipText = if (dark) "Switch to light theme" else "Switch to dark theme"
        frame.repaint()
    }

    private fun toggleRaw() {
        showRaw = !showRaw
        timeline.showRaw = showRaw
        updateRawToggle()
    }

    private fun updateRawToggle() {
        rawToggle.accentOverride = if (showRaw) Theme.accent else null
    }

    /** Shows or hides the score strip. The timeline takes the room it leaves. */
    private fun toggleScoreStrip() {
        scoreVisible = !scoreVisible
        notation.isVisible = scoreVisible
        scoreHeader.isVisible = scoreVisible
        updateScoreToggle()
        stageBody.revalidate()
        stageBody.repaint()
    }

    private fun updateScoreToggle() {
        scoreToggle.accentOverride = if (scoreVisible) Theme.accent else null
        scoreToggle.toolTipText =
            if (scoreVisible) "Hide the score strip" else "Show the score strip"
    }

    /** Starts or stops playback of the current score. */
    private fun togglePlayback() {
        if (isPlaying) stopPlayback() else startPlayback()
    }

    private fun startPlayback() {
        val score = currentScore() ?: return
        // A playback has the window to itself, so a tap round cannot still be listening.
        endTapRound(restoreStatus = true)
        // The playhead sweeps the strip, so a ring around a block would only be in the way.
        notation.clearSelection()
        // Open the audio line before the playback clock starts: opening it can take tens of
        // milliseconds, and an origin sampled before that made the first note start late.
        val player = Player(audioLatency.value, playbackSound)
        currentPlayer = player
        isPlaying = true
        setPlaybackControlsEnabled(true)
        updatePitchesPanel()
        playbackPositionMs = 0.0
        updatePlayButton()
        timeline.score = score
        timeline.playbackPositionMs = 0.0
        notation.playbackPositionMs = 0.0
        timeline.resetScroll() // Long scores start at the beginning, wherever the take left the view.
        val origin =
            player.play(
                score,
                onDone = {
                    SwingUtilities.invokeLater {
                        if (isPlaying) stopPlayback()
                    }
                },
            )
        if (origin == 0L) {
            stopPlayback()
            return
        }
        playbackStartNanos = origin
        frameTimer.start()
    }

    private fun stopPlayback() {
        isPlaying = false
        currentPlayer?.close()
        currentPlayer = null
        frameTimer.stop()
        playbackPositionMs = -1.0
        timeline.playbackPositionMs = -1.0
        notation.playbackPositionMs = -1.0
        pitches.piano.playbackPositionMs = -1.0
        // While a take is being recorded the settings stay off; they come back with the window.
        if (session == null) setPlaybackControlsEnabled(false)
        updatePlayButton()
        updatePitchesPanel()
    }

    private fun updatePlayButton() {
        playButton.glyph = if (isPlaying) Glyph.STOP else Glyph.PLAY
        playButton.toolTipText = if (isPlaying) "Stop playback" else "Play the score"
    }

    /** Toggles the playback sound between the score's pitch and a metronome-style click. */
    private fun togglePlaybackSound() {
        if (isPlaying) return
        playbackSound =
            if (playbackSound == PlaybackSound.NOTE) PlaybackSound.BEAT else PlaybackSound.NOTE
        updateSoundButton()
    }

    private fun updateSoundButton() {
        val beat = playbackSound == PlaybackSound.BEAT
        soundButton.glyph = if (beat) Glyph.METRONOME else Glyph.NOTE
        soundButton.accentOverride = if (beat) Theme.accent else null
        soundButton.toolTipText =
            if (beat) {
                "Playback sound: Beat (a click on every note onset). Click for the score's pitch."
            } else {
                "Playback sound: Note (the score's pitch). Click for a metronome-style click."
            }
    }

    /** The score Play uses: the edited one when there is a take, otherwise whatever the strip shows. */
    private fun currentScore(): Score? = notation.score ?: takeScore

    private fun relayoutStageRight() {
        stageRight.revalidate()
        stageRight.repaint()
    }

    /** The settings that describe the next take; they do not apply while a score is playing. */
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

    private fun setControlsEnabled(on: Boolean) {
        settingsLive = on
        settingControls.forEach { it.isEnabled = on }
        keySetting.setEnabled(on)
        // The copy and save buttons follow the score, and the card owns them: there is
        // nothing to copy and nothing to save when there is no score.
        lilypond.relative.isEnabled = on
        calibrateButton.isEnabled = on
        playButton.isEnabled = on && currentScore() != null
        soundButton.isEnabled = on && currentScore() != null
        updateScoreControls()
        updateThreeFeel()
    }

    /**
     * Applies what the playing state does to the controls.
     *
     * The score that is playing is fixed, and the settings describe the next take, so they are
     * disabled while it plays: a changed tempo or latency would not be heard until the next
     * playback. The header button stays live — it is the **Stop** button then.
     */
    private fun setPlaybackControlsEnabled(playing: Boolean) {
        settingsLive = !playing
        settingControls.forEach { it.isEnabled = !playing }
        keySetting.setEnabled(!playing)
        calibrateButton.isEnabled = !playing
        start.isEnabled = !playing
        soundButton.isEnabled = !playing && currentScore() != null
        playButton.isEnabled = playing || currentScore() != null
        updateScoreControls()
        updateThreeFeel()
    }

    /**
     * The controls that work on the score follow the score.
     *
     * A take throws the score away and a playback fixes it, so there is nothing to edit then:
     * the transpose button and the fill button are off until the take is over. They are also
     * off when there is no score at all, so a click never lands on a button that would do
     * nothing.
     *
     * The MIDI export only reads the score, so it stays on while the score plays.
     *
     * The state is asked of the window and not remembered: a take ends by publishing the
     * score it recorded, and the controls have to come back at that moment.
     */
    private fun updateScoreControls() {
        val live = session == null && !isPlaying && currentScore() != null
        pitches.setEditingEnabled(live)
        midi.button.isEnabled = currentScore() != null
    }

    /**
     * The column beside the settings: the legend above the Start button.
     *
     * The legend says what the colours of the raw taps mean and which keys play the piano,
     * and the Start button stays in the middle of the column.
     */
    private fun buildStartColumn(): JComponent = buildStartColumn(legend, start)

    /**
     * The bottom row of the window: the pitches panel and the LilyPond text.
     *
     * The pitches panel takes two thirds of the row and the LilyPond card the rest. The two
     * cards ask for the width they get, so the window keeps the size it has.
     */
    private fun buildBottom(): JComponent = buildBottom(buildPitchesCard(pitches), lilypond.card)

    /**
     * The stage: the status line, the recording timeline and the score strip below it.
     * Both panels share one grid, so the strip lines up with the timeline.
     */
    private fun buildStage(): JComponent {
        // The buttons of the timeline live in its header: the raw taps, the strip, the sound,
        // the MIDI file, the playback and the beat dots.
        stageRight.apply {
            isOpaque = false
            add(rawToggle)
            add(scoreToggle)
            add(soundButton)
            add(midi.button)
            add(playButton)
            add(beatDots)
        }
        val stageTop =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(status, BorderLayout.CENTER)
                add(stageRight, BorderLayout.EAST)
            }
        stageBody.add(
            timeline,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            },
        )
        stageBody.add(
            scoreHeader,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                // Room between the timeline and the strip, so they read as two sections.
                insets = Insets(14, 0, 6, 0)
            },
        )
        stageBody.add(
            notation,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 2
                weightx = 1.0
                fill = GridBagConstraints.BOTH
            },
        )
        return Card().apply {
            layout = BorderLayout(0, 12)
            border = BorderFactory.createEmptyBorder(14, 16, 16, 16)
            add(stageTop, BorderLayout.NORTH)
            add(stageBody, BorderLayout.CENTER)
        }
    }

    /**
     * Writes the octaves of the text in full, or relative to the note before.
     *
     * The text says in a comment which way it is written, so a fragment that is copied into
     * a file says what it is.
     */
    private fun toggleRelativeOutput() {
        lilypond.toggleRelative(currentScore())
    }

    /**
     * Puts the score in a key.
     *
     * The notes are spelled the way the key wants them, and that is an edit like any other:
     * it can be undone, and Revert puts the take back the way it was recorded.
     *
     * @param key the key the piece is in.
     */
    private fun setKey(key: KeySignature) {
        // The score is respelled in the strip, and that is an edit like any other.
        notation.setKey(key)
        updateKeyViews()
    }

    /** The parts of the window that follow the key: the piano, the tracker and the legend. */
    private fun updateKeyViews() {
        val key = keySetting.key
        pitches.piano.key = key
        pitches.transpose.key = key
        tracker.key = key
        legend.key = key
    }

    /** The score was edited in the strip: the output follows, and Revert comes alive. */
    private fun scoreChanged(edited: Score) {
        if (isPlaying) stopPlayback()
        keySetting.tell(edited.config.key)
        updateKeyViews()
        timeline.score = edited
        lilypond.show(edited)
        revert.isEnabled = edited != takeScore
        updateScoreHeader()
    }

    /** The header follows the selection: the buttons say what can be done with it. */
    private fun updateScoreHeader() {
        splitButton.isEnabled = notation.canSplit
        swapButton.isEnabled = notation.canSwap
        mergeButton.isEnabled = notation.canMerge
        undoButton.isEnabled = notation.canUndo
        redoButton.isEnabled = notation.canRedo
        scoreCaption.text = notation.caption
        // The tooltip carries the whole message, so a long one is still readable.
        scoreCaption.toolTipText = notation.caption
        updatePitchField()
        updatePitchesPanel()
    }

    /**
     * The pitches panel follows the strip: the score it shows, the note that is selected and
     * where the cursor is among the notes.
     *
     * The panel is quiet while the app is recording or playing: there is no score to edit
     * then, and the keys would only invite clicks that do nothing.
     */
    private fun updatePitchesPanel() {
        pitches.piano.score = notation.score
        pitches.piano.selectedPitch = notation.selectedPitch
        pitches.piano.interactive = session == null && !isPlaying
        tracker.sayOctave()
        pitches.setCounter(notation.cursorText)
    }

    /** Puts the score back the way it was when the take ended. The revert can be undone. */
    private fun revertScore() {
        val original = takeScore ?: return
        notation.revertTo(original)
    }

    /** Shows the score of a finished take in the strip. */
    private fun showScore(taken: Score) {
        takeScore = taken
        // A new take is a fresh score: the tracker goes back to the middle of the keyboard.
        tracker.clear()
        keySetting.tell(taken.config.key)
        updateKeyViews()
        notation.score = taken
        timeline.score = taken
        lilypond.show(taken)
        revert.isEnabled = false
        playButton.isEnabled = true
        soundButton.isEnabled = true
        updatePlayButton()
        updateScoreControls()
        updateScoreHeader()
    }

    /** Throws the score away: there is nothing to edit until the next take. */
    private fun clearScore() {
        takeScore = null
        notation.score = null
        timeline.score = null
        revert.isEnabled = false
        playButton.isEnabled = false
        soundButton.isEnabled = false
        updatePlayButton()
        updateScoreControls()
        updateScoreHeader()
        stopPlayback()
    }

    /**
     * Says what is wrong with the inputs of the window, when something is wrong with them.
     *
     * Every input the take depends on is read here: the time signature, the name of the key
     * and the name of the pitch that is waiting in the field.
     *
     * @return the problem, or null when every input can be used.
     */
    private fun inputProblem(): Message? = InputCheck.message(time.text, keySetting.fault, pitchField.text)

    private fun begin() {
        // Every input is read before the take begins: a name that cannot be read is a name
        // that is still being typed, or a typo, and the take should not run on a setting the
        // app does not understand.
        val problem = inputProblem()
        if (problem != null) {
            showMessage(frame, problem)
            return
        }
        // The name in the field is the truth: a key that is typed but not yet applied is
        // still the key the take is recorded in.
        keySetting.apply()
        // A take has the keys to itself, so a tap round cannot still be listening.
        endTapRound(restoreStatus = false)
        val config =
            try {
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
            } catch (e: IllegalArgumentException) {
                showMessage(
                    frame,
                    Message(
                        "Invalid settings",
                        e.message ?: "The settings are not valid.",
                        "Fix the setting and try again.",
                        Glyph.BOLT,
                        Theme.rec,
                    ),
                )
                return
            }
        // The last option of the smallest rest is not a length: it says that no silence is
        // ever written, so the length does not matter there.
        val fraction =
            when (minRest.selectedIndex) {
                0 -> 1
                1 -> 2
                else -> 4
            }
        val minRestCells = maxOf(1, config.cellsPerPulse / fraction)

        // The musical beat: the note value in a simple meter, the dotted quarter in a compound one.
        val pulses = config.timeSignature.pulsesPerBar
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        val clickNanos = (config.clickMs * 1_000_000.0).roundToLong()
        val startNanos = System.nanoTime() + 800_000_000L
        val origin = startNanos + pulses * beatNanos
        val end = origin + config.bars.toLong() * pulses * beatNanos

        val s = Session(config, minRestCells, startNanos, origin, end, beatNanos)
        session = s
        timeline.session = s
        updatePitchesPanel()
        lastPress = null
        lilypond.clear()
        clearScore()
        rawToggle.isEnabled = false
        beatDots.isVisible = true
        relayoutStageRight()
        setControlsEnabled(false)
        start.text = "Stop"
        start.glyph = Glyph.STOP
        start.kind = ButtonKind.DANGER
        beatDots.beats = pulses
        beatDots.active = -1
        beatDots.mood = Mood.COUNT_IN
        status.mood = Mood.COUNT_IN
        status.text = "Get ready..."
        lastBeatIndex = Int.MIN_VALUE

        s.ticker =
            thread(isDaemon = true, name = "metronome") {
                metronome.run(
                    startNanos,
                    clickNanos,
                    config.totalClicks,
                    config.clicksPerBeat,
                    config.clicksPerBar,
                    audioLatency.value,
                )
            }
        frameTimer.start()
    }

    private fun calibrate() {
        // A calibration beeps over a take or a playback, so it only runs on an idle window.
        if (session != null || isPlaying) return

        // The calibration has the keys to itself, so a tap round cannot still be listening.
        endTapRound(restoreStatus = true)

        // The calibration runs on a fixed grid: its own tempo, its own length and its own
        // count-in. The measurement does not depend on the take's settings, and the grid is
        // slow enough to leave room around every beat for the whole output latency.
        val config =
            Config(
                bpm = CALIBRATION_BPM,
                timeSignature = CALIBRATION_SIGNATURE,
                subdivisions = 1,
                bars = CALIBRATION_BARS,
                latencyMs = 0.0,
            )

        val pulses = config.timeSignature.pulsesPerBar
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        val clickNanos = (config.clickMs * 1_000_000.0).roundToLong()
        val startNanos = System.nanoTime() + 800_000_000L
        val origin = startNanos + pulses * beatNanos
        val end = origin + config.bars.toLong() * pulses * beatNanos

        val s = Session(config, 1, startNanos, origin, end, beatNanos)
        session = s
        // Calibration is about the beeps only: the timeline stays empty and the beat dots are
        // hidden, so that a visual guide that is out of sync with the audio cannot mislead the
        // user (and cannot be mistaken for the audio when the taps are measured).
        timeline.session = null
        lilypond.clear()
        clearScore()
        rawToggle.isEnabled = false
        calibrating = true
        calibrationOffsets.clear()
        relayoutStageRight()
        setControlsEnabled(false)
        start.text = "Calibrating..."
        start.glyph = Glyph.STOP
        start.kind = ButtonKind.DANGER
        beatDots.isVisible = false
        beatDots.beats = pulses
        beatDots.active = -1
        beatDots.mood = Mood.COUNT_IN
        status.mood = Mood.COUNT_IN
        status.text = "Calibrating \u00B7 tap with the beeps"
        lastBeatIndex = Int.MIN_VALUE

        s.ticker =
            thread(isDaemon = true, name = "metronome") {
                metronome.run(
                    startNanos,
                    clickNanos,
                    config.totalClicks,
                    config.clicksPerBeat,
                    config.clicksPerBar,
                    audioLatency.value,
                )
            }
        frameTimer.start()
    }

    private fun finishCalibration() {
        val s = session ?: return
        stop(s)

        if (calibrationOffsets.isNotEmpty()) {
            // The clicks were already shifted by the configured latency, so the measured offset
            // is the remaining error: add it to the current value.
            audioLatency.value = calibratedLatency(audioLatency.value, calibrationOffsets)
            val corrected = audioLatency.value
            status.mood = Mood.DONE
            status.text = "Calibrated: ${corrected.roundToLong()} ms audio latency."
            showMessage(
                frame,
                Message(
                    "Calibration complete",
                    "${corrected.roundToLong()} ms",
                    "The click is now shifted earlier by this much. The measurement includes your own " +
                        "reaction time, so lower it a few steps if the click sounds early.",
                    Glyph.CHECK,
                ),
            )
        } else {
            status.mood = Mood.STOPPED
            status.text = "Calibration cancelled."
            showMessage(
                frame,
                Message(
                    "Calibration cancelled",
                    "No taps recorded",
                    "The audio latency was left unchanged.",
                    Glyph.PAUSE,
                    Theme.warn,
                ),
            )
        }

        calibrating = false
        calibrationOffsets.clear()
        setControlsEnabled(true)
        start.text = "Start"
        start.glyph = Glyph.PLAY
        start.kind = ButtonKind.PRIMARY
    }

    /**
     * Updates the beat dots and the status text from the session clock.
     *
     * The guide follows the nominal beat, not the moment the click is written to the audio line:
     * the line is written early by the audio latency, so a guide that followed the write would
     * sit that much ahead of the playhead and of the sound the listener actually hears.
     */
    private fun updateBeatDisplay(s: Session) {
        val index = beatIndexAt(System.nanoTime(), s.startNanos, s.beatNanos)
        if (index == lastBeatIndex) return
        lastBeatIndex = index
        val pulses = s.config.timeSignature.pulsesPerBar
        if (index < 0) {
            status.mood = Mood.COUNT_IN
            beatDots.mood = Mood.COUNT_IN
            beatDots.active = -1
            status.text = "Get ready..."
        } else if (index < pulses) {
            status.mood = Mood.COUNT_IN
            beatDots.mood = Mood.COUNT_IN
            beatDots.active = index
            // While calibrating there is no beat count: a beat number is a claim about when the
            // beat is, and while the audio is late that claim is wrong.
            status.text = if (calibrating) "Get ready..." else "Count-in: ${pulses - index}"
        } else {
            val r = index - pulses
            status.mood = Mood.REC
            beatDots.mood = Mood.REC
            beatDots.active = r % pulses
            status.text =
                if (calibrating) {
                    calibrationStatus()
                } else {
                    "Recording \u00B7 bar ${r / pulses + 1}/${s.config.bars}, beat ${r % pulses + 1}"
                }
        }
    }

    /** The calibration status: a count of the taps, never a beat number. */
    private fun calibrationStatus(): String {
        val taps = calibrationOffsets.size
        return if (taps == 0) {
            "Calibrating \u00B7 tap with the beeps"
        } else {
            "Calibrating \u00B7 $taps taps"
        }
    }

    private fun onFrame() {
        val s = session
        if (s != null) {
            timeline.follow() // Repaints the timeline (including playhead)
            status.repaint() // keeps the dot pulsing
            updateBeatDisplay(s)
            if (System.nanoTime() >= s.endNanos) finish()
        }

        if (isPlaying) {
            val score = currentScore()
            if (score == null) {
                stopPlayback()
            } else {
                val posMs = (System.nanoTime() - playbackStartNanos) / 1e6
                val totalMs = score.config.totalUnits * score.config.unitMs
                val clamped = posMs.coerceIn(0.0, totalMs)
                playbackPositionMs = clamped
                timeline.playbackPositionMs = clamped
                notation.playbackPositionMs = clamped
                pitches.piano.playbackPositionMs = clamped
                // Follow after the position is updated, or the grid scrolls to a stale spot.
                timeline.follow()
                if (clamped >= totalMs) stopPlayback()
            }
        }
    }

    /**
     * A tap on the tap button: the tempo follows the taps.
     *
     * The button flashes on every tap, so a tap is seen even when it does not move the tempo
     * yet - the first tap of a round never does. The button stays armed while the round
     * listens, so the user sees that the key presses count as taps.
     */
    private fun tapTempoNow() {
        val now = System.nanoTime()
        tapButton.accentOverride = Theme.accent
        tapFlash.restart()
        tapButton.armed = true
        tapIdle.restart()
        val tempo = tapTempo.tap(now)
        if (tempo == null) {
            // The first tap of a round: it only starts the round.
            status.text = TAP_STATUS
            return
        }
        bpm.value = tempo.toDouble()
    }

    /**
     * Ends the tap round: the taps are forgotten and the keys belong to the app again.
     *
     * @param restoreStatus true when the status line goes back to the idle message.
     */
    private fun endTapRound(restoreStatus: Boolean) {
        tapTempo.reset()
        tapButton.armed = false
        tapButton.accentOverride = null
        tapFlash.stop()
        tapIdle.stop()
        if (restoreStatus && session == null && !isPlaying) status.text = IDLE_STATUS
    }

    /**
     * Whether a key press can be a tap at all: a plain key, pressed outside a text field.
     *
     * @param e the key event.
     * @return true when the key could be a tap.
     */
    private fun canTap(e: KeyEvent): Boolean {
        if (e.id != KeyEvent.KEY_PRESSED) return false
        if (e.isControlDown || e.isMetaDown || e.isAltDown) return false
        if (e.keyCode in ignoredKeys) return false
        if (e.keyCode == KeyEvent.VK_ESCAPE) return false
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return owner !is JTextComponent
    }

    /**
     * Whether a key press is a tap of the tempo.
     *
     * Only while the round is listening - the button was clicked and the last tap is not too
     * long ago - and never while a text field has the focus: there the keys are text, not
     * taps. A key that repeats itself is not a tap either.
     *
     * @param e the key event.
     * @return true when the key press counts as a tap.
     */
    private fun isTapKey(e: KeyEvent): Boolean {
        if (!canTap(e)) return false
        val now = System.nanoTime()
        if (!tapTempo.isListening(now)) return false
        if (isAutoRepeat(e)) return false
        // A held key repeats itself: two presses of the same key that close together are one tap.
        if (e.keyCode == lastTapKey && now - lastTapNanos < REPEAT_GUARD_NANOS) return false
        lastTapKey = e.keyCode
        lastTapNanos = now
        lastPress = e
        return true
    }

    /**
     * Handles a tracker key: it writes a pitch, or it moves the octave the keys play.
     *
     * The tracker keys are the editor's: while a take is running every key is a tap, and
     * while the score plays they do nothing.
     *
     * @param e the key event.
     * @return true when the key was a tracker key and was used.
     */
    private fun handleTrackerKey(e: KeyEvent): Boolean {
        if (isAutoRepeat(e)) return false
        return tracker.handleKey(e)
    }

    /** Receives every key event in the window; while recording it consumes all of them. */
    private fun handleKey(e: KeyEvent): Boolean {
        // Ctrl/Cmd+Enter starts a take. The chord is never recorded as a note while playing.
        if (e.keyCode == KeyEvent.VK_ENTER) {
            val chord = e.isControlDown || e.isMetaDown
            // A take does not start over a playback: the Start button is off while it plays.
            val idle = session == null && !isPlaying
            if (chord && e.id == KeyEvent.KEY_PRESSED && idle) {
                begin()
                // If begin() failed (invalid settings), session stays null and we fall through
                // to `session ?: return false`, letting the event propagate unconsumed.
                if (session != null) enterGuard = true
                return true
            }
            if (session != null && (chord || enterGuard)) {
                if (e.id == KeyEvent.KEY_RELEASED) enterGuard = false
                return true
            }
        }

        // While the round is listening, a key press is a tap of the tempo. A take has the keys
        // to itself, so this only happens when the app is idle.
        if (session == null && isTapKey(e)) {
            tapTempoNow()
            return true
        }

        // Escape gives up a round that is listening: the keys belong to the app again.
        if (session == null && e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ESCAPE) {
            if (tapTempo.isListening(System.nanoTime())) {
                endTapRound(restoreStatus = true)
                return true
            }
        }

        // The tracker keys play the piano: they write a pitch on the selected note. They do
        // nothing while a take runs (every key is a tap then) or while the score plays.
        if (session == null && !isPlaying && handleTrackerKey(e)) return true

        val s = session ?: return false
        val now = System.nanoTime()

        // Calibration mode: record tap offsets instead of normal recording
        if (calibrating) {
            if (e.keyCode == KeyEvent.VK_ESCAPE && e.id == KeyEvent.KEY_PRESSED) {
                abort()
            } else if (e.id == KeyEvent.KEY_PRESSED && !isAutoRepeat(e) && e.keyCode !in ignoredKeys) {
                lastPress = e
                val ms = (now - s.originNanos) / 1e6
                val beatMs = 60_000.0 / s.config.bpm
                val totalBeats = s.config.bars * s.config.timeSignature.pulsesPerBar
                calibrationOffsetMs(ms, beatMs, totalBeats)?.let {
                    calibrationOffsets += it
                    status.text = calibrationStatus()
                }
            }
            return true
        }

        return when {
            e.id == KeyEvent.KEY_TYPED -> {
                true
            }

            e.keyCode == KeyEvent.VK_ESCAPE -> {
                if (e.id == KeyEvent.KEY_PRESSED) abort()
                true
            }

            e.keyCode in ignoredKeys -> {
                true
            }

            now >= s.endNanos -> {
                finish()
                true
            }

            else -> {
                val ms = (now - s.originNanos) / 1e6
                when (e.id) {
                    KeyEvent.KEY_PRESSED -> {
                        // Auto-repeat sends the same press again; count it only once.
                        if (!isAutoRepeat(e)) {
                            lastPress = e
                            if (e.keyCode == KeyEvent.VK_R) {
                                s.rests += ms
                            } else if (e.keyCode !in s.held) {
                                s.held[e.keyCode] = ms
                            }
                        }
                    }

                    KeyEvent.KEY_RELEASED -> {
                        // repeats are ignored
                        if (!isAutoRepeat(e)) {
                            val down = s.held.remove(e.keyCode)
                            if (down != null) s.finished += HeldNote(down, ms)
                        }
                    }
                }
                true
            }
        }
    }

    /**
     * On X11/Linux a held key produces release+press pairs with identical timestamps.
     * Such a press (or its matching release) repeats the previous one and is not a new press.
     *
     * The check is local to this window, so key events of other applications cannot be mistaken
     * for a repeat of ours.
     */
    private fun isAutoRepeat(e: KeyEvent): Boolean {
        val previous = lastPress ?: return false
        return previous.keyCode == e.keyCode && previous.`when` == e.`when`
    }

    private fun finish() {
        val s = session ?: return
        if (calibrating) {
            finishCalibration()
            return
        }
        val endMs = (s.endNanos - s.originNanos) / 1e6
        val played = ArrayList<HeldNote>(s.finished)
        for (down in s.held.values) played += HeldNote(down, endMs)
        for (rest in s.rests) played += HeldNote(rest, rest, TapKind.REST)
        s.held.clear()
        s.rests.clear()
        stop(s)

        val result = transcribeHeld(played, s.config, s.minRestCells)
        showScore(result.score)
        s.snapped = result.snapped
        rawToggle.isEnabled = result.snapped.isNotEmpty()
        val off = result.meanOffsetMs
        status.mood = Mood.DONE
        status.text =
            if (off != null && played.size >= 4 && abs(off) >= 15) {
                // The offset is the error that is left after the configured latency, so the
                // corrected latency is the configured one plus that error.
                val suggested = (latency.value + off).roundToLong()
                "Done. You pressed ~${abs(off).roundToLong()} ms ${if (off > 0) "late" else "early"}; try latency $suggested."
            } else {
                "Done."
            }
        relayoutStageRight()
    }

    private fun abort() {
        val s = session ?: return
        stop(s)
        clearScore()
        if (calibrating) {
            calibrating = false
            calibrationOffsets.clear()
            status.mood = Mood.STOPPED
            status.text = "Calibration cancelled."
            return
        }
        status.mood = Mood.STOPPED
        status.text = "Aborted."
    }

    private fun stop(s: Session) {
        session = null
        s.done = true
        frameTimer.stop()
        s.ticker.interrupt()
        s.ticker.join(300)
        stopPlayback()
        setControlsEnabled(true)
        updatePitchesPanel()
        start.text = "Start"
        start.glyph = Glyph.PLAY
        start.kind = ButtonKind.PRIMARY
        beatDots.active = -1
        beatDots.mood = Mood.IDLE
        beatDots.isVisible = false
        relayoutStageRight()
        timeline.repaint()
    }
}
