// SPDX-License-Identifier: MIT
package com.github.cereda.lilytap

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.system.exitProcess

/**
 * `run` subcommand: record taps in the terminal and print LilyPond durations.
 *
 * While recording: any key starts a note, `r` starts a rest, `q` aborts.
 */
class Run : CliktCommand("run") {
    override fun help(context: Context) = "Record taps in the terminal and print LilyPond durations."

    val bpm by option("--bpm", help = "Beats per minute.").double().default(60.0)

    val time by
        option("--time", help = "Time signature, e.g. 4/4 or 6/8.")
            .convert("N/M") { raw ->
                try {
                    TimeSignature.parse(raw)
                } catch (e: IllegalArgumentException) {
                    fail(e.message ?: "invalid time signature")
                }
            }.default(TimeSignature(4, 4))

    val bars by option("--bars", help = "Bars to record after the count-in.").int().default(4)

    val subdivisions by option("--subdivisions", help = "Grid cells per beat.").int().default(2)

    val latency by
        option("--latency", help = "Delay subtracted from your taps, in ms.").double().default(0.0)

    val pitch by option("--pitch", help = "Placeholder pitch in the LilyPond output.").default("c")

    val audioLatencyMs by
        option("--audio-latency", help = "Audio output latency, in ms.").double().default(0.0)

    override fun run() {
        val config =
            try {
                Config(
                    bpm = bpm,
                    timeSignature = time,
                    subdivisions = subdivisions,
                    bars = bars,
                    latencyMs = latency,
                    pitch = pitch,
                )
            } catch (e: IllegalArgumentException) {
                val message = e.message ?: "Invalid settings"
                throw UsageError(message)
            }
        val taps =
            Recorder(config, audioLatencyMs).record() ?: run {
                println("Aborted.")
                return
            }
        val result = transcribe(taps, config)

        println()
        println(result.lilypond)
        println()

        val offset = result.meanOffsetMs
        if (offset != null && taps.size >= 4 && abs(offset) >= 15) {
            val direction = if (offset > 0) "late" else "early"
            // The offset is the error that is left after the configured latency, so the corrected
            // latency is the configured one plus that error.
            val suggested = config.latencyMs + offset
            println("Hint: your taps were ${abs(offset).roundToLong()} ms $direction on average.")
            println("      Try --latency ${suggested.roundToLong()} to compensate.")
        }
    }

    /** Runs the count-in + recording session and returns the taps (or null if aborted). */
    private class Recorder(
        private val config: Config,
        private val audioLatencyMs: Double,
    ) {
        private val beats = config.timeSignature.beats

        fun record(): List<Tap>? {
            val terminal = TerminalBuilder.builder().system(true).build()
            if (terminal.type.startsWith(Terminal.TYPE_DUMB)) {
                terminal.close()
                System.err.println(
                    "No interactive terminal detected. Run the installed script " +
                        "(build/install/lilytap/bin/lilytap) rather than `gradle run`.",
                )
                exitProcess(1)
            }

            val metronome = Metronome()
            val out = terminal.writer()

            fun status(text: String) {
                out.print("\r$text\u001b[K")
                out.flush()
            }

            println(
                "${config.bpm} BPM, ${config.timeSignature}, ${config.bars} bar(s), " +
                    "grid = 1/${config.subdivisions} beat",
            )
            println("Any key = note, r = rest, q = abort. Starting in 1 second...")
            if (!metronome.hasAudio) println("(No audio output available: using terminal bell.)")

            val beatNanos = (60_000_000_000.0 / config.bpm).roundToLong()
            val start = System.nanoTime() + 1_000_000_000L
            val origin = start + beats * beatNanos // first recorded beat
            val end = origin + config.bars * beats * beatNanos
            val tapCount = AtomicInteger()

            val saved = terminal.enterRawMode()
            val taps = ArrayList<Tap>()
            var aborted = false

            val ticker =
                thread(isDaemon = true, name = "metronome") {
                    metronome.run(start, beatNanos, beats + config.bars * beats, beats, audioLatencyMs) { i ->
                        if (i < beats) {
                            status("Count-in: ${beats - i}")
                        } else {
                            val r = i - beats
                            status(
                                "REC  bar ${r / beats + 1}/${config.bars}  beat ${r % beats + 1}   " +
                                    "taps: ${tapCount.get()}",
                            )
                        }
                    }
                }

            try {
                val reader = terminal.reader()
                var done = false
                while (!done) {
                    val remainingMs = (end - System.nanoTime()) / 1_000_000
                    if (remainingMs <= 0) {
                        done = true
                    } else {
                        val c = reader.read(remainingMs)
                        val now = System.nanoTime()
                        when {
                            c == NonBlockingReader.READ_EXPIRED -> { }

                            c == NonBlockingReader.EOF || c == 3 || c == 'q'.code -> {
                                aborted = true
                                done = true
                            }

                            c == 27 -> {
                                drainEscapeSequence(reader)
                            }

                            // arrow keys etc. are not taps
                            else -> {
                                val kind = if (c == 'r'.code || c == 'R'.code) TapKind.REST else TapKind.NOTE
                                taps += Tap((now - origin) / 1_000_000.0, kind)
                                tapCount.incrementAndGet()
                            }
                        }
                    }
                }
            } finally {
                ticker.interrupt()
                terminal.setAttributes(saved)
                out.print("\r\u001b[K")
                out.flush()
                metronome.close()
                terminal.close()
            }
            return if (aborted) null else taps
        }

        private fun drainEscapeSequence(reader: NonBlockingReader) {
            while (reader.read(10) >= 0) { /* swallow */ }
        }
    }
}
