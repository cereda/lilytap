// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * The life of the audio line of the metronome.
 *
 * The line is opened when a take starts and closed when the take ends, so the app does not
 * hold the sound card while it waits for the next take. A sound card that is busy with
 * something else blocks the open, and a window that held the card from the moment it opened
 * would make every launch wait for it.
 */
class MetronomeTest {
    @Test
    fun `the metronome does not hold the sound card while it waits`() {
        val metronome = Metronome()

        assertFalse(metronome.hasAudio, "the metronome opens the line only when a take starts")

        metronome.close()
    }

    @Test
    fun `releasing the line of a metronome that never played is harmless`() {
        val metronome = Metronome()

        metronome.release()

        assertFalse(metronome.hasAudio, "there was no line to close")
        metronome.close()
    }
}
