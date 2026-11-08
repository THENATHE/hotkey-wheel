package com.thenathe.hotkeywheel.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public final class KeyboardMixin {
    // Buildprint's order-1000 editor handler consumes reserved keys such as C, Q and E.
    // Only the original-opener closure runs first; other wheel keys keep their ordinary order.
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, order = 900)
    private void hotkeyWheel$menuReturn(long window, int action, KeyEvent event, CallbackInfo callback) {
        InputConstants.Key key = InputConstants.getKey(event);
        if (window == Minecraft.getInstance().getWindow().handle()
                && HotkeyWheelClient.handleMenuReturn(key, action)) {
            KeyMapping.set(key, false);
            callback.cancel();
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void hotkeyWheel$input(long window, int action, KeyEvent event, CallbackInfo callback) {
        InputConstants.Key key = InputConstants.getKey(event);
        if (window == Minecraft.getInstance().getWindow().handle()
                && HotkeyWheelClient.handleInput(key, action)) {
            // Closing a screen can restore physically held keys before this callback returns.
            KeyMapping.set(key, false);
            callback.cancel();
        }
    }
}
