// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/*
 * The piano of the pitches panel: a keyboard that writes the pitch of the selected note.
 *
 * The keys carry the letters of the tracker keys, so a melody can be typed on the keyboard
 * instead of clicked. The piano also shows what the score holds: the notes of the take light
 * their keys, the selected note is marked, and the note that is sounding lights its key while
 * the score plays.
 */

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/** The five semitones of the octave that are not in the scale of C. */
private val BLACK_SEMITONES = setOf(1, 3, 6, 8, 10)

/** Whether the key of the given MIDI number is a black one. */
internal fun isBlackKey(midi: Int): Boolean = midi % 12 in BLACK_SEMITONES

/**
 * The pitch a key sounds.
 *
 * A piano has twelve keys in the octave: the seven of the scale and the five that are a
 * semitone above them. The black key is the sharp of the note below it, the way a real
 * keyboard is built: D♭4 and C♯4 are the same key. Which of the two names the key gets
 * depends on the key of the piece: in F major the black key between the C and the D is a
 * D♭, and in E major it is a D♯.
 *
 * @param midi the MIDI number of the key.
 * @param key the key of the piece.
 * @return the pitch of the key.
 */
internal fun keyPitch(
    midi: Int,
    key: KeySignature = KeySignature.C_MAJOR,
): Pitch = key.spell(midi)

/**
 * The lowest key the piano shows.
 *
 * The piano starts at middle C and reaches lower when the score does: a note below the
 * keyboard has to be somewhere. The octave the tracker plays is always on the keyboard too,
 * or the keys that sound it would be nowhere to be seen.
 *
 * @param score the score of the take.
 * @param liveOctave the octave the tracker plays.
 * @return the MIDI number of the lowest key.
 */
internal fun lowestKey(
    score: Score?,
    liveOctave: Int,
): Int {
    val lowest = score?.events?.mapNotNull { it.pitch?.midi }?.minOrNull() ?: 60
    val tracker = 12 * (liveOctave + 1)
    return minOf(60, (lowest / 12) * 12, (tracker / 12) * 12)
}

/**
 * The highest key the piano shows, plus one.
 *
 * The piano always reaches the C above the octave the tracker plays, so every key that can
 * be played with the keyboard is also visible. A note above that grows the keyboard, and the
 * growth is always a whole octave.
 *
 * @param score the score of the take.
 * @param liveOctave the octave the tracker plays.
 * @return the MIDI number of the highest key, plus one.
 */
internal fun highestKey(
    score: Score?,
    liveOctave: Int,
): Int {
    val highest = score?.events?.mapNotNull { it.pitch?.midi }?.maxOrNull() ?: 72
    val tracker = 12 * (liveOctave + 2) - 1
    return maxOf(84, ((highest + 12) / 12) * 12, ((tracker + 12) / 12) * 12)
}

/**
 * A key of the piano: where it is and what it sounds.
 *
 * @property midi the MIDI number of the key.
 * @property x where the key starts.
 * @property width how wide the key is.
 * @property height how tall the key is.
 * @property black whether the key is a black one.
 */
internal data class PianoKey(
    val midi: Int,
    val x: Double,
    val width: Double,
    val height: Double,
    val black: Boolean,
)

/**
 * The keys of the piano, laid out over the given size.
 *
 * The white keys fill the width; the black keys sit between them, over the seam.
 *
 * @param low the MIDI number of the lowest key.
 * @param high the MIDI number of the highest key, plus one.
 * @param width the width of the piano.
 * @param height the height of the piano.
 * @return the keys, white ones first.
 */
internal fun pianoKeys(
    low: Int,
    high: Int,
    width: Double,
    height: Double,
): List<PianoKey> {
    val whites = (low until high).filter { !isBlackKey(it) }
    if (whites.isEmpty()) return emptyList()
    val whiteWidth = width / whites.size
    val blackWidth = whiteWidth * 0.62
    val blackHeight = height * 0.62
    val keys = mutableListOf<PianoKey>()
    whites.forEachIndexed { index, midi ->
        keys += PianoKey(midi, index * whiteWidth, whiteWidth, height, false)
    }
    // The black key sits on the seam between the white key below it and the next one.
    for (midi in low until high) {
        if (!isBlackKey(midi)) continue
        val below = whites.indexOf(midi - 1)
        if (below >= 0) {
            keys += PianoKey(midi, (below + 1) * whiteWidth - blackWidth / 2, blackWidth, blackHeight, true)
        }
    }
    return keys
}

/**
 * The key under a point of the piano.
 *
 * A black key is over the white key below it, so it wins where the two meet.
 *
 * @param keys the keys as they are drawn.
 * @param x the horizontal position of the point.
 * @param y the vertical position of the point.
 * @param key the key of the piece: it says how the notes are spelled.
 * @return the pitch under the point, or null when the point is not on a key.
 */
internal fun pitchAt(
    keys: List<PianoKey>,
    x: Double,
    y: Double,
    key: KeySignature = KeySignature.C_MAJOR,
): Pitch? {
    val black =
        keys.firstOrNull { it.black && y < it.height && x >= it.x && x < it.x + it.width }
    val white = keys.firstOrNull { !it.black && x >= it.x && x < it.x + it.width }
    val found = black ?: white ?: return null
    return keyPitch(found.midi, key)
}

