# Hotkey Wheel 1.1.1+26.3 verification

**Status: release verified.** The exact release JAR passes every profile with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.3+26.3, Buildprint 0.1.39+26.3 and Blocktone for Buildprint 0.1.39.1. It also passes the integrated profile with the previous Blocktone 0.1.39, whose instant-break is still there. It passes with a dedicated server, standalone Tool Pouch 1.2.0, BRBR 1.11.8, Polymer 0.18.7, Sorted 0.1.41, Controlling 26.3.4 and Mod Menu 21.0.0. The 1.1.0 record below is kept for reference.

Release JAR: `hotkey-wheel-1.1.1+26.3.jar`, SHA-256 `dd45d3f106071f61e48f0dd723bf9cd59caa2275514d5c9fca16dc63a4d686f2`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0; a second clean build gave the same bytes. Compared with 1.1.0, `ModSettingsActions` and `MoveSlotScreen` are new; `ControlPickerScreen`, `ControlsMarker`, `HotkeyWheelClient`, `WheelEditorScreen`, `WheelMenuReturn` and `WheelScreen` changed.

## What changed in 1.1.1

- **Move to…** moves a slot, or a folder with its contents, to another level of the wheel, refusing full levels, places too deep and a folder's own insides
- **Mod settings** wheel actions open a mod's settings screen through Mod Menu's public entrypoints, saved as `modmenu:config/<modid>`, and close with the original opener
- slots saved with Blocktone's removed Toggle instant-break act as Buildprint's Toggle Excavator, including Unbind normal key and recovery

## Results, 2026-11-10

Every profile used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. Inputs came from an isolated copy of the 1.1.0 record's mods with the same hashes; no run touched the user's launcher profiles or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none (no Mod Menu) | wheel 150, key management 88, moving slots 24, mod settings 7, folders 122 | 391 passed |
| integrated | Buildprint 0.1.39+26.3, Blocktone 0.1.39.1 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, dedicated-server camera question 48, moving slots 24, mod settings 20, folders 140 | 774 passed |
| toolpouch | Buildprint, Blocktone + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, Tool Pouch 191, slot toggles 53, moving slots 24, mod settings 20, folders 140 | 970 passed |
| suite | Buildprint, Blocktone + Vanilla++ 1.5.3+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, Tool Pouch and suite 206, BRBR 27, slot toggles 66, moving slots 24, mod settings 23, folders 140 | 1,028 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 1,028 passed |

Total: 4,191 passing checks. With Blocktone 0.1.39 in place of 0.1.39.1, the integrated profile passes 768 checks (Buildprint/Blocktone 227, the 1.1.0 instant-break checks; the rest as above); those reports are kept in the release folder under `qa/blocktone-0.1.39/`.

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Buildprint (original developer release) | 0.1.39+26.3 | `debca39878adab6c8017b89ead5e1c196b609dfec1e925fc5ac957a09589901f` |
| Blocktone for Buildprint | 0.1.39.1 | `75b40792c5d998995ebf17a6907e549c9d73971ba03af89d92ba890da7c919d0` |
| Blocktone for Buildprint (second run) | 0.1.39 | `81f8d793380c340e800c657a418288cc817508c53d2a0c5b87cb09e9e28b8422` |
| Vanilla++ Quality of Life Suite | 1.5.3+26.3 | `807083d8c7fb232c7616004575f36145562b17b46477c870cf4c5888fe3a2b39` |
| Tool Pouch (standalone profile) | 1.2.0+26.3 | `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1` |
| Better Recipe Book Rebetterered | 1.11.8+26.3 | `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16` |
| Polymer | 0.18.7+26.3 | `b18b0bb66fc8455fbf43f8a54e226952c630ab0cfee433e6585520c0e591538b` |
| Mod Menu | 21.0.0 | `837812c6c98d66141db7b5dcae668f27fb78d557e8f4c0c89912985fc26a5e6f` |
| Sorted | 0.1.41+26.3 | `bf378221dd17a81dd6985a3c1fb233ac5c465d5a0a32ae6917ae2d4b649368c1` |

Base set as for 1.1.0: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12; integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash.

