# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.24.0] - 2026-11-26

### Added
- The app holds the keys past seven accidentals. A signature carries up to fourteen accidentals:
  the seven steps of the scale carry one each, and the steps the circle of fifths comes back to
  carry a second one. `G♯ major` therefore has eight accidentals, and its seventh degree is an
  F𝄪 — the rule LilyPond draws its own signatures with, so the notes and the signature always
  agree. The name of such a key is written with the double sign (`F𝄪 major`, `B𝄫 minor`) or
  with LilyPond's own spelling (`Fisis major`, `Beses minor`), and the field takes it. The
  circle of fifths grew to twenty-one positions and reaches the keys with ten accidentals on
  either side of C major; the keys past them are named in the field. The keys past the end,
  G𝄪 major and F𝄫 major, carry fifteen accidentals each and are refused, because LilyPond has
  no sign for a triple sharp or a triple flat.

### Changed
- A transposition that would land in a key with more than fourteen accidentals is refused, and
  the message says how many the key would carry. Before, the limit was seven.
- A MIDI file says the key with a number of sharps or flats, and it holds seven; a key past
  that is left out of the file, the notes carrying their own pitches.

## [0.23.0] - 2026-11-26

### Added
- The window says how a take was played. The status line carries the **bias** of the take (how
  late or early the notes were on average) and the **spread** (how far the notes fell from that
  average, measured as the median, so one slipped note cannot move it): `Done. 23 ms late ·
  spread 18 ms · try latency 223`. The tooltip of the badge carries the whole report: the mean,
  the spread, how many notes were early, late and on time, and the error of every note of the
  take.
- The notes that were off the beat carry a mark on the score strip: a small triangle above the
  block, pointing to the right when the note was late (amber) and to the left when it was early
  (teal) — the same colours as the raw taps of the timeline. A note is on time when it is
  within 25 ms of the beat it belongs to; the silences are not judged, because a rest is not
  played on time or late. The marks belong to the take: a new take takes them away, and so
  does an edit of the score. A **Revert** puts the take back, and the marks come back with it.

### Fixed
- The app does not hold the sound card while it waits. The audio line is opened when a take
  starts and closed when the take ends, and the playback of a score does the same. A sound card
  that is busy with something else blocks the open, so a window that held the card from the
  moment it opened could leave the launch waiting for the device — and could leave the take
  without a click. The line is now opened before the schedule of the take is made, so the first
  click is still in time.

## [0.22.2] - 2026-11-26

### Fixed
- The notes you played stay in the timeline after the take ends. The lane was left empty when
  the take was over, so the only way to see what you had played was the **Raw taps** toggle, and
  turning it off emptied the lane again. The notes come back when the toggle is off, and the
  raw taps still take their place when it is on. The rule that keeps the lane empty during the
  count-in is unchanged: before the downbeat the lane holds the countdown and nothing else.

## [0.22.1] - 2026-11-26

### Changed
- The window is split into smaller classes. `WindowApp.kt` keeps the widgets and the state and
  delegates the work: `TakeRecorder.kt` holds the recording of a take (the session, the metronome
  and the keys), `Calibration.kt` the round that measures the audio latency, `TapTempoRound.kt`
  the round that sets the tempo by tapping, `AutoRepeat.kt` the guard against the keys that
  repeat themselves, `SettingsCard.kt` the card of the settings and `Playback.kt` the playback of
  the score. The window itself is a third of its old size, and the logic that moved out can be
  tested without a display and without a sound card. Nothing changes in the app itself.

## [0.22.0] - 2026-11-25

### Added
- The count-in is counted down on the timeline now. While the take waits, the empty lane of the
  timeline shows a big number with the beats that are left before the recording starts, so the
  downbeat can be found at a glance. The number is gone the moment the take begins.

### Changed
- The count-in is never shorter than two seconds now. It is still a whole number of bars — the
  downbeat of the take falls on a bar line of the clicks — but a bar that would pass in less than
  that is too quick to settle into, so the count-in grows a bar at a time until it lasts long
  enough. In 4/4 the count-in is one bar up to 120 BPM, two bars above it and three at 300 BPM;
  in 6/8, whose bar is half as long at the same tempo number, it grows above 60 BPM. The count-in
  of the calibration is unchanged: it runs at 60 BPM, where a bar is four seconds.

