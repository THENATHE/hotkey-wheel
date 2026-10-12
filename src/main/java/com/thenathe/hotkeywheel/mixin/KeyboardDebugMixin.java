package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.VanillaControlActions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public final class KeyboardDebugMixin {
    @Redirect(method = "handleDebugKeys", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean hotkeyWheel$matchSelectedControl(KeyMapping control, KeyEvent event) {
        return VanillaControlActions.matchesDebugControl(control, event);
    }
}
