package com.thenathe.hotkeywheel.look;

/** Fixed colours for the Minecraft look, plus the small colour sums both looks share. */
public final class Palette {
    // Dark ink for the light grey creative-inventory panel.
    public static final int PANEL_TEXT = 0xFF404040;
    public static final int PANEL_TEXT_OFF = 0xFF8E8E8E;
    public static final int PANEL_DIM = 0xFF707070;
    public static final int PANEL_WARN = 0xFF5C3C00;
    public static final int PANEL_RULE = 0x30000000;
    // Light ink for vanilla buttons.
    public static final int BUTTON_TEXT = 0xFFFFFFFF;
    public static final int BUTTON_TEXT_OFF = 0xFFA0A0A0;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int TITLE_NOTE = 0xFF8A9199;
    public static final int BAD_ON_DARK = 0xFFFF8080;
    public static final int BAD_ON_LIGHT = 0xFF861A14;

    private Palette() {}

    /** Moves each channel a fraction of the way toward white (positive) or black (negative). */
    public static int shade(int argb, float amount) {
        int target = amount > 0 ? 255 : 0;
        float share = Math.abs(amount);
        int red = Math.round(channel(argb, 16) + (target - channel(argb, 16)) * share);
        int green = Math.round(channel(argb, 8) + (target - channel(argb, 8)) * share);
        int blue = Math.round(channel(argb, 0) + (target - channel(argb, 0)) * share);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** Whether light text is wanted on this colour: the vanilla rule that light text has a shadow. */
    public static boolean isLight(int argb) {
        return (channel(argb, 16) * 299 + channel(argb, 8) * 587 + channel(argb, 0) * 114) / 1000 >= 128;
    }

    private static int channel(int argb, int shift) {
        return argb >> shift & 0xFF;
    }
}
