package com.thenathe.hotkeywheel.look;

import com.thenathe.hotkeywheel.WheelConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look of Hotkey Wheel's editor, pickers and dialogs: House (flat and bevelled, light or dark, with an
 * accent) or Minecraft (vanilla sprites). It is kept in hotkey-wheel.json and saved as soon as it changes.
 */
public record Look(boolean house, HouseColors colors) {
    private static WheelConfig lookFor;
    private static Look cached;

    /** The look in the saved settings; looked up again only after the settings change. */
    public static Look current() {
        WheelConfig config = WheelConfig.get();
        if (config != lookFor) {
            lookFor = config;
            cached = new Look(config.houseLook(), HouseColors.of(config.lightLook(), config.accentChoice()));
        }
        return cached;
    }

    /** Applies a look change to the saved settings at once, as the footer switches do. */
    public static void change(Consumer<WheelConfig> edit) {
        WheelConfig.update(edit);
    }

    public String name() { return house ? "House" : "Minecraft"; }

    /** Text on the panel. */
    public int text() { return house ? colors.text() : Palette.PANEL_TEXT; }

    public int textOff() { return house ? colors.textOff() : Palette.PANEL_TEXT_OFF; }

    public int dim() { return house ? colors.textDim() : Palette.PANEL_DIM; }

    public int strong() { return house ? colors.textStrong() : Palette.PANEL_TEXT; }

    public int warn() { return house ? colors.warn() : Palette.PANEL_WARN; }

    public int bad() { return house && !colors.light() ? Palette.BAD_ON_DARK : Palette.BAD_ON_LIGHT; }

    public int rule() { return house ? colors.rule() : Palette.PANEL_RULE; }

    /** The colour that marks the chosen thing: the accent in House, the panel text in Minecraft. */
    public int mark() { return house ? Palette.shade(colors.accent(), colors.light() ? -0.2F : 0.45F) : Palette.PANEL_TEXT; }

    /** House text follows the mode's shadow; dark text on the vanilla panel never has one. */
    public boolean panelShadow() { return house && colors.textShadow(); }

    /** Captions are upper case in House and sentence case with a rule in Minecraft. */
    public String caption(String text) { return house ? text.toUpperCase(java.util.Locale.ROOT) : text; }

    /** Label colour on a button in the given state. */
    public int plateText(boolean active, boolean hot, boolean chosen) {
        if (house) {
            if (hot || chosen) return 0xFFFFFFFF;
            return active ? colors.text() : colors.textOff();
        }
        return active || chosen ? Palette.BUTTON_TEXT : Palette.BUTTON_TEXT_OFF;
    }

    public boolean plateShadow(int colour) {
        return house ? colors.textShadow() || colour == 0xFFFFFFFF : Palette.isLight(colour);
    }

    /** The button surface in both looks. A chosen button stays pressed in. */
    public void plate(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean active, boolean hot, boolean chosen) {
        if (!house) {
            VanillaFrame.sprite(graphics, VanillaFrame.button(active && !chosen, hot), x, y, width, height);
        } else if (chosen) {
            Bevels.raised(graphics, x, y, width, height, hot ? colors.accentHot() : colors.accent());
        } else if (!active) {
            Bevels.sunken(graphics, colors, x, y, width, height);
        } else if (hot) {
            Bevels.raised(graphics, x, y, width, height, colors.accent());
        } else {
            Bevels.raised(graphics, colors, x, y, width, height);
        }
    }

    /** A panel: the House face, or the creative inventory's grey panel. */
    public void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (house) Bevels.panel(graphics, colors, x, y, width, height);
        else VanillaFrame.panel(graphics, x, y, width, height);
    }

    /** A group of rows: a darker House card, or a faint sunken wash on the vanilla panel. */
    public void group(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (house) {
            Bevels.group(graphics, colors, x, y, width, height);
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0x14000000);
        graphics.fill(x, y, x + width, y + 1, 0x50000000);
        graphics.fill(x, y, x + 1, y + height, 0x50000000);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x70FFFFFF);
        graphics.fill(x + width - 1, y, x + width, y + height, 0x70FFFFFF);
    }
}
