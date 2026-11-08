# Hotkey Wheel 1.0.9+26.3 verification

**Status: Buildprint and Blocktone verified; new Vanilla++ candidate still to test.** The candidate passes the full matrix with Buildprint 0.1.38, Blocktone 0.1.38 and the released Vanilla++ 1.4.5. The three-way check with the new Vanilla++ candidate, release packaging and the final statement come next. The 1.0.8 results at the end of this file remain the last accepted release.

Candidate JAR: `build/libs/hotkey-wheel-1.0.9+26.3.jar`, SHA-256 `b3f6fcf41e0ee23728515434caada70ec2eea92f0ac448b0e06e24e6c8a12c0f`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. A clean in-place build and an independent rebuild from exported commit `b1c5181` (no build cache, all tasks rerun) reproduce the JAR and the sources JAR byte for byte. Compared with shipped 1.0.8, only `HotkeyWheelClient`, `WheelConfig`, `WheelScreen`, `KeyboardMixin` and `fabric.mod.json` changed; the other 59 entries are identical.

## Results with Blocktone, 2026-11-08

All runs used the exact candidate JAR on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated | Buildprint 0.1.38+26.3, Blocktone 0.1.38 | wheel 154, key management 88, Buildprint/Blocktone menus 227, Mouse 4 67 | 536 passed |
| toolpouch | integrated + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | integrated reports + Tool Pouch 179 | 715 passed |
| suite | integrated + Vanilla++ 1.4.5+26.3 (nested Tool Pouch 1.1.10), BRBR 1.11.7+26.3 | integrated reports + Tool Pouch 183, BRBR 27 | 746 passed |
| suite-polymer | suite + Polymer 0.18.2+26.3 | same as suite | 746 passed |

Total: 2,981 passing checks. The packager validates them in a dry run into a scratch folder; nothing was written to `Builds/`.

Exact inputs: Buildprint `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8`, Blocktone 0.1.38 `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c`, Tool Pouch 1.2.0 `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1`, Vanilla++ 1.4.5 `2b8da007d555b55512c1e8fb08ce5db3be419bb5f3a47d61a1e13d577f67b97b`, BRBR `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f`, Polymer 0.18.2 `3d79318e8202d0e87af308d7f232220714e5f1409d06c23db774877ec6f3848f`. Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash.

The Blocktone scenarios ran in every Buildprint profile: three Toning picker entries, spline settings validation and Cancel under wheel closure, instant-break settings validation and save, and native-opened Blocktone screens left alone. Blocktone 0.1.38 needed no fixture changes beyond those already made for Buildprint 0.1.38. Tool numbers come from Buildprint's registry, so the extra Paste tool does not shift any check.

`callback-order.json` in each profile is read from Mixin's exported `MouseHandler` and `KeyboardHandler`. In every Buildprint profile, `keyPress` runs the wheel's owned-menu return first, then Buildprint's editor keys, then the ordinary wheel input. `onButton` runs the wheel input before Buildprint's editor and assist clicks.

New checks in this release: Buildprint-reserved openers C and E close a wheel-owned editor; the walking-camera editor; tool numbers read from Buildprint's registry; the sectioned loaded-schematic list; a 1.0.8 configuration loads and saves without changes; an unreadable configuration is copied aside; Tool Pouch 1.2.0 disabled slots. The shipped 1.0.8 JAR fails the reserved-key and unreadable-config checks and passes the 1.0.8 load and save checks; see `qa/regressions/1.0.8-upstream-2026-11/`.

Remaining: rerun the suite and suite-polymer profiles with the new Vanilla++ candidate (and its Polymer version), then package.

## Parked decision

On a multiplayer server where Buildprint has not yet asked which camera to use, the first editor opening shows a camera dialog. `open()` returns false while the dialog is shown, so the wheel does not claim that session. The wheel's opener does not close it; Buildprint's editor key does. Later openings work normally, and singleplayer never asks. Options: (a) keep this behavior, as documented in the README; (b) keep the wheel's claim while Buildprint's camera dialog is open. Option (b) needs a dedicated-server gametest before it can be verified.

# Hotkey Wheel 1.0.8+26.3 verification (previous release)

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. The release folder `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` keeps the full 1.0.8 verification record, including its 0.1.19 Buildprint, Blocktone 0.1.24, Vanilla++ 1.4.4 and Polymer inputs.
