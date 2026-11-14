# Buildprint API integration in Hotkey Wheel 1.0.10

Target: original developer Buildprint 0.1.38+26.3 on Minecraft 26.3. Buildprint remains optional and compile-only; none of its classes or libraries are bundled. The 0.1.17/0.1.18 API paths and 0.1.16 legacy fallback remain available. No original Buildprint or Blocktone source/JAR is modified.

## Buildprint 0.1.38 review in 1.0.9

Every public method the wheel calls keeps its 0.1.19 signature in 0.1.38: `BuildprintEditor.isActive`, `open`, `close`, `isEditorScreen` and `addListener`, `EditorListener.onEnter/onExit`, and `BuildprintHud.hideWhile`. 0.1.38 adds `BuildprintEditor.isAvailable()` and `BuildprintHud.isAvailable()` (both mean "a world and player exist") and a new `BuildprintActions` registry. The wheel already checks for a player and world, and it lists registered key mappings rather than Buildprint actions, so neither addition is needed. Buildprint now bundles its UI, camera and settings code as separate libraries. The menus the wheel tracks still open through `BuildprintScreen`, and child screens keep Screen-valued parents.

- **Reserved editor keys.** Buildprint's order-1000 keyboard handler now also consumes C, the Drop and Inventory keys, Backspace and Escape while the editor has the mouse grabbed, plus Enter, Ctrl+Z, P, R, N and the arrow keys in some tools. Version 1.0.9 adds an order-900 keyboard injector that only runs the wheel's owned-menu return. The original opener of a wheel-owned editor therefore closes it before Buildprint handles the key, as the 1.0.8 mouse injector already does. Opening wheels and every other key keep the ordinary order, so Buildprint's raw shortcuts stay native in editors the wheel did not open.
- **Camera choice.** `open()` goes through the bundled camera library's `CameraChoice`. Singleplayer defaults to the free camera. On a multiplayer server Buildprint asks once (free or walking camera), and `open()` returns false while the question is shown. The editor then starts from the answer. Version 1.0.10 keeps the wheel's pending claim while that exact question is the current screen; it recognizes the `KitChoiceDialog` whose title key ends in `.camera.title` (since 1.2.0; earlier releases matched the full key and class name). The answered session is therefore wheel-owned. The original opener closes the question itself, as with other wheel-opened menus, and Escape cancels it. A cancelled question lets the claim expire after the usual four ticks. Editors opened with Buildprint's key never acquire a claim. 1.0.9 dropped the claim when `open()` returned false. QA covers this on a dedicated server.
- **Walking camera.** The editor can run from the player's own camera. Session identity comes from the public lifecycle listener, not freecam, so ownership and closure work unchanged; QA checks it.
- **Toolbar and lists.** The Magic tool moves Save to toolbar number 4, and loaded schematics are grouped into place sections. These affect only the QA fixtures, which now read tool numbers from Buildprint's registry.

## Side-mouse input ordering in 1.0.8

The reported `key.mouse.4` opener failure concerns input ordering, not a missing Buildprint API. Buildprint's mouse mixin can cancel grabbed editor events before the wheel handler observes them. Changing mixin priority alone was insufficient. Version 1.0.8 retains ordinary mixin priority and sets the mouse injector order to 900, before default-order 1000 injectors. Transformed-bytecode inspection confirms the wheel callback runs first, and the focused native Mouse Button 4 regression passes. Ownership checks, public editor closure and unhandled native fallthrough remain unchanged.

Earlier editor-close acceptance used keyboard openers, so those results did not prove a side-mouse opener works. The new regression uses native mouse callbacks with zero-based button index 3 and passes 67 checks across seven owned-editor scenarios, plus independent native-opening checks. The fresh minimal/integrated/all-four native/Polymer matrix passes 2,188 checks against the exact final 1.0.8 JAR. No additional editor session handoff or new API bridge was added.

## Public API adopted