### Fixed
- The keys pressed during the count-in are not recorded any more, and the timeline waits for the
  downbeat to show the notes of the take. They never were part of the score — the transcription
  drops them — but the timeline drew them as notes at the very start of the take, so a take
  looked as if it had notes before it began. A tap that lands in the first cell of the take is
  the first note of the take, though, and it is kept: a tap that comes a hair early is a tap on
  the beat.

## [0.21.0] - 2026-11-25

### Added
- The score can be written to a MIDI file now. The button with the square wave, in the header of
  the timeline, asks where the file goes and writes it there. The window that asks is the app's
  own - the same one the LilyPond file uses - and the path starts in the home folder with the name
  `lilytap.mid`. The file is a standard MIDI file, written by the app itself: one track says how
  the piece goes (the tempo, the meter and the key) and one holds the notes, with the pitches the
  piano wrote. A rest is silence, so it is not written: the note after it starts later, which is
  what a rest is. The file plays at the tempo of the score: a MIDI file counts the tempo in
  quarter notes, while the app counts it in musical beats, so the beat is written out. The button
  confirms with a check mark when the file is written, the app says what went wrong when it
  cannot be written, and the button is off while there is no score.

## [0.20.0] - 2026-11-25

### Added
- The LilyPond text can be saved to a file now. The button that draws an arrow into a tray,
  beside the copy button, asks where the file goes and writes a whole document there - the
  version, the score block, the staff and the bars - so that LilyPond reads it on its own. The
  window that asks is the app's own: a field with the path and two buttons, drawn like the rest
  of the app. The field is checked as it is typed, and the window says when the file is already
  there and will be replaced. The file is written in the mode the card is in: with the relative
  mode on, the bars go into a `\relative` block. A name without an extension gets `.ly`, the
  button confirms with a check mark when the file is written, and the app says what went wrong
  when it cannot be written.
- The copy and save buttons follow the score: with no score there is nothing to copy and
  nothing to save, so both are off and a click never lands on a button that would do nothing.

## [0.19.0] - 2026-11-25

### Added
- The score can be transposed now. The **Transpose** button, between **Fill the melody** and
  the octave stepper, moves the whole score by a second, a third, a fourth, a fifth, an octave
  or a semitone, up or down. The key follows the notes - the tonic moves by the same interval,
  the mode stays the same - and the notes are spelled in the new key, the way the app spells
  them there. The transposition is an edit like any other: **Ctrl+Z** takes it back, the header
  says what happened (`C major → E♭ major`), and **Revert** puts the take back the way it was
  recorded. A key the app cannot hold - one whose signature carries more than seven
  accidentals - is refused, and so is a transposition that would take a note out of the
  keyboard.

## [0.18.0] - 2026-11-24

### Fixed
- The leading tone of the sharp-side minor keys is spelled with a double sharp: in G♯ minor
  the F is an F𝄪 (`fisis`), in D♯ minor the C is a C𝄪 (`cisis`) and in A♯ minor the G is a
  G𝄪 (`gisis`). The app used to drop the raising and write the note with a natural sign
  (`g`, `d` and `a`).
- The key is off with the other settings while a take runs or a score plays. A key chosen in
  the middle of a take was silently dropped, and a key chosen during a playback stopped the
  music. A take is now recorded in the key the field says, even when the name was typed
  without **Enter**.

### Added
- A pitch can carry a double sharp or a double flat now. The LilyPond text writes it the way
  LilyPond writes it (`fisis`, `beses`), the pitch field reads it back, and the strip shows the
  sign of the double accidental.

## [0.17.0] - 2026-11-24

### Added
- The middle of the circle of fifths picks the key the piece is in: the hole in the middle of
  the circle is not a dead zone.
- The buttons that open a menu have two states: the chevron points down while the menu is
  closed and up while it is open, and a click on the button closes the menu again.

### Changed
- The menu of the **Time** setting is in two groups, **Simple** and **Compound**, two meters to
  a row, so the list is half as tall as a column of them.

### Fixed
- The menus of the **Time** and **Key** settings open with a little space under the button and
  are lined up with its right edge, and their corners are really round: the popup is drawn by
  the app itself, so the square corner of the native window is gone.
