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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

public final class WheelEditorScreen extends Screen {
    private final Screen parent;
    private final WheelConfig draft;
    private final InputConstants.Key[] stagedKeys = new InputConstants.Key[5];
    private final List<ContentWidget> contentWidgets = new ArrayList<>();
    private final List<Caption> captions = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private int activeWheel;
    private int selectedSlot;
    private int capturingKey = -1;
    private int left;
    private int panelWidth;
    private int viewportBottom;
    private int contentHeight;
    private int scroll;
    private String error = "";

    private record ContentWidget(AbstractWidget widget, int offset) {}
    private record Caption(Component text, int offset, int color) {}

    public WheelEditorScreen(Screen parent) {
        super(Component.literal("Hotkey Wheel"));
        this.parent = parent;
        draft = WheelConfig.get().copy();
        for (int index = 0; index < stagedKeys.length; index++) {
            stagedKeys[index] = InputConstants.getKey(keyMapping(index).saveString());
        }
    }

    private static KeyMapping keyMapping(int index) {
        if (index < 3) return HotkeyWheelClient.WHEEL_KEYS[index];
        return index == 3 ? HotkeyWheelClient.CYCLE_KEY : HotkeyWheelClient.CONFIGURE_KEY;
    }

    private WheelConfig.Wheel wheel() { return draft.wheels.get(activeWheel); }