**Moving slots** (`MoveSlotTest`, every profile, 24 checks). On a wheel with a slot that unbinds its key, folders three levels deep and a full folder: the picker lists the wheel and all six folders; the current level, the full folder, a place where a folder would end up four levels deep, a folder's own insides and a full top level are refused and say why,, and choosing a refused level does nothing; a slot may go into a folder three levels deep. A move changes nothing until Save, and Cancel discards it. Saved moves into a folder, out to the top level and of a folder with its contents into another folder keep the slot's control, icon, label, Hold action and Unbind normal key byte for byte, keep the removed key removed and its recovery key remembered, and leave the old level.

**Mod settings** (`ModSettingsTest`, every profile). With Mod Menu: the picker lists Hotkey Wheel, Buildprint (provided by its VoxelSprout hooks), Vanilla++ in the suite profiles and Minecraft (offered by Mod Menu itself), and not Fabric Loader, Fabric API, Searchables or Controlling; choosing Hotkey Wheel's entry saves `modmenu:config/hotkey_wheel`, which round-trips through the file. From a wheel, Hotkey Wheel's editor, Buildprint's VoxelSprout settings and Vanilla++'s Fzzy Config screen open, and the original opener closes each back to the game; a Hold action in a folder opens Hotkey Wheel's settings the same way. A slot for a missing mod says so. Without Mod Menu (minimal, 7 checks): nothing is listed, the saved slot shows "Hotkey Wheel settings (needs Mod Menu)", selecting it opens nothing, and the setting stays saved.

**Blocktone 0.1.39.1** (`BuildprintMenuTest`, `FolderWheelTest`). A slot saved with `key.buildprint_toning.instamine` resolves to Buildprint's Toggle Excavator and is shown under its name; selecting it from the wheel, and from a folder, turns the excavator on and off, and the saved name is not rewritten. Unbind normal key removes and remembers the excavator's key and gives it back. A key remembered for instant-break stays remembered while the slot unbinds, goes to an unbound Toggle Excavator on recovery, and never replaces a key it already has. A slot saved with the removed Instant-break settings opens nothing. The rest of the Buildprint and Blocktone coverage repeats unchanged, including validated closing of Blocktone's spline settings. With Blocktone 0.1.39 the 1.1.0 instant-break checks pass unchanged: its settings open from a wheel and a folder, refuse an invalid value and close with the original opener.

**Vanilla++, Buildprint and Blocktone.** The 1.1.0 coverage repeats with Vanilla++ 1.5.3, Buildprint 0.1.39 and both Blocktone versions: the suite's registered controls and configuration hub, the contextual Tool Pouch radial, BRBR coexistence, wheel-opened menus, child screens and the selection editor closed with the original opener, save, cancel and validation, reserved editor keys, the walking camera and the dedicated-server camera question. Buildprint's default J for its materials list does not collide with the fixtures' wheel keys.

Not covered: Move to… across wheels (it moves within the wheel being edited), every installed mod's settings screen (four were opened), mod icons beyond a screenshot of the picker, the older Buildprint 0.1.16–0.1.19 fallbacks, hardware GPU rendering, a server on a separate machine, and Controlling's other views. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.1.0+26.3 verification (previous release)

**Status: release verified.** The exact release JAR passes every profile with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.3+26.3, Buildprint 0.1.39+26.3 and Blocktone for Buildprint 0.1.39. The same JAR also passes the whole matrix, count for count, with Buildprint 0.1.38+26.3 and Blocktone 0.1.38. It passes with a dedicated server, standalone Tool Pouch 1.2.0, BRBR 1.11.8, Polymer 0.18.7, Sorted 0.1.41 and Controlling 26.3.4. The 1.0.11 record below is kept for reference.

Release JAR: `hotkey-wheel-1.1.0+26.3.jar`, SHA-256 `9ffff819d3b1e925050ddfeb1230e376c907b0b86cca9a7870a90e6f6dedb899`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. An independent rebuild from exported commit `7669438` (no build cache, all tasks rerun) reproduces the JAR and the sources JAR byte for byte. Compared with 1.0.11, `ControlsMarker`, `KeyBindsEntryMixin` and `ControllingEntryMixin` are new. `HotkeyWheelClient`, `WheelBindings`, `WheelConfig`, `WheelEditorScreen`, `WheelScreen` and `WheelUi.IconButton` changed, along with the language file, `fabric.mod.json` and the mixin list.

## What changed in 1.1.0

