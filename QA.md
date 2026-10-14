# Hotkey Wheel 1.0.8+26.3 verification

The focused native Mouse Button 4 regression passes **67 checks across seven owned-editor scenarios**, with exit status 0. The fresh release matrix passes **2,188 checks** against the exact final JAR. Version 1.0.8 sets the existing mouse injector's order to 900 while retaining ordinary mixin priority; transformed-bytecode inspection confirms that it runs before Buildprint's grabbed editor callbacks. A priority-only candidate failed and remains separately identified. No additional editor API bridge or menu-to-editor ownership transfer was added.

Exact final JAR SHA-256: `ed47efefe6f06e7940a5250974b022228a2b61b441ca704571f9a44d3134d4ca`. An independent, cache-disabled Java 25 build from exported source reproduced this JAR and its source JAR byte for byte. Only `MouseMixin.class` and the declared version in `fabric.mod.json` differ from the shipping 1.0.7 JAR; all other packaged entries remain identical.

## Fresh final-candidate results

| Profile | Required coverage | Status |
| --- | --- | --- |
| minimal | Wheel UI, controls, key recovery and optional-mod absence | Passed: 232 |
| integrated | Wheel, Buildprint/Blocktone editor/menu behavior and native side-mouse regression | Passed: 512 |
| suite | All four requested mods, suite/Tool Pouch menus, BRBR coexistence and native side-mouse regression | Passed: 722 |
| suite-polymer | Same all-four matrix with supported Polymer 0.18.2 | Passed: 722 |

Each profile used the exact final installable 1.0.8 JAR. Prior 1.0.7 results are not counted. Release packaging verifies passing current reports, matching candidate/dependency hashes, executed fixture source hashes, archive and mod metadata integrity, and exclusion of development QA classes from the installable JAR. The verification archive retains all 94 fresh full-profile screenshots (9/25/30/30), authenticated console logs, executed-source manifests and snapshots, and the independent source rebuild and artifact comparison.

## Reported side-mouse failure

The user reports that opening Buildprint Selection Editor from a wheel bound to `key.mouse.4` leaves the same button unable to close the editor. Native button index 3 is Minecraft's Mouse Button 4. Buildprint can consume grabbed editor mouse input before the old wheel mixin runs. The 1.0.8 change is limited to mouse callback ordering and the declared release version.

The new regression uses Fabric's native mouse callbacks rather than directly calling the wheel handler. The focused pass covers Hold release opening and same-button closure, click selection while the opener remains held, Toggle and cycled original-opener behavior, Save/Cancel and child dialogs, successful native Save, camera/input restoration, and independent native openings. Press/release consumption and native fallthrough are checked through actual callbacks. The shipping 1.0.7 baseline and rejected priority-only candidate both reproduced the failure; their results remain separate from the passing order-900 candidate and the fresh full-matrix counts.

Narrow old-JAR, rejected priority-only and focused final proof is recorded under `qa/regressions/1.0.7-side-mouse-editor-close/`, with identical fixture ZIP entries and executed sources across all three attempts. The passing focused attempt is separate from the 2,188 full-matrix checks.

The previous exact-artifact editor tests used keyboard openers. Their successful camera/session and validation checks did not establish this side-button input path. No native Buildprint main-menu-to-editor callback was found during the focused investigation, so no additional ownership handoff was introduced.

## Exact integration targets

Minecraft 26.3, Java 25, Fabric Loader 0.19.5. Compilation targets Fabric API 0.161.0+26.3; the existing wheel acceptance profiles use Fabric API 0.162.0+26.3. Required integrations remain:

- Buildprint 0.1.19+26.x: `bd60814e7ba944d7a67bfbf9831a8fa156223277345dc87569cd358a326e3f1f`.
- Blocktone for Buildprint 0.1.24: `b9c42fd4679f9c70c584a6f8a888e51911c0dfe90f10dddd92c4e427efec5940`.
- Vanilla++ Quality of Life Suite 1.4.4+26.3: `e4663dddf2e176c3d5d7a669f59822dc76286ed28e195cc1a77b1bb1b3e7d269`.
- Better Recipe Book Rebetterered 1.11.7+26.3: `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f`.
- Polymer 0.18.2+26.3: `3d79318e8202d0e87af308d7f232220714e5f1409d06c23db774877ec6f3848f`.

The integrated profile also retains Controlling, Searchables, Mod Menu, Sorted and YACL. Suite profiles retain the supported configuration libraries and Tool Pouch 1.1.10. Current provenance must identify the full actual dependency set.

## Historical evidence and release boundaries

The immutable old installable 1.0.7 JAR is SHA-256 `3095b28e981dd995aabc582b7abe785baeccfdc4ac00128eabd6bda0d5a55fd6`. Its release tag, original source/verification archives, 2026-10-13 suite-1.4.4 review and prior baseline/intermediate records remain preserved under `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.7+26.3/`. Baseline reproduction for the new side-mouse issue uses that old JAR explicitly.

The validated 1.0.8 release is packaged under `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.8+26.3/` with its installable JAR, source/evidence archives, metadata and checksums. All freshly captured PNG screenshots are included; launcher assets, private dependency JARs, game/world files and Minecraft mixin-exported classes are excluded from public archives.

Hotkey Wheel remains an original client mod with no port counterpart. The user's installed profile, saved preferences and worlds are preserved. Dedicated multiplayer, arbitrary third-party raw-input controls, Realms/Friends service behavior, fullscreen and the intentional debug-crash chord are outside this acceptance scope. Older Buildprint fallback paths remain available without a fresh compatibility claim. See [API-MIGRATION.md](API-MIGRATION.md) for the retained public API integration and native input-ordering boundary.
