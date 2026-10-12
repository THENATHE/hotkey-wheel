package com.thenathe.hotkeywheel.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccess {
    @Accessor("needsToggle") BooleanSupplier hotkeyWheel$needsToggle();
}
