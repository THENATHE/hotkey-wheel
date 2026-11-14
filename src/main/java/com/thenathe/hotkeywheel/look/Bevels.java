package com.thenathe.hotkeywheel.look;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The House look's boxes: a black outline around a flat fill, with one-pixel lines inside the outline that
 * catch the light from the top left.
 */
public final class Bevels {
    private Bevels() {}

    /** A raised surface in the mode's face colour. */
    public static void raised(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int width, int height) {
        raisedWith(graphics, x, y, width, height, colors.face(), colors.faceHigh(), colors.faceLow(), colors.faceShadow());
    }

    /** A raised surface in any colour, such as the accent; its edge tones come from that colour. */
    public static void raised(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int face) {
        raisedWith(graphics, x, y, width, height, face, Palette.shade(face, 0.32F), Palette.shade(face, 0.16F), Palette.shade(face, -0.55F));
    }

    private static void raisedWith(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int face, int lit, int shaded, int shadow) {
        outlineAndFill(graphics, x, y, width, height, face);
        int innerRight = x + width - 1;
        int innerBottom = y + height - 1;
        graphics.fill(x + 1, y + 1, innerRight, y + 2, lit);
        graphics.fill(x + 1, y + 1, x + 2, innerBottom - 1, lit);
        if (width >= 4) graphics.fill(innerRight - 1, y + 2, innerRight, innerBottom - 1, shaded);
        if (height >= 6) {
            graphics.fill(x + 2, innerBottom - 2, innerRight, innerBottom - 1, shaded);
            graphics.fill(x + 1, innerBottom - 1, innerRight, innerBottom, shadow);
        }
    }

    /** A well pressed into the surface: fields, tracks and disabled buttons. */
    public static void sunken(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int width, int height) {
        sunken(graphics, colors, x, y, width, height, HouseColors.OUTLINE);
    }

    public static void sunken(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int width, int height, int edge) {
        graphics.fill(x, y, x + width, y + height, edge);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, colors.well());
        litCorner(graphics, x, y, width, height, colors.wellEdge());
    }

    public static void panel(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int width, int height) {
        outlineAndFill(graphics, x, y, width, height, colors.face());
        litCorner(graphics, x, y, width, height, colors.faceHigh());
    }

    public static void group(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int width, int height) {
        outlineAndFill(graphics, x, y, width, height, colors.group());
        litCorner(graphics, x, y, width, height, colors.groupEdge());
    }

    /** A one-pixel ring, for focus and chosen marks. */
    public static void ring(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y + 1, x + 1, y + height - 1, colour);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
    }

    private static void outlineAndFill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, HouseColors.OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private static void litCorner(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, colour);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, colour);
    }
}
