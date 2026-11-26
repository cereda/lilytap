# The LilyPond referee

The app writes the LilyPond text of a score and says what the notes of that score sound.
LilyPond reads the text and plays the notes. The referee has LilyPond do exactly that and
compares what comes out with what the app says: if the two do not agree, the app and
LilyPond do not agree about the music, and the app is wrong.

LilyPond is not a dependency of the build, so the referee is not run by Gradle. It is run by
hand, with a LilyPond of your own.

## Running it

The test `LilyPondRefereeTest` writes the cases - the LilyPond text of the score, the MIDI file
the app builds and the pitches the app says it has - into `build/lilypond-referee`:

    ./gradlew test --tests "com.github.cereda.lilytap.LilyPondRefereeTest"

The referee compiles every case with LilyPond, reads the MIDI back and compares the pitches:

    tools/lilypond-referee/referee.py --lilypond /path/to/lilypond

It compares the MIDI file of the app with the one LilyPond wrote as well: the notes have to be
the same, down to when they start and how long they last.

The referee prints one line for every case and says how many of them agree with LilyPond.
It fails when they do not.

## What it checks

Every case is a key, an interval and a direction. The score is the scale of the key, so
every degree of the scale is checked, and the score is transposed the way the app does it:
the notes move by the interval, the key follows and the notes are spelled in the new key.

The cases cover the major and the minor keys, the flat and the sharp sides, the chromatic
semitone, and the keys whose spelling needs a double accidental: the keys past seven
accidentals, up to C𝄪 major and C𝄫 major, are among them.
