# Hotkey Wheel 1.0.9+26.3 verification

**Status: release verified.** The exact release JAR passes every profile with the supported versions of all three integrations: Vanilla++ Quality of Life Suite 1.5.0+26.3, Buildprint 0.1.38+26.3 and Blocktone for Buildprint 0.1.38. It also passes with standalone Tool Pouch 1.2.0, BRBR 1.11.8 and Polymer 0.18.7. Version 1.0.8 and its evidence stay in their own release folder.

Release JAR: `hotkey-wheel-1.0.9+26.3.jar`, SHA-256 `b3f6fcf41e0ee23728515434caada70ec2eea92f0ac448b0e06e24e6c8a12c0f`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. A clean in-place build and an independent rebuild from exported commit `b1c5181` (no build cache, all tasks rerun) reproduce the JAR and the sources JAR byte for byte. Compared with shipped 1.0.8, only `HotkeyWheelClient`, `WheelConfig`, `WheelScreen`, `KeyboardMixin` and `fabric.mod.json` changed; the other 59 entries are identical.

## Results, 2026-11-08

Every profile used the release JAR and the same fixture sources, on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated | Buildprint 0.1.38+26.3, Blocktone 0.1.38 | wheel 154, key management 88, Buildprint/Blocktone menus 227, Mouse 4 67 | 536 passed |
| toolpouch | integrated + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | integrated reports + Tool Pouch 179 | 715 passed |
| suite | integrated + Vanilla++ 1.5.0+26.3 (bundles Tool Pouch 1.2.0), BRBR 1.11.8+26.3 | integrated reports + Tool Pouch and suite 194, BRBR 27 | 758 passed |
| suite-polymer | suite + Polymer 0.18.7+26.3 | same as suite | 758 passed |

Total: 3,005 passing checks.

Exact inputs, SHA-256:

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Buildprint (original developer release) | 0.1.38+26.3 | `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8` |
| Blocktone for Buildprint | 0.1.38 | `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c` |
| Vanilla++ Quality of Life Suite | 1.5.0+26.3 | `2bc95d4b0ad0d41a18b1e1100371e548b6c0dc4a994fa057af48fc9029de8c3c` |
| Tool Pouch (standalone profile) | 1.2.0+26.3 | `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1` |
| Better Recipe Book Rebetterered | 1.11.8+26.3 | `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16` |
| Polymer | 0.18.7+26.3 | `b18b0bb66fc8455fbf43f8a54e226952c630ab0cfee433e6585520c0e591538b` |

Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash.

**Vanilla++.** The suite profiles cover:
- the suite's registered controls, including the unbound settings binding opened from a wheel
- the shared configuration hub and its Tool Pouch settings navigation, with saved wheel settings unchanged
- the contextual Tool Pouch radial with the suite's bundled Tool Pouch 1.2.0, including disabled slots
- BRBR pin and settings coexistence
- every wheel, key-management and Buildprint/Blocktone scenario with the suite installed, natively and with Polymer

**Buildprint and Blocktone.** Every Buildprint profile covers:
- wheel-opened menus, child screens and the selection editor, closed with the original opener (keyboard, Mouse 4, Hold, Toggle and after cycling)
- normal save, cancel and validation behavior
- native-opened menus and editors left independent
- the reserved editor keys C and E, and the walking camera
- three Toning picker entries, spline settings validation and Cancel, and instant-break settings validate, refuse and save

**Sorted.** Sorted 0.1.41 (the user's Vanilla Fast version) is in every Buildprint profile. Buildprint 0.1.38's settings screen opens and closes normally with it.

`callback-order.json` in each profile is read from Mixin's exported `MouseHandler` and `KeyboardHandler`. In every Buildprint profile, `keyPress` runs the wheel's owned-menu return first, then Buildprint's editor keys, then the ordinary wheel input. `onButton` runs the wheel input before Buildprint's editor and assist clicks.

New checks in this release: Buildprint-reserved openers C and E close a wheel-owned editor; the walking-camera editor; tool numbers read from Buildprint's registry; the sectioned loaded-schematic list; a 1.0.8 configuration loads and saves without changes; an unreadable configuration is copied aside; Tool Pouch 1.2.0 disabled slots. The shipped 1.0.8 JAR fails the reserved-key and unreadable-config checks and passes the 1.0.8 load and save checks; see `qa/regressions/1.0.8-upstream-2026-11/`.

Not covered: dedicated multiplayer servers (including Buildprint's first-time camera dialog below), the older Buildprint 0.1.16–0.1.19 fallbacks and hardware GPU rendering. Hotkey Wheel is an original client mod with no ported counterpart.

## Parked decision

On a multiplayer server where Buildprint has not yet asked which camera to use, the first editor opening shows a camera dialog. `open()` returns false while the dialog is shown, so the wheel does not claim that session. The wheel's opener does not close it; Buildprint's editor key does. Later openings work normally, and singleplayer never asks. Options: (a) keep this behavior, as documented in the README; (b) keep the wheel's claim while Buildprint's camera dialog is open. Option (b) needs a dedicated-server gametest before it can be verified.

# Hotkey Wheel 1.0.8+26.3 verification (previous release)

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. The release folder `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` keeps the full 1.0.8 verification record, including its 0.1.19 Buildprint, Blocktone 0.1.24, Vanilla++ 1.4.4 and Polymer inputs.