Folders:
- a folder is a slot with a name, an icon and up to 12 slots of its own, three levels deep; ordinary slots inside it have every usual option
- it opens as its own radial, with its path above it and Back in the center; Escape and right click go up one level and close at the top
- Hold: a click opens a folder while the key stays held; a release over a folder opens it and switches to clicks; a release in the center always closes
- the editor adds Add folder, folder names and icons, Open folder, a path back to each level, and a question before removing a folder that holds slots
- unbinding, recovery keys, Safe mode, Restore keys, Tool Pouch and the original-opener closure of Buildprint and Blocktone menus all count slots inside folders

Key Binds marker:
- a small gold wheel left of the key button marks every control an active wheel uses
- its tooltip names each place, such as Wheel 1 › Tools › Bag, and says when Hotkey Wheel removed the key; the text comes from translation keys
- vanilla's key list gets it through a mixin on its row; Controlling's list through Controlling's public `IKeyEntry` interface, with an optional injection that is skipped if the row changes

## Results, 2026-11-10

Every profile used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. Inputs came from an isolated copy of the profile's mods with the same hashes as the 1.0.11 record; no run touched the user's launcher profiles or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88, folders 122 | 360 passed |
| integrated | Buildprint 0.1.39+26.3, Blocktone 0.1.39 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, dedicated-server camera question 48, folders 140 | 724 passed |
| toolpouch | Buildprint, Blocktone + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, Tool Pouch 191, slot toggles 53, folders 140 | 920 passed |
| suite | Buildprint, Blocktone + Vanilla++ 1.5.3+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, Tool Pouch and suite 206, BRBR 27, slot toggles 66, folders 140 | 975 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 975 passed |

Total: 3,954 passing checks. With Buildprint 0.1.38+26.3 and Blocktone 0.1.38 in place of 0.1.39, the same JAR passes the same 3,954 checks in the same reports; those reports are kept in the release folder under `qa/buildprint-0.1.38/`.

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Buildprint (original developer release) | 0.1.39+26.3 | `debca39878adab6c8017b89ead5e1c196b609dfec1e925fc5ac957a09589901f` |
| Blocktone for Buildprint | 0.1.39 | `81f8d793380c340e800c657a418288cc817508c53d2a0c5b87cb09e9e28b8422` |
| Buildprint (second run) | 0.1.38+26.3 | `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8` |
| Blocktone for Buildprint (second run) | 0.1.38 | `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c` |
| Vanilla++ Quality of Life Suite | 1.5.3+26.3 | `807083d8c7fb232c7616004575f36145562b17b46477c870cf4c5888fe3a2b39` |
| Tool Pouch (standalone profile) | 1.2.0+26.3 | `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1` |
| Better Recipe Book Rebetterered | 1.11.8+26.3 | `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16` |
| Polymer | 0.18.7+26.3 | `b18b0bb66fc8455fbf43f8a54e226952c630ab0cfee433e6585520c0e591538b` |
| Sorted | 0.1.41+26.3 | `bf378221dd17a81dd6985a3c1fb233ac5c465d5a0a32ae6917ae2d4b649368c1` |

Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Polymer came from `Minecraft/thenathe-mod-suite/libs/installation/`, which is now the runner's default. Each profile's `provenance.json` has every mod hash, and `callback-order.json` records the transformed input order.