- `BuildprintEditor.isActive()` and `EditorListener.onEnter/onExit` identify each editor session. Only a session created by a wheel action acquires the original closing key. Native-opened sessions remain independent, including exit/re-entry within one callback.
- `BuildprintEditor.open()` and `close()` now activate the wheel's editor control on 0.1.19 with its existing toggle behavior. Selecting that control while already editing closes the editor without synthesizing a control click. Refused or failed API calls do not fall through to private exit or a synthetic key press.
- Original-opener closure first calls each owned dialog's normal `onClose()`. If validation keeps a dialog open, editor exit stops. Only after dialogs have closed normally does the wheel call public `close()`. This avoids 0.1.19's direct dialog dismissal bypassing native save/cancel/validation.
- `BuildprintEditor.isEditorScreen(Screen)` identifies registered editor screens. Blocktone 0.1.24 registers its Tone, Spline settings and block-picker screens with `registerScreen(Class)`. Hotkey Wheel consumes this registration and follows Screen-valued parents for nested children. It retains its own wheel-origin/session checks, so registration never grants ownership of a native-opened editor.
- `BuildprintHud.hideWhile("hotkey_wheel:radial", predicate)` suppresses Buildprint panels while either radial screen is displayed. Closing, cancelling, or replacing a radial restores ordinary HUD behavior without changing saved preferences.

Implementation: `BuildprintApiIntegration.java`, `BuildprintEditorReturn.java`, `WheelMenuReturn.java`, and the registered-control dispatch in `HotkeyWheelClient.java`.

API methods are detected individually and direct calls stay in isolated classes. Missing methods use the older supported path. A failure from a present lifecycle/screen method fails conservatively and never bypasses the API by calling private methods.

## Requested additions and what remains useful

| Suggested API | Status in 0.1.19, rechecked in 0.1.38 | Remaining work or benefit |
| --- | --- | --- |
| `openEditor()` / `closeEditor()` | Available as `open()` / `close()`; adopted. | Private `EditorController.exit` remains only for older Buildprint. |
| `isEditorScreen(Screen)` and addon registration | Available as `isEditorScreen` / `registerScreen`; adopted. | Parent introspection remains for unregistered nested screens and separately wheel-opened main menus, which the editor API does not classify. |
| Validation-aware `requestCloseEditorSession()` | `close()` exits the session and dismisses recognized screens, but does not call their normal `onClose()`. | A supported operation that invokes normal handlers and reports validation refusal could replace our explicit dialog unwinding and Material List embedded-picker special case. |
| `tools()` / `actions()` with IDs, labels, icons and availability | 0.1.38 adds `BuildprintActions.ids()`, `label(id)`, `isOn(id)` and `run(id)` for Buildprint's quick actions; editor tools are still not listed. | The actions registry could back direct wheel entries for Buildprint actions in a future feature. No such picker is added in 1.0.9, which keeps feature parity. |

`selectTool(id)` still requires an active editor. Selecting a tool generally does not execute its later click or open settings. Future direct-settings entries need an explicit addon action/hook that preserves ordinary validation and lifecycle behavior.

## Compatibility fallbacks

Buildprint 0.1.17/0.1.18 retain public lifecycle/HUD tracking but use private native exit and narrow screen classification because the newer methods do not exist. Buildprint 0.1.16 retains private activity/freecam identity as well. All private fallback sites are marked in code for removal only when those older versions are deliberately retired.

The editor API recognizes registered classes and subclasses, not arbitrary screen ancestry. Buildprint's main menu, Migration and settings screens and directly wheel-opened Blocktone instant-break settings therefore retain their separate screen-parent ownership path. Unrelated replacement screens relinquish wheel ownership. Buildprint's raw reserved editor shortcuts remain native behavior, except that the original opener of a wheel-owned session closes it.

Blocktone 0.1.39.1 removed instant-break and its settings screen in favour of Buildprint's excavator, so its two controls are gone. The instant-break screen match above stays for Blocktone 0.1.39 and 0.1.38. A slot saved with `key.buildprint_toning.instamine` resolves to Buildprint's `key.buildprint.excavator` through `HotkeyWheelClient.findControl` once the old control is no longer registered. Mod settings screens that a wheel opens through Mod Menu's `modmenu` entrypoints are owned directly by `WheelMenuReturn.own`, since the wheel creates them itself, and close the same way.

See `QA.md` for exact artifacts and results. The 1.0.9 release targets Buildprint 0.1.38. The 1.0.8 release targeted Buildprint 0.1.19 with the installable Blocktone 0.1.24 JAR built from its source targeting 0.1.19. The retained 1.0.7 review and older compatibility paths have separately identified historical evidence; their counts are not accepted as current release verification.

