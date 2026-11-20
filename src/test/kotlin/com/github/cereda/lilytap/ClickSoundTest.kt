// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sounds of the metronome clicks.
 *
 * The levels of the click have to be told apart by the ear: the plain tick, the accent on the
 * beat and the downbeat. A change that made two of them alike would leave the click marking
 * nothing.
 */
class ClickSoundTest {
    @Test
    fun `the three clicks are told apart by their pitch`() {
        val normal = pitch(ClickSound.NORMAL.wave(SAMPLE_RATE))
        val beat = pitch(ClickSound.BEAT.wave(SAMPLE_RATE))
        val downbeat = pitch(ClickSound.DOWNBEAT.wave(SAMPLE_RATE))
        assertTrue(downbeat < normal, "the downbeat is lower than the plain click")
        assertTrue(normal < beat, "the beat is higher than the plain click")
    }

    @Test
    fun `the downbeat is the heaviest click`() {
        assertTrue(
            energy(ClickSound.DOWNBEAT.wave(SAMPLE_RATE)) > energy(ClickSound.BEAT.wave(SAMPLE_RATE)),
            "the downbeat carries more of the sound than the beat",
        )
        assertTrue(
            energy(ClickSound.DOWNBEAT.wave(SAMPLE_RATE)) > energy(ClickSound.NORMAL.wave(SAMPLE_RATE)),
            "the downbeat carries more of the sound than the plain click",
        )
    }

    @Test
    fun `no click is clipped`() {
        for (sound in ClickSound.entries) {
            val wave = sound.wave(SAMPLE_RATE)
            for (value in values(wave)) {
                assertTrue(abs(value.toInt()) < Short.MAX_VALUE, "${sound.name} is clipped")
            }
        }
    }

    /** The samples of a click, as signed numbers. */
    private fun values(wave: ByteArray): ShortArray =
        ShortArray(wave.size / 2) { i ->
            ((wave[2 * i + 1].toInt() shl 8) or (wave[2 * i].toInt() and 0xFF)).toShort()
        }

    /** How much sound a click carries. */
    private fun energy(wave: ByteArray): Double = values(wave).sumOf { (it.toDouble()) * it }

    /**
     * The pitch of a click: the frequency of the loudest tone in it.
     *
     * The tone is found with the Goertzel algorithm over a grid of frequencies, which is
     * enough to tell the three clicks apart.
     */
    private fun pitch(wave: ByteArray): Double {
        val samples = values(wave)
        var best = 0.0
        var bestFreq = 0.0
        var frequency = 200.0
        while (frequency <= 3000.0) {
            val magnitude = magnitude(samples, frequency)
            if (magnitude > best) {
                best = magnitude
                bestFreq = frequency
            }
            frequency += 25.0
        }
        return bestFreq
    }

    /** The magnitude of the tone at [frequency] in a signal. */
    private fun magnitude(
        samples: ShortArray,
        frequency: Double,
    ): Double {
        val k = 2 * cos(2 * PI * frequency / SAMPLE_RATE)
        var previous = 0.0
        var before = 0.0
        for (sample in samples) {
            val current = sample + k * previous - before
            before = previous
            previous = current
        }
        return abs(sqrt(previous * previous + before * before - k * previous * before))
    }

    companion object {
        /** The sample rate of the sounds. */
        private const val SAMPLE_RATE = 44_100f
    }
}
