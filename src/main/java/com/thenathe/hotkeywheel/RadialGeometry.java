package com.thenathe.hotkeywheel;

import java.util.ArrayList;
import java.util.List;

/** The same angular boundaries are used for selection and ring rendering. */
public final class RadialGeometry {
    private static final double TURN = Math.PI * 2;

    private RadialGeometry() {}

    public static int selection(double x, double y, double innerRadius, double outerRadius, int count) {
        double distance = Math.hypot(x, y);
        if (count <= 0 || distance < innerRadius || distance > outerRadius) return -1;
        double angle = Math.atan2(y, x) + Math.PI / 2 + Math.PI / count;
        angle = (angle % TURN + TURN) % TURN;
        return Math.min(count - 1, (int) (angle * count / TURN));
    }

    // The hotbar and the hearts row above it take the bottom 39 GUI pixels; the text keeps 2 more clear of them.
    // Below a radial sit the hovered name,
    // the key line and the hint, 13 pixels apart and starting 18 below its edge; a wrapped hint adds 10 a line.
    // When space is short the title moves to the top of the screen, and the radial keeps 24 pixels below it so
    // a hovered top sector, which pops out 7, stays clear of the title.
    private static final int HUD_HEIGHT = 41;
    private static final int TITLE_ABOVE = 24;
    public static final int NAME_OFFSET = 18;
    public static final int KEY_LINE_OFFSET = 31;
    public static final int HINT_OFFSET = 44;
    public static final int HINT_LINE_HEIGHT = 10;

    public record Layout(int centerY, int radius) {
        /** Where the text under the radial ends, with this many hint lines. */
        public int textBottom(int hintLines) {
            return centerY + radius + HINT_OFFSET + HINT_LINE_HEIGHT * (hintLines - 1) + 9;
        }
    }

    /**
     * Where a radial sits on a screen of this size, with its hint wrapped to {@code hintLines} lines. Wherever the
     * text under it clears the hearts and hotbar it is placed as it always was; on shorter screens, such as GUI
     * scale 3 or 4, or a small window, it moves up and if need be shrinks, so the text ends above the hearts.
     */
    public static Layout layout(int width, int height, int hintLines) {
        int centerY = (height - 32) / 2;
        int radius = Math.max(36, Math.min(110, Math.min(width / 2 - 22, (height - 90) / 2)));
        int textBelow = HINT_OFFSET + HINT_LINE_HEIGHT * (hintLines - 1) + 9;
        int hudTop = height - HUD_HEIGHT;
        if (centerY + radius + textBelow <= hudTop) return new Layout(centerY, radius);
        // Down to 30 pixels only here, for the smallest screens such as GUI scale 4 at 1280×800.
        radius = Math.max(30, Math.min(radius, (hudTop - textBelow - TITLE_ABOVE) / 2));
        return new Layout(Math.max(radius + TITLE_ABOVE, hudTop - textBelow - radius), radius);
    }

    public static double angle(int slot, int count) { return slot * TURN / count - Math.PI / 2; }

    public record Run(int x, int y, int length, int sector) {}

    public static List<Run> ring(int innerRadius, int outerRadius, int count) {
        List<Run> runs = new ArrayList<>();
        if (count <= 0) return runs;
        for (int y = -outerRadius; y <= outerRadius; y++) {
            int start = -outerRadius;
            int previous = -1;
            for (int x = -outerRadius; x <= outerRadius + 1; x++) {
                int sector = x > outerRadius ? -1 : selection(x, y, innerRadius, outerRadius, count);
                // Leave a thin seam between radial segments.
                if (sector >= 0 && count > 1) {
                    double offset = Math.atan2(y, x) - angle(sector, count);
                    offset = Math.atan2(Math.sin(offset), Math.cos(offset));
                    if (Math.abs(offset) > Math.PI / count - 0.014) sector = -1;
                }
                if (sector != previous) {
                    if (previous >= 0) runs.add(new Run(start, y, x - start, previous));
                    start = x;
                    previous = sector;
                }
            }
        }
        return runs;
    }
}
