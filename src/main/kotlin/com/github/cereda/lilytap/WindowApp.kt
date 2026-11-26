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
import kotlin.math.abs
import kotlin.math.roundToLong

/** What the status line says when the window waits for a take. */
private const val IDLE_STATUS = "Set the tempo, press Start, then play with any key"

/**
 * What the pitch field says about itself.
 *
 * The field takes the name of the note the way LilyPond writes it: a letter, an optional
 * accidental and the octave marks. LilyPond puts `c` in the octave below middle C, so
 * middle C is `c'` there.
 */
private val PITCH_TOOLTIP =
    tooltip(
        "The pitch of the selected note, written the way LilyPond writes it.",
        "c' (middle C), cis' (sharp), ces' (flat), cisis' (double sharp), c (lower), c'' (higher).",
        "Press Enter to apply it.",
    )

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

    /** The settings card: the controls that describe the next take. */
    private val settingsCard = SettingsCard(onKey = { key -> setKey(key) })

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
    private val frameTimer = Timer(15) { onFrame() }
    private var session: Session? = null

    /** The beat the guide was last updated for, so it only changes when a new beat starts. */
    private var lastBeatIndex = Int.MIN_VALUE

    /** The guard against the keys that repeat themselves. */
    private val autoRepeat = AutoRepeat()

    /** The calibration round: the taps that answer the beeps. */
    private val calibration = Calibration()

    /** The recorder: the take that runs, the metronome and the keys of the take. */
    private val recorder =
        TakeRecorder(
            autoRepeat = autoRepeat,
            calibrationStatus = { calibration.status() },
        )

    /** The playback of the score: the player, the sound and the place the playhead is at. */
    private val playback = Playback()

    /** The score the take had right after it ended, so Revert can put it back. */
    private var takeScore: Score? = null

    /**
     * How the last take was played: the marks of the strip come from it.
     *
     * The marks belong to the take, so they are there while the score is the score of the
     * take. An edit of the score takes them away, and a Revert - which puts the take back -
     * brings them back.
     */
    private var takeTiming: TimingReport? = null

    /**
     * The round that sets the tempo by tapping.
     *
     * The round owns the taps and the clocks around them; the window tells it what the
     * status line says and when it may go back to the idle message.
     */
    private val tapRound =
        TapTempoRound(
            button = settingsCard.tapButton,
            bpm = settingsCard.bpm,
            say = { text -> status.text = text },
            restore = {
                if (session == null && !playback.isPlaying) status.text = IDLE_STATUS
            },
        )

    /** Whether the score strip is shown. */
    private var scoreVisible = true

    init {
        playback.onDone = {
            SwingUtilities.invokeLater {
                if (playback.isPlaying) stopPlayback()
            }
        }
        watchClicks()
        wireScoreEditor()
        wirePitchField()
        wirePitchesPanel()
        // There is no score yet, so the controls that work on one are off.
        updateScoreControls()
    }

    /**
     * The rows of the settings card: three rows of cells, each with a caption above it.
     *
     * The rows are built apart from the card that holds them, so the layout can be looked at
     * without opening the window.
     *
     * @return the column of the settings.
     */
    private fun buildSettingsRows(): JComponent = settingsCard.rows()

    fun show() {
        status.text = IDLE_STATUS
        settingsCard.setToolTips()

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
        rawToggle.toolTipText =
            tooltip(
                "Show your raw key presses as ghosts under the snapped notes.",
                "Amber = pressed late, teal = early.",
            )
        soundButton.isEnabled = false
        beatDots.isVisible = false
        revert.isEnabled = false
        revert.toolTipText =
            tooltip(
                "Put the score back the way it was when the take ended.",
                "Ctrl+Z undoes it.",
            )
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
        start.toolTipText =
            tooltip(
                "Start (Ctrl+Enter, or Cmd+Enter on macOS).",
                "Esc aborts. Press r for a rest.",
            )
        lilypond.relative.onClick = { toggleRelativeOutput() }
        rawToggle.onClick = { toggleRaw() }
        scoreToggle.onClick = { toggleScoreStrip() }
        themeButton.onClick = { applyTheme(!Theme.isDark) }
        settingsCard.calibrateButton.onClick = { if (session == null) calibrate() }
        settingsCard.latencyReset.onClick = { settingsCard.latency.value = 0.0 }
        settingsCard.audioLatencyReset.onClick = { settingsCard.audioLatency.value = 0.0 }
        revert.onClick = { revertScore() }
        settingsCard.tapButton.onClick = {
            // A tap on the button is not a key, so it does not block the next key press.
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
            showMessage(frame, transposeRefused(result.lines))
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
            pitchField.toolTipText = tooltipParagraphs(listOf(PITCH_ERROR_TITLE) + pitchErrorLines(text))
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
                playback.close()
                recorder.close()
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
        settingsCard.time.refreshTheme()
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
        if (playback.isPlaying) stopPlayback() else startPlayback()
    }

    private fun startPlayback() {
        val score = currentScore() ?: return
        // A playback has the window to itself, so a tap round cannot still be listening.
        endTapRound(restoreStatus = true)
        // The playhead sweeps the strip, so a ring around a block would only be in the way.
        notation.clearSelection()
        // Open the audio line before the playback clock starts: opening it can take tens of
        // milliseconds, and an origin sampled before that made the first note start late.
        val started = playback.start(score, settingsCard.audioLatency.value)
        setPlaybackControlsEnabled(true)
        updatePitchesPanel()
        updatePlayButton()
        timeline.score = score
        timeline.playbackPositionMs = 0.0
        notation.playbackPositionMs = 0.0
        timeline.resetScroll() // Long scores start at the beginning, wherever the take left the view.
        if (!started) {
            stopPlayback()
            return
        }
        frameTimer.start()
    }

    private fun stopPlayback() {
        playback.stop()
        frameTimer.stop()
        timeline.playbackPositionMs = -1.0
        notation.playbackPositionMs = -1.0
        pitches.piano.playbackPositionMs = -1.0
        // While a take is being recorded the settings stay off; they come back with the window.
        if (session == null) setPlaybackControlsEnabled(false)
        updatePlayButton()
        updatePitchesPanel()
    }

    private fun updatePlayButton() {
        playButton.glyph = if (playback.isPlaying) Glyph.STOP else Glyph.PLAY
        playButton.toolTipText = if (playback.isPlaying) "Stop playback" else "Play the score"
    }

    /** Toggles the playback sound between the score's pitch and a metronome-style click. */
    private fun togglePlaybackSound() {
        if (playback.isPlaying) return
        playback.toggleSound()
        updateSoundButton()
    }

    private fun updateSoundButton() {
        val beat = playback.sound == PlaybackSound.BEAT
        soundButton.glyph = if (beat) Glyph.METRONOME else Glyph.NOTE
        soundButton.accentOverride = if (beat) Theme.accent else null
        soundButton.toolTipText =
            if (beat) {
                tooltip(
                    "Playback sound: Beat (a click on every note onset).",
                    "Click for the score's pitch.",
                )
            } else {
                tooltip(
                    "Playback sound: Note (the score's pitch).",
                    "Click for a metronome-style click.",
                )
            }
    }

    /** The score Play uses: the edited one when there is a take, otherwise whatever the strip shows. */
    private fun currentScore(): Score? = notation.score ?: takeScore

    private fun relayoutStageRight() {
        stageRight.revalidate()
        stageRight.repaint()
    }

    /** Turns the settings and the controls that work on the score off and on. */
    private fun setControlsEnabled(on: Boolean) {
        settingsCard.setEnabled(on)
        // The copy and save buttons follow the score, and the card owns them: there is
        // nothing to copy and nothing to save when there is no score.
        lilypond.relative.isEnabled = on
        playButton.isEnabled = on && currentScore() != null
        soundButton.isEnabled = on && currentScore() != null
        updateScoreControls()
    }

    /**
     * Applies what the playing state does to the controls.
     *
     * The score that is playing is fixed, and the settings describe the next take, so they are
     * disabled while it plays: a changed tempo or latency would not be heard until the next
     * playback. The header button stays live — it is the **Stop** button then.
     */
    private fun setPlaybackControlsEnabled(playing: Boolean) {
        settingsCard.setEnabled(!playing)
        start.isEnabled = !playing
        soundButton.isEnabled = !playing && currentScore() != null
        playButton.isEnabled = playing || currentScore() != null
        updateScoreControls()
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
        val live = session == null && !playback.isPlaying && currentScore() != null
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
        val key = settingsCard.keySetting.key
        pitches.piano.key = key
        pitches.transpose.key = key
        tracker.key = key
        legend.key = key
    }

    /** The score was edited in the strip: the output follows, and Revert comes alive. */
    private fun scoreChanged(edited: Score) {
        if (playback.isPlaying) stopPlayback()
        settingsCard.keySetting.tell(edited.config.key)
        updateKeyViews()
        timeline.score = edited
        lilypond.show(edited)
        // The marks of the timing are about the take that made this score: an edited score is
        // no longer that score, so the marks go with it. A Revert puts the take back, and the
        // marks come back with it.
        notation.timing = if (edited == takeScore) takeTiming else null
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
        pitches.piano.interactive = session == null && !playback.isPlaying
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
        settingsCard.keySetting.tell(taken.config.key)
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
        // The numbers of the last take are gone with the score they were about.
        takeTiming = null
        notation.score = null
        timeline.score = null
        status.toolTipText = null
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
    private fun inputProblem(): Message? = InputCheck.message(settingsCard.time.text, settingsCard.keySetting.fault, pitchField.text)

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
        settingsCard.keySetting.apply()
        // A take has the keys to itself, so a tap round cannot still be listening.
        endTapRound(restoreStatus = false)
        val config =
            try {
                settingsCard.config()
            } catch (e: IllegalArgumentException) {
                showMessage(
                    frame,
                    Message(
                        "Invalid settings",
                        "Bad setting",
                        listOf(
                            e.message ?: "The settings are not valid.",
                            "Fix the setting and try again.",
                        ),
                        Glyph.BOLT,
                        Theme.rec,
                    ),
                )
                return
            }
        val minRestCells = settingsCard.minRestCells(config)

        // The musical beat: the note value in a simple meter, the dotted quarter in a compound one.
        val pulses = config.timeSignature.pulsesPerBar
        val s = recorder.start(config, minRestCells, settingsCard.audioLatency.value)
        session = s
        timeline.session = s
        updatePitchesPanel()
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
        frameTimer.start()
    }

    private fun calibrate() {
        // A calibration beeps over a take or a playback, so it only runs on an idle window.
        if (session != null || playback.isPlaying) return

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
        val s = recorder.start(config, 1, settingsCard.audioLatency.value)
        session = s
        // Calibration is about the beeps only: the timeline stays empty and the beat dots are
        // hidden, so that a visual guide that is out of sync with the audio cannot mislead the
        // user (and cannot be mistaken for the audio when the taps are measured).
        timeline.session = null
        lilypond.clear()
        clearScore()
        rawToggle.isEnabled = false
        calibration.begin()
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
        frameTimer.start()
    }

    private fun finishCalibration() {
        val s = session ?: return
        stop(s)

        if (calibration.taps > 0) {
            // The clicks were already shifted by the configured latency, so the measured offset
            // is the remaining error: add it to the current value.
            settingsCard.audioLatency.value = calibration.latencyAfter(settingsCard.audioLatency.value)
            val corrected = settingsCard.audioLatency.value
            status.mood = Mood.DONE
            status.text = "Calibrated: ${corrected.roundToLong()} ms audio latency."
            showMessage(
                frame,
                Message(
                    "Calibration complete",
                    "${corrected.roundToLong()} ms",
                    listOf(
                        "The click is now shifted earlier by this much.",
                        "The measurement includes your own reaction time.",
                        "Lower it a few steps if the click sounds early.",
                    ),
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
                    listOf("The audio latency was left unchanged."),
                    Glyph.PAUSE,
                    Theme.warn,
                ),
            )
        }

        calibration.cancel()
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
        val guide = recorder.guide(s, System.nanoTime(), calibration.running)
        if (guide.index == lastBeatIndex) return
        lastBeatIndex = guide.index
        status.mood = guide.mood
        status.text = guide.text
        beatDots.mood = guide.mood
        beatDots.active = guide.activeBeat
    }

    private fun onFrame() {
        val s = session
        if (s != null) {
            timeline.follow() // Repaints the timeline (including playhead)
            status.repaint() // keeps the dot pulsing
            updateBeatDisplay(s)
            if (System.nanoTime() >= s.endNanos) finish()
        }

        if (playback.isPlaying) {
            val score = currentScore()
            if (score == null) {
                stopPlayback()
            } else {
                val posMs = (System.nanoTime() - playback.startNanos) / 1e6
                val totalMs = score.config.totalUnits * score.config.unitMs
                val clamped = posMs.coerceIn(0.0, totalMs)
                playback.positionMs = clamped
                timeline.playbackPositionMs = clamped
                notation.playbackPositionMs = clamped
                pitches.piano.playbackPositionMs = clamped
                // Follow after the position is updated, or the grid scrolls to a stale spot.
                timeline.follow()
                if (clamped >= totalMs) stopPlayback()
            }
        }
    }

    /** A tap on the tap button: the tempo follows the taps. */
    private fun tapTempoNow() {
        tapRound.tap()
    }

    /**
     * Ends the tap round: the taps are forgotten and the keys belong to the app again.
     *
     * @param restoreStatus true when the status line goes back to the idle message.
     */
    private fun endTapRound(restoreStatus: Boolean) {
        tapRound.end(restoreStatus)
    }

    /**
     * Whether a key press is a tap of the tempo.
     *
     * @param e the key event.
     * @return true when the key press counts as a tap.
     */
    private fun isTapKey(e: KeyEvent): Boolean = tapRound.isTapKey(e, autoRepeat)

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
        if (autoRepeat.isRepeat(e)) return false
        return tracker.handleKey(e)
    }

    /** Receives every key event in the window; while recording it consumes all of them. */
    private fun handleKey(e: KeyEvent): Boolean {
        // Ctrl/Cmd+Enter starts a take. The chord is never recorded as a note while playing.
        if (e.keyCode == KeyEvent.VK_ENTER) {
            val chord = e.isControlDown || e.isMetaDown
            // A take does not start over a playback: the Start button is off while it plays.
            val idle = session == null && !playback.isPlaying
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
            if (tapRound.isListening(System.nanoTime())) {
                endTapRound(restoreStatus = true)
                return true
            }
        }

        // The tracker keys play the piano: they write a pitch on the selected note. They do
        // nothing while a take runs (every key is a tap then) or while the score plays.
        if (session == null && !playback.isPlaying && handleTrackerKey(e)) return true

        val s = session ?: return false
        val now = System.nanoTime()

        // Calibration mode: record tap offsets instead of normal recording
        if (calibration.running) {
            if (e.keyCode == KeyEvent.VK_ESCAPE && e.id == KeyEvent.KEY_PRESSED) {
                abort()
            } else if (
                e.id == KeyEvent.KEY_PRESSED &&
                !autoRepeat.isRepeat(e) &&
                e.keyCode !in IGNORED_KEYS
            ) {
                autoRepeat.note(e)
                val ms = (now - s.originNanos) / 1e6
                val beatMs = 60_000.0 / s.config.bpm
                val totalBeats = s.config.bars * s.config.timeSignature.pulsesPerBar
                if (calibration.tap(ms, beatMs, totalBeats)) {
                    status.text = calibration.status()
                }
            }
            return true
        }

        // The take owns the keys now: they are notes, silences, or the end of the take.
        when (recorder.key(s, e, now)) {
            KeyOutcome.ABORT -> abort()
            KeyOutcome.FINISH -> finish()
            KeyOutcome.CONSUMED -> Unit
        }
        return true
    }

    private fun finish() {
        val s = session ?: return
        if (calibration.running) {
            finishCalibration()
            return
        }
        val take = recorder.transcription(s)
        stop(s)
        showScore(take.transcription.score)
        s.snapped = take.transcription.snapped
        rawToggle.isEnabled = take.transcription.snapped.isNotEmpty()
        val timing = take.transcription.timing
        // The marks of the strip: how each note of the take was played.
        takeTiming = timing
        notation.timing = timing
        status.mood = Mood.DONE
        status.text = timingSummary(timing)
        // The tooltip carries the numbers that do not fit in the status line.
        status.toolTipText = timing?.details()?.takeIf { it.isNotEmpty() }?.let { lines -> tooltip(lines) }
        relayoutStageRight()
    }

    /**
     * What the status line says after a take: how the take was played.
     *
     * The line has room for the bias and the spread, and for the suggestion when the bias is
     * big enough to be worth correcting. The rest of the numbers are in the tooltip.
     *
     * @param timing the timing of the take, null when nothing was played.
     * @return the text of the status line.
     */
    private fun timingSummary(timing: TimingReport?): String {
        if (timing == null || !timing.hasStatistics) return "Done."
        val mean = timing.meanMs ?: return "Done."
        val spread = timing.spreadMs?.roundToLong() ?: 0
        val text = "Done. ${timing.headline()} · spread $spread ms"
        // The offset is the error that is left after the configured latency, so the corrected
        // latency is the configured one plus that error.
        if (abs(mean) < 15 || timing.notes < 4) return text
        val suggested = (settingsCard.latency.value + mean).roundToLong()
        return "$text · try latency $suggested"
    }

    private fun abort() {
        val s = session ?: return
        stop(s)
        clearScore()
        if (calibration.running) {
            calibration.cancel()
            status.mood = Mood.STOPPED
            status.text = "Calibration cancelled."
            return
        }
        status.mood = Mood.STOPPED
        status.text = "Aborted."
    }

    private fun stop(s: Session) {
        session = null
        frameTimer.stop()
        recorder.stop(s)
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
