// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import java.awt.GraphicsEnvironment
import kotlin.system.exitProcess

/** Application entry point: opens the lilytap window. */
fun main() {
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println("lilytap: no graphical display available.")
        exitProcess(1)
    }
    launchWindow()
}