- The highlight of the circle of fifths is balanced: every key sits in the middle of its band,
  so the room above and below a key is the same. The circle grew a little to keep the minor
  keys apart.

## [0.16.0] - 2026-11-24

### Added
- The **Key** setting opens the circle of fifths: every key of the piece around a ring, C major
  at the top, the sharps climbing to the right and the flats coming back on the left. Outside
  the ring are the major keys and inside them the relative minors; click the half of the
  position you want and the field takes the name. The key the piece is in is written in the
  middle of the circle.

### Changed
- The button that writes the octaves of the LilyPond text relative is a step now: two dashes, the
  lower one on the left and the upper one on the right, joined by a diagonal. The two note heads
  and the arrow were a smudge at the size the button is drawn.
- The name of the key is written with the sign of its accidental, the way the app writes every
  other pitch: `B♭ major`, not `Bb major`. The field still reads every form of the name.

### Fixed
- The **Key** field reads the name of the key the way LilyPond writes it (`cis major`, `bes minor`)
  as well as the way it is written in English (`C# major`, `Bb major`), and a name that is the
  name of a key the app cannot hold says so: `G♯ major` carries eight accidentals, and a
  signature carries at most seven.
- The app reads every input before a take begins: a name that cannot be read — a time signature
  that is not a meter, a key that is not a key, a pitch that is not a note — stops the take and
  says what is wrong, instead of running it on the last good value.

## [0.15.0] - 2026-11-23

### Added
- The score knows the key it is in: the **Key** setting of the settings card takes the name of the
  key the way it is written in English (`Bb major`, `F# minor`, `C♭ major`), and a menu beside the
  field offers the common ones. The notes are spelled in that key (`ees` and `dis` instead of `f`
  and `e♯`), the LilyPond text declares it (`\key f \major`), and the tracker and the piano play
  the sharps or the flats of the key. Changing the key is an edit like any other, so **Ctrl+Z**
  takes it back, and a note keeps its sound when it is respelled: only its name changes.

### Changed
- The buttons of the LilyPond card are icons, the way the app switches its modes: the button of
  the relative octaves shows two note heads and lights up in the accent colour when it is on, and
  the copy button shows the copy mark and turns into a check mark when the text is on the
  clipboard. Their tooltips say what they do.

## [0.14.0] - 2026-11-22

### Added
- The LilyPond text can be written with the octaves relative: the **Rel** button beside **Copy**
  writes the score as a `\relative` block, where every note is read from the note before it, the
  way LilyPond reads them. The first note of the block is still written in full, because there is
  no note before it, and a rest carries no pitch and does not move the reference.
- The first line of the LilyPond text says how the octaves are written (`% absolute` or
  `% relative`), so a fragment that is copied into a file says what it is.

### Fixed
- The hint of the LilyPond card is no longer cut off: it is wrapped over two lines, so the whole
  sentence fits in the narrow card.

## [0.13.1] - 2026-11-22

### Changed
- The keys of the tracker name the notes of the scale instead of fixed pitches, and the octave
  they play is a state of its own. `1` to `7` are C to B and `Q W E R T` the sharps of the octave
  the panel shows (`C4` at the start), `8` moves that octave down and `9` up, and `0` puts it
  back on the octave of the note you are on. The keys `8`, `9`, `0`, `Y`, `I`, `O` and `P` of
  0.13.0 are free now.
- The tracker follows the cursor: clicking a block of the strip, clicking a key of the piano or
  pressing **Fill the melody** puts it in the octave of that note. The walk that follows a
  written pitch does not move it, so an octave you moved by hand survives the melody.
- The piano marks the octave the tracker plays with bright letters and the other octaves with
  dim ones, and it reaches the octave the tracker is in as well as the notes of the take.
- The octave the tracker plays is a stepper next to **Fill the melody** instead of a button:
  `−` takes it down, `+` takes it up, and a click on the number puts the tracker back on the
  note you are on, the way the `0` key does. Dragging the number moves the octave as well.
- The octaves the app knows run from C0 to C8, the range LilyPond writes in absolute mode.
  The note below C0 (`c,,,,`) is no longer read.

## [0.13.0] - 2026-11-21

