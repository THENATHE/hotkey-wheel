package com.thenathe.hotkeywheel;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class WheelUi {
    static final int GOLD = 0xFFF1C56C;
    static final int TEXT = 0xFFE6E8EA;
    static final int DIM = 0xFF8A9199;
    static final int ERROR = 0xFFE08A8A;

    private WheelUi() {}

    static Component itemName(Item item) {
        if (Minecraft.getInstance().level != null) return new ItemStack(item).getHoverName();
        return Component.translatable(item.getDescriptionId());
    }

    static Item item(String id) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            return item == null || item == Items.AIR ? Items.BARRIER : item;
        } catch (IllegalArgumentException failure) {
            return Items.BARRIER;
        }
    }

    static final class IconButton extends Button {
        private final WheelIcons.Icon icon;
        private final BooleanSupplier selected;
        private boolean folderMark;

        IconButton(int x, int y, int size, WheelIcons.Icon icon, Component description,
                   BooleanSupplier selected, OnPress action) {
            super(x, y, size, size, description, action, DEFAULT_NARRATION);
            this.icon = icon;
            this.selected = selected;
            setTooltip(Tooltip.create(description));
        }

        /** Marks a folder tile, which opens slots of its own, the same way the radial does. */
        void showFolderMark() { folderMark = true; }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int background = isHoveredOrFocused() ? 0xFF45545F : 0xDC263038;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            if (selected.getAsBoolean()) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 2, GOLD);
                graphics.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(), GOLD);
                graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), GOLD);
                graphics.fill(getX() + getWidth() - 2, getY(), getX() + getWidth(), getY() + getHeight(), GOLD);
            }
            if (!icon.needsWorld() || Minecraft.getInstance().level != null) {
                icon.draw(graphics, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            } else {
                // Item components are supplied when connecting; stacks cannot be created at first startup.
                var font = Minecraft.getInstance().font;
                String label = font.plainSubstrByWidth(icon.name().getString(), getWidth() - 6);
                graphics.centeredText(font, label, getX() + getWidth() / 2,
                        getY() + (getHeight() - 9) / 2, TEXT);
            }
            if (folderMark) {
                var font = Minecraft.getInstance().font;
                graphics.text(font, "»", getX() + getWidth() - font.width("»") - 2, getY() + getHeight() - 10, GOLD);
            }
        }
    }
}
