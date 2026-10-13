# Buildprint API integration in Hotkey Wheel 1.0.6

Target: original developer Buildprint 0.1.17+26.x and 0.1.18+26.x on Minecraft 26.3. Buildprint remains optional. The public API is a compile-only dependency and is not bundled. The existing 0.1.16 integration is retained as a legacy fallback; no Buildprint or Blocktone binaries are modified.

## Public API adopted

- `BuildprintEditor.isActive()` and `EditorListener.onEnter/onExit` track each editor session with a fresh local identity token. The API path no longer reads private `EditorMode.freecam()` state. Only a session started by a pending wheel action can acquire a closing key. Another wheel action retains ownership while the same session survives. Native-opened sessions remain independent.
- `BuildprintHud.hideWhile("hotkey_wheel:radial", predicate)` suppresses Buildprint panels while a Hotkey Wheel or Tool Pouch radial is displayed. The predicate follows the current screen, so cancel, confirmation, and replacement restore normal HUD behavior without changing saved settings. Other mods' HUD requests remain independent.
- API classes load only after Buildprint is present and the relevant public class exists. Editor and HUD integration initialize independently. Missing API support keeps the older integration available.

Implementation: `BuildprintApiIntegration.java`, with ownership in `BuildprintEditorReturn.java`.

## Remaining bridges and suggested API additions

| Proposed API | Current code and reason | Future improvement |
| --- | --- | --- |
| `openEditor()` / `closeEditor()` | Wheel opening activates the registered editor control. `BuildprintEditorReturn.NativeEditor.exit` still invokes private `EditorController.exit(Minecraft)` so native camera/input restoration and addon exit notifications run. | Replace private exit reflection with a supported lifecycle operation. An explicit open operation would also avoid toggle ambiguity for future direct tool actions. |
| `requestCloseEditorSession()` returning success/refusal | `BuildprintEditorReturn.closeEditor` and `WheelMenuReturn.closeOwnedMenus` walk known dialogs and invoke native `onClose`, stopping if validation refuses closure. A Material List special case handles its embedded picker. | Let Buildprint close the whole session while preserving native save/cancel/validation rules; remove manual dialog unwinding and special cases. |
| `isEditorScreen(Screen)` plus addon registration | `WheelMenuReturn.isEditorMenu` recognizes specific Buildprint/Blocktone screen classes and traverses reflected Screen-valued parent fields. The new standalone Blocktone `SplineSettingsScreen` is explicitly included. | Replace hard-coded names and private parent inspection with supported ownership information, including independently opened addon settings. Wheel-origin tracking must still remain local. |
| `tools()` / `actions()` with IDs, names, icons, availability | Hotkey Wheel lists registered Minecraft controls. The API provides `selectTool(id)`, but no public registry enumeration. | Offer discoverable direct tool/action entries without maintaining a hand-written list or depending on toolbar slot order. This is a future feature, not added by this migration. |

`selectTool(id)` requires an active editor. Selecting one of Blocktone's registered tools generally selects the tool; it does not perform the later click or open its settings. A future direct-settings integration would need an explicit addon action/hook with ordinary validation and lifecycle behavior.

For legacy Buildprint 0.1.16 only, the old `EditorMode.isActive/freecam` bridge remains necessary because public lifecycle events do not exist. Remove it only when legacy support is deliberately retired.

## Compatibility boundaries

This migration does not take ownership of dialogs merely because an editor is active. Wheel origin, session identity, focus/world state, and known screen ancestry still gate closing. Unrelated screens relinquish ownership. Buildprint's reserved raw editor shortcuts, including H for its controls card, remain native behavior.

See `QA.md` for exact tested artifacts, modes, and results. The modern addon target is Blocktone for Buildprint 0.1.21; legacy checks use 0.1.20. No port counterpart exists for this original Hotkey Wheel release.

Official references: [API overview](https://voxelsprout.com/buildprint/wiki/api), [Editor API](https://voxelsprout.com/buildprint/wiki/editor-api).
