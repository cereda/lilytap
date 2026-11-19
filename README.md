# lilytap

Tap a rhythm along to a metronome and get LilyPond durations.

## Build and run

Java 17 or newer is required.

```bash
./gradlew shadowJar
java -jar build/libs/lilytap.jar
```

The fat jar opens the window directly. Alternatively, build the installed distribution:

```bash
./gradlew installDist
build/install/lilytap/bin/lilytap
```

`./gradlew test` runs the unit tests.

## The window

A dark, custom-painted UI (no extra dependencies, icons are drawn in code):
steppers you can click, hold, scroll or drag, a sliding option picker, a beat indicator, a glowing
playhead over the timeline, and a Copy button that confirms with a check mark.

Light and dark themes (sun/moon button in the header). The theme follows your OS setting on every
launch; the sun/moon button switches it for the session. **Ctrl+Enter** (Cmd+Enter on macOS) starts
a take; Esc aborts.

Press = note starts, release = note ends, `r` = a rest starts (it lasts until the next press).
A silence after a release becomes a rest only if it is at least "Smallest rest" long; shorter gaps
are absorbed into the previous note. Esc aborts.
On Linux/X11, if held notes come out chopped up, run `xset r off` to disable key auto-repeat.

## Options

- **Tempo (BPM)** — beats per minute, counted in the time signature's denominator.
- **Time** — the time signature, e.g. `4/4` or `6/8`.
- **Bars** — how many bars to record after the one-bar count-in.
- **Cells / beat** — the grid resolution: 1, 2 or 4 cells per beat.
- **Smallest rest** — silences shorter than this are absorbed into the previous note.
- **No rests** — never write a rest: every silence extends the note before it.
- **Latency** — the delay of *your taps* (reaction time), subtracted from every tap.
- **Audio latency** — the delay of the *sound*, which shifts the metronome click earlier.
- **Pitch** — the placeholder pitch printed in the LilyPond output and used by Note playback.

After a take, the suggested latency is the configured one plus the timing error that is left.

## No rests

The **No rests** checkbox turns every silence into extra time for the note before it, so the
transcription never contains a rest: `c4 r4 c4 c4` becomes `c2 c4 c4`. A silence before the very
first note has no note to join, so it stays a rest. While the box is ticked, "Smallest rest" is
greyed out — it has nothing left to do.

## The score strip

Below the timeline sits the score of the take: one block for every note and rest, drawn on the
same grid as the timeline, so a block sits exactly under the beat it belongs to. The block shows
its LilyPond text (`c4`, `r2`) when there is room for it.

- **Click a block** to turn it into a rest, and click it again to turn it back into a note.
- **Drag the border between two blocks** to change their lengths: the note before the border
  grows and the one after it shrinks, or the other way round. The border snaps to the nearest
  grid cell, and bar lines pull it harder, so a note can be lined up exactly with the bar. Every
  block keeps at least one cell, and the bar always stays full.
- The **Revert** button puts the score back the way it was when the take ended.
- The eye icon next to "Raw taps" shows and hides the whole strip; the timeline expands to fill
  the space it leaves. Nothing is persisted, so the strip is visible again on the next launch.

The LilyPond text in the output box follows every edit. The edits are literal: a rest you leave
in stays a rest, whatever the "No rests" setting says - that setting applies to the take, not
to what you make of it afterwards.

## Playback

The **Play** button in the timeline header plays the edited score back through the audio output.
The pitch setting sets the tone, and a playhead moves across both the timeline and the score
strip as the score plays. Pressing **Play** scrolls the view back to the beginning, and long
scores scroll along with the playhead so it stays on screen until the end. Click the button again
(it becomes **Stop**) to halt playback. Playback uses the configured audio latency so the sound
lines up with the playhead. Nothing is played if there is no score yet.

The sound button next to **Play** (a note icon for the score's pitch, a metronome icon for a
click) switches the playback sound: **Note** is the score's pitch, while **Beat** replaces each
note onset with a short percussive click (accented on bar downbeats) and leaves rests silent. Beat
mode makes the rhythm easier to follow when the pitch is only a placeholder. Editing a block
stops playback, so the sound and the blocks always agree.

## Audio latency

On some systems the click reaches your ears noticeably later than the visual playhead moves. The
**Audio latency** setting shifts the click earlier so it sounds on time. The **Calibrate** button
measures it for you: the metronome plays a count-in, you tap along with the beeps, and the measured
error is added to the current setting. Only the beeps and the beat dots are shown while calibrating
— the timeline stays empty on purpose, since a playhead that is out of sync with the audio is
exactly what you are trying to measure. The round arrow next to a latency setting resets it to zero.

Nothing is written to disk: the theme follows your OS setting on every launch, and the audio
latency starts at zero.

## Layout

`Rhythm.kt` is the pure transcription logic (taps become a score). `Score.kt` is the score
itself: the notes and rests, the LilyPond text and the edits that can be made to them.
`GridView.kt` is the grid the timeline and the score strip share, `NotationPanel.kt` the score
strip itself. `WindowApp.kt` is the window (layout + recording logic), `Timeline.kt` the
recording timeline, `Player.kt` playback, `Metronome.kt` the count-in and calibration click,
`Sound.kt` the click waveform, `Widgets.kt` the widget kit (buttons, steppers, etc.),
`Theme.kt` the colours and `Glyph.kt` the vector icons. `Main.kt` is the entry point and opens
the window.

Kotlin sources must live under `src/main/kotlin/`, tests under `src/test/kotlin/`.

## Verification

`./gradlew test` runs the unit tests. `./gradlew detekt` and `./gradlew spotlessCheck` enforce
the project's static analysis and formatting gates. `./gradlew check` adds the changelog format
check and the kover coverage floor (80% of the lines of the public API; the Swing UI and the
audio paths are excluded because they need a display and a sound card).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full history, and the
[releases page](https://github.com/cereda/lilytap/releases) for tagged versions.
