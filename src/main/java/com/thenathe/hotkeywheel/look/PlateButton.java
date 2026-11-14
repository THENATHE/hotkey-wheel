package com.thenathe.hotkeywheel.look;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A button drawn in the current look. It is an ordinary vanilla Button underneath, so focus, narration and
 * keyboard presses work as they do everywhere else. A chosen button, such as the open tab, stays pressed in.
 */
public class PlateButton extends Button {
    private BooleanSupplier chosen = () -> false;

    public PlateButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static PlateButton of(String label, int x, int y, int width, OnPress onPress) {
        return new PlateButton(x, y, width, 20, Component.literal(label), onPress);
    }

    public PlateButton chosenWhen(BooleanSupplier chosen) {
        this.chosen = chosen;
        return this;
    }

    public PlateButton help(String text) {
        if (text != null && !text.isEmpty()) setTooltip(Tooltip.create(Component.literal(text)));
        return this;
    }

    protected boolean isChosen() { return chosen.getAsBoolean(); }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Look look = Look.current();
        boolean chosenNow = isChosen();
        boolean hot = active && !chosenNow && Ink.hot(this);
        look.plate(graphics, getX(), getY(), width, height, active, hot, chosenNow);
        drawLabel(graphics, look, look.plateText(active, hot, chosenNow));
    }

    protected void drawLabel(GuiGraphicsExtractor graphics, Look look, int colour) {
        Font font = Minecraft.getInstance().font;
        String text = Ink.trim(font, getMessage().getString(), width - 8);
        graphics.text(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2, colour, look.plateShadow(colour));
    }
}
