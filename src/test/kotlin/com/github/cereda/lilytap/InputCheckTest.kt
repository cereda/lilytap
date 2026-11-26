// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
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
        assertTrue(problem.headline.contains("time signature"), "the message says what is wrong")
        assertTrue(problem.description.contains("must"), "the message says what a meter looks like")
    }

    @Test
    fun `a time signature that does not look like a meter is a problem`() {
        val problem = InputCheck.message("nope", null, "")
        assertNotNull(problem, "the text is not a meter")
        assertTrue(problem.description.contains("4/4"), "the message says what a meter looks like")
    }

    @Test
    fun `an empty time signature is a problem`() {
        assertNotNull(InputCheck.message("", null, ""), "an empty field is not a meter")
    }

    @Test
    fun `a key that cannot be read is a problem`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("Bb dorian"), "")
        assertNotNull(problem, "the name is not a key")
        assertTrue(problem.headline.contains("key"), "the message says what is wrong")
        assertTrue(problem.description.contains("name of a key"), "the message says what a key looks like")
    }

    @Test
    fun `a key with too many accidentals says how many`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("feses major"), "")
        assertNotNull(problem, "the key carries fifteen accidentals")
        assertTrue(problem.headline.contains("15"), "the message says how many accidentals")
        assertTrue(problem.description.contains("fourteen"), "the message says what the limit is")
    }

    @Test
    fun `a pitch that is not a note is a problem`() {
        val problem = InputCheck.message("4/4", null, "h")
        assertNotNull(problem, "the name is not a note")
        assertTrue(problem.headline.contains("pitch"), "the message says what is wrong")
        assertTrue(problem.description.contains("LilyPond"), "the message says what a note looks like")
    }

    @Test
    fun `the time signature is the first thing that is checked`() {
        val problem = InputCheck.message("nope", KeySignature.fault("Bb dorian"), "h")
        assertNotNull(problem)
        assertTrue(problem.headline.contains("time signature"), "the time signature comes first")
    }

    @Test
    fun `the key comes before the pitch`() {
        val problem = InputCheck.message("4/4", KeySignature.fault("Bb dorian"), "h")
        assertNotNull(problem)
        assertTrue(problem.headline.contains("key"), "the key comes before the pitch")
    }
}
