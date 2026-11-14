package com.thenathe.hotkeywheel.look;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The frame shared by Hotkey Wheel's editor and pickers: the title at the top left, an optional navigation
 * column, one panel, and a footer whose left end switches the look. Content is at most 640 wide and centred.
 * The game keeps running underneath, as it does for the wheels.
 */
public abstract class LookScreen extends Screen {
    protected static final int TITLE_Y = 8;
    protected static final int PANEL_Y = 24;
    private static final int FOOTER_FROM_BOTTOM = 26;
    private static final int ACCENT_ROW = 14;
    private static final int ACCENT_POPUP_WIDTH = 92;
    private static final int SWATCH = 8;
    protected int contentX;
    protected int contentWidth;
    protected int navWidth;
    protected int panelX;
    protected int panelWidth;
    protected int panelBottom;
    protected int footerY;
    private int accentX;
    private boolean accentOpen;

    protected LookScreen(Component title) {
        super(title);
    }

    /** Works out the frame for the current size; a navigation column takes about three tenths of the width. */
    protected void layoutFrame(boolean navigation) {
        contentWidth = Math.min(640, width - 20);
        contentX = (width - contentWidth) / 2;
        navWidth = navigation ? Math.clamp(contentWidth * 3 / 10, 90, 180) : 0;
        panelX = navigation ? contentX + navWidth + 6 : contentX;
        panelWidth = contentX + contentWidth - panelX;
        footerY = height - FOOTER_FROM_BOTTOM;
        panelBottom = Math.max(PANEL_Y + 40, footerY - 6);
        accentOpen = false;
    }

    /** Inner edges of the panel, where its rows go. */
    protected int listX() { return panelX + (Look.current().house() ? 10 : 12); }

    protected int listWidth() { return panelWidth - (Look.current().house() ? 20 : 24); }

    protected int listTop() { return PANEL_Y + (Look.current().house() ? 6 : 9); }

    protected int listBottom() { return panelBottom - (Look.current().house() ? 4 : 8); }

    /**
     * Adds the look switch at the left of the footer, and in House the accent and light/dark buttons after it.
     * Returns the x where the footer's free room begins.
     */
    protected int addLookControls() {
        Look look = Look.current();
        int x = contentX;
        String lookLabel = "Look: " + look.name();
        PlateButton lookButton = new PlateButton(x, footerY, Math.max(40, font.width(lookLabel) + 16), 20,
                Component.literal(lookLabel), button -> {
            Look.change(config -> config.setHouseLook(!config.houseLook()));
            rebuildWidgets();
        });
        lookButton.setTooltip(Tooltip.create(Component.literal(look.house()
                ? "Switch to the Minecraft look, drawn from the game's own buttons and panels so resource packs restyle it."
                : "Switch to the House look: flat panels with a colour of your choice, in light or dark.")));
        addRenderableWidget(lookButton);
        x += lookButton.getWidth() + 4;
        if (!look.house()) return x + 4;

        accentX = x;
        PlateButton accent = new AccentButton(x, footerY, button -> accentOpen = !accentOpen);
        accent.setTooltip(Tooltip.create(Component.literal("Colour: " + look.colors().accentChoice().label())));
        addRenderableWidget(accent);
        x += accent.getWidth() + 4;
        PlateButton mode = new ModeButton(x, footerY, button -> {
            Look.change(config -> config.setLightLook(!config.lightLook()));
            rebuildWidgets();
        });
        mode.setTooltip(Tooltip.create(Component.literal(look.colors().light() ? "Light panels · click for dark" : "Dark panels · click for light")));
        addRenderableWidget(mode);
        return x + mode.getWidth() + 8;
    }

    /** A short note after the title, such as an error; empty for none. */
    protected String titleNote() { return ""; }

