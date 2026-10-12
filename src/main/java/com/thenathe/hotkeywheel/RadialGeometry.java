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
