package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

public final class WheelEditorScreen extends Screen {
    private static final String ICON_HELP = "Choose one of Hotkey Wheel's icons, an installed mod's icon, or any block or item. "
            + "Icons are visual labels; you do not need to carry the block or item.";
    private final Screen parent;
    private final WheelConfig draft;
    // Wheel 1–3 openers, cycle, configure, and settings from an open wheel, as HotkeyWheelClient.editorKey numbers them.
    private final InputConstants.Key[] stagedKeys = new InputConstants.Key[6];
    private final List<ContentWidget> contentWidgets = new ArrayList<>();
    private final List<Caption> captions = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    // The folders opened for editing inside the active wheel, outermost first.
    private final List<WheelConfig.Entry> openFolders = new ArrayList<>();
    // The General tab, or the active wheel's tab.
    private boolean showGeneral;
    private int activeWheel;
    private int selectedSlot;
    private int sectionTop;
    private int capturingKey = -1;
    private int left;
    private int panelWidth;
    private int viewportBottom;
    private int contentHeight;
    private int scroll;
    private String error = "";
    private String notice = "";

    private record ContentWidget(AbstractWidget widget, int offset) {}
    private record Caption(Component text, int offset, int color) {}
    private record Section(int top, int bottom) {}

    public WheelEditorScreen(Screen parent) {
        this(parent, 0, List.of());
    }

    /** Opens on a wheel's tab, inside the folders at {@code folderPath}, as the settings key in an open wheel does. */
    public WheelEditorScreen(Screen parent, int wheel, List<Integer> folderPath) {
        super(Component.literal("Hotkey Wheel"));
        this.parent = parent;
        draft = WheelConfig.get().copy();
        for (int index = 0; index < stagedKeys.length; index++) {
            stagedKeys[index] = InputConstants.getKey(HotkeyWheelClient.editorKey(index).saveString());
        }
        activeWheel = Math.clamp(wheel, 0, draft.wheelCount - 1);
        List<WheelConfig.Entry> level = wheel().entries;
        for (int index : folderPath) {
            if (index < 0 || index >= level.size() || !level.get(index).isFolder()) break;
            openFolders.add(level.get(index));
            level = level.get(index).entries;
        }
    }

    /** Whether the General tab is showing; otherwise the index of the wheel being edited. */
    public boolean showingGeneral() { return showGeneral; }

    public int editedWheel() { return activeWheel; }

    /** Names of the folders open for editing, outermost first. */
    public List<String> openFolderNames() { return openFolders.stream().map(WheelConfig.Entry::folderName).toList(); }

    private WheelConfig.Wheel wheel() { return draft.wheels.get(activeWheel); }

