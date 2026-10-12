package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.RadialCursor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftCursorMixin {
    @Inject(method = "resizeGui", at = @At("RETURN"))
    private void hotkeyWheel$centerAfterResize(CallbackInfo callback) {
        RadialCursor.center(Minecraft.getInstance().gui.screen());
    }
}
