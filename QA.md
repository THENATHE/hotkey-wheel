# Hotkey Wheel 1.0.9+26.3 verification

**Status: phase 1 of 2.** The candidate passes its own QA against the new Buildprint and Tool Pouch releases and the released Vanilla++ 1.4.5. The required three-way check with the new Vanilla++ and Blocktone candidates has not been run yet, so this is not a release record. The 1.0.8 results below this section remain the last accepted release.

Candidate JAR: `build/libs/hotkey-wheel-1.0.9+26.3.jar`, SHA-256 `b3f6fcf41e0ee23728515434caada70ec2eea92f0ac448b0e06e24e6c8a12c0f`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0.

## Phase-1 results, 2026-11-08

All runs used the exact candidate JAR on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated, no Blocktone | Buildprint 0.1.38+26.3 | wheel 154, key management 88, Buildprint menus 199, Mouse 4 67 | 508 passed |
| toolpouch, no Blocktone | + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | integrated reports + Tool Pouch 179 | 687 passed |
| suite, no Blocktone | + Vanilla++ 1.4.5+26.3 (nested Tool Pouch 1.1.10), BRBR 1.11.7+26.3 | integrated reports + Tool Pouch 183, BRBR 27 | 718 passed |
| suite-polymer, no Blocktone | + Polymer 0.18.2+26.3 | same as suite | 718 passed |

Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its matching client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Exact hashes for every mod are in each profile's `provenance.json`.

**Blocktone.** The released Blocktone 0.1.25 declares `buildprint = 0.1.25+26.3` and cannot load with Buildprint 0.1.38. The phase-1 runs therefore use `--no-blocktone`, which skips the Blocktone-specific scenarios (spline settings, Toning picker entries, instant-break settings). These are not counted as Blocktone compatibility.

New checks in this release: Buildprint-reserved openers C and E close a wheel-owned editor; the walking-camera editor; tool numbers read from Buildprint's registry; the sectioned loaded-schematic list; a 1.0.8 configuration loads and saves without changes; an unreadable configuration is copied aside; Tool Pouch 1.2.0 disabled slots. The shipped 1.0.8 JAR fails the reserved-key and unreadable-config checks and passes the 1.0.8 load and save checks; see `qa/regressions/1.0.8-upstream-2026-11/`.

Remaining for phase 2: the Blocktone 0.1.38 candidate and the new Vanilla++ candidate, run together in the native and Polymer profiles; an independent source rebuild; and release packaging. `qa/package_release.py` still describes 1.0.8 and must be updated before it can package 1.0.9.

## Parked decision

On a multiplayer server where Buildprint has not yet asked which camera to use, the first editor opening shows a camera dialog. `open()` returns false while the dialog is shown, so the wheel does not claim that session. The wheel's opener does not close it; Buildprint's editor key does. Later openings work normally, and singleplayer never asks. Options: (a) keep this behavior, as documented in the README; (b) keep the wheel's claim while Buildprint's camera dialog is open. Option (b) needs a dedicated-server gametest before it can be verified.

# Hotkey Wheel 1.0.8+26.3 verification (previous release)

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. The release folder `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` keeps the full 1.0.8 verification record, including its 0.1.19 Buildprint, Blocktone 0.1.24, Vanilla++ 1.4.4 and Polymer inputs.
