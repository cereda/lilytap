# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[0.4.0]: https://github.com/cereda/lilytap/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/cereda/lilytap/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/cereda/lilytap/releases/tag/v0.2.1
[0.2.0]: https://github.com/cereda/lilytap/releases/tag/v0.2.0
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
