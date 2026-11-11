# Hotkey Wheel 1.1.3+26.3 verification

**Status: release verified.** The exact release JAR passes every profile of the full matrix with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.3+26.3, Buildprint 0.1.39+26.3 and Blocktone for Buildprint 0.1.39.1. Each profile is followed by a restart, a second game that loads the settings the first one saved, and every profile loads settings written by the published 1.0.11 and 1.1.1 JARs. It passes with a dedicated server, standalone Tool Pouch 1.2.0, BRBR 1.11.8, Polymer 0.18.7, Sorted 0.1.41, Controlling 26.3.4 and Mod Menu 21.0.0. This also covers 1.1.2's features in the integrated, standalone Tool Pouch and Polymer profiles, which 1.1.2 did not run. The 1.1.2 record below is kept for reference.

Release JAR: `hotkey-wheel-1.1.3+26.3.jar`, SHA-256 `bff89476f8717774197ff3bfc524af577edbd3126b661136c99f591371c70de6`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0; a second clean build with every task rerun gave the same bytes. Compared with 1.1.2, `RadialGeometry`, `WheelScreen`, `ToolPouchRadialScreen` and `WheelUi` changed, along with the description in `fabric.mod.json`.

## What changed in 1.1.3

- the hint under the wheel, its folders and the Tool Pouch item radial sits with the hovered name and the key line, just under the radial, instead of over the hotbar; the three lines stay at least 2 pixels above the hearts at every GUI scale and window size, the radial moves up and if needed shrinks to make room, and a hint too wide for the screen wraps
- `fabric.mod.json` describes the new icon choices

## Results, 2026-11-11

Every run used the release JAR and the same fixture sources, on a 1920×1080 Xvfb screen with Mesa's software renderer; the game window is 1280×800 unless a fixture resizes it. Inputs came from an isolated copy of the 1.1.1 record's mods with the same hashes; no run touched the user's launcher profiles or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none (no Mod Menu) | wheel 150, key management 88, moving slots 24, mod settings 7, icons 94, control folders 22, editor layout 60, layout 98, upgrade 22, folders 122 | 687 passed |
| integrated | Buildprint 0.1.39+26.3, Blocktone 0.1.39.1 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, dedicated-server camera question 48, moving slots 24, mod settings 20, icons 94, control folders 42, editor layout 60, layout 98, upgrade 22, folders 140 | 1,090 passed |
| toolpouch | Buildprint, Blocktone + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, Tool Pouch 191, slot toggles 53, moving slots 24, mod settings 20, icons 94, control folders 49, editor layout 60, layout 128, upgrade 22, folders 140 | 1,323 passed |
| suite | Buildprint, Blocktone + Vanilla++ 1.5.3+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, Tool Pouch and suite 206, BRBR 27, slot toggles 66, moving slots 24, mod settings 23, icons 96, control folders 49, editor layout 60, layout 128, upgrade 22, folders 140 | 1,383 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 1,383 passed |
| each profile, restart | the settings that profile saved | restart 8 | 5 × 8 passed |

Total: 5,906 passing checks. The input hashes are those of the 1.1.1 record (Buildprint `debca398…`, Blocktone 0.1.39.1 `75b40792…`, Vanilla++ `807083d8…`, standalone Tool Pouch `fcc2f984…`, BRBR `f02f61c6…`, Polymer `b18b0bb6…`, Mod Menu `837812c6…`, Sorted `bf378221…`); each run's `provenance.json` lists every mod hash, including the five small icon fixture mods.

**Layout** (`LayoutScaleTest`, every profile). The wheel, a folder opened from it and, with Tool Pouch, the Tool Pouch item radial are opened with a slot hovered at GUI scale 2 and 3 in a 1280×800 window, GUI scale 4 in a 1920×1080 window, 640×360 and 683×384 windows at Minecraft's automatic scale, and a 640×480 window at scale 2, which is the smallest GUI Minecraft allows (320×240). Each case checks the scale actually used, then that the hovered name, the key line and the hint end at least 2 pixels above the hearts, that the hint sits under the key line, that the key line fits the screen, and that the title stays clear of a hovered top sector. The folder radial sits exactly where the wheel does, and the Tool Pouch radial follows the same placement rule. Measured, in GUI pixels:

