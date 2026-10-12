package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class RadialCursor {
    private RadialCursor() {}

    public static void center(Screen screen) {
        int centerX;
        int centerY;
        if (screen instanceof WheelScreen wheel) {
            centerX = wheel.centerX();
            centerY = wheel.centerY();
        } else if (screen instanceof ToolPouchRadialScreen pouch) {
            centerX = pouch.centerX();
            centerY = pouch.centerY();
        } else {
            return;
        }
        if (screen.width <= 0 || screen.height <= 0) return;
        Minecraft client = Minecraft.getInstance();
        var window = client.getWindow();
        double x = centerX * (double) window.getScreenWidth() / screen.width;
        double y = centerY * (double) window.getScreenHeight() / screen.height;
        InputConstants.releaseMouse(window, x, y);
        client.mouseHandler.onMove(window.handle(), x, y, 0, 0);
    }
}