    /** The slots being edited: the active wheel's, or those of the innermost open folder. */
    private List<WheelConfig.Entry> entries() {
        return openFolders.isEmpty() ? wheel().entries : openFolders.getLast().entries;
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        captions.clear();
        fixedWidgets.clear();
        panelWidth = Math.min(560, Math.max(180, width - 40));
        left = (width - panelWidth) / 2;
        viewportBottom = height - 57;
        activeWheel = Math.min(activeWheel, draft.wheelCount - 1);
        int tabWidth = (panelWidth - 8 * draft.wheelCount) / (draft.wheelCount + 1);
        Button general = Button.builder(Component.literal((showGeneral ? "● " : "") + "General"), button -> {
            showGeneral = true;
            scroll = 0;
            rebuildWidgets();
        }).bounds(left, 32, tabWidth, 20).tooltip(Tooltip.create(Component.literal(
                "Settings every wheel shares: active wheels, cycle and settings keys, Safe mode, the control chooser and integrations."))).build();
        fixedWidgets.add(addRenderableWidget(general));
        for (int index = 0; index < draft.wheelCount; index++) {
            int target = index;
            Button tab = Button.builder(Component.literal((!showGeneral && index == activeWheel ? "● " : "") + "Wheel " + (index + 1)), button -> {
                if (showGeneral && activeWheel == target) {
                    showGeneral = false;
                } else {
                    showGeneral = false;
                    activeWheel = target;
                    openFolders.clear();
                    selectedSlot = 0;
                }
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + (index + 1) * (tabWidth + 8), 32, tabWidth, 20).build();
            fixedWidgets.add(addRenderableWidget(tab));
        }
        buildContent();
        int half = (panelWidth - 8) / 2;
        fixedWidgets.add(addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left, height - 25, half, 20).build()));
        fixedWidgets.add(addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + half + 8, height - 25, half, 20).build()));
        positionContent();
    }

    private void buildContent() {
        sections.clear();
        int offset = showGeneral ? buildGeneralSettings() : buildWheelSettings();
        contentHeight = offset + 12;
    }

    /** Settings shared by every wheel. */
    private int buildGeneralSettings() {
        int offset = beginSection("General settings · all wheels", 0);
        contentButton("Active wheels: " + draft.wheelCount, offset, panelWidth, left, button -> {
            draft.wheelCount = draft.wheelCount % 3 + 1;
            if (activeWheel >= draft.wheelCount) openFolders.clear();
            activeWheel = Math.min(activeWheel, draft.wheelCount - 1);
            rebuildWidgets();
        }, "Choose 1–3 wheels. Hidden wheels keep their settings. Each wheel's own settings are on its tab.");
        offset += 26;
        addKeyButton(3, "Cycle while open", offset, "Switches to the next active wheel while the opening key stays held.");
        offset += 24;
        addKeyButton(4, "Configure wheels", offset, "Opens this editor during play when no screen is open. Unbound unless you choose a key.");
        offset += 24;
        addKeyButton(5, "Settings from an open wheel", offset,
                "Opens this editor on the wheel and folder you have open. It only acts while a wheel is open, so it may share a key with an ordinary control or with Configure wheels.");
        offset += 26;
        contentButton("Safe mode: " + (draft.safeMode ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.safeMode = !draft.safeMode;
            rebuildWidgets();
        }, "Protect Inventory, Attack/Use, movement, Jump, Sneak, Sprint, Pick Block, chat/commands, and hotbar keys from automatic unbinding. Escape and primary mouse buttons are reserved for normal menu input. Turning this on restores essential keys previously unbound by this mod when you Save.");
        offset += 26;
        Button restore = contentButton("Restore keys unbound by Hotkey Wheel", offset, panelWidth, left, button -> {
            for (WheelConfig.Entry entry : draft.allEntries()) entry.unbindKey = false;
            rebuildWidgets();
        }, "Turn off automatic unbinding for all slots, including slots in folders. Save restores remembered keys that are still unbound, preserving later manual bindings. Cancel discards this request.");
        restore.active = !draft.managedBindings.isEmpty();
        offset = endSection(offset + 20);

        offset = beginSection("Control chooser", offset);
        contentButton("Mod controls: " + (draft.oneModControlsFolder() ? "One Mod controls folder" : "A folder for each mod"), offset, panelWidth, left, button -> {
            draft.modControlsInOneFolder = draft.oneModControlsFolder() ? null : true;
            rebuildWidgets();
        }, "The control chooser lists Minecraft controls, Mod settings, then the controls of each mod that adds them. Choose one Mod controls folder to list every mod's controls together, under each mod's name.");
        offset = endSection(offset + 20);

        offset = beginSection("Integrations", offset);
        Button contextual = contentButton("Tool Pouch contextual wheel: " + (draft.toolPouchWheel ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.toolPouchWheel = !draft.toolPouchWheel;
            rebuildWidgets();
        }, "Contextual Open selected in a wheel automatically shows the native menu's interactable pouch items as a radial. Click an item to choose, including in Hold mode. Requires Tool Pouch, standalone or in Vanilla++.");
        contextual.active = ToolPouchIntegration.available();
        offset += 26;
        Button nativeKey = contentButton("Native contextual key uses wheel: " + (draft.replaceToolPouchContextualKey ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.replaceToolPouchContextualKey = !draft.replaceToolPouchContextualKey;
            rebuildWidgets();
        }, "Use the radial for Tool Pouch's normal Contextual Open key outside Hotkey Wheel as well. Item selection requires a click; releasing the key does not choose an item.");
        nativeKey.active = ToolPouchIntegration.available() && draft.toolPouchWheel;
        offset += 26;
        Button buildprint = contentButton("Wheel key closes menus it opened: " + (draft.closeBuildprintWithWheelKey ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.closeBuildprintWithWheelKey = !draft.closeBuildprintWithWheelKey;
            rebuildWidgets();
        }, "When a wheel opens a Buildprint or Blocktone menu, or a mod's settings, its original opening key closes that menu and its children using their normal save/cancel behavior. Other routes and unrelated menus keep their normal behavior.");
        buildprint.active = FabricLoader.getInstance().isModLoaded("buildprint") || ModSettingsActions.modMenuPresent();
        offset = endSection(offset + 20);
        offset = wrappedCaption("Cycle changes wheels while the opener remains held. Keys also appear in Controls → Key Binds → Hotkey Wheel and are searchable with Controlling.", offset);
        return offset;
    }

    /** The active wheel's own settings, then its slots. */
    private int buildWheelSettings() {
        int offset = beginSection("Wheel " + (activeWheel + 1) + " settings", 0);
        caption("Wheel name", offset, WheelUi.DIM);
        offset += 12;
        EditBox name = new EditBox(font, left, 0, panelWidth, 20, Component.literal("Wheel name"));
        name.setMaxLength(32);
        name.setValue(wheel().name);
        name.setResponder(value -> wheel().name = value);
        content(name, offset);
        offset += 26;
        addKeyButton(activeWheel, "Opening key", offset, "The key that opens this wheel.");
        offset += 26;
        contentButton("Open mode: " + (wheel().toggleOpen ? "Toggle" : "Hold"), offset, panelWidth, left, button -> {
            wheel().toggleOpen = !wheel().toggleOpen;
            rebuildWidgets();
        }, "Hold: keep the wheel key down and release to use the hovered slot. Toggle: press the key to open, then click a slot.");
        offset += 26;
        contentButton("Outside wheel: " + (wheel().selectOutside ? "Select by direction" : "Cancel"), offset, panelWidth, left, button -> {
            wheel().selectOutside = !wheel().selectOutside;
            rebuildWidgets();
        }, "Select by direction keeps the entry under the cursor line selected beyond the wheel's outer edge. Confirm with the usual release or click. The center still cancels.");
        offset = endSection(offset + 20);
        List<WheelConfig.Entry> entries = entries();
        String level = openFolders.isEmpty() ? "Slots" : "Slots in " + openFolders.getLast().folderName();
        offset = beginSection(level + " · " + entries.size() + "/" + WheelConfig.MAX_SLOTS, offset);
        if (!openFolders.isEmpty()) offset = addBreadcrumbs(offset);
        selectedSlot = Math.clamp(selectedSlot, 0, Math.max(0, entries.size() - 1));
        int columns = Math.min(12, Math.max(1, panelWidth / 32));
        for (int index = 0; index < entries.size(); index++) {
            int slot = index;
            WheelConfig.Entry entry = entries.get(index);
            Component description = Component.literal("Slot " + (slot + 1) + ": " + entryName(entry));
            WheelUi.IconButton icon = new WheelUi.IconButton(left + index % columns * 32, 0, 28,
                    WheelIcons.resolve(entry.icon), description, () -> selectedSlot == slot, button -> {
                selectedSlot = slot;
                rebuildWidgets();
            });
            if (entry.isFolder()) icon.showFolderMark();
            content(icon, offset + index / columns * 32);
        }
        offset += Math.max(1, (entries.size() + columns - 1) / columns) * 32 + 4;
        int fifth = (panelWidth - 24) / 5;
        Button add = contentButton("Add", offset, fifth, left, button -> addSlot(new WheelConfig.Entry()),
                "Add another slot, up to 12 per wheel or folder.");
        add.active = entries.size() < WheelConfig.MAX_SLOTS;
        Button addFolder = contentButton("Add folder", offset, fifth, left + fifth + 6,
                button -> addSlot(WheelConfig.Entry.folder("Folder")),
                "Add a named folder with up to 12 slots of its own. Folders can hold folders, three levels deep.");
        addFolder.active = entries.size() < WheelConfig.MAX_SLOTS && openFolders.size() < WheelConfig.MAX_FOLDER_DEPTH;
        Button remove = contentButton("Remove", offset, fifth, left + 2 * (fifth + 6), button -> removeSlot(),
                "Remove the selected slot. Removing a folder that holds slots asks first.");
        remove.active = !entries.isEmpty();
        Button earlier = contentButton("← Move", offset, fifth, left + 3 * (fifth + 6), button -> moveSlot(-1),
                "Move the selected slot one position earlier around the wheel.");
        earlier.active = !entries.isEmpty() && selectedSlot > 0;
        Button later = contentButton("Move →", offset, fifth, left + 4 * (fifth + 6), button -> moveSlot(1),
                "Move the selected slot one position later around the wheel.");
        later.active = !entries.isEmpty() && selectedSlot < entries.size() - 1;
        offset += 33;
        if (!entries.isEmpty() && entries.get(selectedSlot).isFolder()) {
            offset = addFolderSettings(entries.get(selectedSlot), offset);
        } else if (!entries.isEmpty()) {
            WheelConfig.Entry entry = entries.get(selectedSlot);
            caption("Selected slot " + (selectedSlot + 1), offset, WheelUi.DIM);
            offset += 14;
            offset = addMoveButton(entry, offset);
            String controlName = entry.control.isBlank() ? "Choose a control" : HotkeyWheelClient.controlLabel(entry.control);
            contentButton("Control: " + controlName, offset, panelWidth, left, button -> {
                minecraft.gui.setScreen(new ControlPickerScreen(this, control -> entry.control = control, draft.oneModControlsFolder()));
            }, "Choose a standard Minecraft control, a control registered by an installed mod, a Tool Pouch slot toggle, or a mod's settings screen.");
            offset += 26;
            KeyMapping control = HotkeyWheelClient.findControl(entry.control);
            boolean pouchToggle = ToolPouchToggles.handles(entry.control);
            boolean protectedKey = draft.safeMode && control != null && WheelBindings.protectedControl(control);
            String unbindLabel = protectedKey ? "Unbind normal key: Protected by safe mode"
                    : "Unbind normal key: " + (entry.unbindKey ? "ON (on Save)" : "OFF");
            Button unbind = contentButton(unbindLabel, offset, panelWidth, left, button -> {
                entry.unbindKey = !entry.unbindKey;
                rebuildWidgets();
            }, "Remove only this control's ordinary key after Save. The wheel still activates it. Its original key is remembered and restored when you turn this off, remove the slot, or disable the wheel. Other controls sharing the key stay bound.");
            // Tool Pouch keeps slot toggle keys in its own settings; the elytra toggle covers several slots.
            // An option left on for a removed control can still be turned off, which restores its remembered key.
            unbind.active = control != null && !protectedKey || pouchToggle && ToolPouchToggles.canUnbind(entry.control)
                    || control == null && entry.unbindKey;
            offset += 26;
            contentButton("Choose icon…", offset, panelWidth, left, button -> {
                minecraft.gui.setScreen(new IconPickerScreen(this, entry.icon, icon -> entry.icon = icon));
            }, ICON_HELP);
            offset += 29;
            caption("Custom label (optional)", offset, WheelUi.DIM);
            offset += 12;
            EditBox label = new EditBox(font, left, 0, panelWidth, 20, Component.literal("Custom slot label"));
            label.setMaxLength(48);
            label.setHint(Component.literal("Use the control's name"));
            label.setValue(entry.label);
            label.setResponder(value -> entry.label = value);
            content(label, offset);
            offset += 26;
            String action = wheel().toggleOpen ? "Action: Tap (toggle mode)" : "Action: " + (entry.holdAction ? "Hold" : "Tap");
            Button mode = contentButton(action, offset, panelWidth, left, button -> {
                entry.holdAction = !entry.holdAction;
                rebuildWidgets();
            }, "Tap presses the control once. Hold closes the wheel when you click and keeps the control down until you release the wheel key. Hold requires Hold open mode.");
            mode.active = !wheel().toggleOpen;
            offset += 31;
        }
        offset = endSection(offset - 8);
        offset = wrappedCaption("Hold open: release the wheel key to tap the hovered control. For a Hold action, click its slot, then keep the wheel key held until finished.", offset);
        offset += 5;
        offset = wrappedCaption("Folders open as their own radial. Click a folder, or release over it, to open it. Its center goes back; Escape and right click go up one level.", offset);
        offset += 5;
        offset = wrappedCaption("General holds the settings every wheel shares: active wheels, the cycle and settings keys, Safe mode and integrations.", offset);
        return offset;
    }

    /** Opens a titled section; its darker background runs to the offset given to endSection. */
    private int beginSection(String title, int offset) {
        sectionTop = offset;
        caption(title, offset + 4, WheelUi.GOLD);
        return offset + 18;
    }

    private int endSection(int offset) {
        sections.add(new Section(sectionTop, offset + 4));
        return offset + 12;
    }

    private int addBreadcrumbs(int offset) {
        // The wheel, then each open folder; the last one is the level being edited.
        int count = openFolders.size() + 1;
        int crumbWidth = (panelWidth - 6 * (count - 1)) / count;
        for (int depth = 0; depth < count; depth++) {
            int target = depth;
            String name = depth == 0 ? wheel().name : openFolders.get(depth - 1).folderName();
            boolean current = depth == count - 1;
            Button crumb = contentButton((current ? "● " : depth == 0 ? "" : "› ") + name, offset, crumbWidth,
                    left + depth * (crumbWidth + 6), button -> {
                WheelConfig.Entry reopened = target < openFolders.size() ? openFolders.get(target) : null;
                openFolders.subList(target, openFolders.size()).clear();
                // Return with the folder just left selected, so the way back in is one click.
                selectedSlot = reopened == null ? 0 : Math.max(0, entries().indexOf(reopened));
                scroll = 0;
                rebuildWidgets();
            }, current ? "The level you are editing." : "Go back to this level. Changes stay in the draft until Save.");
            crumb.active = !current;
        }
        return offset + 26;
    }

    private int addFolderSettings(WheelConfig.Entry folder, int offset) {
        caption("Selected folder " + (selectedSlot + 1), offset, WheelUi.GOLD);
        offset += 14;
        offset = addMoveButton(folder, offset);
        Button open = contentButton(openFolderLabel(folder), offset, panelWidth, left, button -> {
            openFolders.add(folder);
            selectedSlot = 0;
            scroll = 0;
            rebuildWidgets();
        }, "Edit the slots inside this folder. The path above the slots leads back.");
        offset += 29;
        caption("Folder name", offset, WheelUi.DIM);
        offset += 12;
        EditBox name = new EditBox(font, left, 0, panelWidth, 20, Component.literal("Folder name"));
        name.setMaxLength(32);
        name.setHint(Component.literal("Folder"));
        name.setValue(folder.label);
        name.setResponder(value -> {
            folder.label = value;
            open.setMessage(Component.literal(font.plainSubstrByWidth(openFolderLabel(folder), panelWidth - 10)));
        });
        content(name, offset);
        offset += 26;
        contentButton("Choose icon…", offset, panelWidth, left, button -> {
            minecraft.gui.setScreen(new IconPickerScreen(this, folder.icon, icon -> folder.icon = icon));
        }, ICON_HELP);
        return offset + 31;
    }

    private int addMoveButton(WheelConfig.Entry entry, int offset) {
        String what = entry.isFolder() ? "this folder, with everything in it," : "this slot";
        contentButton("Move to…", offset, panelWidth, left, button -> {
            minecraft.gui.setScreen(new MoveSlotScreen(this, wheel(), entries(), entry, this::moveTo));
        }, "Move " + what + " into a folder, out to the level above, or into another folder of this wheel. Its settings and remembered keys stay as they are. Nothing changes until Save.");
        return offset + 26;
    }

    /** Moves the selected slot to the end of another level of this wheel; called by MoveSlotScreen. */
    private void moveTo(WheelConfig.Entry entry, List<WheelConfig.Entry> destination, String destinationName) {
        List<WheelConfig.Entry> current = entries();
        int index = current.indexOf(entry);
        if (index < 0 || destination == current) return;
        current.remove(index);
        destination.add(entry);
        selectedSlot = Math.max(0, Math.min(index, current.size() - 1));
        notice = "Moved " + entryName(entry) + " to " + destinationName + ". Save to keep it.";
        rebuildWidgets();
    }

    private static String openFolderLabel(WheelConfig.Entry folder) {
        int slots = folder.entries.size();
        return "Open folder: " + folder.folderName() + " · " + slots + (slots == 1 ? " slot" : " slots") + " →";
    }

    private void addSlot(WheelConfig.Entry entry) {
        entries().add(entry);
        selectedSlot = entries().size() - 1;
        rebuildWidgets();
    }

    private void removeSlot() {
        WheelConfig.Entry entry = entries().get(selectedSlot);
        if (!entry.isFolder() || entry.entries.isEmpty()) {
            removeSelectedSlot();
            return;
        }
        int slots = entry.nestedSlotCount();
        Component title = Component.literal("Remove the folder \"" + entry.folderName() + "\"?");
        Component message = Component.literal("It holds " + slots + (slots == 1 ? " slot" : " slots")
                + ", counting folders inside it. Save applies the removal; Cancel in the editor keeps everything.");
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) removeSelectedSlot();
            minecraft.gui.setScreen(this);
        }, title, message, Component.literal("Remove folder"), Component.literal("Keep")));
    }

    private void removeSelectedSlot() {
        entries().remove(selectedSlot);
        selectedSlot = Math.max(0, selectedSlot - 1);
        rebuildWidgets();
    }

    private void addKeyButton(int index, String label, int offset, String help) {
        String text = capturingKey == index ? label + ": press a key or mouse button…"
                : label + ": " + stagedKeys[index].getDisplayName().getString();
        contentButton(text, offset, panelWidth, left, button -> {
            capturingKey = index;
            rebuildWidgets();
        }, help + " Click to bind a keyboard key or mouse button. Escape clears the binding. Key changes are applied when you Save.");
    }

    private Button contentButton(String text, int offset, int buttonWidth, int x, Button.OnPress action, String help) {
        Button button = Button.builder(Component.literal(font.plainSubstrByWidth(text, buttonWidth - 10)), action)
                .bounds(x, 0, buttonWidth, 20).tooltip(Tooltip.create(Component.literal(help))).build();
        content(button, offset);
        return button;
    }

    private void content(AbstractWidget widget, int offset) {
        contentWidgets.add(new ContentWidget(addRenderableWidget(widget), offset));
    }

    private void caption(String text, int offset, int color) {
        captions.add(new Caption(Component.literal(text), offset, color));
    }

    private int wrappedCaption(String text, int offset) {
        Component component = Component.literal(text);
        captions.add(new Caption(component, offset, WheelUi.DIM));
        return offset + font.split(component, panelWidth).size() * 10;
    }

    private static String entryName(WheelConfig.Entry entry) {
        if (entry.isFolder()) return "Folder " + entry.folderName() + " · " + entry.entries.size() + "/" + WheelConfig.MAX_SLOTS;
        if (!entry.label.isBlank()) return entry.label;
        if (entry.control.isBlank()) return "Choose a control";
        return HotkeyWheelClient.controlLabel(entry.control);
    }

    private void moveSlot(int direction) {
        Collections.swap(entries(), selectedSlot, selectedSlot + direction);
        selectedSlot += direction;
        rebuildWidgets();
    }

    private int maxScroll() { return Math.max(0, contentHeight - (viewportBottom - 62)); }

    private void positionContent() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        for (ContentWidget row : contentWidgets) {
            AbstractWidget widget = row.widget();
            widget.setY(62 + row.offset() - scroll);
            widget.visible = widget.getY() >= 62 && widget.getY() + widget.getHeight() <= viewportBottom;
            if (!widget.visible && getFocused() == widget) clearFocus();
        }
    }

    private void capture(InputConstants.Key key) {
        stagedKeys[capturingKey] = key;
        capturingKey = -1;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKey >= 0) {
            capture(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            scroll += (event.key() == InputConstants.KEY_PAGEUP ? -1 : 1) * Math.max(24, viewportBottom - 82);
            positionContent();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (capturingKey >= 0) {
            capture(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (capturingKey >= 0) return true;
        if (vertical != 0 && y >= 62 && y < viewportBottom) {
            scroll += (int) Math.round(-vertical * 26);
            positionContent();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void save() {
        List<InputConstants.Key> assigned = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            if (index < 3 && index >= draft.wheelCount) continue;
            InputConstants.Key key = stagedKeys[index];
            if (key.equals(InputConstants.UNKNOWN)) continue;
            if (assigned.contains(key)) {
                error = "Wheel, cycle, and configure keys must be different.";
                return;
            }
            assigned.add(key);
        }
        // The settings key acts only inside an open wheel, where the configure key does nothing,
        // so only the keys an open wheel listens to must differ from it.
        InputConstants.Key settingsKey = stagedKeys[5];
        for (int index = 0; index < 4; index++) {
            if (index < 3 && index >= draft.wheelCount) continue;
            if (!settingsKey.equals(InputConstants.UNKNOWN) && settingsKey.equals(stagedKeys[index])) {
                error = "The settings key must differ from the wheel and cycle keys.";
                return;
            }
        }
        error = WheelBindings.save(draft, stagedKeys);
        if (!error.isEmpty()) return;
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        positionContent();
        graphics.centeredText(font, title, width / 2, 12, WheelUi.GOLD);
        graphics.fill(left - 6, 57, left + panelWidth + 6, viewportBottom + 1, 0x66000000);
        graphics.enableScissor(left - 2, 62, left + panelWidth + 2, viewportBottom);
        for (Section section : sections) {
            int top = 62 + section.top() - scroll;
            int bottom = 62 + section.bottom() - scroll;
            graphics.fill(left - 2, top, left + panelWidth + 2, bottom, 0x55000000);
            graphics.fill(left - 2, top, left + panelWidth + 2, top + 1, 0x66F1C56C);
        }
        for (Caption caption : captions) {
            graphics.textWithWordWrap(font, caption.text(), left, 62 + caption.offset() - scroll, panelWidth, caption.color());
        }
        for (ContentWidget row : contentWidgets) row.widget().extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int track = viewportBottom - 62;
            int thumb = Math.max(12, track * track / contentHeight);
            int thumbY = 62 + (track - thumb) * scroll / maxScroll();
            graphics.fill(left + panelWidth + 8, 62, left + panelWidth + 11, viewportBottom, 0x33444444);
            graphics.fill(left + panelWidth + 8, thumbY, left + panelWidth + 11, thumbY + thumb, WheelUi.GOLD);
        }
        for (AbstractWidget widget : fixedWidgets) widget.extractRenderState(graphics, mouseX, mouseY, delta);
        String status = capturingKey >= 0 ? "Press a key or mouse button; Escape clears it."
                : !error.isEmpty() ? error
                : !notice.isEmpty() ? notice
                : minecraft.level == null ? "Icon previews load in a world · Scroll to edit settings"
                : "Scroll / Page Up / Page Down · Save applies all changes";
        graphics.text(font, font.plainSubstrByWidth(status, panelWidth), left, height - 44,
                error.isEmpty() ? WheelUi.DIM : WheelUi.ERROR);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
