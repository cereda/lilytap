// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCardTest {
    private fun card(onKey: (KeySignature) -> Unit = {}) = SettingsCard(onKey = onKey)

    @Test
    fun `the card reads the settings of the next take`() {
        val card = card()
        card.bpm.value = 90.0
        card.time.text = "3/4"
        card.bars.value = 2.0
        val config = card.config()
        assertEquals(90.0, config.bpm)
        assertEquals(TimeSignature(3, 4), config.timeSignature)
        assertEquals(2, config.bars)
        assertFalse(config.noRests)
        assertFalse(config.threeFeel)
        assertEquals(KeySignature.C_MAJOR, config.key)
    }

    @Test
    fun `a time signature that cannot be read is refused`() {
        val card = card()
        card.time.text = "nonsense"
        assertFailsWith<IllegalArgumentException> { card.config() }
    }

    @Test
    fun `the smallest rest of a beat is one cell`() {
        val card = card()
        val config = card.config()
        assertEquals(config.cellsPerPulse, card.minRestCells(config))
    }

    @Test
    fun `a smaller smallest rest takes more cells`() {
        val card = card()
        // Two cells to the beat, so the two shorter rests have something to divide.
        card.subdivisions.selectedIndex = 1
        val config = card.config()
        assertEquals(2, config.cellsPerPulse)
        card.minRest.selectedIndex = 1
        assertEquals(1, card.minRestCells(config))
        card.minRest.selectedIndex = 2
        assertEquals(1, card.minRestCells(config))
    }

    @Test
    fun `the smallest rest is never shorter than a cell`() {
        val card = card()
        card.minRest.selectedIndex = 2
        card.subdivisions.selectedIndex = 2
        val config = card.config()
        assertTrue(card.minRestCells(config) >= 1)
    }

    @Test
    fun `the last option of the smallest rest says that no rest is written`() {
        val card = card()
        card.minRest.selectedIndex = 3
        assertTrue(card.config().noRests)
    }

    @Test
    fun `the 3-feel waits for a compound meter`() {
        val card = card()
        assertFalse(card.threeFeelButton.isEnabled)
        card.time.text = "6/8"
        assertTrue(card.threeFeelButton.isEnabled)
    }

    @Test
    fun `the 3-feel keeps its setting when the meter changes`() {
        val card = card()
        card.time.text = "6/8"
        card.threeFeelButton.onClick?.invoke()
        assertTrue(card.threeFeel)
        card.time.text = "4/4"
        assertFalse(card.threeFeelButton.isEnabled)
        assertTrue(card.threeFeel)
        card.time.text = "6/8"
        assertTrue(card.threeFeel)
    }

    @Test
    fun `the settings are off while a take owns the window`() {
        val card = card()
        card.setEnabled(false)
        assertFalse(card.bpm.isEnabled)
        assertFalse(card.time.isEnabled)
        assertFalse(card.tapButton.isEnabled)
        assertFalse(card.calibrateButton.isEnabled)
        card.setEnabled(true)
        assertTrue(card.bpm.isEnabled)
        assertTrue(card.time.isEnabled)
        assertTrue(card.tapButton.isEnabled)
        assertTrue(card.calibrateButton.isEnabled)
    }

    @Test
    fun `the key of the piece is told to the window`() {
        var chosen: KeySignature? = null
        val card = card { key -> chosen = key }
        card.keySetting.field.text = "G major"
        card.keySetting.apply()
        val g = KeySignature.parse("G major")
        assertEquals(g, chosen)
        assertEquals(g, card.config().key)
    }

    @Test
    fun `the time signature menu offers the common meters in two groups`() {
        val card = card()

        val menu = card.timeMenu

        assertEquals(
            listOf(
                MenuGroup("Simple", listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8")),
                MenuGroup("Compound", listOf("6/8", "9/8", "12/8", "6/4")),
            ),
            menu.groups,
        )
        assertEquals(
            listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8", "6/8", "9/8", "12/8", "6/4"),
            menu.options,
        )
    }

    @Test
    fun `the groups of the time menu are the simple and the compound meters`() {
        val card = card()

        val menu = card.timeMenu

        menu.groups.forEach { group ->
            group.options.forEach { meter ->
                val signature = runCatching { TimeSignature.parse(meter) }.getOrNull()
                assertNotNull(signature, "$meter is a time signature")
                val compound = signature.isCompound
                assertEquals(
                    compound,
                    group.name == "Compound",
                    "$meter belongs to the ${group.name} group",
                )
            }
        }
    }

    @Test
    fun `picking a meter in the menu fills the field`() {
        val card = card()

        val menu = card.timeMenu

        val time = card.time

        menu.pick("6/8")
        assertEquals("6/8", time.text)
        assertFalse(time.invalid)
    }

    @Test
    fun `a time signature that cannot be used is marked`() {
        val card = card()

        val time = card.time

        time.text = "6/8"
        assertFalse(time.invalid, "6/8 is a meter")

        time.text = "11/16"
        assertFalse(time.invalid, "the field takes any meter, not only the common ones")

        time.text = "6/6"
        assertTrue(time.invalid, "6/6 is not a meter")

        time.text = ""
        assertTrue(time.invalid, "an empty field is not a meter")
    }

    @Test
    fun `the grid caption names the note the cells cut`() {
        val card = card()

        val caption = card.gridCaption

        val time = card.time

        assertEquals("Cells / beat", caption.text, "in a simple meter the cells cut the beat")

        time.text = "6/8"
        assertEquals("Cells / eighth", caption.text, "in 6/8 they cut the eighth")

        time.text = "6/4"
        assertEquals("Cells / quarter", caption.text, "in 6/4 they cut the quarter")

        time.text = "4/4"
        assertEquals("Cells / beat", caption.text)
    }

    @Test
    fun `the 3-feel button waits for a compound meter`() {
        val card = card()
        val button = card.threeFeelButton
        val time = card.time

        assertFalse(button.isEnabled, "a simple meter has no 3-feel")

        time.text = "6/8"
        assertTrue(button.isEnabled, "6/8 is a compound meter")

        time.text = "7/8"
        assertFalse(button.isEnabled, "an irregular meter is not a compound one")

        time.text = "6/4"
        assertTrue(button.isEnabled, "6/4 is a compound meter too")
    }

    @Test
    fun `the 3-feel button keeps its setting when the meter changes`() {
        val card = card()
        val button = card.threeFeelButton
        val time = card.time

        time.text = "6/8"
        button.onClick?.invoke()
        assertNotNull(button.accentOverride, "the button shows that the 3-feel is on")

        time.text = "4/4"
        assertFalse(button.isEnabled, "a simple meter has no 3-feel")
        assertNull(button.accentOverride, "the button is greyed out in a simple meter")

        time.text = "6/8"
        assertTrue(button.isEnabled)
        assertNotNull(button.accentOverride, "the setting is remembered when the meter comes back")
    }

    @Test
    fun `the smallest rest control holds the no-rest option`() {
        val card = card()
        val control = card.minRest

        control.selectedIndex = 3
        assertEquals("No rests", control.selectedText)
        assertTrue(
            control.toolTipText!!.contains("Never write rests"),
            "the tooltip follows the option that is selected",
        )

        control.selectedIndex = 0
        assertEquals("1 beat", control.selectedText)
        assertTrue(
            control.toolTipText!!.contains("Silences shorter than this"),
            "the tooltip follows the option that is selected",
        )
    }
}
