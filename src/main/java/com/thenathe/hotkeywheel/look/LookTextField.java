package com.thenathe.hotkeywheel.look;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A text field drawn in the current look. Vanilla's frame is turned off and replaced by the vanilla text-field
 * sprite or a House well whose edge shows focus. A search field gets a magnifier and, once it has text, a small
 * cross that empties it. Colours are taken when the field is made, so screens rebuild it after a look change.
 */
public final class LookTextField extends EditBox {
    private static final int PAD = 5;
    private static final int SEARCH_PAD = 17;
    private static final int CLEAR_ROOM = 18;
    private static final int CLEAR_SIZE = 12;
    private final Look look;
    private final boolean search;
    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    public LookTextField(int x, int y, int width, int height, Component narration, String hint, boolean search) {
        super(Minecraft.getInstance().font, 0, 0, 0, 0, narration);
        this.look = Look.current();
        this.search = search;
        setBordered(false);
        setHint(Component.literal(hint).withColor(look.house() ? look.colors().textDim() : 0xFF8A8A8A));
        setTextColor(look.house() ? look.colors().text() : 0xFFFFFFFF);
        // Vanilla's black field keeps its shadowed white text; in House the text follows the mode, as panel text does.
        setTextShadow(!look.house() || look.colors().textShadow());
        setFrame(x, y, width, height);
    }

    /** Places the frame; the editable text sits inside it. */
    public void setFrame(int x, int y, int width, int height) {
        frameX = x;
        frameY = y;
        frameWidth = width;
        frameHeight = height;
        int padLeft = search ? SEARCH_PAD : PAD;
        setX(x + padLeft);
        setY(y + (height - 8) / 2);
        setWidth(width - padLeft - (search ? CLEAR_ROOM : PAD));
        setHeight(10);
    }

    /** Moves the field up or down with a scrolling list; the text area follows the frame. */
    public void setFrameY(int y) {
        if (y != frameY) setFrame(frameX, y, frameWidth, frameHeight);
    }

    public int frameY() { return frameY; }

    public int frameHeight() { return frameHeight; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= frameX && mouseX < frameX + frameWidth && mouseY >= frameY && mouseY < frameY + frameHeight;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean focused = isFocused();
        if (look.house()) {
            HouseColors colors = look.colors();
            int edge = focused ? (colors.light() ? colors.accent() : 0xFFFFFFFF)
                    : isMouseOver(mouseX, mouseY) ? Palette.shade(colors.accent(), 0.2F) : HouseColors.OUTLINE;
            Bevels.sunken(graphics, colors, frameX, frameY, frameWidth, frameHeight, edge);
        } else {
            VanillaFrame.sprite(graphics, focused ? VanillaFrame.TEXT_FIELD_HIGHLIGHTED : VanillaFrame.TEXT_FIELD,
                    frameX, frameY, frameWidth, frameHeight);
        }
        if (search) {
            int colour = look.house() ? (focused ? look.colors().text() : look.colors().textDim()) : 0xFFA0A0A0;
            Glyphs.MAGNIFIER.draw(graphics, frameX + 5, frameY + (frameHeight - Glyphs.MAGNIFIER.height()) / 2, colour);
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        if (showsClear()) drawClear(graphics, overClear(mouseX, mouseY));
    }

    private boolean showsClear() { return search && !getValue().isEmpty(); }

    private int clearX() { return frameX + frameWidth - 15; }

    private int clearY() { return frameY + (frameHeight - CLEAR_SIZE) / 2; }

    private boolean overClear(double mouseX, double mouseY) {
        return mouseX >= clearX() - 1 && mouseX < clearX() + CLEAR_SIZE + 1
                && mouseY >= clearY() - 1 && mouseY < clearY() + CLEAR_SIZE + 1;
    }

    private void drawClear(GuiGraphicsExtractor graphics, boolean hot) {
        if (look.house()) {
            HouseColors colors = look.colors();
            if (hot) Bevels.raised(graphics, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1, colors.accent());
            else Bevels.raised(graphics, colors, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1);
            Glyphs.CROSS.drawCentred(graphics, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1, hot ? 0xFFFFFFFF : colors.textDim());
        } else {
            VanillaFrame.sprite(graphics, hot ? VanillaFrame.CROSS_BUTTON_HIGHLIGHTED : VanillaFrame.CROSS_BUTTON,
                    clearX() - 1, clearY() - 1, CLEAR_SIZE + 2, CLEAR_SIZE + 2);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (showsClear() && overClear(event.x(), event.y())) {
            setValue("");
            setFocused(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
