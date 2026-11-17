// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

/** Entry point for the lilytap application. */
class Main : CliktCommand("lilytap") {
    override fun help(context: Context) = "Tap a rhythm along to a metronome and get LilyPond durations."

    init {
        subcommands(Run(), Gui())
    }

    override fun run() { // no-op: subcommands provide the entry points
    }
}

/** Application entry point: parses args and dispatches to the `run` or `gui` subcommand. */
@Suppress("MemberNameEqualsClassName")
fun main(args: Array<String>) {
    Main().main(args)
}
