package com.thenathe.hotkeywheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.hotkeywheel.ElytraFlight;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerGlideMixin {
    // When this returns false, aiStep neither starts the glide on the client nor sends START_FALL_FLYING.
    // Optional, so a mod that replaces this call only costs the elytra switch, never the game.
    @WrapOperation(method = "aiStep", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private boolean hotkeyWheel$holdGlide(LocalPlayer player, Operation<Boolean> original) {
        if (ElytraFlight.blocksGliding()) {
            ElytraFlight.refused(player);
            return false;
        }
        return original.call(player);
    }
}
