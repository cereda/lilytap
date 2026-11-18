# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/cereda/lilytap/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/cereda/lilytap/releases/tag/v0.2.1
[0.2.0]: https://github.com/cereda/lilytap/releases/tag/v0.2.0
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
