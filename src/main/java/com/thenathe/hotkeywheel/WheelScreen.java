package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class WheelScreen extends Screen {
    private static final String TOGGLE_HINT = "Click to select · wheel key again to select · Esc to cancel";
    private static final String TOGGLE_FOLDER_HINT = "Click to select · center, right click or Esc goes back";
    private static final String HOLD_HINT = "Release to select · click to hold an action · Esc to cancel";
    private static final String HOLD_FOLDER_HINT = "Release to select · click to hold or go back · Esc goes back";
    private final InputConstants.Key opener;
    private final boolean toggleOpen;
    private int wheelIndex;
    private int centerX;
    private int centerY;
    private int radius;
    private int innerRadius;
    private boolean closing;
    // Releasing a Hold opener over a folder keeps the folder open, and clicks then choose, as in the Tool Pouch radial.
    private boolean clickToSelect;
    private final List<WheelConfig.Entry> openFolders = new ArrayList<>();
    private List<RadialGeometry.Run> ring = List.of();
    // Looked up once per wheel instead of every frame; null marks a control that is not registered.
    private List<WheelIcons.Icon> icons = List.of();
    private List<KeyMapping> controls = List.of();
    private List<Boolean> pouchToggles = List.of();

    public WheelScreen(int index, InputConstants.Key opener, boolean toggleOpen) {
        super(Component.literal("Hotkey Wheel"));
        this.wheelIndex = index;
        this.opener = opener;
        this.toggleOpen = toggleOpen;
    }

    public InputConstants.Key opener() { return opener; }
    /** Whether the opener confirms on press rather than release: Toggle wheels, and Hold wheels after a release opened a folder. */
    public boolean toggleOpen() { return toggleOpen || clickToSelect; }
    public int wheelIndex() { return wheelIndex; }
    /** How many folders deep the radial is; 0 is the wheel itself. */
    public int folderDepth() { return openFolders.size(); }
    public int slotCount() { return entries().size(); }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int radius() { return radius; }
    /** How many lines the hint under this wheel takes at the current screen width. */
    public int hintLines() { return WheelUi.hintLines(font, currentHint(), width).size(); }

    private WheelConfig.Wheel wheel() { return WheelConfig.get().wheels.get(wheelIndex); }

    private List<WheelConfig.Entry> entries() {
        return openFolders.isEmpty() ? wheel().entries : openFolders.getLast().entries;
    }

    /** Where each open folder sits in the level above it, outermost first. */
    public List<Integer> folderPath() {
        List<Integer> path = new ArrayList<>();
        List<WheelConfig.Entry> level = wheel().entries;
        for (WheelConfig.Entry folder : openFolders) {
            path.add(level.indexOf(folder));
            level = folder.entries;
        }
        return path;
    }

    /** The wheel name followed by each open folder, as shown above the radial. */
    public String location() {
        StringBuilder location = new StringBuilder(wheel().name);
        for (WheelConfig.Entry folder : openFolders) location.append(" › ").append(folder.folderName());
        return location.toString();
    }

    @Override
    protected void init() {
        centerX = width / 2;
        RadialGeometry.Layout layout = RadialGeometry.layout(width, height,
                WheelUi.hintLineCount(font, width, TOGGLE_HINT, TOGGLE_FOLDER_HINT, HOLD_HINT, HOLD_FOLDER_HINT));
        centerY = layout.centerY();
        radius = layout.radius();
        innerRadius = (int) (radius * 0.42);
        rebuildWheel();
        // Always begin inside Cancel, including after a window resize.
        RadialCursor.center(this);
    }

    private void rebuildWheel() {
        List<WheelConfig.Entry> entries = entries();
        ring = RadialGeometry.ring(innerRadius, radius, entries.size());
        icons = new ArrayList<>(entries.size());
        controls = new ArrayList<>(entries.size());
        pouchToggles = new ArrayList<>(entries.size());
        for (WheelConfig.Entry entry : entries) {
            icons.add(WheelIcons.resolve(entry.icon));
            controls.add(HotkeyWheelClient.findControl(entry.control));
            pouchToggles.add(ToolPouchToggles.handles(entry.control) || ModSettingsActions.available(entry.control));
        }
    }

    public void switchWheel(int index) {
        wheelIndex = Math.floorMod(index, WheelConfig.get().wheelCount);
        // Another wheel starts at its own top level; a released Hold opener stays released.
        openFolders.clear();
        rebuildWheel();
    }

    public void cycle() { switchWheel(wheelIndex + 1); }

    public int selection(double x, double y) {
        double outerRadius = wheel().selectOutside ? Double.POSITIVE_INFINITY : radius + 16;
        return RadialGeometry.selection(x - centerX, y - centerY, innerRadius, outerRadius, entries().size());
    }

    private boolean overCenter(double x, double y) {
        return Math.hypot(x - centerX, y - centerY) < innerRadius;
    }

    /** Uses the hovered slot. {@code clicked} is false for the opener's release (Hold) or press (Toggle). */
    public void confirm(boolean clicked) {
        double x = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double y = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        int slot = selection(x, y);
        WheelConfig.Entry entry = slot < 0 ? null : entries().get(slot);
        if (entry != null && entry.isFolder()) {
            // A click keeps a held opener in charge; a release leaves nothing held, so clicks choose from here on.
            if (!clicked && !toggleOpen()) clickToSelect = true;
            openFolder(entry);
            return;
        }
        // The center is Back for clicks and Toggle presses; releasing a Hold opener there always closes, as before.
        boolean released = !clicked && !toggleOpen();
        if (entry == null && !released && !openFolders.isEmpty() && overCenter(x, y)) {
            goBack();
            return;
        }
        closing = true;
        minecraft.gui.setScreen(null);
        if (entry != null && !entry.control.isBlank()) {
            HotkeyWheelClient.queueAction(entry, opener, clicked && entry.holdAction && !toggleOpen);
        }
    }

    private void openFolder(WheelConfig.Entry folder) {
        openFolders.add(folder);
        rebuildWheel();
        RadialCursor.center(this);
    }

    /** Escape and right click: up one folder, or close the wheel at its top level. */
    public void goBack() {
        if (openFolders.isEmpty()) {
            onClose();
            return;
        }
        openFolders.removeLast();
        rebuildWheel();
        RadialCursor.center(this);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            confirm(true);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            goBack();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) goBack();
        return true;
    }

    @Override
    public void onClose() {
        closing = true;
        minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        // A replacement screen cancels naturally; only confirm() queues an action.
        closing = true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (closing) return;
        int selected = selection(mouseX, mouseY);
        List<WheelConfig.Entry> entries = entries();
        for (RadialGeometry.Run run : ring) {
            boolean highlighted = run.sector() == selected;
            double angle = RadialGeometry.angle(run.sector(), entries.size());
            int offsetX = highlighted ? (int) Math.round(Math.cos(angle) * 7) : 0;
            int offsetY = highlighted ? (int) Math.round(Math.sin(angle) * 7) : 0;
            int x = centerX + run.x() + offsetX;
            int y = centerY + run.y() + offsetY;
            graphics.fill(x, y, x + run.length(), y + 1, highlighted ? 0xee53616c : 0xdc263038);
        }
        String count = "  " + (wheelIndex + 1) + "/" + WheelConfig.get().wheelCount;
        // A deep path keeps its end, where the open folder is named.
        String title = font.plainSubstrByWidth(location(), width - 24 - font.width(count), true) + count;
        graphics.centeredText(font, title, centerX, Math.max(8, centerY - radius - 24), 0xfff1c56c);
        for (int slot = 0; slot < entries.size(); slot++) {
            double angle = RadialGeometry.angle(slot, entries.size());
            double distance = radius * 0.73 + (selected == slot ? 10 : 0);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            float scale = selected == slot ? 1.25f : 1.0f;
            graphics.pose().scale(scale, scale);
            icons.get(slot).draw(graphics, -8, -8);
            graphics.pose().popMatrix();
            if (entries.get(slot).isFolder()) graphics.text(font, "»", x + 5, y + 2, 0xfff1c56c);
            else if (controls.get(slot) == null && !pouchToggles.get(slot)) graphics.text(font, "!", x + 5, y - 11, 0xffff9999);
        }
        drawLine(graphics, centerX, centerY, mouseX, mouseY, selected < 0 ? 0x998a9199 : 0xfff1c56c);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xfff1c56c);
        boolean inFolder = !openFolders.isEmpty();
        graphics.centeredText(font, inFolder ? "Back" : "Cancel", centerX, centerY + 7, selected < 0 ? 0xfff1c56c : 0xff8a9199);
        graphics.fill(0, centerY + radius + 13, width, height, 0x99000000);
        if (selected >= 0) {
            WheelConfig.Entry entry = entries.get(selected);
            String label = entry.isFolder() ? entry.folderName() + " » " + entry.entries.size() + (entry.entries.size() == 1 ? " slot" : " slots")
                    : !entry.label.isBlank() ? entry.label
                    : entry.control.isBlank() ? "Unassigned"
                    : HotkeyWheelClient.controlLabel(entry.control);
            graphics.centeredText(font, font.plainSubstrByWidth(label, width - 24), centerX, centerY + radius + RadialGeometry.NAME_OFFSET, 0xffe6e8ea);
        }
        String keys = "Cycle wheel: " + HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString();
        if (!HotkeyWheelClient.SETTINGS_KEY.isUnbound()) {
            keys += " · Settings: " + HotkeyWheelClient.SETTINGS_KEY.getTranslatedKeyMessage().getString();
        }
        graphics.centeredText(font, keys, centerX, centerY + radius + RadialGeometry.KEY_LINE_OFFSET, 0xfff1c56c);
        String hint = currentHint();
        // Grouped with the key line above the hearts and hotbar, which used to cover it at the bottom of the screen.
        int hintY = centerY + radius + RadialGeometry.HINT_OFFSET;
        for (FormattedCharSequence line : WheelUi.hintLines(font, hint, width)) {
            graphics.centeredText(font, line, centerX, hintY, 0xff8a9199);
            hintY += RadialGeometry.HINT_LINE_HEIGHT;
        }
    }

    private String currentHint() {
        boolean inFolder = !openFolders.isEmpty();
        return toggleOpen() ? (inFolder ? TOGGLE_FOLDER_HINT : TOGGLE_HINT) : (inFolder ? HOLD_FOLDER_HINT : HOLD_HINT);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int startX, int startY, int endX, int endY, int color) {
        int length = Math.max(Math.abs(endX - startX), Math.abs(endY - startY));
        for (int step = 0; step <= length; step++) {
            double fraction = length == 0 ? 0 : (double) step / length;
            int x = (int) Math.round(startX + (endX - startX) * fraction);
            int y = (int) Math.round(startY + (endY - startY) * fraction);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
