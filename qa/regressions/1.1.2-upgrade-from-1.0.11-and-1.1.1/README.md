# 1.0.11 and 1.1.1 settings written by those releases

These runs use the published `hotkey-wheel-1.0.11+26.3.jar` (SHA-256 `02ea7a0b77bbf723a348baa42ebbdce2e4f4e394da606cb65cd72a2d0388d28b`) and `hotkey-wheel-1.1.1+26.3.jar` (SHA-256 `dd45d3f106071f61e48f0dd723bf9cd59caa2275514d5c9fca16dc63a4d686f2`) with the `UpgradeSourceTest` fixture, in the minimal profile. Each release loads a settings file covering its own features and saves it with its own code, so the file in `upgrade/` is exactly what that release writes.

- 1.0.11: two wheels, one in Toggle mode and one with Select by direction, a label, a Hold action, a Tool Pouch slot toggle, Unbind normal key with its remembered key, and Safe mode, the Tool Pouch wheel and the menu closing turned off.
- 1.1.1: folders three levels deep, a mod settings action, a slot saved with Blocktone's old instant-break control, Unbind normal key inside a folder and its remembered key.

`UpgradeTest` loads both files in 1.1.2 in the minimal and Vanilla++ suite profiles. Each one loads without being called unreadable, keeps every setting and remembered key, and is written back byte for byte by a Save in the new editor after visiting the General and Wheel 1 tabs. Giving two slots new icons then changes only those two `icon` values. See [QA.md](../../../QA.md).

Each folder keeps the report, provenance with every mod hash, the executed fixture sources and the settings file the release wrote. Console logs are kept locally and not committed.
