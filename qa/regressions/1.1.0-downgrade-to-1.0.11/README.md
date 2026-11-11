# 1.1.0 settings loaded by 1.0.11

This run uses the published `hotkey-wheel-1.0.11+26.3.jar` (SHA-256 `02ea7a0b77bbf723a348baa42ebbdce2e4f4e394da606cb65cd72a2d0388d28b`) with the 1.1.0 `DowngradeTest` fixture, in the minimal profile. It shows what happens when someone goes back to 1.0.11 after saving folders in 1.1.0. It is not part of the release matrix.

The fixture writes a 1.1.0 settings file with a folder holding a slot and a nested folder, where the folder slot's Unbind normal key has removed F5 from Toggle Perspective. `FolderWheelTest` checks that 1.1.0 loads and saves this exact file unchanged.

1.0.11 loads it without errors and without treating it as unreadable, and leaves the file on disk as it was. The folder appears as an unassigned slot with the folder's name and icon; selecting it closes the wheel and does nothing. Other slots work. Loading changes no key binding.

If the user then saves in 1.0.11's editor, the folder's slot is kept but the slots inside it are dropped, because 1.0.11 has no place for them. The same Save gives back the keys that only folder slots had removed, so no key binding is lost. The saved file is in `downgrade-results.json`.

The folder keeps the report, provenance with every mod hash, and the executed fixture sources. The console log is kept locally and not committed.