### Added
- A **Pitches** panel below the score: a piano that writes the pitch of the selected note. Click a
  key, or play it on the keyboard — the number row carries the white keys (`1` is middle C and `0`
  is the E above) and the top row the black ones (`Q` is C♯, `W` is D♯, `E` is F♯, `R` is G♯, `T` is
  A♯, and `Y`, `I`, `O`, `P` carry the octave above). The keys of the strip (**S**, **X**, **M**)
  keep their meaning, so the tracker lives on the rows above them.
- The cursor walks the melody: every pitch that is written - by a click, by a tracker key or in the
  pitch field - moves it to the next note, the silences are stepped over, and the counter says where
  it is (`3 / 12`). **Fill the melody** takes the cursor to the first note, and a tracker key with
  nothing selected starts the walk there.
- A pitch on a silence turns it into a note, so a gap in the melody can be filled in place: the gap
  keeps its length and sounds the note that was given to it.
- A legend above the Start button says what the colours of the raw taps mean (amber = pressed late,
  teal = pressed early) and which keys play the piano.

### Changed
- The bottom of the window is split in two: the Pitches panel takes two thirds of the width and the
  LilyPond text the third that is left, which wraps now.
- The **Pitch** field is emptied once the pitch is written: it waits for the next note, which the
  cursor has already moved on to.
- The piano is an editor, not an instrument: it is greyed out while a take is being recorded and
  while the score plays. While the score plays the key that is sounding is lit, and the notes of
  the take stay lit faintly.

## [0.12.1] - 2026-11-21

### Changed
- The band at the foot of a block says the length of the note in a colour of its own: four
  buckets — the sixteenths and the shorter values, the eighths, the quarters, and the halves and
  the longer values — with a blue, a green, a gold and an orange band. The band was a four-pixel
  stripe of a ramp of brightness, which looked like a shadow under the block and was hard to tell
  apart. It is now three times taller, and the pitch label sits above it.
- Hovering a block says the pitch and the exact duration of the note, so the colour of the band
  only has to carry the rough idea.

## [0.12.0] - 2026-11-21

### Added
- Every note of the score carries its own pitch. The header of the score strip has a **Pitch**
  field: click a block, type the name of the note the way LilyPond writes it (`c'` for middle C,
  `cis'` for a sharp, `ces'` for a flat, `c''` for an octave up, `c` for an octave down) and press
  **Enter**. The strip shows the pitch in scientific notation (`C4`, `C♯5`), the LilyPond text and
  the playback use it, and setting it is an edit like any other, so **Ctrl+Z** takes it back. A
  note that has no pitch yet takes the pitch of the note before it, so filling a silence in the
  middle of a melody does not change the melody.

### Changed
- The block of the score strip shows the pitch of the note instead of its LilyPond text, and a
  stripe at its foot says how long the note is: the deeper the stripe, the longer the note. The
  exact duration is still in the width of the block and in the LilyPond text.
- The **Pitch** setting is gone from the settings card: the pitch belongs to the note now, not to
  the take, and every note of a take starts on middle C.
- The LilyPond text is read-only: it is generated from the score, so it cannot be typed over.
- The notes of the LilyPond text carry their own pitch, so they are written with the octave mark
  of that pitch: middle C is `c'`, the way LilyPond writes it, instead of the bare `c` of the old
  placeholder.
- The window is 60 pixels wider (935 instead of 875): the message in the header of the score strip
  has room to breathe, and the timeline, the score strip and the LilyPond text are wider too.
- The **Revert** button is the word alone, in red: it throws the edits away, and now it says so.
  The reset arrow it used to carry means "reset this value to its default" on the steppers.
- The header of the score strip has no "Score" label any more: the strip below it says what it is,
  and the message of the header has the room the label took (235 pixels now).
- The message in the header of the score strip never makes the window wider: a message that is too
  long is shortened with an ellipsis, and the full text is in its tooltip.
- The selection of the score strip goes when the score is played, and when you click anything that
  is not the editor (the strip, the pitch field and the edit buttons): the ring would only be in
  the way.

### Fixed
- The ring around a selected block is drawn in the colour of the text, not in the accent colour:
  the blocks are painted in the accent colours, and a ring in the accent disappeared into the
  block it surrounded. A faint glow now surrounds the ring, so the block stands out from the strip
  as well. The ring now follows the shape of the block and touches it: a sliver of the strip
  used to show between the block and the ring, around the rounded corners of the block.

## [0.11.0] - 2026-11-20

