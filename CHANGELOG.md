# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- `Player.toneSamples()` now computes attack/release envelopes independently, so short notes don't fade to silence.
- `Player.play()` join logic simplified to a single blocking wait before starting a new thread.
- `WindowApp.onFrame()` no longer has a dead code path when `takeScore` is null during playback, and no longer double-repaints the timeline.
- Playback now scrolls the shared timeline/score grid to follow the playhead: it snaps back to the
  start when **Play** is pressed, follows a long score, and pins at the end instead of letting the
  playhead run off-screen. `WindowApp.onFrame()` now updates `TimelinePanel.playbackPositionMs`,
  which `follow()` needs to compute the scroll target.

### Improved
- `Player.close()` closes the audio line first to unblock in-progress writes, then joins the thread with a longer timeout.

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

[0.4.3]: https://github.com/cereda/lilytap/compare/v0.4.2...v0.4.3
[0.4.3]: https://github.com/cereda/lilytap/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/cereda/lilytap/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/cereda/lilytap/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/cereda/lilytap/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/cereda/lilytap/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/cereda/lilytap/releases/tag/v0.2.1
[0.2.0]: https://github.com/cereda/lilytap/releases/tag/v0.2.0
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
