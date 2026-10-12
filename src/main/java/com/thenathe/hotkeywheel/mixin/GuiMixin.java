package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.RadialCursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("RETURN"))
    private void hotkeyWheel$centerAfterMouseSync(Screen screen, CallbackInfo callback) {
        // Minecraft polls the desktop cursor after initializing a screen.
        if (Minecraft.getInstance().gui.screen() == screen) RadialCursor.center(screen);
    }
}
