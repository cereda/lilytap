// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.BorderLayout
import java.awt.Color
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
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
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

private fun escapeHtml(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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

/** Colours for the few stock Swing pieces we still use (tooltips). */
private fun applyDialogTheme() {
    UIManager.put("ToolTip.background", Theme.field)
    UIManager.put("ToolTip.foreground", Theme.text)
    UIManager.put("ToolTip.border", BorderFactory.createLineBorder(Theme.line))
}

internal class WindowApp {
    private val frame = JFrame("lilytap")
    private val bpm = Stepper(60.0, 20.0, 300.0, 1.0) { it.roundToLong().toString() }
    private val time = StyledField("4/4", 4)
    private val bars = Stepper(4.0, 1.0, 64.0, 1.0) { it.roundToLong().toString() }
    private val subdivisions = SegmentedControl(listOf("1", "2", "4"), 1)
    private val minRest = SegmentedControl(listOf("1 beat", "\u00BD beat", "\u00BC beat"), 0)
    private val noRests = CheckBox()
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
    private val pitch = StyledField("c", 4)
    private val start = PillButton("Start", Glyph.PLAY, ButtonKind.PRIMARY, minW = 168, heightPx = 48)
    private val status = StatusBadge()
    private val beatDots = BeatDots()
    private val grid = GridView()
    private val timeline = TimelinePanel(grid)
    private val notation = NotationPanel(grid)
    private val revert = PillButton("Revert", Glyph.RESET, heightPx = 32)
    private val output = CodeArea()
    private val copy = PillButton("Copy", Glyph.COPY, minW = 104, heightPx = 36)
    private val rawToggle = PillButton("Raw taps", Glyph.LAYERS, heightPx = 32)
    private val scoreToggle = PillButton("", Glyph.EYE, heightPx = 32)
    private val soundButton = PillButton("", Glyph.NOTE, heightPx = 32)
    private val playButton = PillButton("", Glyph.PLAY, heightPx = 32)
    private val themeButton = IconButton(Glyph.SUN)
    private val scroll = JScrollPane(output)
    private val stageRight = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
    private val stageBody = JPanel(GridBagLayout()).apply { isOpaque = false }

    /** The header of the score strip: its name on the left, Revert on the right. */
    private val scoreHeader =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(GlyphLabel(Glyph.SCORE, "Score", 13f, { Theme.text }, Font.BOLD), BorderLayout.WEST)
            add(revert, BorderLayout.EAST)
        }
    private var showRaw = false
    private var enterGuard = false
    private val metronome = Metronome()
    private val frameTimer = Timer(15) { onFrame() }
    private val copyReset = Timer(1500) { resetCopyButton() }.apply { isRepeats = false }
    private var session: Session? = null

    /** The beat the guide was last updated for, so it only changes when a new beat starts. */
    private var lastBeatIndex = Int.MIN_VALUE
    private var currentPlayer: Player? = null
    private var calibrating = false
    private val calibrationOffsets = mutableListOf<Double>()

    /** The score the take had right after it ended, so Revert can put it back. */
    private var takeScore: Score? = null

    /** Whether the score strip is shown. */
    private var scoreVisible = true

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

    fun show() {
        status.text = "Set the tempo, press Start, then play with any key"
        bpm.toolTipText = "Beats per minute. Click +/-, scroll, or drag the number."
        bars.toolTipText = "How many bars to record after the one-bar count-in."
        latency.toolTipText = "Delay subtracted from your taps (audio output + reaction time)."
        latencyReset.toolTipText = "Reset the tap latency to 0 ms."
        audioLatency.toolTipText = "Audio output latency: shifts the metronome click earlier so it sounds on time."
        audioLatencyReset.toolTipText = "Reset the audio latency to 0 ms."
        calibrateButton.toolTipText = "Tap along with the metronome clicks to measure your audio output latency automatically."
        minRest.toolTipText = "Silences shorter than this are absorbed into the previous note."
        noRests.toolTipText = "Never write rests: every silence is absorbed into the previous note."
        subdivisions.toolTipText = "Grid cells per beat."
        time.toolTipText = "Time signature, e.g. 4/4 or 6/8."
        pitch.toolTipText = "Placeholder pitch printed in the LilyPond output."

        fun labeled(
            glyph: Glyph,
            label: String,
            c: JComponent,
        ) = JPanel(BorderLayout(0, 7)).apply {
            isOpaque = false
            add(GlyphLabel(glyph, label), BorderLayout.NORTH)
            add(c, BorderLayout.CENTER)
        }

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

        val rows =
            JPanel(GridLayout(3, 1, 0, 16)).apply {
                isOpaque = false
                add(
                    row(
                        labeled(Glyph.METRONOME, "Tempo (BPM)", bpm),
                        labeled(Glyph.CLOCK, "Time", time),
                        labeled(Glyph.BARS, "Bars", bars),
                        labeled(Glyph.GRID, "Cells / beat", subdivisions),
                    ),
                )
                add(
                    row(
                        labeled(Glyph.PAUSE, "Smallest rest", minRest),
                        labeled(Glyph.CHECK, "No rests", noRests),
                        labeled(Glyph.BOLT, "Latency", withReset(latency, latencyReset)),
                    ),
                )
                add(
                    row(
                        labeled(Glyph.NOTE, "Pitch", pitch),
                        labeled(Glyph.BOLT, "Audio latency", withReset(audioLatency, audioLatencyReset)),
                        slot(calibrateButton),
                    ),
                )
            }
        val startWrap =
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(0, 20, 0, 0)
                add(start)
            }
        val settings =
            Card().apply {
                layout = BorderLayout()
                border = BorderFactory.createEmptyBorder(18, 20, 18, 20)
                add(rows, BorderLayout.CENTER)
                add(startWrap, BorderLayout.EAST)
            }

        rawToggle.isEnabled = false
        rawToggle.toolTipText = "Show your raw key presses as ghosts under the snapped notes. Amber = pressed late, teal = early."
        soundButton.isEnabled = false
        beatDots.isVisible = false
        revert.isEnabled = false
        revert.toolTipText = "Put the score back the way it was when the take ended."
        stageRight.apply {
            isOpaque = false
            add(rawToggle)
            add(scoreToggle)
            add(soundButton)
            add(playButton)
            add(beatDots)
        }
        val stage = buildStage()

        scroll.apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = Theme.code
            verticalScrollBar.ui = ThinScrollBar()
            verticalScrollBar.preferredSize = Dimension(10, 0)
            horizontalScrollBar.ui = ThinScrollBar()
            horizontalScrollBar.preferredSize = Dimension(0, 10)
        }
        val codeCard =
            Card({ Theme.code }, 12.0).apply {
                layout = BorderLayout()
                border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
                add(scroll, BorderLayout.CENTER)
            }
        val outputHeader =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(GlyphLabel(Glyph.NOTE, "LilyPond", 14f, { Theme.text }, Font.BOLD), BorderLayout.WEST)
                add(copy, BorderLayout.EAST)
            }
        val outputCard =
            Card().apply {
                layout = BorderLayout(0, 10)
                border = BorderFactory.createEmptyBorder(14, 18, 16, 18)
                add(outputHeader, BorderLayout.NORTH)
                add(codeCard, BorderLayout.CENTER)
            }

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
                add(outputCard, BorderLayout.SOUTH)
            }

        start.onClick = { if (session == null) begin() else abort() }
        start.toolTipText = "Start (Ctrl+Enter, or Cmd+Enter on macOS). Esc aborts. Press r for a rest."
        copy.onClick = { copyOutput() }
        rawToggle.onClick = { toggleRaw() }
        scoreToggle.onClick = { toggleScoreStrip() }
        themeButton.onClick = { applyTheme(!Theme.isDark) }
        calibrateButton.onClick = { if (session == null) calibrate() }
        latencyReset.onClick = { latency.value = 0.0 }
        audioLatencyReset.onClick = { audioLatency.value = 0.0 }
        revert.onClick = { revertScore() }
        notation.onEdit = { edited -> scoreChanged(edited) }
        playButton.onClick = { togglePlayback() }
        soundButton.onClick = { togglePlaybackSound() }
        // "Smallest rest" only means something when rests are written at all.
        noRests.onChange = { checked -> minRest.isEnabled = !checked }
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(KeyEventDispatcher { e -> handleKey(e) })
        registerShutdownHook()

        updateRawToggle()
        updateScoreToggle()
        updateSoundButton()
        applyTheme(Theme.isDark)
        frame.iconImage = logoImage(64)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.pack()
        frame.minimumSize = Dimension(frame.width, frame.height)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        // Nothing should look selected on startup: park focus on the background, and let a click
        // on any empty area take focus away from a text field later on.
        val background = frame.contentPane as JComponent
        val release =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    background.requestFocusInWindow()
                }
            }
        for (c in listOf(background, settings, stage, outputCard)) c.addMouseListener(release)
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
        scroll.viewport.background = Theme.code
        output.refreshTheme()
        time.refreshTheme()
        pitch.refreshTheme()
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
        // Open the audio line before the playback clock starts: opening it can take tens of
        // milliseconds, and an origin sampled before that made the first note start late.
        val player = Player(audioLatency.value, playbackSound)
        currentPlayer = player
        isPlaying = true
        setPlaybackControlsEnabled(true)
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
        // While a take is being recorded the settings stay off; they come back with the window.
        if (session == null) setPlaybackControlsEnabled(false)
        updatePlayButton()
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
            time,
            bars,
            subdivisions,
            minRest,
            noRests,
            latency,
            latencyReset,
            audioLatency,
            audioLatencyReset,
            pitch,
        )

    private fun setControlsEnabled(on: Boolean) {
        settingControls.forEach { it.isEnabled = on }
        copy.isEnabled = on
        calibrateButton.isEnabled = on
        playButton.isEnabled = on && currentScore() != null
        soundButton.isEnabled = on && currentScore() != null
    }

    /**
     * Applies what the playing state does to the controls.
     *
     * The score that is playing is fixed, and the settings describe the next take, so they are
     * disabled while it plays: a changed tempo or latency would not be heard until the next
     * playback. The header button stays live — it is the **Stop** button then.
     */
    private fun setPlaybackControlsEnabled(playing: Boolean) {
        settingControls.forEach { it.isEnabled = !playing }
        calibrateButton.isEnabled = !playing
        start.isEnabled = !playing
        soundButton.isEnabled = !playing && currentScore() != null
        playButton.isEnabled = playing || currentScore() != null
    }

    /**
     * The stage: the status line, the recording timeline and the score strip below it.
     * Both panels share one grid, so the strip lines up with the timeline.
     */
    private fun buildStage(): JComponent {
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
     * A themed modal dialog: undecorated, draggable by its header, closed with OK, Enter or Esc.
     */
    private fun showMessage(
        title: String,
        headline: String,
        description: String,
        glyph: Glyph,
        accent: Color = Theme.accent,
    ) {
        val dialog = JDialog(frame, null, Dialog.ModalityType.APPLICATION_MODAL)
        dialog.isUndecorated = true
        dialog.isResizable = false

        val ok = PillButton("OK", Glyph.CHECK, ButtonKind.PRIMARY, minW = 120, heightPx = 40)
        val header = GlyphLabel(glyph, title, 16f, { Theme.text }, Font.BOLD)
        val value =
            JLabel(headline).apply {
                font = Theme.font(22f, Font.BOLD)
                foreground = accent
            }
        val body =
            JLabel("<html><body style='width:320px'>${escapeHtml(description)}</body></html>").apply {
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

    private fun copyOutput() {
        if (output.text.isBlank()) return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(output.text), null)
        copy.text = "Copied"
        copy.glyph = Glyph.CHECK
        copy.accentOverride = Theme.ok
        copyReset.restart()
    }

    /** The score was edited in the strip: the output follows, and Revert comes alive. */
    private fun scoreChanged(edited: Score) {
        if (isPlaying) stopPlayback()
        timeline.score = edited
        output.text = edited.lilypond
        output.caretPosition = 0
        revert.isEnabled = edited != takeScore
    }

    /** Puts the score back the way it was when the take ended. */
    private fun revertScore() {
        val original = takeScore ?: return
        if (isPlaying) stopPlayback()
        notation.score = original
        timeline.score = original
        output.text = original.lilypond
        output.caretPosition = 0
        revert.isEnabled = false
    }

    /** Shows the score of a finished take in the strip. */
    private fun showScore(taken: Score) {
        takeScore = taken
        notation.score = taken
        timeline.score = taken
        output.text = taken.lilypond
        output.caretPosition = 0
        revert.isEnabled = false
        playButton.isEnabled = true
        soundButton.isEnabled = true
        updatePlayButton()
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
        stopPlayback()
    }

    private fun resetCopyButton() {
        copy.text = "Copy"
        copy.glyph = Glyph.COPY
        copy.accentOverride = null
    }

    private fun begin() {
        val config =
            try {
                Config(
                    bpm = bpm.value,
                    timeSignature = TimeSignature.parse(time.text),
                    subdivisions = subdivisions.selectedText.toInt(),
                    bars = bars.value.roundToInt(),
                    latencyMs = latency.value,
                    pitch = pitch.text.trim().ifEmpty { "c" },
                    noRests = noRests.selected,
                )
            } catch (e: IllegalArgumentException) {
                showMessage(
                    "Invalid settings",
                    e.message ?: "The settings are not valid.",
                    "Fix the setting and try again.",
                    Glyph.BOLT,
                    Theme.rec,
                )
                return
            }
        val fraction =
            when (minRest.selectedIndex) {
                0 -> 1
                1 -> 2
                else -> 4
            }
        val minRestCells = maxOf(1, config.subdivisions / fraction)

        val beats = config.timeSignature.beats
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        val startNanos = System.nanoTime() + 800_000_000L
        val origin = startNanos + beats * beatNanos
        val end = origin + config.bars.toLong() * beats * beatNanos

        val s = Session(config, minRestCells, startNanos, origin, end, beatNanos)
        session = s
        timeline.session = s
        lastPress = null
        output.text = ""
        clearScore()
        resetCopyButton()
        copyReset.stop()
        rawToggle.isEnabled = false
        beatDots.isVisible = true
        relayoutStageRight()
        setControlsEnabled(false)
        start.text = "Stop"
        start.glyph = Glyph.STOP
        start.kind = ButtonKind.DANGER
        beatDots.beats = beats
        beatDots.active = -1
        beatDots.mood = Mood.COUNT_IN
        status.mood = Mood.COUNT_IN
        status.text = "Get ready..."
        lastBeatIndex = Int.MIN_VALUE

        s.ticker =
            thread(isDaemon = true, name = "metronome") {
                metronome.run(startNanos, beatNanos, beats + config.bars * beats, beats, audioLatency.value)
            }
        frameTimer.start()
    }

    private fun calibrate() {
        // A calibration beeps over a take or a playback, so it only runs on an idle window.
        if (session != null || isPlaying) return

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

        val beats = config.timeSignature.beats
        val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
        val startNanos = System.nanoTime() + 800_000_000L
        val origin = startNanos + beats * beatNanos
        val end = origin + config.bars.toLong() * beats * beatNanos

        val s = Session(config, 1, startNanos, origin, end, beatNanos)
        session = s
        // Calibration is about the beeps only: the timeline stays empty and the beat dots are
        // hidden, so that a visual guide that is out of sync with the audio cannot mislead the
        // user (and cannot be mistaken for the audio when the taps are measured).
        timeline.session = null
        output.text = ""
        clearScore()
        resetCopyButton()
        copyReset.stop()
        rawToggle.isEnabled = false
        calibrating = true
        calibrationOffsets.clear()
        relayoutStageRight()
        setControlsEnabled(false)
        start.text = "Calibrating..."
        start.glyph = Glyph.STOP
        start.kind = ButtonKind.DANGER
        beatDots.isVisible = false
        beatDots.beats = beats
        beatDots.active = -1
        beatDots.mood = Mood.COUNT_IN
        status.mood = Mood.COUNT_IN
        status.text = "Calibrating \u00B7 tap with the beeps"
        lastBeatIndex = Int.MIN_VALUE

        s.ticker =
            thread(isDaemon = true, name = "metronome") {
                metronome.run(startNanos, beatNanos, beats + config.bars * beats, beats, audioLatency.value)
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
                "Calibration complete",
                "${corrected.roundToLong()} ms",
                "The click is now shifted earlier by this much. The measurement includes your own " +
                    "reaction time, so lower it a few steps if the click sounds early.",
                Glyph.CHECK,
            )
        } else {
            status.mood = Mood.STOPPED
            status.text = "Calibration cancelled."
            showMessage(
                "Calibration cancelled",
                "No taps recorded",
                "The audio latency was left unchanged.",
                Glyph.PAUSE,
                Theme.warn,
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
        val beats = s.config.timeSignature.beats
        if (index < 0) {
            status.mood = Mood.COUNT_IN
            beatDots.mood = Mood.COUNT_IN
            beatDots.active = -1
            status.text = "Get ready..."
        } else if (index < beats) {
            status.mood = Mood.COUNT_IN
            beatDots.mood = Mood.COUNT_IN
            beatDots.active = index
            // While calibrating there is no beat count: a beat number is a claim about when the
            // beat is, and while the audio is late that claim is wrong.
            status.text = if (calibrating) "Get ready..." else "Count-in: ${beats - index}"
        } else {
            val r = index - beats
            status.mood = Mood.REC
            beatDots.mood = Mood.REC
            beatDots.active = r % beats
            status.text =
                if (calibrating) {
                    calibrationStatus()
                } else {
                    "Recording \u00B7 bar ${r / beats + 1}/${s.config.bars}, beat ${r % beats + 1}"
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
                val totalMs = score.config.totalCells * score.config.cellMs
                val clamped = posMs.coerceIn(0.0, totalMs)
                playbackPositionMs = clamped
                timeline.playbackPositionMs = clamped
                notation.playbackPositionMs = clamped
                // Follow after the position is updated, or the grid scrolls to a stale spot.
                timeline.follow()
                if (clamped >= totalMs) stopPlayback()
            }
        }
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
                val totalBeats = s.config.bars * s.config.timeSignature.beats
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
