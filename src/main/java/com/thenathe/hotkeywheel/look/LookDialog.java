package com.thenathe.hotkeywheel.look;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A question in a small centred panel in the current look: a bold title, the explanation, and two buttons at the
 * bottom right with the one that acts last. Escape answers no. Nothing outside the panel reacts to clicks.
 */
public final class LookDialog extends Screen {
    private static final int PAD = 10;
    private final Component message;
    private final String yesLabel;
    private final String noLabel;
    private final BooleanConsumer answer;
    private List<FormattedCharSequence> lines = List.of();
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;

    public LookDialog(String title, String message, String yesLabel, String noLabel, BooleanConsumer answer) {
        super(Component.literal(title).withStyle(ChatFormatting.BOLD));
        this.message = Component.literal(message);
        this.yesLabel = yesLabel;
        this.noLabel = noLabel;
        this.answer = answer;
    }

    @Override
    protected void init() {
        boxWidth = Math.min(320, width - 20);
        lines = font.split(message, boxWidth - 2 * PAD);
        int noWidth = Math.max(70, font.width(noLabel) + 20);
        int yesWidth = Math.max(70, font.width(yesLabel) + 20);
        boolean stacked = noWidth + yesWidth + 4 > boxWidth - 2 * PAD;
        int buttonsHeight = stacked ? 44 : 20;
        boxHeight = PAD + 12 + lines.size() * 10 + 10 + buttonsHeight + PAD;
        boxX = (width - boxWidth) / 2;
        boxY = Math.max(4, (height - boxHeight) / 2);
        int buttonsY = boxY + boxHeight - PAD - buttonsHeight;
        PlateButton no = PlateButton.of(noLabel, 0, 0, noWidth, button -> answer.accept(false));
        PlateButton yes = PlateButton.of(yesLabel, 0, 0, yesWidth, button -> answer.accept(true));
        if (stacked) {
            no.setRectangle(boxWidth - 2 * PAD, 20, boxX + PAD, buttonsY);
            yes.setRectangle(boxWidth - 2 * PAD, 20, boxX + PAD, buttonsY + 24);
        } else {
            int right = boxX + boxWidth - PAD;
            yes.setPosition(right - yesWidth, buttonsY);
            no.setPosition(right - yesWidth - 4 - noWidth, buttonsY);
        }
        addRenderableWidget(no);
        addRenderableWidget(yes);
        setInitialFocus(no);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Look look = Look.current();
        if (!look.house() && minecraft.level != null) extractTransparentBackground(graphics);
        else super.extractBackground(graphics, mouseX, mouseY, delta);
        look.panel(graphics, boxX, boxY, boxWidth, boxHeight);
        graphics.text(font, title, boxX + PAD, boxY + PAD, look.strong(), look.panelShadow());
        int y = boxY + PAD + 14;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, boxX + PAD, y, look.text(), look.panelShadow());
            y += 10;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            answer.accept(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
