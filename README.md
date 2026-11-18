# lilytap

Tap a rhythm along to a metronome and get LilyPond durations.

## Build and run

```bash
./gradlew test
./gradlew installDist
build/install/lilytap/bin/lilytap run --bpm 60 --time 4/4 --bars 4
build/install/lilytap/bin/lilytap gui        # window version (detects held keys)
```

Run the installed script, not `./gradlew run` (Gradle swallows the terminal).

The CLI has two subcommands:

- `run` — terminal version: tap along to the metronome, print LilyPond durations.
- `gui` — Swing window version: custom-painted UI with held-key detection.

## Terminal version (`run`): keys while recording

- any key: a note starts here (it lasts until your next tap)
- `r`: a rest starts here
- `q`: abort

## Window version (`gui`)

A dark, custom-painted UI (no extra dependencies, icons are drawn in code):
steppers you can click, hold, scroll or drag, a sliding option picker, a beat indicator, a glowing
playhead over the timeline, and a Copy button that confirms with a check mark.

Light and dark themes (sun/moon button in the header). The theme follows your OS setting on every
launch; the sun/moon button switches it for the session. **Ctrl+Enter** (Cmd+Enter on macOS) starts
a take; Esc aborts.

After a take, the **Raw taps** button (off by default) shows what you actually played as ghost
bars under the notes snapped to the grid (amber line = pressed late, teal = early).

Press = note starts, release = note ends, `r` = a rest starts (it lasts until the next press).
A silence after a release becomes a rest only if it is at least "Smallest rest" long; shorter gaps
are absorbed into the previous note. Esc aborts.
On Linux/X11, if held notes come out chopped up, run `xset r off` to disable key auto-repeat.

### No rests

The **No rests** checkbox turns every silence into extra time for the note before it, so the
transcription never contains a rest: `c4 r4 c4 c4` becomes `c2 c4 c4`. A silence before the very
first note has no note to join, so it stays a rest. While the box is ticked, "Smallest rest" is
greyed out — it has nothing left to do.

### Audio latency

On some systems the click reaches your ears noticeably later than the visual playhead moves. The
**Audio latency** setting shifts the click earlier so it sounds on time. The **Calibrate** button
measures it for you: the metronome plays a count-in, you tap along with the beeps, and the measured
average is written into the setting. Only the beeps and the beat dots are shown while calibrating —
the timeline stays empty on purpose, since a playhead that is out of sync with the audio is exactly
what you are trying to measure. The round arrow next to a latency setting resets it to zero.

Nothing is written to disk: the theme follows your OS setting on every launch, and the audio
latency starts at zero.

## Options

`--bpm N` `--time N/M` `--bars N` `--subdivisions N` `--latency MS` `--pitch TEXT`
`--audio-latency MS` `--no-rests`

`--subdivisions` is the number of grid cells per beat: 1, 2, 4 or 8 (anything that divides the
beat evenly). The values are checked: an invalid number or time signature is reported as a usage
error, and `lilytap run --help` lists every option.

`--latency` is the delay of *your taps* (it is subtracted from every tap), `--audio-latency` is
the delay of the *sound* (it shifts the metronome click earlier so that it is heard on time).
After a take, the suggested latency is the configured one plus the timing error that is left.

`--no-rests` never writes a rest: every silence extends the note before it (a silence before the
first note stays a rest). It is the same thing as the "No rests" checkbox in the window version.

## Layout

`Rhythm.kt` is the pure transcription logic (taps become a score). `Score.kt` is the score
itself: the notes and rests, the LilyPond text and the edits that can be made to them. `Run.kt`
is the terminal CLI, `WindowApp.kt` the window (layout + recording logic), `Timeline.kt` the
recording timeline, `Widgets.kt` the widget kit (buttons, steppers, etc.), `Theme.kt` the
colours and `Glyph.kt` the vector icons. `Main.kt`, `Run.kt` and `Gui.kt` form the Clikt CLI
entry point.

Kotlin sources must live under `src/main/kotlin/`, tests under `src/test/kotlin/`.

## Verification

`./gradlew test` runs the unit tests. `./gradlew detekt` and `./gradlew spotlessCheck` enforce
the project's static analysis and formatting gates. `./gradlew check` adds the changelog format
check and the kover coverage floor (80% of the lines of the public API; the Swing UI and the
interactive CLI/audio paths are excluded because they need a display, a terminal and a sound card).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full history. The latest release is
[v0.2.1](https://github.com/cereda/lilytap/releases/tag/v0.2.1).
