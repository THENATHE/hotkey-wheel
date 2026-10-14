# Hotkey Wheel 1.0.7+26.3 fresh integration review

Verified on 2026-10-13 with Minecraft Java **26.3**, Java **25**, Fabric Loader **0.19.5**, and Fabric API **0.162.0+26.3**. Compilation targets Fabric API **0.161.0+26.3**. Graphical client gametests create isolated singleplayer worlds and run the real client and integrated server. Existing profile configuration, mod JARs, and worlds were preserved.

## Results

| Profile | Wheel / UI | Key management | Buildprint | Tool Pouch / Suite | BRBR | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| minimal | 150 | 82 | — | — | — | **232** |
| integrated | 154 | 82 | 209 | — | — | **445** |
| suite | 154 | 82 | 209 | 183 | 27 | **655** |
| suite-polymer | 154 | 82 | 209 | 183 | 27 | **655** |

**1987 assertions pass across four freshly run installation profiles.** Both suite profiles install all four requested latest mods together, with exact developer Buildprint 0.1.19. All profiles test the unchanged 1.0.7 installable JAR, including the existing simplified wheel icon. A clean Java 25 Gradle build with declared Maven dependencies reproduces that binary byte for byte; archive integrity, mod metadata, and icon integrity pass. Development QA classes and optional mod APIs are excluded from the installable JAR.

No Hotkey Wheel production change was required. The fresh API audit, expanded test source, and all-four reports are dated review evidence. The original code tag, production JAR, source archive, and original 1933-assertion verification archive remain available with their historical Vanilla++ 1.4.0 / Polymer 0.18.4 inputs. Their counts are not reused in this review.

The final suite profiles were repeated against the frozen Vanilla++ 1.4.4 candidate after the general suite HUD review. The complete earlier 1.4.3 wheel baseline remains under `reviews/2026-10-13/`; superseded 1.4.4 candidate results are separately labeled intermediate. Neither is counted as final 1.4.4 evidence. Minimal and Buildprint-only profiles remain valid because their wheel and integration inputs did not change.

Final JAR SHA-256: `3095b28e981dd995aabc582b7abe785baeccfdc4ac00128eabd6bda0d5a55fd6`.

## Profiles and provenance

The minimal profile runs without optional integrations. The integrated profile adds **Controlling 26.3.4**, **Searchables 1.0.3**, **Mod Menu 21.0.0**, **Sorted 0.1.26**, **Buildprint 0.1.19+26.x**, **Blocktone for Buildprint 0.1.24**, and **YACL 3.9.7+26.3**.

The suite profile adds the exact current **Vanilla++ 1.4.4+26.3**, **Better Recipe Book Rebetterered 1.11.7+26.3**, and their dependencies. Tool Pouch 1.1.10 remains the bundled native component. The suite-polymer profile adds the suite's supported **Polymer 0.18.2+26.3** installation. Buildprint 0.1.19 and Blocktone 0.1.24 use the same exact inputs across all three integration profiles; suite and BRBR binaries are identical across native and Polymer profiles. Older compatibility paths remain available but have no new verification claim from this latest-version review.

Integration input SHA-256 values:

- `buildprint-fabric-0.1.19+26.x.jar`: `bd60814e7ba944d7a67bfbf9831a8fa156223277345dc87569cd358a326e3f1f`
- `buildprint-toning-0.1.24.jar`: `b9c42fd4679f9c70c584a6f8a888e51911c0dfe90f10dddd92c4e427efec5940`
- `vanilla-plusplus-quality-of-life-suite-1.4.4+26.3.jar`: `e4663dddf2e176c3d5d7a669f59822dc76286ed28e195cc1a77b1bb1b3e7d269`
- `better-recipe-book-rebetterered-1.11.7+26.3.jar`: `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f`
- `polymer-bundled-0.18.2+26.3.jar`: `3d79318e8202d0e87af308d7f232220714e5f1409d06c23db774877ec6f3848f`

The dated `hotkey-wheel-1.0.7+26.3-combined-QA-2026-10-13-suite-1.4.4.zip` retains fresh JSON reports, dependency hashes, screenshots, and the API audit manifest. `hotkey-wheel-1.0.7+26.3-reviewed-source-2026-10-13-suite-1.4.4.zip` retains the expanded test source and current documentation. Final local evidence lives under `Builds/Minecraft/Hotkey Wheel/Main Plugin/1.0.7+26.3/reviews/2026-10-13/suite-1.4.4/`, with separate `qa/minimal/`, `qa/integrated/`, `qa/suite/`, and `qa/suite-polymer/` directories retaining console logs too. Public source contains no launcher assets, dependency JARs, private suite binaries, configuration, or world files.