Official references: [API overview](https://voxelsprout.com/buildprint/wiki/api), [Editor API](https://voxelsprout.com/buildprint/wiki/editor-api).

## Fresh review of every documented API area, 2026-10-13

All ten requested documentation pages were read, then their public classes and methods were checked in the exact developer Buildprint `0.1.19+26.x` JAR. The overview lists nine API categories; editor interfaces and the format data record belong to those categories. The documentation capture URLs and hashes, exact JAR hash, and signature evidence are recorded in `qa/buildprint-api-audit.json`.

This section and capture manifest are retained from the completed 1.0.7 review. The target Buildprint 0.1.19 JAR and adopted API calls are unchanged; 1.0.8 changes mouse callback ordering only. It does not claim a new documentation capture or API upgrade.

| Documentation area | Available in 0.1.19 | Hotkey Wheel use and assessment |
| --- | --- | --- |
| [Overview](https://voxelsprout.com/buildprint/wiki/api) | All nine documented categories. | Follows optional, compile-only integration, isolated direct API calls, client-thread operations, and individual newer-method detection. |
| [Blueprints](https://voxelsprout.com/buildprint/wiki/blueprints-api) | `BuildprintBlueprints`: persistent owned blueprint writes and metadata. | No blueprint generation or mutation in the wheel. Registered controls retain Buildprint's own implementations. No wheel call needed. |
| [Placements](https://voxelsprout.com/buildprint/wiki/placements-api) | `BuildprintPlacements`: placement metadata, status/material reads, snapshots, listeners. | The wheel picks registered controls and inventory icons; it does not inspect builds, render progress, or decide block edits. No placement scan/listener needed. |
| [HUD](https://voxelsprout.com/buildprint/wiki/hud-api) | `BuildprintHud.hideWhile`, `release`, `isHidden`. | Uses one constant-owner `hideWhile` predicate for both radials. Predicate becomes false immediately after closure/replacement. This permanent client registration needs no per-opening `release`; `isHidden` is used in QA to verify restoration. |
| [Previews](https://voxelsprout.com/buildprint/wiki/previews-api) | `BuildprintPreviews`: create/update/discard and temporary visibility management. | The wheel never creates a schematic preview or changes placement visibility. No preview lifecycle needed. |
| [Library](https://voxelsprout.com/buildprint/wiki/library-api) | `BuildprintLibrary.save`: asynchronous saving of a loaded placement. | A wheel-opened editor's native Save dialog remains responsible for its save/cancel behavior. Replacing that dialog with a placement save would change the selected operation. No direct library call needed. |
| [Selection](https://voxelsprout.com/buildprint/wiki/selection-api) | `BuildprintSelection`: selection boxes and world capture. | The wheel opens/closes the editor and preserves its native selection behavior; it does not add/remove boxes or capture regions. Session identity comes from editor lifecycle listeners. No selection manipulation needed. |
| [Rules](https://voxelsprout.com/buildprint/wiki/rules-api) | `BuildprintRules`: server policy and printer caps. | The wheel dispatches registered controls or the native editor open/close API. It does not implement placing, destroying, printing, pasting, or capture; Buildprint retains enforcement inside those actions. No duplicate policy layer needed. |
| [Editor](https://voxelsprout.com/buildprint/wiki/editor-api) | Lifecycle, screen ownership/registration, tools/actions/overlays, listeners, target/state/ray APIs. | Uses `isActive`, `open`, `close`, `isEditorScreen`, and `addListener` with `onEnter/onExit`. Radials are separate transient menus, so registering them as editor dialogs would incorrectly extend editor screen ownership. It adds no editor tool, drawing, target change, or direct tool action. No missing applicable hook found. |
| [Format](https://voxelsprout.com/buildprint/wiki/format-api) | `BuildprintFormats.register`, `BlueprintFormat`, `BlueprintData`. | Wheel config is its own JSON settings file and icons are Minecraft items. It does not import/export schematics or introduce a schematic format. No adapter needed. |

The modern paths use all documented hooks needed for the wheel's implemented behavior. The remaining reflection concerns generic parent-screen ancestry and unsupported older Buildprint versions; 0.1.19 exposes no main-menu or validation-aware parent-unwinding API to replace those paths. A future direct editor-tool picker would need tool/action discovery metadata or an explicitly maintained list, rather than requiring unrelated data APIs.
