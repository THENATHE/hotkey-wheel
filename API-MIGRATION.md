# Buildprint API integration in Hotkey Wheel 1.0.7

Target: original developer Buildprint 0.1.19+26.x on Minecraft 26.3. Buildprint remains optional and compile-only; none of its classes or libraries are bundled. The 0.1.17/0.1.18 API paths and 0.1.16 legacy fallback remain available. No original Buildprint or Blocktone source/JAR is modified.

## Public API adopted

- `BuildprintEditor.isActive()` and `EditorListener.onEnter/onExit` identify each editor session. Only a session created by a wheel action acquires the original closing key. Native-opened sessions remain independent, including exit/re-entry within one callback.
- `BuildprintEditor.open()` and `close()` now activate the wheel's editor control on 0.1.19 with its existing toggle behavior. Selecting that control while already editing closes the editor without synthesizing a control click. Refused or failed API calls do not fall through to private exit or a synthetic key press.
- Original-opener closure first calls each owned dialog's normal `onClose()`. If validation keeps a dialog open, editor exit stops. Only after dialogs have closed normally does the wheel call public `close()`. This avoids 0.1.19's direct dialog dismissal bypassing native save/cancel/validation.
- `BuildprintEditor.isEditorScreen(Screen)` identifies registered editor screens. Blocktone 0.1.24 registers its Tone, Spline settings and block-picker screens with `registerScreen(Class)`. Hotkey Wheel consumes this registration and follows Screen-valued parents for nested children. It retains its own wheel-origin/session checks, so registration never grants ownership of a native-opened editor.
- `BuildprintHud.hideWhile("hotkey_wheel:radial", predicate)` suppresses Buildprint panels while either radial screen is displayed. Closing, cancelling, or replacing a radial restores ordinary HUD behavior without changing saved preferences.

Implementation: `BuildprintApiIntegration.java`, `BuildprintEditorReturn.java`, `WheelMenuReturn.java`, and the registered-control dispatch in `HotkeyWheelClient.java`.

API methods are detected individually and direct calls stay in isolated classes. Missing methods use the older supported path. A failure from a present lifecycle/screen method fails conservatively and never bypasses the API by calling private methods.

## Requested additions and what remains useful

| Suggested API | Status in 0.1.19 | Remaining work or benefit |
| --- | --- | --- |
| `openEditor()` / `closeEditor()` | Available as `open()` / `close()`; adopted. | Private `EditorController.exit` remains only for older Buildprint. |
| `isEditorScreen(Screen)` and addon registration | Available as `isEditorScreen` / `registerScreen`; adopted. | Parent introspection remains for unregistered nested screens and separately wheel-opened main menus, which the editor API does not classify. |
| Validation-aware `requestCloseEditorSession()` | `close()` exits the session and dismisses recognized screens, but does not call their normal `onClose()`. | A supported operation that invokes normal handlers and reports validation refusal could replace our explicit dialog unwinding and Material List embedded-picker special case. |
| `tools()` / `actions()` with IDs, labels, icons and availability | Not exposed publicly. | Would allow automatic discovery of direct editor tool/action entries rather than a manually maintained list. No direct-tool picker feature is added here. |

`selectTool(id)` still requires an active editor. Selecting a tool generally does not execute its later click or open settings. Future direct-settings entries need an explicit addon action/hook that preserves ordinary validation and lifecycle behavior.

## Compatibility fallbacks

Buildprint 0.1.17/0.1.18 retain public lifecycle/HUD tracking but use private native exit and narrow screen classification because the newer methods do not exist. Buildprint 0.1.16 retains private activity/freecam identity as well. All private fallback sites are marked in code for removal only when those older versions are deliberately retired.

The editor API recognizes registered classes and subclasses, not arbitrary screen ancestry. Buildprint's main menu, Migration/YACL screens and directly wheel-opened Blocktone instant-break settings therefore retain their separate screen-parent ownership path. Unrelated replacement screens relinquish wheel ownership. Buildprint's raw reserved editor shortcuts remain native behavior.

See `QA.md` for exact artifacts and results. This build tests Buildprint 0.1.19 with the installable Blocktone 0.1.24 JAR built from its source targeting 0.1.19. Per the user’s instruction, older compatibility paths are retained but not re-tested for 1.0.7; their historical results are not evidence for this build.

Official references: [API overview](https://voxelsprout.com/buildprint/wiki/api), [Editor API](https://voxelsprout.com/buildprint/wiki/editor-api).