## Wheel controls and settings

Every profile verifies all three opener bindings, Hold/Toggle modes, center Cancel, pointer geometry across 1–12 entries, cycling and wraparound, original opener ownership after cycling, keyboard and mouse bindings, floating icon rendering, hover emphasis, outside-edge selection, and a small 854×480 window. Cursor checks require the actual mouse position at the radial center after Minecraft finishes its native mouse synchronization, rather than treating an outside-cancel result as proof of centering.

The actual editor and searchable icon picker render and save before joining a world. This checks the user's previous settings crash path. The original 1.0.0 failure (`Components not bound yet`) was reproduced against the unchanged older artifact; its local evidence remains under `qa/regressions/1.0.0-main-menu-crash/`. In-world item previews are verified separately.

Controls activate by registered identity, including Inventory when unbound and two controls sharing an ordinary key. Tests cover perspective, temporary held movement, physical key release while a wheel Hold remains active, toggle crouch behavior, cancellation and screen interruption. Native debug overlay, unbound advanced tooltips, and screenshot handlers are exercised. Controlling's actual search finds all five Hotkey Wheel bindings and retains the Wheels shortcut. Mod Menu's actual settings factory opens successfully before a world loads.

## Safe unbinding and recovery

Tests use the real editor, committed configuration, and Minecraft `options.txt`: opting in does nothing before Save; Cancel discards edits; Save unbinds only the selected control and remembers its original key. The wheel still activates the unbound control. Safe mode protects all 22 essential mappings tested, including inventory, primary interactions, movement, chat/commands, and hotbar slots. Escape and primary mouse buttons are reserved for wheel bindings in safe mode.

Coverage includes duplicate assignments, disabled wheels, removed slots, turning unbinding off, switching unsafe settings back to safe mode, bulk Restore with Save/Cancel, manual rebinding taking precedence, and avoiding automatic key changes on configuration load. Malformed recovery keys and blocked config/options destinations fail safely; rollback preserves keys, configuration, and destination contents, and retry succeeds. Expected error logs from these deliberate failure fixtures are part of the acceptance checks.

## BRBR with all four mods

Both native and Polymer profiles use the exact released BRBR 1.11.7 JAR. BRBR's registered pin control remains discoverable. Tests assign its physical key to G alongside the wheel opener: during gameplay, the wheel opens and selects the actual Inventory screen without leaking a pin press or held state; inside the inventory, native G pins and unpins a hovered recipe without opening a wheel. The wheel-opened inventory retains BRBR's actual recipe-book and pinned-tab integration.

BRBR settings open through its real Mod Menu factory with that inventory as parent. Routed mouse input edits Keep Centered without changing committed preferences before Save. Native Save commits exactly that preference and preserves every other setting in both memory and the TOML file. Another native edit followed by Cancel and the ordinary discard confirmation preserves the saved preferences and returns to the original inventory. Cloth computes the Save label and enabled state during rendering, so the fixture waits for that state and uses a real pending edit.

The pin action requires an open recipe screen and a hovered/focused recipe. The wheel's gameplay action list cannot invent that context; this review verifies native pin interaction and coexistence, not a context-free gameplay pin action.

## Tool Pouch contextual integration

Tests follow native contextual filters: normal context lists shulker boxes; a projectile weapon in either hand lists supported ammunition. Sparse storage and duplicate item types retain filtered indices, custom names, stack data, and counts. Selecting Contextual Open from Hold and Toggle wheels automatically opens the nested radial. Releasing the parent/native opener over an item never chooses it; an actual click confirms.

The integrated server confirms the selected shulker's name and contents and the native ammunition selection index. Single-item Cancel, empty lists, changed contents or held weapon, inventory-use restrictions, and leggings-pouch priority are covered. A 15-item list remains fully accessible across pages; cycle, arrow, and mouse-scroll navigation reset the cursor to Cancel, and second-page selection sends the correct global index.

The current suite's registered controls are checked, including unbound contextual/settings actions, a contextual control sharing the physical wheel key, and actual suite settings/Tool Pouch configuration navigation. Opening and closing these configuration screens preserves committed wheel and Tool Pouch preferences.

Native contextual-key replacement is checked ON/OFF with both native quick-select settings and the master integration disabled. OFF retains the original widget and its normal opening behavior. ON suppresses the original renderer to avoid duplicate/single-item auto-open and uses the same click-only radial.