### Added
- The 3-feel marks the downbeat with a sound of its own: a low, long click, so that the bar is
  audible and not only the grouping of three. Every other meter keeps the accent it has always
  had.

## [0.10.0] - 2026-11-20

### Added
- The **3-feel**: the round button beside the time signature makes the metronome click every note
  value of a compound meter (the eighth in 6/8) instead of the musical beat, so the subdivision
  of the beat is audible. The beat itself does not move: the tempo, the count-in, the beat dots
  and the score stay what they were, and the accent still falls on the beat, so the click and the
  beat dots agree. The button is greyed out in a simple meter and keeps its setting when the
  meter changes.

### Changed
- **No rests** is the last option of the **Smallest rest** control now, instead of a checkbox of
  its own, so the two settings that decide what happens to a silence sit in one place.
- The window is 21 pixels wider (875 instead of 854), because the 3-feel button has a place of
  its own beside the time signature.

## [0.9.0] - 2026-11-20

### Added
- The tempo can be set by tapping: the round button next to the tempo field takes the beat of
  your taps and settles on the median of the intervals, so one slipped tap does not wreck the
  result. While the button is armed, a key press is a tap too (a text field keeps its keys), and
  **Esc** gives up a round that is still listening.

## [0.8.0] - 2026-11-20

### Added
- The score strip can undo an edit: **Ctrl+Z** takes the last edit back and **Ctrl+Shift+Z**
  (or **Ctrl+Y**) does it again. The header carries two more buttons for it, and they are greyed
  out when there is nothing to undo or to redo. The selection and the message come back with the
  score, and a drag of a border is one edit, not one per pixel.
- **Revert** is an edit like any other now, so a mis-click on it can be undone.

## [0.7.0] - 2026-11-20

### Added
- The time signature field has a menu of the common meters beside it (4/4, 3/4, 2/4, 2/2, 3/8,
  6/8, 9/8, 12/8, 5/4, 6/4, 7/8, 5/8). The field still takes any other meter, and its border
  turns red when the text is not one.

### Changed
- In a compound meter (6/8, 9/8, 12/8, 6/4...) the LilyPond text follows the beat: a duration is
  written inside a group of three, so a note that crosses a group becomes two tied notes
  (`c4. ~ c8`) and a silence that crosses one becomes two rests (`r4. r8`). Simple meters are
  written as they always were, and irregular ones (5/8, 7/8) are left alone.
- In a compound meter the metronome now clicks the musical beat - the dotted quarter - and the
  tempo counts those beats: 60 BPM in 6/8 is 60 dotted quarters a minute, so a bar lasts two
  seconds. The count-in, the beat dots and the bar/beat counter follow the same beat. The grid
  does not move: the cells still cut the note value (the eighth in 6/8), so the eighths of a 6/8
  bar land exactly on it, and the setting says so - its caption reads `Cells / eighth` there.
- The tempo and the bars are a little narrower, so the settings row reads more evenly.

## [0.6.0] - 2026-11-20

### Added
- The score strip is now an editor: click a block to select it, shift-click to select a range,
  and double-click to turn it into a rest (and back again).
- The score strip takes the keyboard while it is selected: **S** splits the selected block in
  half, **X** swaps the two selected blocks, **M** merges the selected blocks into one, the arrow
  keys move the selection and **Esc** clears it.
- The header of the score strip carries **Split**, **Swap** and **Merge** buttons that do the
  same as the keys. A button is greyed out when the current selection does not allow it, and the
  header says what is selected or what just happened.
- The three buttons have their own icons: a block with a cut through it for **Split**, two
  opposite arrows for **Swap**, and two lines coming together for **Merge**.
- Splitting cuts a block at its middle: the first half keeps the kind of the block and the second
  half becomes a note, so a silence that is split becomes a silence followed by a note.
- Swapping trades two neighbouring blocks, each keeping its own length and moving in time.
- Merging folds a range of blocks into one; a note wins over a silence, so the silences inside
  the range are absorbed by the note.

### Changed
- Clicking a block no longer turns it into a rest: a click selects it and a double-click turns it
  into a rest.
- The message in the header of the score strip now says what the edit did: the blocks before the
  edit, an arrow, and the blocks that are there now (`c2 → c4 c4`). A merge of many blocks says
  how many blocks it folded together (`12 blocks → c1`), so the message always fits.
