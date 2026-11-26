// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The check that every input of the window can be used before the app acts. */
class InputCheckTest {
    @Test
    fun `good inputs are no problem`() {
        assertNull(InputCheck.message("4/4", null, ""))
        assertNull(InputCheck.message("6/8", null, "c'"))
        assertNull(InputCheck.message("12/8", null, "   "))
    }

    @Test
    fun `a time signature that is not a meter is a problem`() {
        val problem = InputCheck.message("7/5", null, "")
        assertNotNull(problem, "the time signature is not a meter")
        assertEquals("Bad meter", problem.headline, "the message has a short name")
        assertTrue(
            problem.lines.any { it.contains("7/5") },
            "the message quotes the time signature: ${problem.lines}",
        )
        assertTrue(
            problem.lines.any { it.contains("must") },
            "the message says what a meter looks like: ${problem.lines}",
        )
    }

    @Test
    fun `a time signature that does not look like a meter is a problem`() {
        val problem = InputCheck.message("nope", null, "")
        assertNotNull(problem, "the text is not a meter")
        assertTrue(
            problem.lines.any { it.contains("4/4") },
            "the message says what a meter looks like: ${problem.lines}",
        )
    }

    @Test
    fun `an empty time signature is a problem`() {
        val problem = InputCheck.message("", null, "")
        assertNotNull(problem, "an empty field is not a meter")
        assertTrue(
            problem.lines.any { it.contains("empty") },
            "the message says the field is empty: ${problem.lines}",
        )
    }

    @Test
    fun `a key that cannot be read is a problem`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("Bb dorian"), "")
        assertNotNull(problem, "the name is not a key")
        assertEquals("Not a key", problem.headline, "the message has a short name")
        assertTrue(
            problem.lines.any { it.contains("Bb dorian") },
            "the message quotes the name of the key: ${problem.lines}",
        )
        assertTrue(
            problem.lines.any { it.contains("Bb major") },
            "the message says what a key looks like: ${problem.lines}",
        )
    }

    @Test
    fun `a key with too many accidentals says how many`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("feses major"), "")
        assertNotNull(problem, "the key carries fifteen accidentals")
        assertEquals("Too many accidentals", problem.headline, "the message has a short name")
        assertTrue(
            problem.lines.any { it.contains("15") },
            "the message says how many accidentals: ${problem.lines}",
        )
        assertTrue(
            problem.lines.any { it.contains("fourteen") },
            "the message says what the limit is: ${problem.lines}",
        )
    }

    @Test
    fun `a pitch that is not a note is a problem`() {
        val problem = InputCheck.message("4/4", null, "h")
        assertNotNull(problem, "the name is not a note")
        assertEquals("Not a note name", problem.headline, "the message has a short name")
        assertTrue(
            problem.lines.any { it.contains("h") },
            "the message quotes the pitch: ${problem.lines}",
        )
        assertTrue(
            problem.lines.any { it.contains("LilyPond") },
            "the message says what a note looks like: ${problem.lines}",
        )
    }

    @Test
    fun `the time signature is the first thing that is checked`() {
        val problem = InputCheck.message("nope", KeySignature.fault("Bb dorian"), "h")
        assertNotNull(problem)
        assertEquals("Bad meter", problem.headline, "the time signature comes first")
    }

    @Test
    fun `the key comes before the pitch`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("Bb dorian"), "h")
        assertNotNull(problem)
        assertEquals("Not a key", problem.headline, "the key comes before the pitch")
    }
}
