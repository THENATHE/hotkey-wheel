package com.thenathe.hotkeywheel.mixin;

import com.thenathe.hotkeywheel.ToolPouchIntegration;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Without Tool Pouch, or with a version whose widget changed, the mixin simply does not apply.
@Pseudo
@Mixin(targets = "me.pajic.toolpouch.hud.ContextualSelectionWidget", remap = false)
public abstract class ToolPouchWidgetMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hotkeyWheel$replaceWidget(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        if (ToolPouchIntegration.suppressNativeWidget()) callback.cancel();
    }
}
