// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

/** `gui` subcommand: launch the Swing window version (detects held keys). */
class Gui : CliktCommand("gui") {
    override fun help(context: Context) = "Launch the Swing window version (detects held keys)."

    override fun run() {
        launchWindow()
    }
}
