#!/usr/bin/env python3
"""The referee: LilyPond plays the app's text and the MIDI is read back.

The app writes the LilyPond text of a score and the pitches it says the score has. This
script has LilyPond compile the text into a MIDI file, reads the pitches out of it, and
compares them with the ones the app says. A difference means that the app and LilyPond do
not agree about the music, which is the one thing the app must never do.

LilyPond is not a dependency of the build, so this script is not run by Gradle: point it at
a LilyPond and run it by hand.

    tools/lilypond-referee/referee.py --lilypond /path/to/lilypond

The files it reads are written by the test `LilyPondRefereeTest`, into
`build/lilypond-referee`.
"""

import argparse
import pathlib
import subprocess
import sys

# The folder the test writes the files into.
FOLDER = pathlib.Path("build/lilypond-referee")


def read_midi(path):
    """The pitches of the notes of a MIDI file, in the order they are played.

    @param path: the MIDI file.
    @return: the list of the pitches, as MIDI numbers.
    """
    data = path.read_bytes()
    if data[:4] != b"MThd":
        raise ValueError(f"{path} is not a MIDI file")
    position = 8 + int.from_bytes(data[4:8], "big")
    pitches = []
    while position < len(data):
        if data[position : position + 4] != b"MTrk":
            raise ValueError(f"{path} has a track LilyPond did not write")
        length = int.from_bytes(data[position + 4 : position + 8], "big")
        track = data[position + 8 : position + 8 + length]
        position += 8 + length
        pitches += read_track(track)
    return pitches


def read_track(track):
    """The pitches of the notes of one track of a MIDI file.

    @param track: the bytes of the track.
    @return: the list of the pitches, as MIDI numbers.
    """
    pitches = []
    position = 0
    running = 0
    while position < len(track):
        position = skip_delta(track, position)
        status = track[position]
        position += 1
        if status < 0x80:
            # A running status: the byte before is the status again.
            position -= 1
            status = running
        else:
            running = status
        kind = status & 0xF0
        if kind == 0x90:
            note = track[position]
            velocity = track[position + 1]
            position += 2
            if velocity > 0:
                pitches.append(note)
        elif kind in (0x80, 0xA0, 0xB0, 0xE0):
            position += 2
        elif kind in (0xC0, 0xD0):
            position += 1
        elif status == 0xFF:
            kind = track[position]
            position += 1
            length, position = read_length(track, position)
            position += length
        else:
            # A system message: it is as long as it says.
            length, position = read_length(track, position)
            position += length
    return pitches


def skip_delta(track, position):
    """The place of the first event after the time gap at the given place.

    @param track: the bytes of the track.
    @param position: the place of the time gap.
    @return: the place of the event that follows it.
    """
    while track[position] & 0x80:
        position += 1
    return position + 1


def read_length(track, position):
    """The length of a piece of data and the place where it ends.

    The length is written in a variable number of bytes: every byte but the last has its
    highest bit set.

    @param track: the bytes of the track.
    @param position: the place of the length.
    @return: the length of the data and the place where it ends.
    """
    length = 0
    while True:
        byte = track[position]
        position += 1
        length = (length << 7) | (byte & 0x7F)
        if not byte & 0x80:
            return length, position


def check(case, lilypond):
    """Checks one case: LilyPond plays the text and the MIDI is read back.

    @param case: the .ly file of the case.
    @param lilypond: the LilyPond to run.
    @return: the message of the check, and whether it passed.
    """
    expected = [int(line) for line in case.with_suffix(".expected").read_text().split()]
    result = subprocess.run(
        [lilypond, "-d", "no-point-and-click", "-o", str(case.with_suffix("")), str(case)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return f"{case.name}: LilyPond did not compile the text:\n{result.stderr}", False
    # LilyPond writes its progress to the error stream even when all goes well, so only the
    # warnings and the errors count.
    complaints = [
        line
        for line in result.stderr.splitlines()
        if "warning:" in line or "error:" in line or "failed" in line.lower()
    ]
    if complaints:
        return f"{case.name}: LilyPond complained:\n" + "\n".join(complaints), False
    midi = case.with_suffix(".midi")
    if not midi.exists():
        return f"{case.name}: LilyPond wrote no MIDI", False
    played = read_midi(midi)
    if played != expected:
        return f"{case.name}: LilyPond played {played}, the app says {expected}", False
    return f"{case.name}: {len(played)} notes, as the app says", True


def main():
    """Runs the referee over every case."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--lilypond",
        default="lilypond",
        help="the LilyPond to run (the default is the one on the PATH)",
    )
    parser.add_argument(
        "--folder",
        default=str(FOLDER),
        help="the folder the cases are in",
    )
    options = parser.parse_args()

    folder = pathlib.Path(options.folder)
    cases = sorted(folder.glob("*.ly"))
    if not cases:
        print(f"There are no cases in {folder}. Run the test LilyPondRefereeTest first.")
        return 1

    failures = 0
    for case in cases:
        message, passed = check(case, options.lilypond)
        print(("OK   " if passed else "FAIL ") + message)
        if not passed:
            failures += 1
    print(f"{len(cases) - failures} of {len(cases)} cases agree with LilyPond.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
