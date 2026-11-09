# Changes

## 1.0.11+26.3

- Tool Pouch 1.2.0 slot toggles can be chosen as wheel actions. Each shows its item, on/off state and Tool Pouch key. **Toggle pouch Elytra** switches all elytra-like pouch slots. Toggles use Tool Pouch's own slot toggle and server message.
- **Unbind normal key** also works for single-slot toggles: Save removes and remembers the slot's Tool Pouch key, and restoring puts it back.
- Wheel slots saved with Vanilla++'s **Toggle Tool Pouch Elytra** control keep working. When a newer Vanilla++ removes that control, they toggle the pouch elytra through Tool Pouch. The saved setting and any remembered key are kept unchanged.
- With Tool Pouch before 1.2.0, saved slot toggles stay in the wheel and are shown as needing Tool Pouch 1.2.

## 1.0.10+26.3

- Fix: on a multiplayer server where Buildprint first asks whether the editor uses freecam or the player's camera, the editor started from the answer now belongs to the wheel that opened it, so the original opener closes it. Pressing the opener while the question is shown closes the question. Escape still cancels it, and editors opened with Buildprint's own key stay independent.
- QA joins a real dedicated 26.3 server, started in the test JVM, where the question appears.
- Verified with Vanilla++ 1.5.1+26.3, Buildprint 0.1.38+26.3, Blocktone for Buildprint 0.1.38, standalone Tool Pouch 1.2.0, BRBR 1.11.8 and Polymer 0.18.7: 3,051 checks across five profiles. See [QA.md](QA.md).

## 1.0.9+26.3

Packaged locally but never published; 1.0.10 includes all of its changes.

- Built against Buildprint 0.1.38+26.3, Tool Pouch 1.2.0+26.3 and Fabric API 0.165.2+26.3. The requirements stay at Fabric API 0.161.0 or newer, Tool Pouch 1.1.10 or newer, and any Buildprint with the existing fallbacks.
- Fix: a wheel bound to a key that Buildprint 0.1.38 reserves in the editor, such as C or E, now closes the editor it opened. Buildprint's own shortcuts are unchanged in editors the wheel did not open.
- Fix: an unreadable `hotkey-wheel.json` is copied to `hotkey-wheel.json.unreadable` before a later Save can replace it.
- Tool Pouch 1.2.0 disabled slots are left out of the item radial, as in Tool Pouch's own menu, and choosing an item still opens the right shulker box.
- Buildprint's walking-camera editor works with the original-opener closure.
- The build pins Modrinth version IDs. The version number alone resolved to the NeoForge Tool Pouch file.
- The radial looks up icons and controls once per wheel instead of every frame.
- Verified with Vanilla++ 1.5.0+26.3, Buildprint 0.1.38+26.3, Blocktone for Buildprint 0.1.38, standalone Tool Pouch 1.2.0, BRBR 1.11.8 and Polymer 0.18.7: 3,003 checks across five profiles. See [QA.md](QA.md).
- QA runs on a virtual display, adds a standalone Tool Pouch profile and checks that 1.0.8 settings load and save without loss. See [AUDIT-2026-11-08.md](AUDIT-2026-11-08.md).

## 1.0.8+26.3

- Use explicit mouse injector order 900 for the existing wheel ownership handler, ahead of default-order editor mouse injectors. The focused native `key.mouse.4` regression passes without changing editor ownership or public API behavior; the complete fresh release matrix passes 2,188 checks.
- Add native side-mouse press/release regression coverage and repeat the complete minimal, Buildprint, and four-mod native/Polymer acceptance matrix against the exact final JAR.
- Include every freshly captured acceptance screenshot in the release evidence. Keep the original 1.0.7 release and its reviewed source/evidence separate.

## 1.0.7+26.3

Adopted Buildprint 0.1.19's public editor open/close and registered-screen ownership APIs. The separately dated review added all-four integration checks and the full documented API audit while preserving the original production JAR and tag.
