package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Chooses a shulker box or ender chest to open from the list a Vanilla+ Features server sent, twelve to a page,
 * drawn like the Tool Pouch item radial. It closes as soon as the inventory or the ender chest contents change,
 * since the list may no longer match them.
 */
public final class ContainerRadialScreen extends Screen implements ItemRadial {
    private static final int PAGE_SIZE = 12;
    private static final String HINT = "Click to open · right click or Esc to cancel";
    private final List<ContainerProtocol.Entry> entries;
    private List<ItemStack> inventoryWhenListed = List.of();
    private int page;
    private int centerX;
    private int centerY;
    private int radius;
    private int innerRadius;
    private boolean closing;
    private List<RadialGeometry.Run> ring = List.of();

    public ContainerRadialScreen(List<ContainerProtocol.Entry> entries) {
        super(Component.literal("Open a shulker box or ender chest"));
        this.entries = List.copyOf(entries);
    }

    @Override
    public int centerX() { return centerX; }

    @Override
    public int centerY() { return centerY; }

    public int pageCount() { return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    @Override
    protected void init() {
        if (inventoryWhenListed.isEmpty() && minecraft.player != null) inventoryWhenListed = contents(minecraft.player);
        centerX = width / 2;
        RadialGeometry.Layout layout = RadialGeometry.layout(width, height, WheelUi.hintLineCount(font, width, HINT));
        centerY = layout.centerY();
        radius = layout.radius();
        innerRadius = (int) (radius * 0.42);
        ring = RadialGeometry.ring(innerRadius, radius, visibleCount());
        RadialCursor.center(this);
    }

    /** Every inventory slot, then every ender chest slot, as the client sees them. */
    private static List<ItemStack> contents(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) stacks.add(player.getInventory().getItem(slot).copy());
        var enderChest = player.getEnderChestInventory();
        for (int slot = 0; slot < enderChest.getContainerSize(); slot++) stacks.add(enderChest.getItem(slot).copy());
        return stacks;
    }

    private boolean contentsChanged() {
        if (minecraft.player == null) return true;
        List<ItemStack> now = contents(minecraft.player);
        if (now.size() != inventoryWhenListed.size()) return true;
        for (int slot = 0; slot < now.size(); slot++) {
            if (!ItemStack.matches(now.get(slot), inventoryWhenListed.get(slot))) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (closing) return;
        if (minecraft.player == null || minecraft.level == null || !minecraft.isWindowActive()
                || minecraft.player.containerMenu != minecraft.player.inventoryMenu || contentsChanged()) {
            onClose();
        }
    }

    private int visibleCount() {
        return Math.min(PAGE_SIZE, entries.size() - page * PAGE_SIZE);
    }

    @Override
    public void cyclePage(int direction) {
        if (pageCount() <= 1) return;
        page = Math.floorMod(page + direction, pageCount());
        ring = RadialGeometry.ring(innerRadius, radius, visibleCount());
        RadialCursor.center(this);
    }

    private int selection(double x, double y) {
        return RadialGeometry.selection(x - centerX, y - centerY, innerRadius, Double.POSITIVE_INFINITY, visibleCount());
    }

    @Override
    public void confirm() {
        if (closing) return;
        int selected = selection(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
        // A list that no longer matches the inventory is never used.
        boolean stale = contentsChanged();
        closing = true;
        minecraft.gui.setScreen(null);
        if (selected >= 0 && !stale) ContainerRequests.open(minecraft, entries.get(page * PAGE_SIZE + selected));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) confirm();
        else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) onClose();
        else if (HotkeyWheelClient.CYCLE_KEY.matches(InputConstants.Type.MOUSE.getOrCreate(event.button()))) cyclePage(1);
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
        // Releasing the wheel key never chooses; only a click does.
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
        String title = "Shulker boxes and ender chests";
        if (pageCount() > 1) title += " · " + (page + 1) + "/" + pageCount();
        graphics.centeredText(font, title, centerX, Math.max(8, centerY - radius - 24), WheelUi.GOLD);
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = entries.get(page * PAGE_SIZE + slot).display();
            double angle = RadialGeometry.angle(slot, count);
            double distance = radius * 0.73 + (selected == slot ? 10 : 0);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            float scale = selected == slot ? 1.25f : 1.0f;
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(stack, -8, -8);
            graphics.pose().popMatrix();
        }
        WheelUi.pointerLine(graphics, centerX, centerY, mouseX, mouseY, selected < 0 ? 0x998a9199 : WheelUi.GOLD);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, WheelUi.GOLD);
        graphics.centeredText(font, "Cancel", centerX, centerY + 7, selected < 0 ? WheelUi.GOLD : WheelUi.DIM);
        graphics.fill(0, centerY + radius + 13, width, height, 0x99000000);
        if (selected >= 0) {
            ContainerProtocol.Entry entry = entries.get(page * PAGE_SIZE + selected);
            String label = entry.display().getHoverName().getString() + " · " + location(entry.path());
            graphics.centeredText(font, font.plainSubstrByWidth(label, width - 24), centerX, centerY + radius + RadialGeometry.NAME_OFFSET, WheelUi.TEXT);
        }
        String cycle = HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString();
        String pages = pageCount() > 1 ? "Cycle page: " + cycle + " · arrows or scroll" : "Everything within reach · cycle key: " + cycle;
        graphics.centeredText(font, pages, centerX, centerY + radius + RadialGeometry.KEY_LINE_OFFSET, WheelUi.GOLD);
        int hintY = centerY + radius + RadialGeometry.HINT_OFFSET;
        for (FormattedCharSequence line : WheelUi.hintLines(font, HINT, width)) {
            graphics.centeredText(font, line, centerX, hintY, WheelUi.DIM);
            hintY += RadialGeometry.HINT_LINE_HEIGHT;
        }
    }

    /** Where a listed container is, such as "Ender chest › Shulker box". */
    static String location(List<ContainerProtocol.Step> path) {
        List<String> places = new ArrayList<>();
        for (ContainerProtocol.Step step : path) {
            places.add(switch (step.container()) {
                case ContainerProtocol.CONTAINER_ENDER_CHEST -> "Ender chest";
                case ContainerProtocol.CONTAINER_BUNDLE -> "Bundle";
                case ContainerProtocol.CONTAINER_SHULKER_BOX -> "Shulker box";
                default -> "Inventory";
            });
        }
        // Each step names the container that holds the next one; the last holds the box or chest itself.
        return String.join(" › ", places);
    }
}