**Folders** (`FolderWheelTest`, last in every profile). The fixture checks:
- settings: a 1.0.11 file with slot toggles, the old suite elytra mapping and remembered keys loads and saves unchanged, with no folder fields added; 1.0.8 is covered by the key-management fixture as before; three levels of folders round-trip; a hand-edited fourth level keeps the slot but not its contents; a folder keeps at most 12 slots; a malformed folder and runaway nesting are copied aside as unreadable
- editor: Add folder, rename, icon through the icon picker, controls through the control picker, Unbind normal key on a folder slot, the third-level limit, breadcrumbs back to each level, the remove question with Keep, Escape and Remove folder, Cancel after a removal, and an empty folder removed without a question
- keys: a folder use two levels deep unbinds; Restore keys reaches folders; a use in a folder keeps a key unbound after the top-level use goes; hiding the wheel that holds the folder restores it; Safe mode protects folder slots and disables their option
- Hold: click into a folder and release inside; release over a folder, then click; nested folders; Escape and right click go back; Escape at the top closes; the center click goes back; a release in the center closes with no action; outside the ring cancels; a Hold action in a folder stays down until the key is released; after a release, Hold slots tap; the cycle key and another wheel's key switch to that wheel's top level
- Toggle: click a folder, the opener over the center goes back, the opener over a folder opens it, nesting, right click and Escape go back, Escape at the top closes, and a click activates without leaking into Attack
- controls with no key: Toggle Perspective (polled through clicks) and Player List as a Hold action, unbound in Controls and unbound by Unbind normal key, from the top level and from folders one and two levels deep; Buildprint's menu control the same way with Buildprint installed
- Buildprint and Blocktone from a folder: the menu, the selection editor and Blocktone's instant-break settings open from folder slots and close with the original opener
- Key Binds marker: the tooltip names each place inside folders and says when the wheel removed the key; hidden wheels are not marked; the marker's pixels are read from screenshots beside a used control and absent beside an unused one; the key and Reset buttons keep their positions; hovering shows the tooltip; the conflict stripe and the marker show side by side; Reset still works on a marked row. Controlling replaces the vanilla list whenever Key Binds opens, so the minimal profile checks the vanilla list and the other four check Controlling's, including its search

**Tool Pouch in folders.** `ToolPouchClientTest` opens Contextual Open from inside a folder and chooses a shulker from the item wheel. `ToolPouchToggleTest` toggles a pouch slot from inside a folder, removes its Tool Pouch key with Unbind normal key, and restores it when the folder is removed.

**Downgrade.** `DowngradeTest` loads a 1.1.0 settings file with folders into the published 1.0.11 JAR (11 checks): no error and no unreadable copy, the file on disk unchanged, the folder shown as an unassigned slot that does nothing, other slots working, and no key changed. A Save in 1.0.11 keeps the folder's slot but drops the slots inside it, and gives back keys that only folder slots had removed. See `qa/regressions/1.1.0-downgrade-to-1.0.11/`.

**Vanilla++, Buildprint and Blocktone.** The 1.0.11 coverage repeats unchanged with Vanilla++ 1.5.3 and both Buildprint/Blocktone pairs: the suite's registered controls and configuration hub, the contextual Tool Pouch radial, BRBR coexistence, wheel-opened menus, child screens and the selection editor closed with the original opener, save, cancel and validation, reserved editor keys, the walking camera and the dedicated-server camera question.

Not covered: the older Buildprint 0.1.16–0.1.19 fallbacks, hardware GPU rendering, a server on a separate machine, and Controlling's sort orders and its Show Unbound, Show Conflicts and Toggle Free views; only its default list and search were checked. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.0.11+26.3 verification (previous release)

**Status: release verified.** The exact release JAR passes every profile with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.2+26.3, Buildprint 0.1.38+26.3 and Blocktone for Buildprint 0.1.38. It also passes with a dedicated server, standalone Tool Pouch 1.2.0, BRBR 1.11.8, Polymer 0.18.7 and Sorted 0.1.41. The 1.0.10 record below is kept for reference.

Release JAR: `hotkey-wheel-1.0.11+26.3.jar`, SHA-256 `02ea7a0b77bbf723a348baa42ebbdce2e4f4e394da606cb65cd72a2d0388d28b`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. An independent rebuild from exported commit `5116d07` (no build cache, all tasks rerun) reproduces the JAR and the sources JAR byte for byte. Compared with 1.0.10, the changed classes are `ControlPickerScreen`, `HotkeyWheelClient`, `WheelBindings`, `WheelEditorScreen` and `WheelScreen`, plus `fabric.mod.json`; `ToolPouchToggles` is new.

## What changed in 1.0.11

Tool Pouch 1.2.0 slot toggles are wheel actions:
- the control picker lists **Toggle pouch Elytra** and each filled or key-bound slot of the active pouch, with its item, on/off state and Tool Pouch key
- selecting one uses Tool Pouch's own slot toggle and `C2SToggleSlotPayload`
- **Unbind normal key** removes and remembers a single slot's Tool Pouch key, and restoring puts it back

Vanilla++ 1.5.2 removes its `key.toolpouch.toggle_elytra` control. Wheel slots saved with it:
- keep their saved string and now toggle the pouch elytra slots
- if the wheel had removed that control's key, it returns as the Tool Pouch toggle key of free elytra slots when the option is turned off, the slot is removed or keys are restored
- an existing key is never replaced; without a free elytra slot, the key stays remembered