| Screen | GUI | Radius | Centre | Text ends | Hearts start |
| --- | --- | --- | --- | --- | --- |
| 1280×800, scale 2 | 640×400 | 110 | 184 | 347 | 361 |
| 1280×800, scale 3 | 427×267 | 74 | 99 | 226 | 228 |
| 1920×1080, scale 4 | 480×270 | 76 | 100 | 229 | 231 |
| 640×360 window | 640×360 | 110 | 156 | 319 | 321 |
| 683×384 window | 683×384 | 110 | 176 | 339 | 345 |
| 640×480, scale 2 | 320×240 | 61 | 85 | 199 | 201 |

At 640×400, 683×384, 720×450 and 960×540 the wheel keeps 1.1.1's geometry; only the hint moved. Every current hint fits on one line even at 320 GUI pixels, so the wrapping path is not exercised. The Move to… checks from 1.1.2 repeat unchanged.

**1.1.2 features in every profile.** The icons, the control chooser's folders, the editor's General and wheel tabs with the settings key, the restart and the upgrade from 1.0.11 and 1.1.1 settings pass in all five profiles, as recorded for 1.1.2 below. In the integrated profile the control folders are Minecraft controls, Mod settings, Buildprint, Mod Menu and Sorted; the standalone Tool Pouch is its own folder in the toolpouch profile, with its slot toggles. U, the settings key's default, is shared with no other control in any profile.

**Vanilla++, Buildprint and Blocktone.** The 1.1.1 coverage repeats with Vanilla++ 1.5.3, Buildprint 0.1.39 and Blocktone 0.1.39.1, natively and with Polymer: the suite's registered controls and configuration hub, the contextual Tool Pouch radial, BRBR coexistence, wheel-opened menus, child screens and the selection editor closed with the original opener, save, cancel and validation, reserved editor keys, the walking camera and the dedicated-server camera question.

Not covered: Blocktone 0.1.39, the older Buildprint 0.1.16–0.1.19 fallbacks, the hint's wrapping (no current hint needs it), key bindings surviving a restart without the harness's option reset (read back with `Options.load`), hardware GPU rendering, a server on a separate machine, and Controlling's other views. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.1.2+26.3 verification (previous release)

**Status: smoke-tested and targeted-tested, at the user's request.** The full five-profile matrix was not run for this release. The exact release JAR passes every report in the minimal profile and in the Vanilla++ suite profile with Vanilla++ Quality of Life Suite 1.5.3+26.3, Buildprint 0.1.39+26.3 and Blocktone for Buildprint 0.1.39.1, and each profile then passes a restart: a second game that loads the settings the first one saved. The integrated, standalone Tool Pouch and Polymer profiles were not run, and neither was the dedicated-server camera question, which only runs in the integrated profile. The 1.1.1 record below is kept for reference.

Release JAR: `hotkey-wheel-1.1.2+26.3.jar`, SHA-256 `d65f3c998d1bd258d5af0eb94c411b8bc840b4f9609f59bdd3eded3d01795284`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0; a second clean build with every task rerun gave the same bytes. Compared with 1.1.1, `WheelIcons`, `ModIcons`, `ControlOwners` and the `KeyMappingOwnerMixin` are new, along with 39 GUI sprites in `textures/gui/sprites/icon/`; `ControlPickerScreen`, `HotkeyWheelClient`, `IconPickerScreen`, `ModSettingsActions`, `MoveSlotScreen`, `RadialGeometry`, `ToolPouchRadialScreen`, `WheelBindings`, `WheelConfig`, `WheelEditorScreen`, `WheelScreen` and `WheelUi` changed, as did the language file and the mixin list.

## What changed in 1.1.2

- the icon picker offers 39 original Hotkey Wheel icons and every installed mod's icon besides blocks and items, saved as `hotkey_wheel:icon/<name>` and `hotkey_wheel:mod/<modid>`
- **Settings from an open wheel** (U by default) opens the editor on the open wheel and folder
- the control chooser sorts controls into Minecraft controls, Mod settings, a folder per mod and Other controls, with an option for one Mod controls folder
- the editor has a General tab, and each wheel's tab starts with that wheel's own settings
- fixes: at GUI scale 3 the wheel's hovered name and key line no longer cover the hearts and hotbar, and the Move to… subtitle wraps and says "go" for a folder

