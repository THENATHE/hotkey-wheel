package com.thenathe.hotkeywheel;

import com.thenathe.hotkeywheel.look.Bevels;
import com.thenathe.hotkeywheel.look.Ink;
import com.thenathe.hotkeywheel.look.Look;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class WheelUi {
    static final int GOLD = 0xFFF1C56C;
    static final int TEXT = 0xFFE6E8EA;
    static final int DIM = 0xFF8A9199;
    static final int ERROR = 0xFFE08A8A;

    private WheelUi() {}

    /** The hint under a radial, wrapped to the screen with a margin on each side. */
    static List<FormattedCharSequence> hintLines(Font font, String hint, int screenWidth) {
        return font.split(Component.literal(hint), screenWidth - 16);
    }

    /** The most lines any of a radial's hints needs, so the radial is placed once for all of them. */
    static int hintLineCount(Font font, int screenWidth, String... hints) {
        int lines = 1;
        for (String hint : hints) lines = Math.max(lines, hintLines(font, hint, screenWidth).size());
        return lines;
    }

    /** The thin line from a radial's centre to the pointer. */
    static void pointerLine(GuiGraphicsExtractor graphics, int startX, int startY, int endX, int endY, int color) {
        int length = Math.max(Math.abs(endX - startX), Math.abs(endY - startY));
        for (int step = 0; step <= length; step++) {
            double fraction = length == 0 ? 0 : (double) step / length;
            int x = (int) Math.round(startX + (endX - startX) * fraction);
            int y = (int) Math.round(startY + (endY - startY) * fraction);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

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
            Look look = Look.current();
            boolean hot = Ink.hot(this);
            look.plate(graphics, getX(), getY(), getWidth(), getHeight(), true, hot, false);
            if (selected.getAsBoolean()) {
                // Two rings, so the chosen tile still stands out while it is hovered and lit in the accent.
                int ring = look.house() && !hot ? look.colors().accent() : 0xFFFFFFFF;
                Bevels.ring(graphics, getX(), getY(), getWidth(), getHeight(), ring);
                Bevels.ring(graphics, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, ring);
            }
            var font = Minecraft.getInstance().font;
            if (!icon.needsWorld() || Minecraft.getInstance().level != null) {
                icon.draw(graphics, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            } else {
                // Item components are supplied when connecting; stacks cannot be created at first startup.
                String label = font.plainSubstrByWidth(icon.name().getString(), getWidth() - 6);
                int colour = look.plateText(true, hot, false);
                graphics.text(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2, colour, look.plateShadow(colour));
            }
            if (folderMark) {
                int colour = look.house() ? 0xFFFFFFFF : GOLD;
                graphics.text(font, "»", getX() + getWidth() - font.width("»") - 2, getY() + getHeight() - 10, colour, true);
            }
        }
    }
}
