# Changes

## 1.1.1+26.3

- **Move to…** in the editor moves the selected slot, or a folder with everything in it, into a folder, up to the level above, or into another folder of the same wheel. A small picker shows the wheel and its folders as a tree with their slot counts. Full levels, places too deep for the three-level limit, and a folder's own insides are greyed out and say why. The slot keeps all its settings and Hotkey Wheel's remembered keys; as with every edit, nothing changes until Save.
- **Mod settings** in the control picker: a slot can open any installed mod's settings screen, the one Mod Menu opens, with no key needed. The searchable group lists each mod that has a settings screen, with its icon. Slots save the action as `modmenu:config/<modid>`. The screen returns to the game when it closes, and the original wheel opener closes it, like the Buildprint menus, through the screen's own save and cancel. Without Mod Menu the slots stay saved and show as needing Mod Menu.
- The option for that is now called **Wheel key closes menus it opened**; its saved setting is unchanged.
- Blocktone for Buildprint 0.1.39.1 replaced instant-break with Buildprint's excavator. Wheel slots saved with **Toggle instant-break** keep their saved setting and act as Buildprint's **Toggle Excavator** once Blocktone no longer registers the old control. Unbind normal key then applies to the excavator's key; a key Hotkey Wheel had removed from instant-break is given to Toggle Excavator on recovery, never over a key it already has. Blocktone 0.1.39 and 0.1.38 keep working as before, including their instant-break settings.
- QA adds `MoveSlotTest` and `ModSettingsTest` to every profile and checks the instant-break slots with the new Blocktone and the old one. See [QA.md](QA.md).

## 1.1.0+26.3

- Folders: a slot can be a named folder with its own icon and up to 12 slots, each with the usual control, icon, label, Tap / Hold and Unbind normal key. Folders can hold folders, three levels deep.
- A folder opens as its own radial with the same look. The path is shown above it, and its center is **Back**. Escape and right click go up one level and close the wheel at the top. In Hold mode, click a folder and release inside it, or release over a folder to keep it open and then click, as in the Tool Pouch item radial.
- The editor adds **Add folder**, folder names and icons, **Open folder**, and a path back to each level. Removing a folder that holds slots asks first.
- Unbind normal key, Safe mode, **Restore keys unbound by Hotkey Wheel**, Tool Pouch, and the Buildprint and Blocktone menus closed with the original opener all work for slots inside folders. A use inside a folder counts when deciding whether to restore a key.
- **Options → Controls → Key Binds** marks each control a wheel uses with a small wheel beside its key button. Its tooltip names each place, such as Wheel 1 › Tools › Pickaxe, and says when Hotkey Wheel removed the key. Controlling's key list shows the same marker.
- Settings from 1.0.x load and save unchanged. 1.0.11 can still load settings with folders; see the README for what a Save there keeps.
- QA adds `FolderWheelTest` to every profile and a downgrade check that loads 1.1.0 settings into the 1.0.11 JAR. Activation of controls with no key at all is now covered for a vanilla control, a Buildprint control and Hold actions, at the top level and in folders. See [QA.md](QA.md).

## 1.0.11+26.3

- Tool Pouch 1.2.0 slot toggles can be chosen as wheel actions. Each shows its item, on/off state and Tool Pouch key. **Toggle pouch Elytra** switches all elytra-like pouch slots. Toggles use Tool Pouch's own slot toggle and server message.
- **Unbind normal key** also works for single-slot toggles: Save removes and remembers the slot's Tool Pouch key, and restoring puts it back.
- Wheel slots saved with Vanilla++'s **Toggle Tool Pouch Elytra** control keep working. Once Vanilla++ 1.5.2 removes that control, they toggle the pouch elytra through Tool Pouch, and the saved setting is unchanged. A key Hotkey Wheel had removed from that control is restored to the pouch's free elytra slots as their Tool Pouch toggle key, never over an existing key; without a free slot it stays remembered.
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
