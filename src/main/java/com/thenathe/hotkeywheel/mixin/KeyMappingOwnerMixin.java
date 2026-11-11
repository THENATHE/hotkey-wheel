package com.thenathe.hotkeywheel.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlOwners;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes which code creates each control; every KeyMapping constructor ends in this one. */
@Mixin(KeyMapping.class)
public final class KeyMappingOwnerMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V",
            at = @At("RETURN"))
    private void hotkeyWheel$noteCreator(String name, InputConstants.Type type, int key, KeyMapping.Category category,
                                         int order, CallbackInfo callback) {
        ControlOwners.created((KeyMapping) (Object) this);
    }
}
