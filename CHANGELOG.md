# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- "No rests" option: every silence is absorbed into the note before it, so the transcription never
  contains a rest. A silence before the first note has no note to join and stays a rest.
- `--no-rests` flag for the terminal version, the same thing as the checkbox in the window version.

## [0.1.0] - 2026-11-17

### Added
- Initial release.

[Unreleased]: https://github.com/cereda/lilytap/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/cereda/lilytap/releases/tag/v0.1.0
