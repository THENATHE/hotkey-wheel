# Hotkey Wheel 1.0.10+26.3 verification

**Status: Buildprint and Blocktone verified; Vanilla++ 1.5.1 candidate still to test.** The exact 1.0.10 JAR passes the minimal, integrated (including a dedicated server) and standalone Tool Pouch profiles. The suite and suite-polymer runs wait for the Vanilla++ 1.5.1 candidate, followed by packaging. 1.0.9 was packaged locally but never published; its folder and records are kept as they were.

Candidate JAR: `build/libs/hotkey-wheel-1.0.10+26.3.jar`, SHA-256 `88639ec136fac83078680635acc64cf3969d6f1287593ec3d0eda0809747d4c0`. Built with Microsoft OpenJDK 25.0.4.1, Fabric Loom 1.17.20 and Gradle 9.6.0. An independent rebuild from exported commit `1989cbf` (no build cache, all tasks rerun) reproduces the JAR and the sources JAR byte for byte. Compared with 1.0.9, only `BuildprintApiIntegration`, `BuildprintEditorReturn` (with their nested classes) and `fabric.mod.json` changed.

## What changed in 1.0.10

The first time the Buildprint editor opens on a multiplayer server, Buildprint 0.1.38 asks whether to use freecam or the player's own camera. 1.0.9 dropped the wheel's claim when `open()` returned false for that question, so the editor started from the answer could not be closed with the wheel opener. 1.0.10 keeps the claim while that exact question is shown. The original opener also closes the question itself, and Escape still cancels it. Editors opened with Buildprint's own key are never claimed, and singleplayer, which never asks, behaves as before.

## Results so far, 2026-11-08

All runs used the exact candidate JAR on Xvfb with Mesa's software renderer. None of them touched the user's profile or display.

| Profile | Inputs beyond the base set | Reports | Checks |
| --- | --- | --- | --- |
| minimal | none | wheel 150, key management 88 | 238 passed |
| integrated | Buildprint 0.1.38+26.3, Blocktone 0.1.38 | wheel 154, key management 88, Buildprint/Blocktone menus 227, Mouse 4 67, dedicated-server camera question 48 | 584 passed |
| toolpouch | integrated + standalone Tool Pouch 1.2.0+26.3, Fzzy Config 0.7.7+fix3 | wheel, key management, Buildprint/Blocktone, Mouse 4, Tool Pouch 179 | 715 passed |
| suite | integrated + Vanilla++ 1.5.1 candidate, BRBR 1.11.8 | pending | pending |
| suite-polymer | suite + Polymer 0.18.7+26.3 | pending | pending |

**Dedicated server.** `ServerCameraChoiceTest` starts Minecraft 26.3's dedicated server in the test JVM through the Fabric client gametest API (offline mode, isolated world, `eula=true` in the QA game folder), then joins it as a multiplayer client. Buildprint asks its camera question there. The fixture covers six scenarios:
1. first wheel opening, answered with free camera, closed with the wheel opener
2. second wheel opening with no question, closed with the opener
3. question cancelled with Escape, then a native opening that the wheel does not claim
4. question closed with the original opener, no editor, no leftover key state
5. first opening answered with the walking camera, closed with the opener
6. a native opening on an answered server, not claimed

Unpublished 1.0.9 fails scenario 1; see `qa/regressions/1.0.9-server-camera-question/`. Singleplayer behavior is covered unchanged by the existing Buildprint fixture.

Exact inputs, SHA-256: Buildprint `97f1d94530a42ea9632d848964d591aa94b7af9df526bb637d767b5fae39ecc8`, Blocktone 0.1.38 `929478a597b1b4ba1bddc5d73877ebf8ed98462dbd443d64b8d81522d1fed33c`, Tool Pouch 1.2.0 `fcc2f984c910c4002f9936bb5dcd2ee4f6a065b0bfee5d814325c425d0a9a1e1`. Base set: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.2+26.3 and its client gametest module 6.0.12. Integrated profiles add Controlling 26.3.4, Searchables 1.0.3, Sorted 0.1.41, Mod Menu 21.0.0 and YACL 3.9.7. Each profile's `provenance.json` has every mod hash, and `callback-order.json` records the transformed input order.

Earlier 1.0.9 runs with Vanilla++ 1.5.0, BRBR 1.11.8 and Polymer 0.18.7 passed but are not evidence for 1.0.10. 1.0.9's own records are in its release folder.

# Hotkey Wheel 1.0.8+26.3 verification (previous release)

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. The release folder `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` keeps the full 1.0.8 verification record, including its 0.1.19 Buildprint, Blocktone 0.1.24, Vanilla++ 1.4.4 and Polymer inputs.
