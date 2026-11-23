# Backlog

Ideas that are parked: they are not on the roadmap right now, but they are worth
remembering. When one of them is worth doing, it becomes a branch off `main` and
leaves this file.

## Two silences in a row

The strip never shows two rests in a row: a silence is always one block. The LilyPond
text still spells a long silence with several rests when the meter asks for it
(`r4. r8` in 6/8), but the model keeps one block.

Relaxing this would mean the model could hold two silences that touch, and the strip
would show two rest blocks side by side. It is a real change of what the strip *is*,
and the editor (split, swap, merge) would have to keep the two apart. **My advice is
to leave it alone** unless the strip is ever about to show rests as first-class
blocks.

## MIDI export

The app only speaks LilyPond text. A `.mid` export would need the onset, the duration
and the pitch of every block, plus a file dialog and a MIDI writer (either a new
dependency or a hand-rolled one). The data is all there; most of the work is on the
file side.

## The length of the count-in

The count-in is one bar of clicks. Two bars would give a runner more room to settle in,
at the cost of two more seconds before every take. A small change, if it is ever
wanted.

## The spelling of the notes comes from the signature, not from the scale

The key signature says which letters the key alters, and the app spells a note from that
map: the letter the signature alters comes first, then the natural note, then the sharp or
the flat the key points at. Inside the seven accidentals the app holds, that is the same
thing as the diatonic spelling of the scale - but not at the edges:

- in C♭ major the seventh degree is B𝄫, and the app writes B♭ (`bes`);
- the raised seventh of G♯, D♯ and A♯ minor is a double sharp (F𝄪, C𝄪, G𝄪), and the app
  skips the raising instead of writing a double accidental.

Spelling the notes from the **scale** of the key - the seven degrees, with the alterations
they carry - would fix both. It is also what a key with more than seven accidentals needs:
`\key gis \major` is eight sharps, and the app refuses it because a signature is capped at
seven. Going past the cap would also need `Pitch` to carry a double accidental (`fisis`,
`beses`), and the pitch field to read it.
