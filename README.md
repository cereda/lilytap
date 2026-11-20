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

- **Tempo (BPM)** — beats per minute. The beat is the one you hear: the note value in a simple
  meter, the dotted quarter in a compound one. The round button next to it sets the tempo by
  tapping — see [Tap tempo](#tap-tempo).
- **Time** — the time signature, e.g. `4/4` or `6/8`. A menu beside the field offers the common
  meters; the field takes any other one, and its border turns red when the text is not a meter.
  The round button with the three dots beside the menu is the 3-feel — see
  [The 3-feel](#the-3-feel).
- **Bars** — how many bars to record after the one-bar count-in.
- **Cells / beat** — the grid resolution: 1, 2 or 4 cells per beat. In a compound meter the
  caption names the note the cells cut instead (e.g. `Cells / eighth` in 6/8), because there the
  beat is the dotted quarter.
- **Smallest rest** — silences shorter than this are absorbed into the previous note. The last
  option of the control, **No rests**, says that no rest is ever written — see
  [No rests](#no-rests).
- **Latency** — the delay of *your taps* (reaction time), subtracted from every tap.
- **Audio latency** — the delay of the *sound*, which shifts the metronome click earlier.
- **Pitch** — the placeholder pitch printed in the LilyPond output and used by Note playback.

After a take, the suggested latency is the configured one plus the timing error that is left.

## Tap tempo

The round button next to the tempo sets the tempo by tapping, the way every DAW does it:

- **Click the button in time.** The tempo field follows the taps, so you watch it settle.
- **Or press any key** while the button is armed (its ring turns violet). The keys belong to the
  tap round until it ends, so a text field keeps them: typing a pitch is never a tap.
- The tempo is the **median of the intervals** between the taps, so one slipped tap does not
  wreck the result.
- A pause of more than three and a half seconds ends the round: the next tap starts a new one.
  **Esc** gives up a round that is still listening.

The tempo stays inside the range the app can hold (20–300 BPM) and is rounded to a whole
number. The button is greyed out while a take runs or a score plays, like the other settings.

## No rests

The last option of the **Smallest rest** control — **No rests** — turns every silence into extra
time for the note before it, so the transcription never contains a rest: `c4 r4 c4 c4` becomes
`c2 c4 c4`. A silence before the very first note has no note to join, so it stays a rest. The
other three options say how short a silence may be before it becomes a rest of its own.

## The score strip

Below the timeline sits the score of the take: one block for every note and rest, drawn on the
same grid as the timeline, so a block sits exactly under the beat it belongs to. The block shows
its LilyPond text (`c4`, `r2`) when there is room for it.

- **Click a block** to select it. The selected block gets a ring around it.
- **Shift-click** a second block to select a range of blocks.
- **Double-click a block** to turn it into a rest, and double-click it again to turn it back
  into a note.
- **Drag the border between two blocks** to change their lengths: the note before the border
  grows and the one after it shrinks, or the other way round. The border snaps to the nearest
  grid cell, and bar lines pull it harder, so a note can be lined up exactly with the bar. Every
  block keeps at least one cell, and the bar always stays full.
- The **Revert** button puts the score back the way it was when the take ended. It is an edit
  like any other, so **Ctrl+Z** brings your work back if you press it by mistake.
- The eye icon next to "Raw taps" shows and hides the whole strip; the timeline expands to fill
  the space it leaves. Nothing is persisted, so the strip is visible again on the next launch.

The LilyPond text in the output box follows every edit. The edits are literal: a rest you leave
in stays a rest, whatever the "No rests" setting says - that setting applies to the take, not
to what you make of it afterwards.

### Editing the selected blocks

The strip has the keyboard after you click it (a thin line along its bottom edge says so), and
the header above it carries three buttons - **Split**, **Swap** and **Merge**, each with its own
icon - that do the same thing as the keys. A button is greyed out when the current selection does
not allow it, and the text next to the "Score" label says what is selected (`c2 selected`,
`3 blocks selected`) or what just happened (`c2 → c4 c4`): the blocks before the edit, an arrow,
and the blocks that are there now. A long merge says how many blocks it folded together
(`12 blocks → c1`).

| | |
|---|---|
| **S** - split | Cuts the selected block in half. The first half keeps the kind of the block and the second half becomes a note, so a silence that is split becomes a silence followed by a note (`r2` becomes `r4 c4`) and a note that is split is re-articulated (`c2` becomes `c4 c4`). |
| **X** - swap | Trades the two selected blocks. Each keeps its own length and moves in time, so a note that is swapped with the silence next to it keeps sounding for as long as it did - it just starts later. Two silences that end up touching are merged into one. |
| **M** - merge | Folds the selected blocks into one. A note wins over a silence: the silences inside the range are absorbed by the note, so a note followed by a silence becomes one longer note (`c2 r2` becomes `c1`). |
| **Ctrl+Z** - undo | Takes the last edit back. The selection and the message come back with it. |
| **Ctrl+Shift+Z** - redo | Does the edit that was undone again. **Ctrl+Y** does the same. |
| **←** **→** | Move the selection one block to the left or to the right. |
| **Esc** | Clear the selection. |

The message in the header always fits: a merge of many blocks says `12 blocks → c1` instead of
listing every one of them, and the full text is also in the tooltip of the message.

### Undoing an edit

The header carries two more buttons, an arrow that turns back and an arrow that turns forward.
They are greyed out when there is nothing to undo or to redo, so they say whether there is
anything to do. The keys do the same thing.

An undo takes one edit back at a time, and the selection and the message come back with it: the
header says what it said before the edit (`c2 selected` instead of `c2 → c4 c4`). A drag of a
border is one edit, not one per pixel, and a new take starts the history over. An edit made
after an undo throws away the edits that were undone - the history is a line, not a tree.

**Revert** is the panic button: it jumps all the way back to the score the take ended with. It is
an edit like any other, so **Ctrl+Z** brings your work back if you press it by mistake.

A silence is always one block: the strip never shows two rests in a row. That is why swapping two
blocks can merge two silences into one, and why splitting a silence turns its second half into a
note. (The LilyPond text still spells a long silence with several rests when the meter asks for
it, e.g. `r4. r8` in 6/8.)

### Compound meters

In a compound meter - 6/8, 9/8, 12/8, 6/4... - the beat is a dotted note: three of the note values
the time signature names. Everything that counts beats follows that beat:

- the **tempo**: 60 BPM in 6/8 is 60 dotted quarters a minute, so a bar lasts two seconds;
- the **click**: the metronome clicks the dotted quarter (twice a bar in 6/8), and the count-in is
  one bar of those clicks;
- the **beat dots** and the bar/beat counter in the status line;
- the **LilyPond text**: a duration is written inside a group of three, so it never straddles the
  division.

| in 6/8 | the text |
|---|---|
| a note of four eighths | `c4. ~ c8` (a dotted quarter tied to an eighth) |
| a silence of four eighths | `r4. r8` (a dotted quarter and an eighth) |
| a note that fills the bar | `c4. ~ c4.` |
| a note of three eighths | `c4.` (a dotted quarter - it fills a group) |

The **grid** does not move: the cells still cut the note value (the eighth in 6/8), so the eighths
of a 6/8 bar land exactly on the grid, and the setting says so - its caption reads `Cells / eighth`
instead of `Cells / beat`.

A simple meter (4/4, 3/4, 2/4) is written the way it always was, and an irregular one (5/8, 7/8)
is left alone: those meters can be grouped in more than one way, and the app does not guess. The
blocks of the strip keep their own durations either way - a block of four eighths still says `c2`,
while the text spells it the way the meter wants it.

### The 3-feel

The round button with the three dots beside the time signature makes the click fall on every note
value of a compound meter instead of on the beat: in 6/8 the click is on every eighth, not on the
dotted quarter, so the subdivision of the beat is audible.

The beat itself does not move. The tempo still counts dotted quarters, the count-in is still one
bar, the beat dots still show the two beats of the bar, and the score is written the same way.
Only the click is finer — and the accent still falls on the beat, so the click and the beat dots
agree: in 6/8 the click is *click click click* with the accent on the first and the fourth.

The downbeat has a sound of its own: a low, long click, so that the bar is audible and not only
the grouping of three. Without it, six eighths a bar would be heard as two groups of three with
no bar line — which is what a compound meter is not. Every other meter keeps the accent it has
always had: there the click is the beat, and the accent marks the downbeat as it always did.

The button is greyed out in a simple meter (4/4, 3/4, 2/4): there the click is already on the
note value, so there is nothing to change. It keeps its setting when the meter changes, so a
compound meter that comes back is still in 3-feel.

## Playback

The **Play** button in the timeline header plays the edited score back through the audio output.
The pitch setting sets the tone, and a playhead moves across both the timeline and the score
strip as the score plays. Pressing **Play** scrolls the view back to the beginning, and long
scores scroll along with the playhead so it stays on screen until the end. Click the button again
(it becomes **Stop**) to halt playback. Playback uses the configured audio latency so the sound
lines up with the playhead: every note is written to the audio output that much earlier, and the
playhead waits for the same head start before it moves, so the first note lands with the rest.
Nothing is played if there is no score yet.

The sound button next to **Play** (a note icon for the score's pitch, a metronome icon for a
click) switches the playback sound: **Note** is the score's pitch, while **Beat** replaces each
note onset with a short percussive click (accented on bar downbeats) and leaves rests silent. Beat
mode makes the rhythm easier to follow when the pitch is only a placeholder. Editing a block
stops playback, so the sound and the blocks always agree.

While a score is playing, the settings and the **Calibrate** button are disabled: they describe
the next take, not the one that is playing, and a calibration would beep over the playback. The
header button stays live and becomes **Stop**.

## Audio latency

On some systems the click reaches your ears noticeably later than the visual playhead moves. The
**Audio latency** setting shifts the click earlier so it sounds on time. The **Calibrate** button
measures it for you: the metronome plays a count-in, you tap along with the beeps, and the measured
error is added to the current setting. Only the beeps are shown while calibrating — the timeline
stays empty and the beat dots are hidden, on purpose: a guide that is out of sync with the audio
is exactly what you are trying to measure. The round arrow next to a latency setting resets it to
zero.

The calibration always runs on a grid of its own: 60 BPM, one bar of count-in and two bars to tap
along with, whatever the tempo and the length of the take are. The slow grid leaves room around
every beat for the whole output delay, so a tap that answers a beep which reached your ears late
still belongs to the beat it followed. The result is the median of your tap offsets, so a single
stray tap or one missed beat cannot move it, and the status shows how many taps you have given.
The measurement includes your own reaction time, so it reads a little higher than the raw output
delay: lower it a few steps if the click sounds early.

Outside calibration, the beat dots and the status text follow the beat itself, so they line up
with the playhead rather than with the moment the click is written to the sound card.

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
