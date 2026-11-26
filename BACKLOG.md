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

