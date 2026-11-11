package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.ControlsMarker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsEntryMixin {
    @Shadow @Final private KeyMapping key;
    @Shadow @Final private Button changeButton;

    // After the row has placed its buttons for this frame, so the marker follows the key button.
    @Inject(method = "extractContent", at = @At("TAIL"))
    private void hotkeyWheel$markWheelControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                                              float partialTick, CallbackInfo callback) {
        ControlsMarker.draw(graphics, key, changeButton, mouseX, mouseY, hovered);
    }
}
