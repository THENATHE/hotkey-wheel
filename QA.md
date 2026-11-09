# Hotkey Wheel 1.0.11+26.3 verification

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
