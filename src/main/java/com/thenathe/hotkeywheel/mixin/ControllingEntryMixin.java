package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.ControlsMarker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Controlling replaces the key list with rows of its own. Without it, or if its row changes, there is simply no marker.
@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.NewKeyBindsList$KeyEntry", remap = false)
public abstract class ControllingEntryMixin {
    @Inject(method = "extractContent", at = @At("TAIL"), require = 0)
    private void hotkeyWheel$markWheelControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                                              float partialTick, CallbackInfo callback) {
        ControlsMarker.drawControllingRow(this, graphics, mouseX, mouseY, hovered);
    }
}
