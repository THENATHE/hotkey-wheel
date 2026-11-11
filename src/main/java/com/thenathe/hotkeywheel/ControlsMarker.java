package com.thenathe.hotkeywheel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Marks the controls a wheel uses in Options → Controls → Key Binds, and in Controlling's replacement list. */
public final class ControlsMarker {
    public static final int SIZE = 9;
    // Vanilla and Controlling draw a 3-pixel conflict stripe 6 pixels left of the key button; the marker stays clear of it.
    public static final int GAP = 8;
    // A small wheel, one string per pixel row: rim, spokes and hub.
    private static final String[] GLYPH = {
            "..XXXXX..",
            ".X..X..X.",
            "X...X...X",
            "X...X...X",
            "XXXXXXXXX",
            "X...X...X",
            "X...X...X",
            ".X..X..X.",
            "..XXXXX..",
    };
    private static final String SEPARATOR = " › ";
    private static final int MAX_LISTED = 6;
    private static WheelConfig indexed;
    private static Map<String, List<Component>> uses = Map.of();
    private static Method controllingKey;
    private static Method controllingKeyButton;
    private static boolean controllingUnavailable;

    private ControlsMarker() {}

    /** Where the active wheels use a control, such as "Wheel 1 › Tools › Pickaxe"; empty when no slot uses it. */
    public static List<Component> uses(KeyMapping control) {
        // Saving or loading settings replaces the configuration object, which is when the index goes stale.
        if (indexed != WheelConfig.get()) {
            indexed = WheelConfig.get();
            Map<String, List<Component>> found = new HashMap<>();
            for (int index = 0; index < Math.min(indexed.wheelCount, indexed.wheels.size()); index++) {
                WheelConfig.Wheel wheel = indexed.wheels.get(index);
                collect(wheel.entries, wheel.name, found);
            }
            uses = found;
        }
        return uses.getOrDefault(control.getName(), List.of());
    }

    private static void collect(List<WheelConfig.Entry> entries, String path, Map<String, List<Component>> found) {
        for (int slot = 0; slot < entries.size(); slot++) {
            WheelConfig.Entry entry = entries.get(slot);
            if (entry.isFolder()) {
                collect(entry.entries, path + SEPARATOR + entry.folderName(), found);
            } else if (!entry.control.isBlank()) {
                Component name = entry.label.isBlank() ? Component.translatable("hotkey_wheel.controls.slot", slot + 1)
                        : Component.literal(entry.label);
                // A slot saved with a replaced control marks the control it now uses.
                KeyMapping mapping = HotkeyWheelClient.findControl(entry.control);
                found.computeIfAbsent(mapping == null ? entry.control : mapping.getName(), control -> new ArrayList<>())
                        .add(Component.literal(path + SEPARATOR).append(name));
            }
        }
    }

    public static boolean unboundByWheel(KeyMapping control) {
        return control.isUnbound() && WheelConfig.get().managedBindings.containsKey(control.getName());
    }

    /** The marker's tooltip lines, or an empty list for a control no wheel uses. */
    public static List<Component> tooltip(KeyMapping control) {
        List<Component> places = uses(control);
        if (places.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hotkey_wheel.controls.used_by").withStyle(ChatFormatting.GOLD));
        lines.addAll(places.subList(0, Math.min(places.size(), MAX_LISTED)));
        if (places.size() > MAX_LISTED) {
            lines.add(Component.translatable("hotkey_wheel.controls.more", places.size() - MAX_LISTED).withStyle(ChatFormatting.GRAY));
        }
        if (unboundByWheel(control)) {
            lines.add(Component.translatable("hotkey_wheel.controls.unbound_by_wheel").withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("hotkey_wheel.controls.unbound_by_wheel.restore").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Draws the marker beside a key list row's key button; {@code rowHovered} keeps clipped rows from showing a tooltip. */
    public static void draw(GuiGraphicsExtractor graphics, KeyMapping control, AbstractWidget keyButton,
                            int mouseX, int mouseY, boolean rowHovered) {
        if (uses(control).isEmpty()) return;
        int x = keyButton.getX() - GAP - SIZE;
        int y = keyButton.getY() + (keyButton.getHeight() - SIZE) / 2;
        boolean hovered = rowHovered && mouseX >= x - 1 && mouseX <= x + SIZE && mouseY >= y - 1 && mouseY <= y + SIZE;
        int color = hovered ? WheelUi.TEXT : WheelUi.GOLD;
        for (int row = 0; row < GLYPH.length; row++) {
            String pixels = GLYPH[row];
            int start = -1;
            for (int column = 0; column <= pixels.length(); column++) {
                boolean filled = column < pixels.length() && pixels.charAt(column) == 'X';
                if (filled && start < 0) start = column;
                if (!filled && start >= 0) {
                    graphics.fill(x + start, y + row, x + column, y + row + 1, color);
                    start = -1;
                }
            }
        }
        if (hovered) graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltip(control), mouseX, mouseY);
    }

    /** Controlling's rows expose their control and key button through its public {@code IKeyEntry} interface. */
    public static void drawControllingRow(Object row, GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean rowHovered) {
        if (controllingUnavailable) return;
        try {
            if (controllingKey == null) {
                Class<?> entry = Class.forName("com.blamejared.controlling.api.entries.IKeyEntry", false, ControlsMarker.class.getClassLoader());
                controllingKeyButton = entry.getMethod("getBtnChangeKeyBinding");
                controllingKey = entry.getMethod("getKey");
            }
            KeyMapping control = (KeyMapping) controllingKey.invoke(row);
            AbstractWidget keyButton = (AbstractWidget) controllingKeyButton.invoke(row);
            if (control != null && keyButton != null) draw(graphics, control, keyButton, mouseX, mouseY, rowHovered);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // A changed Controlling only loses the markers; its own list keeps working.
            controllingUnavailable = true;
            LoggerFactory.getLogger("hotkey-wheel").warn("Cannot mark wheel controls in Controlling's key list", failure);
        }
    }
}