    @Override
    protected void init() {
        contentWidgets.clear();
        captions.clear();
        fixedWidgets.clear();
        panelWidth = Math.min(560, Math.max(180, width - 40));
        left = (width - panelWidth) / 2;
        viewportBottom = height - 57;
        activeWheel = Math.min(activeWheel, draft.wheelCount - 1);
        int tabWidth = (panelWidth - 8 * (draft.wheelCount - 1)) / draft.wheelCount;
        for (int index = 0; index < draft.wheelCount; index++) {
            int target = index;
            Button tab = Button.builder(Component.literal((index == activeWheel ? "● " : "") + "Wheel " + (index + 1)), button -> {
                activeWheel = target;
                selectedSlot = 0;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + index * (tabWidth + 8), 32, tabWidth, 20).build();
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
        int offset = 0;
        contentButton("Active wheels: " + draft.wheelCount, offset, panelWidth, left, button -> {
            draft.wheelCount = draft.wheelCount % 3 + 1;
            activeWheel = Math.min(activeWheel, draft.wheelCount - 1);
            rebuildWidgets();
        }, "Choose 1–3 wheels. Hidden wheels keep their settings.");
        offset += 26;
        contentButton("Safe mode: " + (draft.safeMode ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.safeMode = !draft.safeMode;
            rebuildWidgets();
        }, "Protect Inventory, Attack/Use, movement, Jump, Sneak, Sprint, Pick Block, chat/commands, and hotbar keys from automatic unbinding. Escape and primary mouse buttons are reserved for normal menu input. Turning this on restores essential keys previously unbound by this mod when you Save.");
        offset += 26;
        Button restore = contentButton("Restore keys unbound by Hotkey Wheel", offset, panelWidth, left, button -> {
            for (WheelConfig.Wheel configured : draft.wheels) {
                for (WheelConfig.Entry entry : configured.entries) entry.unbindKey = false;
            }
            rebuildWidgets();
        }, "Turn off automatic unbinding for all slots. Save restores remembered keys that are still unbound, preserving later manual bindings. Cancel discards this request.");
        restore.active = !draft.managedBindings.isEmpty();
        offset += 30;
        caption("Wheel name", offset, WheelUi.DIM);
        offset += 12;
        EditBox name = new EditBox(font, left, 0, panelWidth, 20, Component.literal("Wheel name"));
        name.setMaxLength(32);
        name.setValue(wheel().name);
        name.setResponder(value -> wheel().name = value);
        content(name, offset);
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
        offset += 32;
        caption("Wheel keys", offset, WheelUi.GOLD);
        offset += 14;
        for (int index = 0; index < 3; index++) {
            addKeyButton(index, "Open wheel " + (index + 1), offset);
            offset += 24;
        }
        addKeyButton(3, "Cycle while open", offset);
        offset += 24;
        addKeyButton(4, "Configure wheels", offset);
        offset += 33;
        caption("Slots · " + wheel().entries.size() + "/12", offset, WheelUi.GOLD);
        offset += 16;
        selectedSlot = Math.clamp(selectedSlot, 0, Math.max(0, wheel().entries.size() - 1));
        int columns = Math.min(12, Math.max(1, panelWidth / 32));
        for (int index = 0; index < wheel().entries.size(); index++) {
            int slot = index;
            WheelConfig.Entry entry = wheel().entries.get(index);
            Component description = Component.literal("Slot " + (slot + 1) + ": " + entryName(entry));
            WheelUi.IconButton icon = new WheelUi.IconButton(left + index % columns * 32, 0, 28,
                    WheelUi.item(entry.icon), description, () -> selectedSlot == slot, button -> {
                selectedSlot = slot;
                rebuildWidgets();
            });
            content(icon, offset + index / columns * 32);
        }
        offset += Math.max(1, (wheel().entries.size() + columns - 1) / columns) * 32 + 4;
        int quarter = (panelWidth - 18) / 4;
        Button add = contentButton("Add", offset, quarter, left, button -> {
            wheel().entries.add(new WheelConfig.Entry());
            selectedSlot = wheel().entries.size() - 1;
            rebuildWidgets();
        }, "Add another slot, up to 12 per wheel.");
        add.active = wheel().entries.size() < 12;
        Button remove = contentButton("Remove", offset, quarter, left + quarter + 6, button -> {
            wheel().entries.remove(selectedSlot);
            selectedSlot = Math.max(0, selectedSlot - 1);
            rebuildWidgets();
        }, "Remove the selected slot.");
        remove.active = !wheel().entries.isEmpty();
        Button earlier = contentButton("← Move", offset, quarter, left + 2 * (quarter + 6), button -> moveSlot(-1),
                "Move the selected slot one position earlier around the wheel.");
        earlier.active = !wheel().entries.isEmpty() && selectedSlot > 0;
        Button later = contentButton("Move →", offset, quarter, left + 3 * (quarter + 6), button -> moveSlot(1),
                "Move the selected slot one position later around the wheel.");
        later.active = !wheel().entries.isEmpty() && selectedSlot < wheel().entries.size() - 1;
        offset += 33;
        if (!wheel().entries.isEmpty()) {
            WheelConfig.Entry entry = wheel().entries.get(selectedSlot);
            caption("Selected slot " + (selectedSlot + 1), offset, WheelUi.GOLD);
            offset += 14;
            String controlName = entry.control.isBlank() ? "Choose a control" : Component.translatable(entry.control).getString();
            contentButton("Control: " + controlName, offset, panelWidth, left, button -> {
                minecraft.gui.setScreen(new ControlPickerScreen(this, control -> entry.control = control));
            }, "Choose a standard Minecraft control or a control registered by an installed mod.");
            offset += 26;
            KeyMapping control = HotkeyWheelClient.findControl(entry.control);
            boolean protectedKey = draft.safeMode && control != null && WheelBindings.protectedControl(control);
            String unbindLabel = protectedKey ? "Unbind normal key: Protected by safe mode"
                    : "Unbind normal key: " + (entry.unbindKey ? "ON (on Save)" : "OFF");
            Button unbind = contentButton(unbindLabel, offset, panelWidth, left, button -> {
                entry.unbindKey = !entry.unbindKey;
                rebuildWidgets();
            }, "Remove only this control's ordinary key after Save. The wheel still activates it. Its original key is remembered and restored when you turn this off, remove the slot, or disable the wheel. Other controls sharing the key stay bound.");
            unbind.active = control != null && !protectedKey;
            offset += 26;
            contentButton("Choose block / item icon…", offset, panelWidth, left, button -> {
                minecraft.gui.setScreen(new IconPickerScreen(this, entry.icon, icon -> entry.icon = icon));
            }, "Icons are visual labels. You do not need to carry the block or item.");
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
        offset = wrappedCaption("Hold open: release the wheel key to tap the hovered control. For a Hold action, click its slot, then keep the wheel key held until finished.", offset);
        offset += 5;
        offset = wrappedCaption("Cycle changes wheels while the opener remains held. Keys also appear in Controls → Key Binds → Hotkey Wheel and are searchable with Controlling.", offset);
        offset += 14;
        caption("Integrations", offset, WheelUi.GOLD);
        offset += 14;
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
        Button buildprint = contentButton("Wheel key closes Buildprint menus: " + (draft.closeBuildprintWithWheelKey ? "ON" : "OFF"), offset, panelWidth, left, button -> {
            draft.closeBuildprintWithWheelKey = !draft.closeBuildprintWithWheelKey;
            rebuildWidgets();
        }, "When a wheel opens a Buildprint or Blocktone menu, its original opening key closes that menu and its children using their normal save/cancel behavior. Other routes and unrelated menus keep their normal behavior.");
        buildprint.active = FabricLoader.getInstance().isModLoaded("buildprint");
        offset += 26;
        contentHeight = offset + 12;
    }

    private void addKeyButton(int index, String label, int offset) {
        String text = capturingKey == index ? label + ": press a key or mouse button…"
                : label + ": " + stagedKeys[index].getDisplayName().getString();
        contentButton(text, offset, panelWidth, left, button -> {
            capturingKey = index;
            rebuildWidgets();
        }, "Click to bind a keyboard key or mouse button. Escape clears the binding. Key changes are applied when you Save.");
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
        if (!entry.label.isBlank()) return entry.label;
        if (entry.control.isBlank()) return "Choose a control";
        return Component.translatable(entry.control).getString();
    }

    private void moveSlot(int direction) {
        Collections.swap(wheel().entries, selectedSlot, selectedSlot + direction);
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
        for (int index = 0; index < stagedKeys.length; index++) {
            if (index < 3 && index >= draft.wheelCount) continue;
            InputConstants.Key key = stagedKeys[index];
            if (key.equals(InputConstants.UNKNOWN)) continue;
            if (assigned.contains(key)) {
                error = "Wheel, cycle, and configure keys must be different.";
                return;
            }
            assigned.add(key);
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
