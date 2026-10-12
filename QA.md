# Hotkey Wheel 1.0.3+26.3 verification

Verified on 2026-10-12 with Minecraft Java **26.3**, Java **25**, Fabric Loader **0.19.5**, and Fabric API **0.162.0+26.3**. Compilation targets Fabric API **0.161.0+26.3**. Graphical client gametests create isolated singleplayer worlds and run the real client and integrated server. Existing profile configuration, mod JARs, and worlds were preserved.

## Results

| Profile | Wheel / UI | Key management | Buildprint | Tool Pouch | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Minimal | 150 | 77 | — | — | **227** |
| Integrated | 154 | 77 | 58 | — | **289** |
| Suite | 154 | 77 | 58 | 142 | **431** |

**947 assertions pass across three installation profiles.** All profiles test the exact final installable JAR, including the new icon. Build, archive integrity, mod metadata, and icon integrity pass. The public Maven dependency build also succeeds without local dependency overrides. Development QA classes and optional mod APIs are excluded from the installable JAR.

Final JAR SHA-256: `5057ded646ea3c9d97b444c572f515c3914bcfd12d0bb1dec1edfb1523e2527b`.

## Profiles and provenance

The minimal profile runs without optional integrations. The integrated profile adds **Controlling 26.3.4**, **Searchables 1.0.3**, **Mod Menu 21.0.0**, **Sorted 0.1.25**, **Buildprint 0.1.16+26.x**, **Blocktone for Buildprint 0.1.20**, and **YACL 3.9.7+26.3**.

The suite profile adds the exact active **Vanilla++ 1.1.6+26.3** and its dependencies. It uses original developer **Tool Pouch 1.1.10**, bundled as `toolpouch-fabric-1.1.10+26.3.jar`. Its SHA-256 is `a5bb4f084e7d929f3a89cc43317af63bd45abe3e3f4fed8b45f99b2da846e903`. No suite component or original dependency is modified. The paused SSO port track is excluded; this uses the active developer suite.

The public verification ZIP retains JSON reports, dependency hashes, and screenshots. The local release's `qa/minimal/`, `qa/integrated/`, and `qa/suite/` directories retain JSON reports, exact dependency hashes, console logs, and rendered screenshots. `qa/*Test.java` and `qa/run.py` provide the source tests and isolated launcher. Public source contains no launcher assets, dependency JARs, private suite binaries, configuration, or world files.

## Wheel controls and settings

Every profile verifies all three opener bindings, Hold/Toggle modes, center Cancel, pointer geometry across 1–12 entries, cycling and wraparound, original opener ownership after cycling, keyboard and mouse bindings, floating icon rendering, hover emphasis, outside-edge selection, and a small 854×480 window. Cursor checks require the actual mouse position at the radial center after Minecraft finishes its native mouse synchronization, rather than treating an outside-cancel result as proof of centering.

The actual editor and searchable icon picker render and save before joining a world. This checks the user's previous settings crash path. The original 1.0.0 failure (`Components not bound yet`) was reproduced against the unchanged older artifact; its local evidence remains under `qa/regressions/1.0.0-main-menu-crash/`. In-world item previews are verified separately.

Controls activate by registered identity, including Inventory when unbound and two controls sharing an ordinary key. Tests cover perspective, temporary held movement, physical key release while a wheel Hold remains active, toggle crouch behavior, cancellation and screen interruption. Native debug overlay, unbound advanced tooltips, and screenshot handlers are exercised. Controlling's actual search finds all five Hotkey Wheel bindings and retains the Wheels shortcut. Mod Menu's actual settings factory opens successfully before a world loads.

## Safe unbinding and recovery

Tests use the real editor, committed configuration, and Minecraft `options.txt`: opting in does nothing before Save; Cancel discards edits; Save unbinds only the selected control and remembers its original key. The wheel still activates the unbound control. Safe mode protects all 22 essential mappings tested, including inventory, primary interactions, movement, chat/commands, and hotbar slots. Escape and primary mouse buttons are reserved for wheel bindings in safe mode.

Coverage includes duplicate assignments, disabled wheels, removed slots, turning unbinding off, switching unsafe settings back to safe mode, bulk Restore with Save/Cancel, manual rebinding taking precedence, and avoiding automatic key changes on configuration load. Malformed recovery keys and blocked config/options destinations fail safely; rollback preserves keys, configuration, and destination contents, and retry succeeds. Expected error logs from these deliberate failure fixtures are part of the acceptance checks.

## Tool Pouch contextual integration

Tests follow native contextual filters: normal context lists shulker boxes; a projectile weapon in either hand lists supported ammunition. Sparse storage and duplicate item types retain filtered indices, custom names, stack data, and counts. Selecting Contextual Open from Hold and Toggle wheels automatically opens the nested radial. Releasing the parent/native opener over an item never chooses it; an actual click confirms.

The integrated server confirms the selected shulker's name and contents and the native ammunition selection index. Single-item Cancel, empty lists, changed contents or held weapon, inventory-use restrictions, and leggings-pouch priority are covered. A 15-item list remains fully accessible across pages; cycle, arrow, and mouse-scroll navigation reset the cursor to Cancel, and second-page selection sends the correct global index.

Native contextual-key replacement is checked ON/OFF with both native quick-select settings and the master integration disabled. OFF retains the original widget and its normal opening behavior. ON suppresses the original renderer to avoid duplicate/single-item auto-open and uses the same click-only radial.

## Buildprint menu closing

Actual wheel-selected Buildprint and Blocktone settings screens close through their native `onClose` handlers. Tests verify pending schematic name commits, preview visibility cleanup, descendants through Migration and YACL screens, original opener after cycling, unrelated replacement screens, disabled integration, and menus opened through the native shortcut remaining independent.

Invalid Blocktone settings refuse closure and retain their validation error; correcting them allows native save and closure. Non-menu Buildprint actions cannot claim incidental screens. Tests also hold the closing key while another gameplay control shares it, then release it, verifying that neither held state nor clicks leak. Escape cancellation while holding the wheel opener receives the same check. This catches Minecraft's restoration of physical key state when the mouse is grabbed after GUI closure.

## Verification boundaries

These are graphical client and integrated-server tests in generated singleplayer worlds. Dedicated multiplayer and Realms/Friends service behavior were not independently tested. Fullscreen uses the native handler but was not toggled during acceptance. The intentional debug-crash chord is not synthesized or tested. Every arbitrary third-party raw-input control is outside this tested integration scope. Tool Pouch still requires its normal server support for its actions.

This is an original client mod with no port counterpart. Source is published at [THENATHE/hotkey-wheel](https://github.com/THENATHE/hotkey-wheel), with the release built from the same committed source. The user's live Vanilla Fast instance was not modified by the test launcher.
