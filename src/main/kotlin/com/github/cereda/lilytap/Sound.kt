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
 */
internal fun clickWave(
    freq: Double,
    seconds: Double,
    decay: Double,
    sampleRate: Float = 44_100f,
): ByteArray {
    val frames = (sampleRate * seconds).toInt().coerceAtLeast(1)
    val out = ByteArray(frames * 2)
    for (i in 0 until frames) {
        val t = i / sampleRate.toDouble()
        val value = (sin(2 * PI * freq * t) * exp(-t * decay) * 0.8 * Short.MAX_VALUE).toInt()
        out[2 * i] = (value and 0xFF).toByte()
        out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return out
}
