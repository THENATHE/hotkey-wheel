package com.thenathe.hotkeywheel.look;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The Minecraft look's pieces, cut from Minecraft's own sprites by id so resource packs restyle them. */
public final class VanillaFrame {
    public static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    public static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
    public static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    public static final Identifier TEXT_FIELD = Identifier.withDefaultNamespace("widget/text_field");
    public static final Identifier TEXT_FIELD_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/text_field_highlighted");
    public static final Identifier CROSS_BUTTON = Identifier.withDefaultNamespace("widget/cross_button");
    public static final Identifier CROSS_BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/cross_button_highlighted");
    public static final Identifier POPUP = Identifier.withDefaultNamespace("popup/background");
    private static final Identifier CREATIVE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");

    private VanillaFrame() {}

    public static Identifier button(boolean active, boolean hot) {
        if (!active) return BUTTON_DISABLED;
        return hot ? BUTTON_HIGHLIGHTED : BUTTON;
    }

    public static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }

    /**
     * The creative inventory's grey panel stretched to any size: its four corners as they are, and the edges
     * and middle tiled from plain stretches of the texture.
     */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int right = x + width - 7;
        int bottom = y + height - 7;
        creative(graphics, x, y, 0, 0, 7, 8);
        creative(graphics, right, y, 188, 0, 7, 8);
        creative(graphics, x, bottom, 0, 129, 7, 7);
        creative(graphics, right, bottom, 188, 129, 7, 7);
        for (int tileX = x + 7; tileX < right; tileX += 60) {
            int tileWidth = Math.min(60, right - tileX);
            creative(graphics, tileX, y, 100, 0, tileWidth, 8);
            creative(graphics, tileX, bottom, 100, 129, tileWidth, 7);
            for (int tileY = y + 8; tileY < bottom; tileY += 8) {
                creative(graphics, tileX, tileY, 100, 8, tileWidth, Math.min(8, bottom - tileY));
            }
        }
        for (int tileY = y + 8; tileY < bottom; tileY += 100) {
            int tileHeight = Math.min(100, bottom - tileY);
            creative(graphics, x, tileY, 0, 20, 7, tileHeight);
            creative(graphics, right, tileY, 188, 20, 7, tileHeight);
        }
    }

    private static void creative(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CREATIVE_TEXTURE, x, y, u, v, width, height, 256, 256);
    }
}
