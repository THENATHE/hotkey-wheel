package com.thenathe.hotkeywheel;

import com.thenathe.hotkeywheel.mixin.LivingEntityGlideAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A client-side switch that stops the player from starting a glide. The client simply never asks the server to
 * start fall flying, so it works on any server, with anything that has the {@code glider} component: elytras,
 * Vanilla+ Features' armored chestplates and Armored Elytra's combined items alike. A glide already under way
 * goes on. Vanilla++ has its own pouch elytra toggle, so with it installed the switch is not offered and a
 * saved Off does nothing.
 */
public final class ElytraFlight {
    private static final long REMINDER_INTERVAL_MILLIS = 2000;
    private static Boolean offered;
    private static long lastReminder;

    private ElytraFlight() {}

    public static boolean offered() {
        if (offered == null) offered = !FabricLoader.getInstance().isModLoaded("vpp_mod_suite");
        return offered;
    }

    /** Whether a glide may not start now; read by the LocalPlayer mixin. */
    public static boolean blocksGliding() {
        return offered() && WheelConfig.get().elytraFlightOff();
    }

    public static void toggle(Minecraft client) {
        set(client, !WheelConfig.get().elytraFlightOff());
    }

    /** Switches gliding off ({@code off}) or back on, saves it at once and says so above the hotbar. */
    public static void set(Minecraft client, boolean off) {
        if (!offered()) {
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal("Vanilla++ is installed; use its pouch elytra toggle."));
            }
            return;
        }
        if (WheelConfig.get().elytraFlightOff() != off) WheelConfig.update(config -> config.setElytraFlightOff(off));
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(off ? "Elytra flight off" : "Elytra flight on"));
    }

    /** A jump in the air did not start a glide; remind a player who could have glided, now and then. */
    public static void refused(LocalPlayer player) {
        long now = Util.getMillis();
        if (now - lastReminder < REMINDER_INTERVAL_MILLIS || player.isFallFlying()
                || !((LivingEntityGlideAccess) player).hotkeyWheel$canGlide()) return;
        lastReminder = now;
        player.sendOverlayMessage(Component.literal("Elytra flight is off"));
    }

    public static String label() {
        return "Elytra flight · " + (WheelConfig.get().elytraFlightOff() ? "Off" : "On");
    }
}
