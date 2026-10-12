package com.thenathe.hotkeywheel.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccess {
    @Invoker("toggleFullscreen")
    void hotkeyWheel$toggleFullscreen();

    @Invoker("toggleFriendsScreen")
    boolean hotkeyWheel$toggleFriendsScreen();
}