- The score is measured in musical units (a sixty-fourth of a whole note) instead of grid cells,
  so a block can be shorter than a cell and a border can sit inside one. The recording is
  unchanged, and an event on the grid keeps exactly the time it had before.

## [0.5.0] - 2026-11-20

### Added
- Playback has a **Note / Beat** toggle (a note/metronome icon next to **Play**). **Beat**
  replaces each note onset with a short percussive click (accented on bar downbeats), with a
  timbre distinct from the metronome; rests stay silent.

### Changed
- lilytap now launches directly into the window; the shadow jar is the executable
  (`java -jar build/libs/lilytap.jar`) and the `run`/`gui` subcommands are gone.
- Calibration adds the measured residual to the configured audio latency instead of replacing
  it, and includes the first recorded bar.
- Calibration now runs on a grid of its own: 60 BPM, one bar of count-in and two bars to tap
  along with, whatever the tempo, time signature and length of the take are. The slow grid leaves
  room around every beat for the whole output delay.
- Calibration takes the median of the tap offsets instead of their average, so a single stray tap
  or one missed beat cannot move the result.
- The calibration status shows the number of taps instead of a beat number, and the count-in is
  announced as "Get ready…": a beat number is a claim about when the beat is, and while the audio
  is late that claim is wrong.
- The value a calibration produces includes your own reaction time, so it reads a little higher
  than the raw output delay.

### Removed
- The terminal CLI (`run` subcommand), its options and the terminal recorder.
- The `clikt` and `jline` dependencies.

### Fixed
- Playback now uses the edited score, and the timeline shows the edited score (not the raw take)
  during playback. Editing a block stops playback, so sound and blocks cannot disagree.
- Score blocks on the timeline are positioned by grid cell; they used to be converted to
  milliseconds before the pixel mapping, which put later blocks far off-screen.
- The timeline grid no longer collapses to the left edge when it has a score but no active
  session.
- Playback event timing rounds the fractional cell duration to nanoseconds instead of
  truncating it, removing cumulative drift at tempos such as 63 BPM.
- The first note of a playback no longer starts late: the playback origin now includes the audio
  latency, so the first event has the same head start as the later ones and the score keeps its
  rhythm from the very first note.
- The beat dots and the status text follow the beat itself instead of the moment the click is
  written to the sound card, so the guide lines up with the playhead.
- Calibration is no longer biased by the beat dots: they are hidden while calibrating, so the
  taps can only follow the beeps.
- Calibration no longer reads a late tap as an early one: the round runs on its own slow grid,
  where a tap that answers a beep which reached the ears late stays on the beat it followed, and
  the result is the median of the offsets rather than their average.
- A calibration result that falls outside the range of the setting is discarded, so a stray tap
  cannot move the audio latency by a whole beat.
- While a score is playing, the settings and the **Calibrate** button are disabled: they describe
  the next take, and a calibration used to beep over the playback.
- The timeline header no longer clips long status messages: the sound toggle is icon-only, the
  per-beat calibration status is shorter, and the status badge elides when it runs out of room.
- Starting and immediately stopping playback no longer writes to a closed audio line; the
  player rejects playback after `close()`.
- Calibration records only key presses (not typed/released events), respects Esc, and no longer
  skips the first bar or cancels itself when only one bar is recorded.
- `Metronome` publishes its running thread atomically, so `close()` can always interrupt it;
  closing is idempotent and ignores clicks on a closed line.
- `Config` rejects non-finite BPM and latency values and blank pitches.

## [0.4.11] - 2026-11-19

### Fixed
- Playback now scrolls the shared timeline/score grid to follow the playhead. `WindowApp.onFrame()`
  updates `TimelinePanel.playbackPositionMs` before calling `follow()`, so the scroll target is
  current; the view snaps back to the beginning when **Play** is pressed, follows a long score,
  and pins at the end instead of letting the playhead run off-screen.
- `WindowApp.startPlayback()` resets the shared grid to the beginning.
- Restored the timeline repaint in the non-scrollable recording path, so the playhead keeps moving
  on short takes.

### Improved
- New `TimelinePanelTest` covers the follow maths and painting; `WindowAppTest` guards that
  `onFrame()` feeds the timeline position.
