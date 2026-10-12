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