## Results, 2026-11-08

Every profile used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated | Buildprint 0.1.38+26.3, Blocktone 0.1.38 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, dedicated-server camera question 48 | 584 passed |
| toolpouch | Buildprint, Blocktone + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, Tool Pouch 180, slot toggles 47 | 763 passed |
| suite | Buildprint, Blocktone + Vanilla++ 1.5.2+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, Tool Pouch and suite 195, BRBR 27, slot toggles 60 | 818 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 818 passed |

Total: 3,221 passing checks.

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Buildprint (original developer release) | 0.1.38+26.3 | `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8` |
| Blocktone for Buildprint | 0.1.38 | `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c` |
| Vanilla++ Quality of Life Suite | 1.5.2+26.3 | `490cd993ee52d8bdaa868554c257d73dc9989b3470f4b0a583c979332b162d65` |
| Tool Pouch (standalone profile) | 1.2.0+26.3 | `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1` |
| Better Recipe Book Rebetterered | 1.11.8+26.3 | `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16` |
| Polymer | 0.18.7+26.3 | `b18b0bb66fc8455fbf43f8a54e226952c630ab0cfee433e6585520c0e591538b` |
| Sorted | 0.1.41+26.3 | `bf378221dd17a81dd6985a3c1fb233ac5c465d5a0a32ae6917ae2d4b649368c1` |

Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash, and `callback-order.json` records the transformed input order.

**Slot toggles** (`ToolPouchToggleTest`, natively and with Polymer). The fixture checks:
- the picker lists, labels and searches toggles
- slot and elytra toggles change the server's `toolpouch:disabled_slots`, and Tool Pouch stops finding a glider while the elytra slot is off
- Tool Pouch's own toggle key keeps working outside the wheel, and a wheel key on the same physical key takes precedence
- Unbind normal key removes, remembers and restores the slot's Tool Pouch key
- with Vanilla++ 1.5.2 the old elytra mapping is absent (`suite_elytra_mapping: false`), the saved slot keeps its string and toggles the pouch elytra, and a remembered key moves to the free elytra slot without replacing an existing key
- the Pouch Magnet, Pouch Collector and MapStitch atlas slots, toggled from the wheel, switch the suite's own `PickupUpgrades.hasMagnet`/`hasCollector` and `AtlasBridge.atlases` off and on

**Vanilla++.** The suite profiles also cover:
- the suite's registered controls and shared configuration hub with Tool Pouch settings navigation
- the contextual Tool Pouch radial with disabled slots
- BRBR coexistence
- every wheel, key-management, Buildprint/Blocktone and Mouse 4 scenario with the suite installed

**Buildprint and Blocktone.** Every Buildprint profile covers:
- wheel-opened menus, child screens and the selection editor, closed with the original opener
- save, cancel and validation, and native-opened menus left independent
- reserved editor keys and the walking camera
- the Blocktone scenarios, plus the six dedicated-server camera-question scenarios in the integrated profile

**Older Tool Pouch.** A supplementary run with Vanilla++ 1.4.5 (Tool Pouch 1.1.10) on the release JAR passes 185 Tool Pouch checks. No slot toggles are offered, and a saved toggle stays, labelled as needing Tool Pouch 1.2; see `qa/regressions/1.0.11-toolpouch-1.1.10-degrade/`.

Not covered: the older Buildprint 0.1.16–0.1.19 fallbacks, hardware GPU rendering, a server on a separate machine, and Vanilla++'s own carry-over of keys from `options.txt`, which belongs to the suite's QA. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.0.10+26.3 verification (previous release)

**Status: release verified.** The exact release JAR passes every profile with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.1+26.3, Buildprint 0.1.38+26.3 and Blocktone for Buildprint 0.1.38. It also passes with a dedicated server, standalone Tool Pouch 1.2.0, BRBR 1.11.8 and Polymer 0.18.7. 1.0.9 was packaged locally but never published; its folder and records are kept as they were.

Release JAR: `hotkey-wheel-1.0.10+26.3.jar`, SHA-256 `88639ec136fac83078680635acc64cf3969d6f1287593ec3d0eda0809747d4c0`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. An independent rebuild from exported commit `1989cbf` (no build cache, all tasks rerun) reproduces the JAR and the sources JAR byte for byte. Compared with 1.0.9, only `BuildprintApiIntegration`, `BuildprintEditorReturn` (with their nested classes) and `fabric.mod.json` changed.

