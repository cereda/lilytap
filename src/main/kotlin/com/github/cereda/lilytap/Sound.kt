// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A short percussive click: a sine wave at [freq] Hz fading out exponentially.
 *
 * Shared by the recording metronome and the beat playback sound, so both use the same
 * synthesis and only differ in frequency, length and decay.
 *
 * @param freq the frequency of the tone, in Hz.
 * @param seconds how long the click lasts.
 * @param decay how fast the click fades out.
 * @param sampleRate the sample rate of the sound.
 * @param gain how loud the click is, from 0 to 1.
 */
internal fun clickWave(
    freq: Double,
    seconds: Double,
    decay: Double,
    sampleRate: Float = 44_100f,
    gain: Double = 0.8,
): ByteArray {
    val frames = (sampleRate * seconds).toInt().coerceAtLeast(1)
    val out = ByteArray(frames * 2)
    for (i in 0 until frames) {
        val t = i / sampleRate.toDouble()
        val value = (sin(2 * PI * freq * t) * exp(-t * decay) * gain * Short.MAX_VALUE).toInt()
        out[2 * i] = (value and 0xFF).toByte()
        out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return out
}

/**
 * The sounds of the recording metronome: one recipe for each level of the click.
 *
 * The recipes live together, so that a click is changed in one place: how high it sounds,
 * how long it lasts, how fast it fades out and how loud it is.
 *
 * @property freq the frequency of the tone, in Hz.
 * @property seconds how long the click lasts.
 * @property decay how fast the click fades out.
 * @property gain how loud the click is, from 0 to 1.
 */
internal enum class ClickSound(
    val freq: Double,
    val seconds: Double,
    val decay: Double,
    val gain: Double,
) {
    /** The plain click: the note value in a 3-feel, the beat otherwise. */
    NORMAL(1000.0, 0.025, 180.0, 0.8),

    /** The accent on the musical beat. */
    BEAT(1600.0, 0.025, 180.0, 0.8),

    /** The downbeat of the bar: low and long, so it is the heaviest level of the click. */
    DOWNBEAT(700.0, 0.035, 120.0, 1.0),
    ;

    /**
     * The samples of this click.
     *
     * @param sampleRate the sample rate of the sound.
     */
    fun wave(sampleRate: Float): ByteArray = clickWave(freq, seconds, decay, sampleRate, gain)
}
