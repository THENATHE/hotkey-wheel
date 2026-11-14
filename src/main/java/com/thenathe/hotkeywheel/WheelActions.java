package com.thenathe.hotkeywheel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Hotkey Wheel's own actions, which are not key mappings: the elytra flight switch and the Vanilla+ Features
 * container actions. Slots save them as {@code hotkey_wheel:action/<name>}. An action that cannot be used here
 * keeps its slot and shows as unavailable, with the reason.
 */
public final class WheelActions {
    public static final String PREFIX = "hotkey_wheel:action/";
    public static final String ELYTRA_FLIGHT = PREFIX + "elytra_flight";
    public static final String OPEN_ENDER_CHEST = PREFIX + "open_ender_chest";
    public static final String OPEN_SHULKER_BOX = PREFIX + "open_shulker_box";

    public record Choice(String control, String name, String note) {}

    private WheelActions() {}

    public static boolean handles(String control) {
        return control.startsWith(PREFIX);
    }

    /** Why the action cannot be used now, or null when it can. */
    public static String unavailableReason(String control) {
        return switch (control) {
            case ELYTRA_FLIGHT -> ElytraFlight.offered() ? null : "Vanilla++ is installed; use its pouch elytra toggle";
            case OPEN_ENDER_CHEST -> ContainerRequests.enderChestUnavailable();
            case OPEN_SHULKER_BOX -> ContainerRequests.chooserUnavailable();
            default -> "needs a newer Hotkey Wheel";
        };
    }

    public static boolean available(String control) {
        return handles(control) && unavailableReason(control) == null;
    }

    private static String name(String control) {
        return switch (control) {
            case ELYTRA_FLIGHT -> ElytraFlight.label();
            case OPEN_ENDER_CHEST -> "Open ender chest";
            case OPEN_SHULKER_BOX -> "Open shulker box…";
            default -> control.substring(PREFIX.length());
        };
    }

    /** The name a slot shows, with a short reason while the action is unavailable. */
    public static String label(String control) {
        String reason = unavailableReason(control);
        return reason == null ? name(control) : name(control) + " (" + reason + ")";
    }

    /** The actions offered in the control picker; unavailable ones are listed too, with their reason. */
    public static List<Choice> choices() {
        List<Choice> choices = new ArrayList<>();
        // The elytra switch is left out with Vanilla++, which already has one.
        if (ElytraFlight.offered()) choices.add(new Choice(ELYTRA_FLIGHT, "Toggle elytra flight", "no key needed"));
        for (String control : List.of(OPEN_ENDER_CHEST, OPEN_SHULKER_BOX)) {
            String reason = unavailableReason(control);
            choices.add(new Choice(control, name(control), reason == null ? "no key needed" : reason));
        }
        return choices;
    }

    /** Each action says itself why it cannot run, above the hotbar. */
    public static void activate(Minecraft client, String control) {
        switch (control) {
            case ELYTRA_FLIGHT -> ElytraFlight.toggle(client);
            case OPEN_ENDER_CHEST -> ContainerRequests.openEnderChest(client);
            case OPEN_SHULKER_BOX -> ContainerRequests.chooseContainer(client);
            default -> {
                if (client.player != null) client.player.sendOverlayMessage(Component.literal(label(control) + "."));
            }
        }
    }
}
