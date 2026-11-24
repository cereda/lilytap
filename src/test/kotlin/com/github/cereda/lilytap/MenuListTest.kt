// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The list of options of a menu: the groups of it, two options to a row. */
class MenuListTest {
    private val groups =
        listOf(
            MenuGroup("Simple", listOf("4/4", "3/4", "2/4", "2/2", "3/8", "5/4", "7/8", "5/8")),
            MenuGroup("Compound", listOf("6/8", "9/8", "12/8", "6/4")),
        )

    private fun list(): MenuList = MenuList(groups) {}

    @Test
    fun `every option of the list can be picked`() {
        val list = list()

        groups.flatMap { it.options }.forEach { option ->
            assertNotNull(spotOf(list, option), "the option $option can be picked")
        }
    }

    @Test
    fun `the options of a group sit two to a row`() {
        val list = list()

        val first = spotOf(list, "4/4")!!
        val second = spotOf(list, "3/4")!!
        val third = spotOf(list, "2/4")!!
        assertEquals(first.y, second.y, "the first two options of a row sit on the same line")
        assertTrue(first.x < second.x, "the first option of a row is on the left")
        assertTrue(third.y > second.y, "the third option of a row is on the line below")
    }

    @Test
    fun `the name of a group is not an option`() {
        val list = list()

        val last = spanOf(list, "5/8")!!
        val first = spanOf(list, "6/8")!!
        assertTrue(last.last < first.first, "the second group comes below the first one")
        for (y in last.last + 1 until first.first) {
            for (x in 0 until list.preferredSize.width) {
                assertNull(list.pickAt(x, y), "the name of the group picks nothing at $x,$y")
            }
        }
    }

    @Test
    fun `the list is as tall as its rows and its names`() {
        val list = list()

        val rows = groups.sumOf { (it.options.size + 1) / 2 }
        val height = list.preferredSize.height
        val width = list.preferredSize.width
        val wanted = rows * 28 + groups.size * 22
        assertTrue(height in wanted..(wanted + 16), "the list is as tall as its rows: $height")
        assertTrue(width in 80..140, "the list is not too wide: $width")
    }

    /**
     * The first place of the list where an option is picked.
     *
     * @param list the list.
     * @param option the option to look for.
     * @return the place of the option, or null when it is not picked anywhere.
     */
    private fun spotOf(
        list: MenuList,
        option: String,
    ): Spot? {
        val size = list.preferredSize
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                if (list.pickAt(x, y) == option) return Spot(x, y)
            }
        }
        return null
    }

    /**
     * The lines of the list where an option is picked.
     *
     * @param list the list.
     * @param option the option to look for.
     * @return the first and the last line of the option, or null when it is not picked anywhere.
     */
    private fun spanOf(
        list: MenuList,
        option: String,
    ): IntRange? {
        val size = list.preferredSize
        val lines = mutableListOf<Int>()
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                if (list.pickAt(x, y) == option) {
                    lines += y
                    break
                }
            }
        }
        if (lines.isEmpty()) return null
        return lines.first()..lines.last()
    }

    /** A place in the list. */
    private data class Spot(
        val x: Int,
        val y: Int,
    )
}