/**
 * The piano: a keyboard that writes the pitch of the selected note.
 *
 * The piano is an editor: it says nothing while a take is being recorded, and while the score
 * plays it only shows what is sounding.
 */
internal class PianoPanel : JComponent() {
    /** The score the keys are read from. */
    var score: Score? = null
        set(value) {
            field = value
            repaint()
        }

    /** The pitch of the selected note, when there is one. */
    var selectedPitch: Pitch? = null
        set(value) {
            field = value
            repaint()
        }

    /** The octave the tracker plays: its keys carry the letter of their key. */
    var liveOctave: Int = Pitch.DEFAULT.octave
        set(value) {
            field = value
            repaint()
        }

    /** The key of the piece: it says how the notes of the keyboard are spelled. */
    var key: KeySignature = KeySignature.C_MAJOR
        set(value) {
            field = value
            repaint()
        }

    /** Where the playback is, in milliseconds; -1 when nothing is playing. */
    var playbackPositionMs: Double = -1.0
        set(value) {
            field = value
            repaint()
        }

    /** Whether the keys answer the mouse. */
    var interactive: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Called with the pitch of the key that was clicked. */
    var onPitch: ((Pitch) -> Unit)? = null

    init {
        isOpaque = false
        // The piano does not take the keyboard: the keys of the tracker belong to the score.
        isFocusable = false
        toolTipText =
            tooltip(
                "Click a key to give it to the selected note.",
                "The keyboard plays the piano too: 1 to 7 are C to B, Q W E R T the accidentals.",
                "8 moves the octave down, 9 moves it up and 0 puts it back on the note you are on.",
            )
        val mouse =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (!interactive) return
                    val pitch = pitchAt(keys(), e.x.toDouble(), e.y.toDouble(), key) ?: return
                    onPitch?.invoke(pitch)
                }
            }
        addMouseListener(mouse)
    }

    override fun getPreferredSize(): Dimension = Dimension(420, 110)

    /** The keys of the piano, as they are drawn now. */
    internal fun keys(): List<PianoKey> =
        pianoKeys(
            lowestKey(score, liveOctave),
            highestKey(score, liveOctave),
            width.toDouble(),
            height.toDouble(),
        )

    /** The pitch that is sounding right now, when the score is playing. */
    private fun sounding(): Pitch? {
        val s = score ?: return null
        if (playbackPositionMs < 0) return null
        val units = (playbackPositionMs / s.config.unitMs).toInt()
        val event = s.events.firstOrNull { units >= it.startUnits && units < it.endUnits }
        return event?.pitch
    }

    /** The notes of the take, by the key they light. */
    private fun notes(): Set<Int> = score?.events?.mapNotNull { it.pitch?.midi }?.toSet() ?: emptySet()

    /** The colour of a key, given what the piano knows about it. */
    private fun keyColour(
        midi: Int,
        notes: Set<Int>,
        sounding: Int?,
    ): Color {
        val selected = selectedPitch?.midi
        val base =
            when {
                midi == sounding -> Theme.accent2
                selected != null && midi == selected -> Theme.accent
                midi in notes -> Theme.accent.alpha(120)
                isBlackKey(midi) -> Theme.line
                else -> Theme.field
            }
        // The piano says nothing while the app is recording or playing: the keys go quiet.
        return if (interactive || midi == sounding) base else base.alpha(110)
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val keys = keys()
            if (keys.isEmpty()) return@paint2d
            val notes = notes()
            val sounding = sounding()?.midi
            // The white keys first, so the black ones sit over their seams.
            for (key in keys.filter { !it.black }) {
                paintKey(g2, key, keyColour(key.midi, notes, sounding))
            }
            for (key in keys.filter { it.black }) {
                paintKey(g2, key, keyColour(key.midi, notes, sounding))
            }
        }
    }

    /** Paints one key, with the letter of the tracker key that plays it. */
    private fun paintKey(
        g2: Graphics2D,
        key: PianoKey,
        colour: Color,
    ) {
        val shape =
            rr(
                key.x + 1.0,
                1.0,
                key.width - 2.0,
                key.height - 2.0,
                if (key.black) 3.0 else 4.0,
            )
        g2.color = colour
        g2.fill(shape)
        g2.color = Theme.bg.alpha(if (interactive) 90 else 50)
        g2.draw(shape)
        val label = TRACKER_LABELS[key.midi % 12] ?: return
        g2.font = Theme.font(11f, Font.BOLD)
        val fm = g2.fontMetrics
        if (fm.stringWidth(label) > key.width - 4) return
        g2.color =
            when {
                key.midi == sounding()?.midi -> Theme.onAccent
                selectedPitch?.midi == key.midi -> Theme.onAccent
                key.midi / 12 - 1 == liveOctave -> Theme.text
                else -> Theme.dim
            }
        val tx = (key.x + (key.width - fm.stringWidth(label)) / 2).toFloat()
        val ty = (key.height - 8).toFloat()
        g2.drawString(label, tx, ty)
    }
}
