# Skill: add a landscape

1. Write a scene function in `tools/landscapes/scenes*.py` with the helpers in `vec.py`, and add it
   to that module's `SCENES`.
2. `cd tools/landscapes && python3 build.py ../../app/src/main/res/drawable ./preview <module>`
3. Look at `preview/sheet.png`; fix anything that reads wrong. Keep the XML under about 20 KB.
4. Add a `PresetImage.TR_<NAME>` entry with three sample colours (top, middle, bottom) and its
   name in all three string files.
5. Run the checks in `sentinel_test_skill.md`.
