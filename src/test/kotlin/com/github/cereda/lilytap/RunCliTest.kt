// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunCliTest {
    @Test
    fun `run default options produce expected config`() {
        val config = Config(60.0, TimeSignature(4, 4), 2, 4, 0.0, "c")
        assertEquals(60.0, config.bpm)
        assertEquals(TimeSignature(4, 4), config.timeSignature)
        assertEquals(2, config.subdivisions)
        assertEquals(4, config.bars)
        assertEquals(0.0, config.latencyMs)
        assertEquals("c", config.pitch)
    }

    @Test
    fun `run custom options produce expected config`() {
        val config = Config(120.0, TimeSignature(6, 8), 4, 2, 50.0, "d")
        assertEquals(120.0, config.bpm)
        assertEquals(TimeSignature(6, 8), config.timeSignature)
        assertEquals(4, config.subdivisions)
        assertEquals(2, config.bars)
        assertEquals(50.0, config.latencyMs)
        assertEquals("d", config.pitch)
    }

    @Test
    fun `the run command has a help screen`() {
        val result = Run().test("--help")
        assertEquals(0, result.statusCode)
    }

    @Test
    fun `the run command has a no rests flag`() {
        val result = Run().test("--help")
        assertTrue(result.stdout.contains("--no-rests"))
    }

    @Test
    fun `a non-numeric tempo is a usage error`() {
        val result = Run().test("--bpm fast")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `a non-numeric bar count is a usage error`() {
        val result = Run().test("--bars many")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `an invalid time signature is a usage error`() {
        val result = Run().test("--time 4-4")
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("4/4"))
    }
}