- The README playback section documents the scrolling behaviour.

## [0.4.10] - 2026-11-19

### Fixed
- `TimelinePanel.follow()` gained a playback branch and `WindowApp.onFrame()` called it while
  playing. The view still did not move because the timeline's playback position was never updated;
  this is fixed in 0.4.11.

## [0.4.9] - 2026-11-19

### Fixed
- The timeline now draws the score blocks during playback, when there is no active recording
  session, instead of showing only the playhead.
- `TimelinePanel` gained a `score` property, set by `WindowApp.showScore()` and cleared by
  `clearScore()`.

## [0.4.8] - 2026-11-19

### Fixed
- `TimelinePanel.follow()` now always repaints after attempting to scroll, so the playhead no
  longer freezes once the view reaches `maxScroll`.

## [0.4.7] - 2026-11-19

### Changed
- Re-tagged 0.4.6; no code changes.

## [0.4.6] - 2026-11-19

### Fixed
- `Player.play()` uses `compareAndSet()` for the playing flag, preventing a TOCTOU race where two
  threads could both start playback.
- `Metronome.close()` interrupts any running `run()` thread, so it leaves `sleepUntil()` promptly
  instead of waiting for the sleep deadline.

## [0.4.5] - 2026-11-19

### Fixed
- `Player.play()` interrupts and re-joins a previous playback thread that is still alive after the
  initial join budget, preventing two writers from sharing the audio line.
- `WindowApp.stopPlayback()` closes the player instead of only stopping it, releasing the audio
  line.
- The code area's empty hint no longer starts with a stray `%`.

### Improved
- `NotationPanel`'s wheel handler no longer double-repaints: `grid.scrollTo()` already notifies the
  grid listeners.

## [0.4.4] - 2026-11-19

### Fixed
- `Player.toneSamples()` computes attack/release envelopes independently, so short notes don't fade
  to silence.
- `Player.play()` joins a previous playback thread with a single blocking wait before starting a
  new one.
- `WindowApp.onFrame()` no longer has a dead code path when `takeScore` is null during playback,
  and no longer double-repaints the timeline.
- `TimelinePanel.follow()` repaints the playhead even when the grid does not scroll.

### Improved
- `Player.close()` closes the audio line first to unblock in-progress writes, then joins the thread
  with a longer timeout.

## [0.4.3] - 2026-11-19

### Fixed
- `currentPlayer` is now closed in the JVM shutdown hook, so the audio line is
  released cleanly when the app exits during playback.
- `Player.play()` now joins any previous playback thread with a fallback wait,
  preventing two threads from writing to the audio line concurrently.
- `Player.play()`'s `onPosition` callback is now optional; `WindowApp.onFrame()`
  is the single source of truth for the playhead position.

### Improved
- `WindowApp` changed from `private` to `internal` for testability.
- `registerShutdownHook()` extracted from `show()`, reducing `show()` length.
- New `WindowAppTest` covers playback start/stop and `clearScore()` transitions.
- `PlayerTest` covers playing without an `onPosition` callback.

## [0.4.2] - 2026-11-19

### Fixed
- `Player.sleepUntil()` now checks `Thread.isInterrupted`, so `stop()` interrupts
  the playback thread and it exits promptly instead of parking for up to 2s.
- `Player.stop()` now interrupts the playback thread for immediate shutdown.
- `WindowApp.stopPlayback()` stops `frameTimer` so it doesn't keep firing 15 ms
  repaints during playback.

### Improved
- `TimelinePanel.detach()` and `NotationPanel.detach()` remove their grid-change
  listeners; both are called in the JVM shutdown hook so GridView never holds
  stale panel references.
- `Player` tone envelope uses ADSR with sustain decay (0.7) and a quieter second
  harmonic for a warmer, less organ-like tone.

## [0.4.1] - 2026-11-19

### Fixed
- Playback playhead now draws on the timeline during playback (previously only on the score strip).
- `Player.play()` joins any previous playback thread before starting a new one, preventing two
  threads from writing to the audio line concurrently.
- `Player.close()` joins the playback thread before closing the audio line, avoiding writes to a
  closed line.
- Playback audio timing uses the same `startNanos` reference as the visual playhead, eliminating
  scheduling-drift offset.
- `Player` tone envelope uses attack/sustain/release instead of exponential decay, so sustained
  notes don't fade to silence.

