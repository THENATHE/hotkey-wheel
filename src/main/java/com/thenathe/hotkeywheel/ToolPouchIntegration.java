package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import me.pajic.toolpouch.hud.ContextualSelectionWidget;
import me.pajic.toolpouch.keybind.ModKeybinds;
import me.pajic.toolpouch.keybind.ScrollHandler;
import me.pajic.toolpouch.network.ModPayloads;
import me.pajic.toolpouch.platform.MultiLoaderUtil;
import me.pajic.toolpouch.util.GameplayUtil;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.slf4j.LoggerFactory;

public final class ToolPouchIntegration {
    public static final String CONTROL_NAME = "key.toolpouch.open_widget";
    private static InputConstants.Key nativeOpener;
    // Set when a Tool Pouch method this integration calls is missing or fails; the integration then stays off.
    private static boolean broken;

    private ToolPouchIntegration() {}

    public enum Context { SHULKERS, AMMO }

    public record ItemChoice(int index, ItemStack stack) {}

    public record Snapshot(Context context, List<ItemChoice> choices) {
        public boolean matches(Snapshot other) {
            if (context != other.context || choices.size() != other.choices.size()) return false;
            for (int index = 0; index < choices.size(); index++) {
                ItemChoice previous = choices.get(index);
                ItemChoice current = other.choices.get(index);
                if (previous.index() != current.index() || !ItemStack.matches(previous.stack(), current.stack())) return false;
            }
            return true;
        }
    }

    public static boolean available() {
        return !broken && FabricLoader.getInstance().isModLoaded("toolpouch");
    }

    /** Switches the integration off for the session after a Tool Pouch version this release cannot call. */
    private static void stop(Throwable failure) {
        if (!broken) LoggerFactory.getLogger("hotkey-wheel").warn("Tool Pouch integration stopped; Tool Pouch keeps working on its own", failure);
        broken = true;
        nativeOpener = null;
    }

    public static boolean open(Minecraft client) {
        if (!WheelConfig.get().toolPouchWheel || !available()) return false;
        try {
            return openRadial(client);
        } catch (RuntimeException | LinkageError failure) {
            stop(failure);
            return false;
        }
    }

    private static boolean openRadial(Minecraft client) {
        if (client.player == null || client.level == null) return false;
        NativeAccess.dismissWidget();
        Snapshot snapshot = NativeAccess.snapshot(client);
        if (!NativeAccess.canSend(snapshot.context())) {
            client.player.sendSystemMessage(Component.literal("Hotkey Wheel: the server does not support this Tool Pouch action."));
            return true;
        }
        if (snapshot.choices().isEmpty()) {
            String items = snapshot.context() == Context.AMMO ? "compatible ammunition" : "shulker boxes";
            client.player.sendSystemMessage(Component.literal("Hotkey Wheel: no " + items + " are available in your active Tool Pouch."));
            return true;
        }
        client.gui.setScreen(new ToolPouchRadialScreen(snapshot));
        return true;
    }

    public static boolean handleNativeKey(InputConstants.Key key, int action) {
        try {
            return handleNativeKeyUnguarded(key, action);
        } catch (RuntimeException | LinkageError failure) {
            stop(failure);
            return false;
        }
    }