## Reported selection-editor issue

The unchanged 1.0.3 release reproduced the user's failure: assigning `key.buildprint.editor` opens Buildprint freecam with no GUI Screen, and pressing the original wheel opener reopened a wheel instead of leaving editing mode. Old-JAR report and exact dependency hashes are preserved locally under `qa/regressions/1.0.3-selection-editor-close/`.

The candidate tracks the specific freecam session created by the wheel and calls Buildprint's native editor exit routine. Tests verify Hold/Toggle and cycled original-opener closure, restored player camera and input, preserved regions on exit/Cancel, actual blueprint output from native Save and its normal selection clearing, and closure through the Save dialog. Native-key openings remain independent, including a new editor session opened after a wheel-owned session closes.

## Buildprint menu closing

Actual wheel-selected Buildprint and Blocktone settings screens close through their native `onClose` handlers. Tests verify pending schematic name commits, preview visibility cleanup, descendants through Migration and YACL screens, original opener after cycling, unrelated replacement screens, disabled integration, and menus opened through the native shortcut remaining independent.

Invalid Blocktone settings refuse closure and retain their validation error; correcting them allows native save and closure. Non-menu Buildprint actions cannot claim incidental screens. Tests also hold the closing key while another gameplay control shares it, then release it, verifying that neither held state nor clicks leak. Escape cancellation while holding the wheel opener receives the same check. This catches Minecraft's restoration of physical key state when the mouse is grabbed after GUI closure.

## Review regressions fixed in 1.0.5

The key-management regression removes F5, assigns Q manually through the native mapping, saves an unrelated wheel change with unbinding still enabled, reloads configuration, then uses the editor Restore action. Both live and persisted bindings must return to Q.

The Buildprint regression opens selection editing through G, selects Screenshot from a second wheel on an unused key, and requires the exact original freecam session to remain active. Opening its native Save dialog and pressing G must close the dialog and editor together and restore the camera and player input. Repeating the screenshot action in a native-opened editor must not give the wheel ownership. The test uses an unused key because Buildprint reserves H for its editor controls card before ordinary key handlers.

## Public lifecycle and screen APIs in 1.0.7

Buildprint 0.1.19 uses public editor open/close operations, activity checks, and enter/exit callbacks. QA requires both public API flags and verifies that private native activity/freecam/exit reflection was never resolved. Tests cover balanced lifecycle events, same-callback exit/re-entry, wheel-origin isolation, actual API tool picker, and preserved opener ownership after another wheel action.

A registered QA dialog outside the hard-coded mod packages first refuses normal onClose validation. Pressing the original opener must leave the dialog and editor active. After correction, its onClose must run while the editor remains active, and public close must then exit. The same registered dialog in a native-opened editor remains independent. Selecting the editor action from another wheel while already active closes it without leaked key clicks; a later native opening must not inherit that opener.

Blocktone 0.1.24 registers its own editor screens through the new API. Actual spline settings open via native tool selection/click, preserve invalid-Done validation and ordinary Cancel behavior, and exit with the original wheel key only in a wheel-owned editor. The exact pinned addon JAR is copied into each disposable profile; addon source and production installation are preserved.

The public HUD predicate hides panels while a radial is open and restores them afterward. A separate Tool Pouch radial fixture checks predicate recognition; suite tests independently verify real Tool Pouch networking and interactions.

`API-MIGRATION.md` records which requested hooks are now adopted. A validation-aware whole-session close, main-menu ownership, nested-screen ancestry, and tool/action enumeration remain useful API additions. Editor/dialog fallback code for older releases remains in source without a fresh compatibility claim.

## Verification boundaries

These are graphical client and integrated-server tests in generated singleplayer worlds, both without Polymer and with Polymer plus all native suite components. An unmodified vanilla client cannot run Hotkey Wheel and is outside this client-mod test scope. Dedicated multiplayer and Realms/Friends service behavior were not independently tested. Fullscreen uses the native handler but was not toggled during acceptance. The intentional debug-crash chord is not synthesized or tested. Every arbitrary third-party raw-input control is outside this tested integration scope. Tool Pouch still requires its normal server support for its actions.

This is an original client mod with no port counterpart. The unchanged production release is published at [THENATHE/hotkey-wheel](https://github.com/THENATHE/hotkey-wheel); the reviewed test/documentation source is a separate snapshot from the original tag. The user's live Vanilla Fast instance was not modified by the test launcher.
