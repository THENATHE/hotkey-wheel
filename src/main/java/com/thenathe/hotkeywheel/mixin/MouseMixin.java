package com.thenathe.hotkeywheel.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public final class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hotkeyWheel$input(long window, MouseButtonInfo event, int action, CallbackInfo callback) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
        if (window == Minecraft.getInstance().getWindow().handle()
                && HotkeyWheelClient.handleInput(key, action)) {
            KeyMapping.set(key, false);
            callback.cancel();
        }
    }
}