## [0.4.0] - 2026-11-19

### Added
- Playback: play the edited score back through the metronome audio, with the pitch setting the
  tone. A playhead moves across the timeline and score strip as the score plays, and a Play /
  Stop button controls playback. The configured audio latency is applied so the sound lines up
  with the playhead.

## [0.3.0] - 2026-11-19

### Added
- A score strip below the timeline: the notes and rests of the take, drawn on the same grid as
  the timeline and editable in place. Clicking a block turns it into a rest (and back again),
  dragging the border between two blocks changes their lengths. The border snaps to the nearest
  grid cell and sticks to bar lines.
- A "Revert" button that puts the score back the way it was when the take ended.
- An eye icon next to "Raw taps" that shows and hides the score strip; the timeline expands to
  fill the space it leaves.

## [0.2.1] - 2026-11-18

### Changed
- The transcription now produces a score - the notes and rests of a take - instead of only its
  LilyPond text. The text itself is unchanged.

## [0.2.0] - 2026-11-18

### Added
- "No rests" option: every silence is absorbed into the note before it, so the transcription never
  contains a rest. A silence before the first note has no note to join and stays a rest.
- `--no-rests` flag for the terminal version, the same thing as the checkbox in the window version.

### Changed
- The settings rows were rearranged: the pitch field moved to the third row, in front of the audio
  latency setting.

## [0.1.0] - 2026-11-17

### Added
- Initial release.

[Unreleased]: https://github.com/cereda/lilytap/compare/v0.24.0...HEAD
[0.24.0]: https://github.com/cereda/lilytap/compare/v0.23.0...v0.24.0
[0.23.0]: https://github.com/cereda/lilytap/compare/v0.22.2...v0.23.0
[0.22.2]: https://github.com/cereda/lilytap/compare/v0.22.1...v0.22.2
[0.22.1]: https://github.com/cereda/lilytap/compare/v0.22.0...v0.22.1
[0.22.0]: https://github.com/cereda/lilytap/compare/v0.21.0...v0.22.0
[0.21.0]: https://github.com/cereda/lilytap/compare/v0.20.0...v0.21.0
[0.20.0]: https://github.com/cereda/lilytap/compare/v0.19.0...v0.20.0
[0.19.0]: https://github.com/cereda/lilytap/compare/v0.18.0...v0.19.0
[0.18.0]: https://github.com/cereda/lilytap/compare/v0.17.0...v0.18.0
[0.17.0]: https://github.com/cereda/lilytap/compare/v0.16.0...v0.17.0
[0.16.0]: https://github.com/cereda/lilytap/compare/v0.15.0...v0.16.0
[0.15.0]: https://github.com/cereda/lilytap/compare/v0.14.0...v0.15.0
[0.14.0]: https://github.com/cereda/lilytap/compare/v0.13.1...v0.14.0
[0.13.1]: https://github.com/cereda/lilytap/compare/v0.13.0...v0.13.1
[0.13.0]: https://github.com/cereda/lilytap/compare/v0.12.1...v0.13.0
[0.12.1]: https://github.com/cereda/lilytap/compare/v0.12.0...v0.12.1
[0.12.0]: https://github.com/cereda/lilytap/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/cereda/lilytap/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/cereda/lilytap/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/cereda/lilytap/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/cereda/lilytap/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/cereda/lilytap/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/cereda/lilytap/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/cereda/lilytap/compare/v0.4.11...v0.5.0
[0.4.11]: https://github.com/cereda/lilytap/compare/v0.4.10...v0.4.11
[0.4.10]: https://github.com/cereda/lilytap/compare/v0.4.9...v0.4.10
[0.4.9]: https://github.com/cereda/lilytap/compare/v0.4.8...v0.4.9
[0.4.8]: https://github.com/cereda/lilytap/compare/v0.4.7...v0.4.8
[0.4.7]: https://github.com/cereda/lilytap/compare/v0.4.6...v0.4.7
[0.4.6]: https://github.com/cereda/lilytap/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/cereda/lilytap/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/cereda/lilytap/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/cereda/lilytap/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/cereda/lilytap/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/cereda/lilytap/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/cereda/lilytap/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/cereda/lilytap/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/cereda/lilytap/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/cereda/lilytap/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
