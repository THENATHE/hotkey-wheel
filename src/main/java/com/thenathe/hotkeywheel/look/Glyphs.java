package com.thenathe.hotkeywheel.look;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small pixel pictures drawn with fills, one string per row where '#' is a lit pixel. */
public enum Glyphs {
    CHECK(
            "........#",
            ".......##",
            "#.....##.",
            "##...##..",
            ".##.##...",
            "..###....",
            "...#....."),
    CROSS(
            "##...##",
            ".##.##.",
            "..###..",
            ".##.##.",
            "##...##"),
    MAGNIFIER(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".####..",
            "....##.",
            ".....##"),
    SUN(
            "....#....",
            ".#.....#.",
            "...###...",
            "..#####..",
            "#.#####.#",
            "..#####..",
            "...###...",
            ".#.....#.",
            "....#...."),
    MOON(
            "...####..",
            "..###....",
            ".###.....",
            ".###.....",
            ".###.....",
            ".###.....",
            "..###....",
            "...####..");

    private final String[] rows;

    Glyphs(String... rows) {
        this.rows = rows;
    }

    public int width() { return rows[0].length(); }

    public int height() { return rows.length; }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') graphics.fill(x + column, y + row, x + column + 1, y + row + 1, colour);
            }
        }
    }

    public void drawCentred(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
        draw(graphics, x + (width - width()) / 2, y + (height - height()) / 2, colour);
    }
}