## What changed in 1.0.10

The first time the Buildprint editor opens on a multiplayer server, Buildprint 0.1.38 asks whether to use freecam or the player's own camera. 1.0.9 dropped the wheel's claim when `open()` returned false for that question, so the editor started from the answer could not be closed with the wheel opener. 1.0.10 keeps the claim while that exact question is shown. The original opener also closes the question itself, and Escape still cancels it. Editors opened with Buildprint's own key are never claimed, and singleplayer, which never asks, behaves as before.

## Results, 2026-11-08

Every profile used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated | Buildprint 0.1.38+26.3, Blocktone 0.1.38 | wheel 154, key management 88, Buildprint/Blocktone menus 227, Mouse 4 67, dedicated-server camera question 48 | 584 passed |
| toolpouch | integrated + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel, key management, Buildprint/Blocktone, Mouse 4, Tool Pouch 179 | 715 passed |
| suite | Buildprint, Blocktone + Vanilla++ 1.5.1+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 227, Mouse 4 67, Tool Pouch and suite 194, BRBR 27 | 757 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 757 passed |

Total: 3,051 passing checks.

**Dedicated server.** `ServerCameraChoiceTest` starts Minecraft 26.3's dedicated server in the test JVM through the Fabric client gametest API (offline mode, isolated world, `eula=true` in the QA game folder), then joins it as a multiplayer client. Buildprint asks its camera question there. The fixture covers six scenarios:
1. first wheel opening, answered with free camera, closed with the wheel opener
2. second wheel opening with no question, closed with the opener
3. question cancelled with Escape, then a native opening that the wheel does not claim
4. question closed with the original opener, no editor, no leftover key state
5. first opening answered with the walking camera, closed with the opener
6. a native opening on an answered server, not claimed

Unpublished 1.0.9 fails scenario 1; see `qa/regressions/1.0.9-server-camera-question/`. Singleplayer behavior is covered unchanged by the existing Buildprint fixture.

Exact inputs, SHA-256:

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Buildprint (original developer release) | 0.1.38+26.3 | `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8` |
| Blocktone for Buildprint | 0.1.38 | `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c` |
| Vanilla++ Quality of Life Suite | 1.5.1+26.3 | `00aa245b80aa62cdca2cee5a7a3cbdf30ec0c7e0df37b71cea8a146ba9f8328c` |
| Tool Pouch (standalone profile) | 1.2.0+26.3 | `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1` |
| Better Recipe Book Rebetterered | 1.11.8+26.3 | `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16` |
| Polymer | 0.18.7+26.3 | `b18b0bb66fc8455fbf43f8a54e226952c630ab0cfee433e6585520c0e591538b` |
| Sorted | 0.1.41+26.3 | `bf378221dd17a81dd6985a3c1fb233ac5c465d5a0a32ae6917ae2d4b649368c1` |

**Vanilla++.** The suite profiles cover:
- the suite's registered controls, including the unbound settings binding opened from a wheel
- the shared configuration hub and its Tool Pouch settings navigation, with saved wheel settings unchanged
- the contextual Tool Pouch radial with the bundled Tool Pouch 1.2.0, including disabled slots
- BRBR pin and settings coexistence
- every wheel, key-management, Buildprint/Blocktone and Mouse 4 scenario with the suite installed, natively and with Polymer

**Sorted.** Sorted 0.1.41 is loaded in every Buildprint profile, including both suite profiles with 1.5.1's Sorted integration. Buildprint 0.1.38's settings screen opens and closes normally with it. Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash, and `callback-order.json` records the transformed input order.

Not covered: the older Buildprint 0.1.16–0.1.19 fallbacks and hardware GPU rendering. The dedicated server runs in the test JVM, not as a separate machine. Earlier 1.0.9 runs are not counted as evidence for 1.0.10; 1.0.9's own records are in its release folder. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.0.8+26.3 verification (previous release)

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. The release folder `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` keeps the full 1.0.8 verification record, including its 0.1.19 Buildprint, Blocktone 0.1.24, Vanilla++ 1.4.4 and Polymer inputs.
