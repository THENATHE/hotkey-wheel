# 1.2.0 look: dialog title past its panel, shadowed dark text on light panels

`LookTest` run against the published `hotkey-wheel-1.2.0+26.3.jar` (SHA-256 `4bdc737e0c1d6aea6f410b42db79504be2685ae841ee7933f700e852d82c0639`) in the minimal profile stops at its first failure:

> look-house-dark-scale-2-remove-folder: the title and message fit inside the panel (333 of 300)

The remove-folder question drew its bold title on one line, so with a folder named "Gemstones and other shiny things" it is 333 GUI pixels wide in a 320-pixel panel with 10-pixel margins, and runs past the panel's right edge at both GUI scale 2 and 3. `release-1.2.0/` keeps that run's report, provenance and executed fixture sources; `crops/1.2.0-remove-folder-scale-2.png` is its screenshot.

The other two defects come later in the same fixture, so the 1.2.0 run never reaches them. Their "before" crops were taken from a run of the first 1.2.1 candidate (`faef3ce…`), which changed only the container messages and kept 1.2.0's look code unchanged; the "after" crops are from the 1.2.1 release JAR:

- `crops/*-minecraft-page-label.png`: the control chooser's "Page 1/1" in the Minecraft look, dark grey with a black shadow, then without it. The icon picker's page label and both "No matching…" lines used the same call.
- `crops/*-house-light-text-field.png`: text typed in a House light text field, dark with a dark shadow, then without it.

1.2.1 wraps the title, draws centred panel text with the look's own shadow rule, and turns the text field's shadow off in House light. `LookTest` now checks the dialog's widest line, reads the text field's shadow setting in every look, and counts dark pixels under the page label on light panels.