## Results, 2026-11-11

Every run used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. Inputs came from an isolated copy of the 1.1.1 record's mods with the same hashes; no run touched the user's launcher profiles or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none (no Mod Menu) | wheel 150, key management 88, moving slots 24, mod settings 7, icons 94, control folders 22, editor layout 60, GUI scale 32, upgrade 22, folders 122 | 621 passed |
| minimal, restart | the settings saved above | restart 8 | 8 passed |
| suite | Buildprint 0.1.39+26.3, Blocktone 0.1.39.1, Vanilla++ 1.5.3+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | wheel 154, key management 88, Buildprint/Blocktone 233, Mouse 4 67, Tool Pouch and suite 206, BRBR 27, slot toggles 66, moving slots 24, mod settings 23, icons 96, control folders 49, editor layout 60, GUI scale 32, upgrade 22, folders 140 | 1,287 passed |
| suite, restart | the settings saved above | restart 8 | 8 passed |

Total: 1,924 passing checks. The input hashes are those of the 1.1.1 record: Buildprint `debca398…`, Blocktone 0.1.39.1 `75b40792…`, Vanilla++ `807083d8…`, BRBR `f02f61c6…`, Mod Menu `837812c6…`, Sorted `bf378221…`; each run's `provenance.json` lists every mod hash, including the five small fixture mods below.

**Icons** (`IconPickerTest`, both profiles). Installed mods that declare an icon are listed (Hotkey Wheel, Fabric API, Fabric Loader and, in the suite, Vanilla++, the Tool Pouch, Chalk, MapStitch and other mods it bundles, BRBR, Buildprint, Mod Menu and the libraries); Minecraft and Java are not; Fabric API's modules, whose icon is Fabric API's, are not listed separately; a mod without an icon is not listed. Five fixture mods test unusable icons: a 48×24 PNG loads into a 64×64 texture with transparent bands above and below; a file that is not a PNG, a PNG header claiming 9000×9000 pixels, and a declared icon missing from the JAR are refused without a crash and drawn as the puzzle piece; the texture is uploaded once, released, and loaded again when next needed. With no search the picker lists the 39 icons first, cog first, then mods' icons by name, then blocks and items, modded ones included; all 38 requested icons and the puzzle piece have sprites. Searches: **settings** and **gear** find the cog first, **x** finds Close first, **list** both lists, **book** gives the Book item, then the bookmark icon, then Bookshelf, then in the suite BRBR's icon before Enchanted Book; a mod's name and ID find its icon; **stone** and `minecraft:iron_pickaxe` still work. Through the editor, slots and folders take a Hotkey Wheel icon and a mod icon, including a slot inside a folder; Save writes them to the `icon` field, a reload keeps them, and a slot for a removed mod keeps `hotkey_wheel:mod/hkwqa_removed_mod`. On the wheel, screenshots are read pixel by pixel: the cog, the folder's star and the removed mod's puzzle piece draw as light glyphs, and the mod icon draws its own picture. Before joining a world the picker can already choose a Hotkey Wheel icon.

**Restart** (`RestartTest`, a second game in each profile). The settings file the first game saved loads at startup unchanged, with every icon; the cog draws on the wheel; selecting it opens the inventory; the settings key opens the editor from the wheel; and Save writes the same file. The client gametest harness starts every game with default options, so the saved key bindings are read back with Minecraft's own `Options.load` and the settings key's new binding (P) is checked after that.

**Upgrade** (`UpgradeTest`, both profiles). Settings files written by the published 1.0.11 and 1.1.1 JARs themselves (`qa/regressions/1.1.2-upgrade-from-1.0.11-and-1.1.1/`) load without being called unreadable and keep every setting, remembered key, pouch toggle, mod settings action, the instant-break control and folders three levels deep. A Save in the new editor, after visiting General and Wheel 1, writes each file back byte for byte and keeps the removed key removed. Giving two slots new icons changes only those two values. The control chooser opens on the upgraded settings.

