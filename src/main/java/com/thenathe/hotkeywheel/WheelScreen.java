package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WheelScreen extends Screen {
    private final InputConstants.Key opener;
    private final boolean toggleOpen;
    private int wheelIndex;
    private int centerX;
    private int centerY;
    private int radius;
    private int innerRadius;
    private boolean closing;
    private List<RadialGeometry.Run> ring = List.of();

    public WheelScreen(int index, InputConstants.Key opener, boolean toggleOpen) {
        super(Component.literal("Hotkey Wheel"));
        this.wheelIndex = index;
        this.opener = opener;
        this.toggleOpen = toggleOpen;
    }

    public InputConstants.Key opener() { return opener; }
    public boolean toggleOpen() { return toggleOpen; }
    public int wheelIndex() { return wheelIndex; }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int radius() { return radius; }

    private WheelConfig.Wheel wheel() { return WheelConfig.get().wheels.get(wheelIndex); }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = (height - 32) / 2;
        radius = Math.max(36, Math.min(110, Math.min(width / 2 - 22, (height - 90) / 2)));
        innerRadius = (int) (radius * 0.42);
        rebuildRing();
        // Always begin inside Cancel, including after a window resize.
        RadialCursor.center(this);
    }

    private void rebuildRing() { ring = RadialGeometry.ring(innerRadius, radius, wheel().entries.size()); }

    public void switchWheel(int index) {
        wheelIndex = Math.floorMod(index, WheelConfig.get().wheelCount);
        rebuildRing();
    }

    public void cycle() { switchWheel(wheelIndex + 1); }

    public int selection(double x, double y) {
        double outerRadius = wheel().selectOutside ? Double.POSITIVE_INFINITY : radius + 16;
        return RadialGeometry.selection(x - centerX, y - centerY, innerRadius, outerRadius, wheel().entries.size());
    }

    public void confirm(boolean clicked) {
        int slot = selection(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        WheelConfig.Entry entry = slot < 0 ? null : wheel().entries.get(slot);
        closing = true;
        minecraft.gui.setScreen(null);
        if (entry != null && !entry.control.isBlank()) {
            HotkeyWheelClient.queueAction(entry, opener, clicked && entry.holdAction && !toggleOpen);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            confirm(true);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) onClose();
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
        for (RadialGeometry.Run run : ring) {
            boolean highlighted = run.sector() == selected;
            double angle = RadialGeometry.angle(run.sector(), wheel().entries.size());
            int offsetX = highlighted ? (int) Math.round(Math.cos(angle) * 7) : 0;
            int offsetY = highlighted ? (int) Math.round(Math.sin(angle) * 7) : 0;
            int x = centerX + run.x() + offsetX;
            int y = centerY + run.y() + offsetY;
            graphics.fill(x, y, x + run.length(), y + 1, highlighted ? 0xee53616c : 0xdc263038);
        }
        graphics.centeredText(font, wheel().name + "  " + (wheelIndex + 1) + "/" + WheelConfig.get().wheelCount,
                centerX, Math.max(8, centerY - radius - 24), 0xfff1c56c);
        for (int slot = 0; slot < wheel().entries.size(); slot++) {
            WheelConfig.Entry entry = wheel().entries.get(slot);
            double angle = RadialGeometry.angle(slot, wheel().entries.size());
            double distance = radius * 0.73 + (selected == slot ? 10 : 0);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            float scale = selected == slot ? 1.25f : 1.0f;
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(icon(entry.icon), -8, -8);
            graphics.pose().popMatrix();
            if (HotkeyWheelClient.findControl(entry.control) == null) graphics.text(font, "!", x + 5, y - 11, 0xffff9999);
        }
        drawLine(graphics, centerX, centerY, mouseX, mouseY, selected < 0 ? 0x998a9199 : 0xfff1c56c);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xfff1c56c);
        graphics.centeredText(font, "Cancel", centerX, centerY + 7, selected < 0 ? 0xfff1c56c : 0xff8a9199);
        graphics.fill(0, centerY + radius + 13, width, height, 0x99000000);
        if (selected >= 0) {
            WheelConfig.Entry entry = wheel().entries.get(selected);
            var mapping = HotkeyWheelClient.findControl(entry.control);
            String label = !entry.label.isBlank() ? entry.label : mapping == null ? "Unassigned" : Component.translatable(mapping.getName()).getString();
            graphics.centeredText(font, font.plainSubstrByWidth(label, width - 24), centerX, centerY + radius + 18, 0xffe6e8ea);
        }
        String cycle = "Cycle wheel: " + HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString();
        graphics.centeredText(font, cycle, centerX, centerY + radius + 31, 0xfff1c56c);
        graphics.centeredText(font, toggleOpen ? "Click to select · wheel key again to select · Esc to cancel"
                : "Release to select · click to hold an action · Esc to cancel", centerX, height - 12, 0xff8a9199);
    }

    public static ItemStack icon(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return new ItemStack(Items.BARRIER);
        var item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.BARRIER);
        return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
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
