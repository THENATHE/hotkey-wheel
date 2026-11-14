package com.thenathe.hotkeywheel.look;

import java.util.Locale;

/** The eight accent colours of the House look. They are muted, so white text stays readable on them. */
public enum Accent {
    BLUE("Blue", 0xFF3F5F8C),
    CYAN("Cyan", 0xFF2F7F7A),
    GREEN("Green", 0xFF4A7F35),
    PURPLE("Purple", 0xFF6B4A9A),
    MAGENTA("Magenta", 0xFF9A4A6E),
    RED("Red", 0xFF9A4A3A),
    ORANGE("Orange", 0xFF9A7A2A),
    GRAY("Gray", 0xFF5A5A5A);

    private final String label;
    private final int argb;

    Accent(String label, int argb) {
        this.label = label;
        this.argb = argb;
    }

    public String label() { return label; }

    public int argb() { return argb; }

    /** The name saved in hotkey-wheel.json. */
    public String savedName() { return name().toLowerCase(Locale.ROOT); }

    /** The accent a saved name stands for; blue for none or a name this version does not know. */
    public static Accent fromSaved(String name) {
        if (name == null) return BLUE;
        for (Accent accent : values()) {
            if (accent.savedName().equals(name)) return accent;
        }
        return BLUE;
    }
}
