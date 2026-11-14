package com.thenathe.hotkeywheel.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityGlideAccess {
    // Whatever the player could glide with now: a worn glider, or one another mod offers, such as Tool Pouch's.
    @Invoker("canGlide") boolean hotkeyWheel$canGlide();
}