    private static boolean handleNativeKeyUnguarded(InputConstants.Key key, int action) {
        if (nativeOpener != null && nativeOpener.equals(key)) {
            if (action == InputConstants.RELEASE) {
                nativeOpener = null;
                if (available()) NativeAccess.dismissWidget();
            }
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (action != InputConstants.PRESS || !available() || !WheelConfig.get().toolPouchWheel || !WheelConfig.get().replaceToolPouchContextualKey
                || client.gui.screen() != null || client.player == null || client.level == null
                || !client.isWindowActive() || !NativeAccess.control().matches(key)) return false;
        nativeOpener = key;
        openRadial(client);
        return true;
    }

    public static boolean suppressNativeWidget() {
        return available() && (WheelConfig.get().toolPouchWheel && WheelConfig.get().replaceToolPouchContextualKey || nativeOpener != null
                || Minecraft.getInstance().gui.screen() instanceof ToolPouchRadialScreen);
    }

    public static void tick(Minecraft client) {
        if (suppressNativeWidget()) dismissWidget();
        if (client.gui.screen() instanceof ToolPouchRadialScreen radial
                && (client.player == null || client.level == null || !client.isWindowActive())) radial.onClose();
    }

    public static void clear() {
        boolean dismiss = suppressNativeWidget();
        nativeOpener = null;
        if (dismiss) dismissWidget();
    }

    private static void dismissWidget() {
        try {
            NativeAccess.dismissWidget();
        } catch (RuntimeException | LinkageError failure) {
            stop(failure);
        }
    }

    public static boolean activate(Minecraft client, Snapshot original, int index) {
        if (!available() || client.player == null || client.level == null || index < 0 || index >= original.choices().size()) return false;
        try {
            return activateUnguarded(client, original, index);
        } catch (RuntimeException | LinkageError failure) {
            stop(failure);
            return false;
        }
    }

    private static boolean activateUnguarded(Minecraft client, Snapshot original, int index) {
        Snapshot current = NativeAccess.snapshot(client);
        if (!original.matches(current)) {
            client.player.sendSystemMessage(Component.literal("Hotkey Wheel: the pouch contents or held weapon changed. Open the item wheel again."));
            return false;
        }
        if (!NativeAccess.canSend(current.context())) {
            client.player.sendSystemMessage(Component.literal("Hotkey Wheel: the server no longer supports this Tool Pouch action."));
            return false;
        }
        NativeAccess.dismissWidget();
        NativeAccess.activate(client, current.context(), current.choices().get(index).index());
        return true;
    }

    // Tool Pouch classes are resolved only after Fabric confirms the optional mod is loaded.
    private static final class NativeAccess {
        private static KeyMapping control() {
            return ModKeybinds.OPEN_WIDGET;
        }

        private static void dismissWidget() {
            ContextualSelectionWidget.widgetOpen = false;
            ModKeybinds.OPEN_WIDGET.setDown(false);
            while (ModKeybinds.OPEN_WIDGET.consumeClick()) {}
        }

        private static Snapshot snapshot(Minecraft client) {
            boolean ammo = GameplayUtil.isHoldingProjectileWeapon(client.player);
            List<ItemStackTemplate> contents = ToolPouchUtil.getItemsFromToolPouch(client.player,
                    ammo ? GameplayUtil.getSupportedAmmo(client.player) : stack -> stack.is(ItemTags.SHULKER_BOXES));
            List<ItemChoice> choices = new ArrayList<>(contents.size());
            for (int index = 0; index < contents.size(); index++) {
                ItemStack stack = contents.get(index).create();
                if (!stack.isEmpty()) choices.add(new ItemChoice(index, stack.copy()));
            }
            return new Snapshot(ammo ? Context.AMMO : Context.SHULKERS, List.copyOf(choices));
        }

        private static boolean canSend(Context context) {
            if (Minecraft.getInstance().getConnection() == null) return false;
            if (context == Context.AMMO) return ClientPlayNetworking.canSend(ModPayloads.C2SSyncArrowSlot.TYPE);
            return ClientPlayNetworking.canSend(ModPayloads.C2SSyncShulkerSlot.TYPE)
                    && ClientPlayNetworking.canSend(ModPayloads.C2SOpenShulkerBoxPayload.TYPE);
        }

        private static void activate(Minecraft client, Context context, int index) {
            if (context == Context.AMMO) {
                ScrollHandler.selectedArrowSlot = index;
                MultiLoaderUtil.INSTANCE.sendToServer(new ModPayloads.C2SSyncArrowSlot(index));
            } else {
                ScrollHandler.selectedShulkerSlot = index;
                MultiLoaderUtil.INSTANCE.sendToServer(new ModPayloads.C2SSyncShulkerSlot(index));
                MultiLoaderUtil.INSTANCE.sendToServer(new ModPayloads.C2SOpenShulkerBoxPayload(index));
                client.player.playSound(SoundEvents.SHULKER_BOX_OPEN);
            }
        }
    }
}