    protected boolean titleNoteIsError() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Look look = Look.current();
        // The Minecraft look keeps the world in view behind a plain dim, with no blur.
        if (!look.house() && minecraft.level != null) extractTransparentBackground(graphics);
        else super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, contentX, TITLE_Y, Palette.TITLE, true);
        String note = titleNote();
        if (!note.isEmpty()) {
            int noteX = contentX + font.width(title) + 10;
            String trimmed = Ink.trim(font, note, contentX + contentWidth - noteX);
            graphics.text(font, trimmed, noteX, TITLE_Y, titleNoteIsError() ? Palette.BAD_ON_DARK : Palette.TITLE_NOTE, true);
        }
        look.panel(graphics, panelX, PANEL_Y, panelWidth, panelBottom - PANEL_Y);
    }

    /** A caption over a group: upper case and dim in House, sentence case with a rule to its right in Minecraft. */
    protected void caption(GuiGraphicsExtractor graphics, String text, int x, int y, int width) {
        Look look = Look.current();
        String shown = Ink.trim(font, look.caption(text), width);
        graphics.text(font, shown, x, y, look.dim(), look.panelShadow());
        if (!look.house()) {
            int ruleX = x + font.width(shown) + 4;
            if (ruleX < x + width) graphics.fill(ruleX, y + 4, x + width, y + 5, look.rule());
        }
    }

    /** Text centred on the panel, shadowed only where the look's panel text is, unlike vanilla's centred text. */
    protected void centredText(GuiGraphicsExtractor graphics, String text, int centreX, int y, int colour) {
        graphics.text(font, text, centreX - font.width(text) / 2, y, colour, Look.current().panelShadow());
    }

    /** Draws the accent list over everything else; screens call this last when they draw. */
    protected void extractAccentPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!accentOpen || !Look.current().house()) return;
        Look look = Look.current();
        int top = accentTop();
        Bevels.panel(graphics, look.colors(), accentX, top, ACCENT_POPUP_WIDTH, Accent.values().length * ACCENT_ROW + 4);
        for (Accent accent : Accent.values()) {
            int rowY = top + 2 + accent.ordinal() * ACCENT_ROW;
            boolean hovered = mouseX >= accentX + 2 && mouseX < accentX + ACCENT_POPUP_WIDTH - 2 && mouseY >= rowY && mouseY < rowY + ACCENT_ROW;
            if (hovered) graphics.fill(accentX + 2, rowY, accentX + ACCENT_POPUP_WIDTH - 2, rowY + ACCENT_ROW, look.colors().faceHigh());
            int swatchY = rowY + (ACCENT_ROW - SWATCH) / 2;
            graphics.fill(accentX + 5, swatchY, accentX + 5 + SWATCH, swatchY + SWATCH, HouseColors.OUTLINE);
            graphics.fill(accentX + 6, swatchY + 1, accentX + 4 + SWATCH, swatchY + SWATCH - 1, accent.argb());
            graphics.text(font, accent.label(), accentX + 9 + SWATCH, rowY + 3, look.text(), look.panelShadow());
            if (accent == look.colors().accentChoice()) {
                Glyphs.CHECK.draw(graphics, accentX + ACCENT_POPUP_WIDTH - 14, rowY + (ACCENT_ROW - Glyphs.CHECK.height()) / 2, look.mark());
            }
        }
    }

    private int accentTop() {
        return footerY - 2 - Accent.values().length * ACCENT_ROW - 4;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (accentOpen) {
            // A click anywhere closes the list; a click on a colour also chooses it.
            accentOpen = false;
            int top = accentTop() + 2;
            if (event.x() >= accentX && event.x() < accentX + ACCENT_POPUP_WIDTH && event.y() >= top
                    && event.y() < top + Accent.values().length * ACCENT_ROW) {
                Accent chosen = Accent.values()[(int) (event.y() - top) / ACCENT_ROW];
                Look.change(config -> config.setAccentChoice(chosen));
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (accentOpen && event.key() == InputConstants.KEY_ESCAPE) {
            accentOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** "Colour", after a small square of the current accent. */
    private final class AccentButton extends PlateButton {
        AccentButton(int x, int y, OnPress onPress) {
            super(x, y, Math.max(40, LookScreen.this.font.width("Colour") + 16 + SWATCH + 4), 20, Component.literal("Colour"), onPress);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            Look look = Look.current();
            boolean hot = active && Ink.hot(this);
            look.plate(graphics, getX(), getY(), width, height, active, hot, false);
            int swatchX = getX() + 6;
            int swatchY = getY() + (height - SWATCH) / 2;
            graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, HouseColors.OUTLINE);
            graphics.fill(swatchX + 1, swatchY + 1, swatchX + SWATCH - 1, swatchY + SWATCH - 1, look.colors().accent());
            int colour = look.plateText(active, hot, false);
            graphics.text(font, getMessage(), swatchX + SWATCH + 4, getY() + (height - 8) / 2, colour, look.plateShadow(colour));
        }
    }

    /** A square button with a sun in light mode and a moon in dark mode. */
    private static final class ModeButton extends PlateButton {
        private static final int SUN = 0xFFF5B83D;
        private static final int MOON = 0xFFDDE3F0;

        ModeButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 20, Component.literal("Light or dark"), onPress);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            Look look = Look.current();
            look.plate(graphics, getX(), getY(), width, height, active, active && Ink.hot(this), false);
            boolean light = look.colors().light();
            (light ? Glyphs.SUN : Glyphs.MOON).drawCentred(graphics, getX(), getY(), width, height, light ? SUN : MOON);
        }
    }
}
