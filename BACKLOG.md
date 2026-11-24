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
thing as the diatonic spelling of the scale - the signature and the scale agree on every
degree of every key the app can hold.

Past the cap they part company, and the scale is what a key needs:

- the fourth degree of F♭ major (eight flats) is a B𝄫, and the signature says B♭;
- the natural seventh of a minor key whose tonic is a double flat is a double flat too.

So spelling the notes from the **scale** of the key - the seven degrees, with the alterations
they carry - is what a key with more than seven accidentals needs: `\key gis \major` is eight
sharps, and the app refuses it because a signature is capped at seven. Going past the cap
would also need the pitch field to read a double accidental on its own (`fisis`, `beses`) -
that part is done now, the pitch model carries a double accidental - and the circle of fifths
to show the keys that lie beyond the seven.

**The cap stays at seven accidentals**, so this stays parked with the cap. The one place
where the scale and the signature part company inside the cap was the leading tone of the
sharp-side minor keys, and that is fixed: G♯, D♯ and A♯ minor spell it F𝄪, C𝄪 and G𝄪.

