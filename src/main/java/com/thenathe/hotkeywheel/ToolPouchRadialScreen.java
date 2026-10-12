package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ToolPouchRadialScreen extends Screen {
    private static final int PAGE_SIZE = 12;
    private final ToolPouchIntegration.Snapshot snapshot;
    private int page;
    private int centerX;
    private int centerY;
    private int radius;
    private int innerRadius;
    private boolean closing;
    private List<RadialGeometry.Run> ring = List.of();

    public ToolPouchRadialScreen(ToolPouchIntegration.Snapshot snapshot) {
        super(Component.literal("Tool Pouch item wheel"));
        this.snapshot = snapshot;
    }

    public ToolPouchIntegration.Snapshot snapshot() { return snapshot; }
    public int page() { return page; }
    public int pageCount() { return Math.max(1, (snapshot.choices().size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int radius() { return radius; }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = (height - 32) / 2;
        radius = Math.max(36, Math.min(110, Math.min(width / 2 - 22, (height - 90) / 2)));
        innerRadius = (int) (radius * 0.42);
        rebuildRing();
        centerPointer();
    }

    private int visibleCount() {
        return Math.min(PAGE_SIZE, snapshot.choices().size() - page * PAGE_SIZE);
    }

    private void rebuildRing() {
        ring = RadialGeometry.ring(innerRadius, radius, visibleCount());
    }

    private void centerPointer() {
        RadialCursor.center(this);
    }

    public void cyclePage(int direction) {
        if (pageCount() <= 1) return;
        page = Math.floorMod(page + direction, pageCount());
        rebuildRing();
        centerPointer();
    }

    public int selection(double x, double y) {
        return RadialGeometry.selection(x - centerX, y - centerY, innerRadius, Double.POSITIVE_INFINITY, visibleCount());
    }

    public void confirm() {
        if (closing) return;
        int selected = selection(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        closing = true;
        minecraft.gui.setScreen(null);
        if (selected >= 0) ToolPouchIntegration.activate(minecraft, snapshot, page * PAGE_SIZE + selected);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) confirm();
        else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) onClose();
        else if (HotkeyWheelClient.CYCLE_KEY.matches(key)) cyclePage(1);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) cyclePage(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) onClose();
        else if (event.key() == InputConstants.KEY_LEFT) cyclePage(-1);
        else if (event.key() == InputConstants.KEY_RIGHT || HotkeyWheelClient.CYCLE_KEY.matches(InputConstants.getKey(event))) cyclePage(1);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // Source-key release never chooses an item, including from a Hold wheel.
        return true;
    }

    @Override
    public void onClose() {
        closing = true;
        minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
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
        int count = visibleCount();
        for (RadialGeometry.Run run : ring) {
            boolean highlighted = run.sector() == selected;
            double angle = RadialGeometry.angle(run.sector(), count);
            int offsetX = highlighted ? (int) Math.round(Math.cos(angle) * 7) : 0;
            int offsetY = highlighted ? (int) Math.round(Math.sin(angle) * 7) : 0;
            int x = centerX + run.x() + offsetX;
            int y = centerY + run.y() + offsetY;
            graphics.fill(x, y, x + run.length(), y + 1, highlighted ? 0xee53616c : 0xdc263038);
        }
        String kind = snapshot.context() == ToolPouchIntegration.Context.AMMO ? "Ammunition" : "Shulker boxes";
        String title = "Tool Pouch · " + kind;
        if (pageCount() > 1) title += " · " + (page + 1) + "/" + pageCount();
        graphics.centeredText(font, title, centerX, Math.max(8, centerY - radius - 24), 0xfff1c56c);
        for (int slot = 0; slot < count; slot++) {
            var choice = snapshot.choices().get(page * PAGE_SIZE + slot);
            double angle = RadialGeometry.angle(slot, count);
            double distance = radius * 0.73 + (selected == slot ? 10 : 0);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            float scale = selected == slot ? 1.25f : 1.0f;
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(choice.stack(), -8, -8);
            graphics.itemDecorations(font, choice.stack(), -8, -8);
            graphics.pose().popMatrix();
        }
        drawLine(graphics, centerX, centerY, mouseX, mouseY, selected < 0 ? 0x998a9199 : 0xfff1c56c);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xfff1c56c);
        graphics.centeredText(font, "Cancel", centerX, centerY + 7, selected < 0 ? 0xfff1c56c : 0xff8a9199);
        graphics.fill(0, centerY + radius + 13, width, height, 0x99000000);
        if (selected >= 0) {
            String label = snapshot.choices().get(page * PAGE_SIZE + selected).stack().getHoverName().getString();
            graphics.centeredText(font, font.plainSubstrByWidth(label, width - 24), centerX, centerY + radius + 18, 0xffe6e8ea);
        }
        String cycle = HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString();
        String pages = pageCount() > 1 ? "Cycle page: " + cycle + " · arrows or scroll" : "All available items · cycle key: " + cycle;
        graphics.centeredText(font, pages, centerX, centerY + radius + 31, 0xfff1c56c);
        graphics.centeredText(font, "Click to select · right click or Esc to cancel", centerX, height - 12, 0xff8a9199);
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
