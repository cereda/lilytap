// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {
    @Test
    fun `empty pitch is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "")
        }
    }

    @Test
    fun `non-positive BPM is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(0.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        }
        assertFailsWith<IllegalArgumentException> {
            Config(-60.0, TimeSignature(4, 4), 2, 1, 0.0, "c")
        }
    }

    @Test
    fun `zero bars is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 2, 0, 0.0, "c")
        }
    }

    @Test
    fun `subdivisions too fine for time signature are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 32), 4, 1, 0.0, "c")
        }
    }

    @Test
    fun `zero subdivisions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 0, 1, 0.0, "c")
        }
    }

    @Test
    fun `negative subdivisions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), -1, 1, 0.0, "c")
        }
    }

    @Test
    fun `valid config is accepted`() {
        val cfg = Config(120.0, TimeSignature(6, 8), 4, 2, 50.0, "d")
        assertEquals(120.0, cfg.bpm)
        assertEquals(TimeSignature(6, 8), cfg.timeSignature)
        assertEquals(4, cfg.subdivisions)
        assertEquals(2, cfg.bars)
        assertEquals(50.0, cfg.latencyMs)
        assertEquals("d", cfg.pitch)
    }

    @Test
    fun `non-finite BPM is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(Double.POSITIVE_INFINITY, TimeSignature(4, 4), 2, 1, 0.0, "c")
        }
        assertFailsWith<IllegalArgumentException> {
            Config(Double.NaN, TimeSignature(4, 4), 2, 1, 0.0, "c")
        }
    }

    @Test
    fun `non-finite latency is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 2, 1, Double.NaN, "c")
        }
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 2, 1, Double.POSITIVE_INFINITY, "c")
        }
    }

    @Test
    fun `blank pitch is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Config(60.0, TimeSignature(4, 4), 2, 1, 0.0, "   ")
        }
    }
}
