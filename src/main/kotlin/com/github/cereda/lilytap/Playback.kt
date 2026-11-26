// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

/**
 * The playback of a score: the player, the sound and the place the playhead is at.
 *
 * The class holds the state of the playback and nothing else: the window applies the position
 * to the timeline, to the score strip and to the piano, and it owns the buttons.
 */
internal class Playback {
    /** Called when the score has been played to the end. */
    var onDone: () -> Unit = {}

    /** The player that plays the score, or null when nothing plays. */
    private var player: Player? = null

    /** Whether a score is playing. */
    internal var isPlaying: Boolean = false

    /** The sound the playback uses: the pitch of the score, or a click on every onset. */
    internal var sound: PlaybackSound = PlaybackSound.NOTE

    /** The position of the playhead in milliseconds (-1 when nothing plays). */
    internal var positionMs: Double = -1.0

    /** The moment the score started, in nanoseconds. */
    internal var startNanos: Long = 0L

    /**
     * Plays a score.
     *
     * The audio line is opened before the clock of the playback starts: opening it can take tens
     * of milliseconds, and an origin sampled before that made the first note start late.
     *
     * @param score the score to play.
     * @param audioLatencyMs how much earlier the sound is written to the audio output.
     * @return false when the score could not be played.
     */
    fun start(
        score: Score,
        audioLatencyMs: Double,
    ): Boolean {
        val player = Player(audioLatencyMs, sound)
        this.player = player
        isPlaying = true
        val origin = player.play(score, onDone = onDone)
        if (origin == 0L) return false
        startNanos = origin
        positionMs = 0.0
        return true
    }

    /** Stops the playback: the score stops sounding at once. */
    fun stop() {
        isPlaying = false
        player?.close()
        player = null
        positionMs = -1.0
    }

    /** Toggles the sound between the pitch of the score and a click on every onset. */
    fun toggleSound() {
        sound = if (sound == PlaybackSound.NOTE) PlaybackSound.BEAT else PlaybackSound.NOTE
    }

    /** Closes the player: the app is going down. */
    fun close() {
        player?.close()
    }
}
