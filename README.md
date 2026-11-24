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
playhead over the timeline, a copy button that confirms with a check mark, and a button with a step
that writes the octaves of the LilyPond text relative to the note before.

The bottom of the window is split in two: the **Pitches** panel takes two thirds of the width and
carries a piano that writes the melody of the take, and the LilyPond text takes the third that is
left. A legend above the Start button says what the colours of the raw taps mean and which keys
play the piano. Until there is a transcription, the box says that it will appear there - the
sentence is wrapped over two lines, so it fits in the narrow card.

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
- **Key** — the key the piece is in, e.g. `B♭ major` or `F♯ minor`. The notes are spelled in it
  and the LilyPond text declares it — see [The key of the piece](#the-key-of-the-piece).

The **pitch** of the notes is not a setting but an edit: every note of the score carries its own
pitch, and the strip lets you set it — see [The pitch of a note](#the-pitch-of-a-note).

Every input is read before a take begins. A name that cannot be read — a time signature that is
not a meter, a key that is not a key, a pitch that is not a note — stops the take and says what
is wrong, instead of running the take on the last good value.

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
time for the note before it, so the transcription never contains a rest: `c'4 r4 c'4 c'4` becomes
`c'2 c'4 c'4`. A silence before the very first note has no note to join, so it stays a rest. The
other three options say how short a silence may be before it becomes a rest of its own.

## The key of the piece

The **Key** setting says the key the piece is in. The name is a letter, an optional accidental and
the mode (`major` or `minor`, `maj` and `min` are read as well), and the accidental can be written
in any of the ways it is written: `C# major`, `C♯ major`, `cis major`, `Bb major`, `B♭ major`,
`bes minor`. The field writes the name back the way the app writes every other pitch, with the
sign of the accidental: `B♭ major`. The field takes any name at all, and its border turns red
when the text is not the name of a key. A key whose signature carries more than seven accidentals
is refused too, and the field says so: `G♯ major` has eight. The name is read when it is
finished, with **Enter** or when the field loses the focus.

Beside the field, a button opens the **circle of fifths**: every key of the piece around a ring,
C major at the top, the sharps climbing to the right and the flats coming back on the left.
Outside the ring are the major keys and inside them the minor keys that have the same signature,
the relative minors, written with an `m` as in a circle of fifths chart (`C` outside, `Am`
inside). Click the half of the position you want — the outer one for the major key, the inner one
for the minor key — and the field takes the name. The key the piece is in is written in the
middle of the circle and lit where it stands; clicking the middle keeps that key.

The key has three parts:

- **The notes are spelled in it.** In F major the black key between the C and the D is a D♭ and
  the text writes `des`, not `cis`; in E major the same key is a D♯, and the text writes `dis`. A
  note keeps its sound when it is respelled: only its name changes, and a name that crosses the C
  takes its octave with it (a B♯4 is a C5).
- **The LilyPond text declares it.** A key other than C major adds a line to the text
  (`\key f \major`), at the top of the text or inside the `\relative` block. C major says nothing,
  the way it always did.
- **The tracker and the piano play it.** The top row of the keyboard (`Q W E R T`) plays the
  accidentals of the key: the sharps in a sharp key and the flats in a flat one, and the legend
  above the Start button says which of the two it is.

The seventh of a minor key is the one exception to the spelling: it is raised, so in D minor the
C is a C♯ and not a D♭.

Changing the key is an edit like any other: **Ctrl+Z** takes it back, the message in the header
of the score strip says what changed (`C major → F major`), and **Revert** puts the take back the
way it was recorded. The **Pitch** field is not touched by it: a name that is typed there is the
name you wrote.

Nothing is persisted: the key is C major on every launch.

## The score strip

Below the timeline sits the score of the take: one block for every note and rest, drawn on the
same grid as the timeline, so a block sits exactly under the beat it belongs to. The block shows
the pitch of the note (`C4`, `C♯5`) when there is room for it, and a band at its foot says how
long the note is: the band has a colour of its own for each of the four lengths — the sixteenths
and the shorter values, the eighths, the quarters, and the halves and the longer values — so the
length of a note reads at a glance. Hovering a block says the pitch and the exact duration. The
exact duration is also in the width of the block and in the LilyPond text.

- **Click a block** to select it. The selected block gets a ring around it, in the colour of the
  text, with a faint glow around the ring, so it stands out from the block it surrounds and from
  the strip around it. The ring follows the shape of the block and touches it: no corner of the
  block, and no sliver of the strip, shows between the two.
- **Shift-click** a second block to select a range of blocks.
- The selection goes when you play the score, and when you click anything that is not the editor
  (the strip, the pitch field and the edit buttons): the ring would only be in the way.
- **Double-click a block** to turn it into a rest, and double-click it again to turn it back
  into a note.
- **Drag the border between two blocks** to change their lengths: the note before the border
  grows and the one after it shrinks, or the other way round. The border snaps to the nearest
  grid cell, and bar lines pull it harder, so a note can be lined up exactly with the bar. Every
  block keeps at least one cell, and the bar always stays full.
- The **Revert** button puts the score back the way it was when the take ended. It is the one
  button in red, because it throws your edits away. It is an edit like any other, so **Ctrl+Z**
  brings your work back if you press it by mistake.
- The eye icon next to "Raw taps" shows and hides the whole strip; the timeline expands to fill
  the space it leaves. Nothing is persisted, so the strip is visible again on the next launch.

The LilyPond text in the output box follows every edit. The edits are literal: a rest you leave
in stays a rest, whatever the "No rests" setting says - that setting applies to the take, not
to what you make of it afterwards.

### Editing the selected blocks

The strip has the keyboard after you click it (a thin line along its bottom edge says so), and
the header above it carries three buttons - **Split**, **Swap** and **Merge**, each with its own
icon - that do the same thing as the keys. A button is greyed out when the current selection does
not allow it, and the message in the header says what is selected (`c'2 selected`,
`3 blocks selected`) or what just happened (`c'2 → c'4 c'4`): the blocks before the edit, an arrow,
and the blocks that are there now. A long merge says how many blocks it folded together
(`12 blocks → c'1`). The header has no title of its own: the strip below it says what it is.

| | |
|---|---|
| **S** - split | Cuts the selected block in half. The first half keeps the kind of the block and the second half becomes a note, so a silence that is split becomes a silence followed by a note (`r2` becomes `r4 c'4`) and a note that is split is re-articulated (`c'2` becomes `c'4 c'4`). |
| **X** - swap | Trades the two selected blocks. Each keeps its own length and moves in time, so a note that is swapped with the silence next to it keeps sounding for as long as it did - it just starts later. Two silences that end up touching are merged into one. |
| **M** - merge | Folds the selected blocks into one. A note wins over a silence: the silences inside the range are absorbed by the note, so a note followed by a silence becomes one longer note (`c'2 r2` becomes `c'1`). |
| **Ctrl+Z** - undo | Takes the last edit back. The selection and the message come back with it. |
| **Ctrl+Shift+Z** - redo | Does the edit that was undone again. **Ctrl+Y** does the same. |
| **←** **→** | Move the selection one block to the left or to the right. |
| **Esc** | Clear the selection. |

The message in the header always fits: a merge of many blocks says `12 blocks → c'1` instead of
listing every one of them, and a message that is still too wide is shortened with an ellipsis
(`ces'4 → ces'8…`). The full text is always in the tooltip of the message. The message never
makes the window wider, so the timeline and the score strip always keep their place.

### The pitch of a note

Every note of the score carries its own pitch. A take says *when* the notes sound, not which ones
they are, so they all start on middle C; the pitch is set afterwards, on the score itself.

- **Click a block** to select it, then type its pitch in the **Pitch** field of the header and
  press **Enter**. The field takes the name the way LilyPond writes it: `c'` (middle C), `cis'`
  (a sharp), `ces'` (a flat), `c''` (an octave up), `c` (an octave down). A name that cannot be
  read turns the border of the field red.
- The strip shows the pitch in scientific notation (`C4`, `C♯5`, `C♭3`), where the digit is
  always the octave. The LilyPond text and the field keep LilyPond's own spelling (`cis'`), which
  puts `c` in the octave *below* middle C: LilyPond's middle C is `c'`.
- Setting the pitch is an edit like any other: **Ctrl+Z** takes it back, and the message in the
  header says what changed (`C4 → C♯5`).
- The field is emptied once the pitch is written: it waits for the next note of the melody, and
  the cursor has already moved on to it — see [The pitches panel](#the-pitches-panel).
- Splitting a note keeps its pitch in both halves, merging keeps the pitch of the first note of
  the range, and swapping moves the pitch with the note.

### The pitches panel

Below the score strip sits the **Pitches** panel: a piano that writes the pitch of the selected
note, and a counter that says where the cursor is among the notes of the take (`3 / 12`).

- **Click a key** of the piano to give it to the selected note.
- **Play it on the keyboard** instead: the number row names the notes of the scale and the top
  row the accidentals of the key. The keys of the strip (**S**, **X**, **M**) keep their meaning,
  so the tracker lives on the rows above them.

  | | |
  |---|---|
  | `1` `2` `3` `4` `5` `6` `7` | the notes of the scale: C, D, E, F, G, A, B |
  | `Q` `W` `E` `R` `T` | the accidentals of the same octave: the sharps in a sharp key, the flats in a flat one |
  | `8` `9` | an octave down, an octave up |
  | `0` | back to the octave of the note you are on |

- The keys play **the octave the panel shows** (`C4` at the start), not a fixed one: the same
  `1` is a C4, a C5 or a C3, depending on where the tracker is. The piano marks that octave with
  bright letters and the others with dim ones, and the keyboard follows your hand: clicking a
  key of the piano puts the tracker in its octave, and clicking a block of the strip puts it in
  the octave of that block. The walk that follows a written pitch does not move it, so an octave
  you moved to by hand survives the melody.
- The **stepper** next to **Fill the melody** says the same octave (`C4`) and moves it with the
  mouse: `−` takes it down, `+` takes it up, and a click on the number puts the tracker back on
  the octave of the note you are on — the way the `0` key does. Dragging the number moves the
  octave as well, and the wheel turns it.
- The number row climbs to the right, so the smaller of the two octave keys is the lower one:
  `8` is the octave down and `9` the octave up, like the piano, whose low keys are on the left.
- The **cursor walks the melody**: every pitch that is written - by a click, by a tracker key or in
  the pitch field - moves it to the next note, and the silences are stepped over, because a rest
  has no pitch. The counter says where the cursor is, and the message in the header of the strip
  says what the last edit did (`C4 → C♯4`).
- **Fill the melody** takes the cursor to the first note of the take. A tracker key with nothing
  selected starts the walk there as well, so the first pitch of the melody does not need a click.
- A pitch on a silence turns it into a note: the gap keeps its length and sounds the note you
  gave it.
- The piano is an editor, not an instrument: it is greyed out while a take is being recorded and
  while the score plays. While the score plays the key that is sounding is lit, and the notes of
  the take stay lit faintly.
- The tracker keys are ignored while a text field has the keyboard: there the keys are text, not
  pitches. Click the strip (or press **Tab**) to give the keyboard back to the piano.

### Undoing an edit

The header carries two more buttons, an arrow that turns back and an arrow that turns forward.
They are greyed out when there is nothing to undo or to redo, so they say whether there is
anything to do. The keys do the same thing.

An undo takes one edit back at a time, and the selection and the message come back with it: the
header says what it said before the edit (`c'2 selected` instead of `c'2 → c'4 c'4`). A drag of a
border is one edit, not one per pixel, and a new take starts the history over. An edit made
after an undo throws away the edits that were undone - the history is a line, not a tree.

**Revert** is the panic button: it jumps all the way back to the score the take ended with. It is
the word in red at the end of the header, because it throws the edits away. It is an edit like any
other, so **Ctrl+Z** brings your work back if you press it by mistake.

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
| a note of four eighths | `c'4. ~ c'8` (a dotted quarter tied to an eighth) |
| a silence of four eighths | `r4. r8` (a dotted quarter and an eighth) |
| a note that fills the bar | `c'4. ~ c'4.` |
| a note of three eighths | `c'4.` (a dotted quarter - it fills a group) |

The **grid** does not move: the cells still cut the note value (the eighth in 6/8), so the eighths
of a 6/8 bar land exactly on the grid, and the setting says so - its caption reads `Cells / eighth`
instead of `Cells / beat`.

A simple meter (4/4, 3/4, 2/4) is written the way it always was, and an irregular one (5/8, 7/8)
is left alone: those meters can be grouped in more than one way, and the app does not guess. The
blocks of the strip keep their own durations either way - a block of four eighths still says `c'2`,
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

## The LilyPond text

The text in the output box follows the score, one bar per line. The button with the step beside
the copy button chooses how the octaves are written; it lights up in the accent colour when the
relative mode is on.

By default every note carries its own octave marks, and the first line of the text says so:

```
% absolute
c'4 d'4 e'4 f'4 |
```

With the relative mode on, the text is a `\relative` block: every note is written relative to the note
before it, the way LilyPond reads them. The first note of the block is still written in full,
because there is no note before it.

```
% relative
\relative {
c'4 d e f |
}
```

LilyPond puts a note in the octave that is nearest to the one before it, counting the letters of
the scale and not the accidentals: a note that would be a fifth or more away goes the other way
instead, and the marks (`'`, `,`) add whole octaves to the octave that was worked out. A rest
carries no pitch and does not move the reference, so the note after a silence is still read from
the note before the silence. The comment on the first line says which way the octaves are
written, so a fragment that is copied into a file says what it is.

The choice is yours for the session: nothing is remembered, and the app starts in absolute mode
every time it is launched.

The key of the piece is declared in the text, at the top of it or inside the `\relative` block:

```
% absolute
\key bes \major
c'4 des'4 ees'4 f'4 |
```

C major says nothing: the text of a piece in C major is the text it always was.

## Playback

The **Play** button in the timeline header plays the edited score back through the audio output.
Each note sounds its own pitch, and a playhead moves across both the timeline and the score
strip as the score plays. Pressing **Play** scrolls the view back to the beginning, and long
scores scroll along with the playhead so it stays on screen until the end. Click the button again
(it becomes **Stop**) to halt playback. Playback uses the configured audio latency so the sound
lines up with the playhead: every note is written to the audio output that much earlier, and the
playhead waits for the same head start before it moves, so the first note lands with the rest.
Nothing is played if there is no score yet.

The sound button next to **Play** (a note icon for the score's pitch, a metronome icon for a
click) switches the playback sound: **Note** plays the pitches of the score, while **Beat**
replaces each note onset with a short percussive click (accented on bar downbeats) and leaves
rests silent. Beat mode makes the rhythm easier to follow when the melody does not matter.
Editing a block stops playback, so the sound and the blocks always agree.

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
strip itself. `PianoPanel.kt` is the piano of the pitches panel, `PitchesPanel.kt` the panel that
holds it, and `Tracker.kt` the keys of the keyboard that play it and the octave they play.
`LegendPanel.kt` is the legend above the Start button. `WindowApp.kt` is the window (layout +
recording logic), `Timeline.kt` the recording timeline, `Player.kt` playback, `Metronome.kt` the
count-in and calibration click, `Sound.kt` the click waveform, `Widgets.kt` the widget kit
(buttons, steppers, etc.), `Theme.kt` the colours and `Glyph.kt` the vector icons. `Main.kt` is
the entry point and opens the window.

Kotlin sources must live under `src/main/kotlin/`, tests under `src/test/kotlin/`.

## Verification

`./gradlew test` runs the unit tests. `./gradlew detekt` and `./gradlew spotlessCheck` enforce
the project's static analysis and formatting gates. `./gradlew check` adds the changelog format
check and the kover coverage floor (80% of the lines of the public API; the Swing UI and the
audio paths are excluded because they need a display and a sound card).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full history, and the
[releases page](https://github.com/cereda/lilytap/releases) for tagged versions.