**Control folders** (`ControlFolderTest`, both profiles). Every control is credited to the mod whose code created it: Minecraft's to Minecraft, Hotkey Wheel's to Hotkey Wheel, and in the suite Buildprint's 16 controls to Buildprint, Tool Pouch's to the Tool Pouch bundled in Vanilla++, and BRBR, MapStitch, Mod Menu, Sorted, Tiered Backpacks and Vanilla++ to themselves; Fabric API's registration helper is never credited, and no suite control lands in Other controls. Blocktone 0.1.39.1 registers no controls of its own. The folders come in order: Minecraft controls, Mod settings (with Mod Menu), each mod by name, then Other controls; every control is in exactly one folder; Minecraft controls holds only Minecraft's, under its categories; Tool Pouch's folder holds its controls and its slot toggles. Escape inside a folder goes back; paging through Minecraft controls and choosing Inventory saves `key.inventory`; a search lists matches from every folder with the folder's name. With one Mod controls folder the list is Minecraft controls, Mod settings, Mod controls, and Mod controls holds every mod's controls. The General setting reaches the picker opened from a slot before Save; Cancel discards it; Save writes `"modControlsInOneFolder": true`, which survives a reload and is left out of the file again when turned off.

**Editor and the settings key** (`EditorLayoutTest`, both profiles). The default U is shared with no other control in either profile, including Buildprint's (which uses K) and Vanilla++'s, and is not a key Safe mode reserves. The editor opens on Wheel 1; the tabs are General and each active wheel. A wheel tab shows its name, opening key, Open mode, Outside wheel and the slots, and none of the general settings; General shows active wheels, cycle, configure and settings keys, Safe mode, Restore keys, Mod controls and the three integrations, and no wheel settings. Edits on both tabs wait for Save, Cancel discards them, switching tabs keeps the draft, and Save applies and keeps them through a reload. The settings key may not share the cycle key or another wheel's opener, Safe mode refuses the left mouse button for it, and it may share Configure wheels. In a world, U pressed with wheel 1 open two folders deep opens the editor on Wheel 1 inside Tools › Deep; releasing the held wheel key leaves the editor open and uses no slot; Cancel returns to the game. From wheel 2 in Toggle mode it opens on Wheel 2. Outside a wheel U does nothing.

**GUI scale 3** (`LayoutScaleTest`, both profiles). At 1280×800 and GUI scale 3 (427×267 GUI pixels) the wheel's radius drops from 88 to 80 and its centre moves up 9 pixels, so the hovered name and the key line end at the top of the hearts row (228 of 228) and the title stays on screen. At GUI scale 2 the wheel is placed exactly as in 1.1.1, as are 640×360, 683×384, 720×450 and 960×540. The Move to… subtitle for a slot is one line at GUI scale 2, as before, and says "goes"; for a folder it says "go" and wraps to two lines at both scales; every line fits the screen and the levels start below it, at the same place as before on GUI scale 2.

**Smoke.** In both profiles the existing fixtures repeat unchanged apart from their editor navigation: wheels open, select, run controls and Hold actions, the editor opens and saves, folders, Move to…, mod settings, the Key Binds marker and Controlling's list work, and in the suite profile Vanilla++'s controls and configuration, the Tool Pouch radial and slot toggles, BRBR, and Buildprint and Blocktone menus closed with the original opener all pass as in 1.1.1. The fixtures now press General before Restore keys and the cycle key, the icon button is called **Choose icon…**, and Controlling's search finds the wheel's six keys.

Not covered in 1.1.2: the integrated, standalone Tool Pouch and Polymer profiles; the dedicated-server camera question; Blocktone 0.1.39; key bindings surviving a restart without the harness's option reset (read back with `Options.load` instead); GUI scales other than 2 and 3 in a running game; mods whose code creates other mods' controls; the Tool Pouch item radial at GUI scale 3, which shares the wheel's placement but was not screenshotted; hardware GPU rendering. Hotkey Wheel is an original client mod with no ported counterpart.

# Hotkey Wheel 1.1.1+26.3 verification (previous release)

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
