# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/cereda/lilytap/compare/v0.7.0...HEAD
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
[0.2.1]: https://github.com/cereda/lilytap/releases/tag/v0.2.1
[0.2.0]: https://github.com/cereda/lilytap/releases/tag/v0.2.0
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
