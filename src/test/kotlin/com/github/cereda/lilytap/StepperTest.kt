// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The stepper: the control that holds a number and moves it. */
class StepperTest {
    /** A stepper that says "C4" and moves an octave at a time. */
    private fun stepper(
        initial: Double = 4.0,
        width: Int = 116,
    ): Stepper =
        Stepper(
            initial = initial,
            minValue = 0.0,
            maxValue = 8.0,
            stepSize = 1.0,
            width = width,
            format = { "C${it.toInt()}" },
        )

    /** A mouse event of the given kind at the given place. */
    private fun mouse(
        component: JComponent,
        kind: Int,
        x: Int,
        y: Int = 10,
    ): MouseEvent =
        MouseEvent(
            component,
            kind,
            System.currentTimeMillis(),
            0,
            x,
            y,
            1,
            false,
            MouseEvent.BUTTON1,
        )

    /** Presses and releases the mouse at the given place. */
    private fun click(
        component: JComponent,
        x: Int,
    ) {
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_PRESSED, x))
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_RELEASED, x))
    }

    /** Presses the mouse at one place, moves it to another and releases it there. */
    private fun drag(
        component: JComponent,
        from: Int,
        to: Int,
    ) {
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_PRESSED, from))
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_DRAGGED, to))
        component.dispatchEvent(mouse(component, MouseEvent.MOUSE_RELEASED, to))
    }

    @Test
    fun `the stepper writes its value`() {
        val stepper = stepper()
        assertEquals("C4", stepper.text)
        stepper.value = 6.0
        assertEquals("C6", stepper.text, "the value is written as it is formatted")
    }

    @Test
    fun `the left end of the stepper takes the value down`() {
        val stepper = stepper()

        click(stepper, 10)

        assertEquals(3.0, stepper.value, "the value went down")
    }

    @Test
    fun `the right end of the stepper takes the value up`() {
        val stepper = stepper()

        click(stepper, 105)

        assertEquals(5.0, stepper.value, "the value went up")
    }

    @Test
    fun `the stepper stays inside its range`() {
        val stepper = stepper(initial = 0.0)

        click(stepper, 10)

        assertEquals(0.0, stepper.value, "the value cannot go below the bottom")
    }

    @Test
    fun `the stepper tells whoever is watching when the user moves it`() {
        val stepper = stepper()
        val seen = mutableListOf<Double>()
        stepper.onChange = { seen.add(it) }

        click(stepper, 10)

        assertEquals(listOf(3.0), seen, "the user moved the value once")
    }

    @Test
    fun `the stepper is quiet when the app moves it`() {
        val stepper = stepper()
        val seen = mutableListOf<Double>()
        stepper.onChange = { seen.add(it) }

        stepper.value = 6.0

        assertTrue(seen.isEmpty(), "the app moved the value on its own")
    }

    @Test
    fun `a click on the value tells whoever is watching`() {
        val stepper = stepper()
        var clicks = 0
        stepper.onMiddleClick = { clicks++ }

        click(stepper, 58)

        assertEquals(1, clicks, "the value was clicked")
        assertEquals(4.0, stepper.value, "the click on the value does not move it")
    }

    @Test
    fun `a drag of the value moves it and is not a click`() {
        val stepper = stepper()
        var clicks = 0
        stepper.onMiddleClick = { clicks++ }

        drag(stepper, 58, 64)

        assertEquals(0, clicks, "the value was dragged, not clicked")
        assertEquals(5.0, stepper.value, "the drag took the value up")
    }

    @Test
    fun `a drag that does not move is still a click`() {
        val stepper = stepper()
        var clicks = 0
        stepper.onMiddleClick = { clicks++ }

        stepper.dispatchEvent(mouse(stepper, MouseEvent.MOUSE_PRESSED, 58))
        stepper.dispatchEvent(mouse(stepper, MouseEvent.MOUSE_DRAGGED, 58))
        stepper.dispatchEvent(mouse(stepper, MouseEvent.MOUSE_RELEASED, 58))

        assertEquals(1, clicks, "the value was clicked")
        assertEquals(4.0, stepper.value, "the value did not move")
    }

    @Test
    fun `a press after a drag starts from a clean slate`() {
        val stepper = stepper()
        var clicks = 0
        stepper.onMiddleClick = { clicks++ }

        drag(stepper, 58, 70)
        click(stepper, 10)

        assertEquals(0, clicks, "the click on the end is not a click on the value")
        assertEquals(5.0, stepper.value, "the end took the value down from where the drag left it")
    }

    @Test
    fun `the wheel moves the stepper`() {
        val stepper = stepper()
        val seen = mutableListOf<Double>()
        stepper.onChange = { seen.add(it) }

        val event =
            MouseWheelEvent(
                stepper,
                MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                0,
                58,
                10,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                1,
            )
        stepper.dispatchEvent(event)

        assertEquals(3.0, stepper.value, "the wheel took the value down")
        assertEquals(listOf(3.0), seen, "the user moved the value with the wheel")
    }

    @Test
    fun `a stepper with no middle click does not mind`() {
        val stepper = stepper()
        stepper.onMiddleClick = null

        click(stepper, 58)

        assertEquals(4.0, stepper.value, "nothing happened")
    }

    @Test
    fun `the stepper is as tall as the other controls`() {
        assertEquals(38, stepper().preferredSize.height)
    }
}
