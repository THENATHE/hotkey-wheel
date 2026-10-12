package com.thenathe.hotkeywheel.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccess {
    @Accessor("clickCount") int hotkeyWheel$getClicks();
    @Accessor("clickCount") void hotkeyWheel$setClicks(int count);
    @Accessor("isDown") void hotkeyWheel$setDownDirect(boolean down);
}
