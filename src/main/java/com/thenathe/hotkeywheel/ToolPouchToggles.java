package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import me.pajic.toolpouch.keybind.SlotToggleKeys;
import me.pajic.toolpouch.network.ModPayloads;
import me.pajic.toolpouch.platform.MultiLoaderUtil;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

/**
 * Tool Pouch 1.2.0 switches single pouch slots on and off, and binds those toggles to raw keys rather than key
 * mappings. Wheel entries name a slot with {@code toolpouch:slot_toggle/<slot>}, or every elytra-like slot with
 * {@code toolpouch:slot_toggle/elytra}.
 */
public final class ToolPouchToggles {
    public static final String SLOT_PREFIX = "toolpouch:slot_toggle/";
    public static final String ELYTRA = SLOT_PREFIX + "elytra";
    // Vanilla++ 1.5.1 and earlier registered this mapping for the same job before Tool Pouch had slot toggles.
    public static final String SUITE_ELYTRA_MAPPING = "key.toolpouch.toggle_elytra";
    private static Boolean supported;

    public record Choice(String control, String name, String key) {}

    private ToolPouchToggles() {}

    public static boolean supported() {
        if (supported == null) {
            boolean present = ToolPouchIntegration.available();
            if (present) {
                try {
                    Class.forName("me.pajic.toolpouch.keybind.SlotToggleKeys", false, ToolPouchToggles.class.getClassLoader());
                } catch (ClassNotFoundException olderToolPouch) {
                    present = false;
                }
            }
            supported = present;
        }
        return supported;
    }

    /** The toggle a wheel control stands for, or null when it is an ordinary key mapping or unavailable. */
    public static String target(String control) {
        if (!supported()) return null;
        if (control.equals(SUITE_ELYTRA_MAPPING)) {
            // A slot saved with the suite's mapping keeps working once the suite stops registering it.
            return HotkeyWheelClient.findControl(control) == null ? ELYTRA : null;
        }
        if (control.equals(ELYTRA) || slot(control) >= 0) return control;
        return null;
    }

    public static boolean handles(String control) {
        return target(control) != null;
    }

    /** The pouch slot of a single-slot toggle, or -1. */
    static int slot(String control) {
        if (!control.startsWith(SLOT_PREFIX)) return -1;
        try {
            int slot = Integer.parseInt(control.substring(SLOT_PREFIX.length()));
            return slot >= 0 ? slot : -1;
        } catch (NumberFormatException notSingleSlot) {
            return -1;
        }
    }

    public static String label(String control) {
        String target = target(control);
        if (target == null) {
            if (control.startsWith(SLOT_PREFIX)) return fallbackLabel(control) + " (needs Tool Pouch 1.2)";
            return Component.translatable(control).getString();
        }
        try {
            return Native.label(Minecraft.getInstance(), target);
        } catch (RuntimeException | LinkageError failure) {
            unavailable(failure);
            return fallbackLabel(target);
        }
    }

    private static String fallbackLabel(String target) {
        return target.equals(ELYTRA) ? "Toggle pouch Elytra" : "Toggle pouch slot " + (slot(target) + 1);
    }

    /** Pouch toggles to offer in the control picker: the elytra toggle and each filled or key-bound pouch slot. */
    public static List<Choice> choices() {
        if (!supported()) return List.of();
        try {
            return Native.choices(Minecraft.getInstance());
        } catch (RuntimeException | LinkageError failure) {
            unavailable(failure);
            return List.of();
        }
    }

    /** Toggles the slots a control stands for. Returns false only when the control is not a pouch toggle. */
    public static boolean activate(Minecraft client, String control) {
        String target = target(control);
        if (target == null) return false;
        try {
            Native.toggle(client, target);
        } catch (RuntimeException | LinkageError failure) {
            unavailable(failure);
            if (client.player != null) client.player.sendSystemMessage(Component.literal("Hotkey Wheel: the Tool Pouch toggle failed; see the game log."));
        }
        return true;
    }

    /** Tool Pouch's own raw key for a single-slot toggle, or null. */
    public static InputConstants.Key boundKey(String control) {
        String target = target(control);
        if (target == null || slot(target) < 0) return null;
        try {
            return Native.boundKey(slot(target));
        } catch (RuntimeException | LinkageError failure) {
            unavailable(failure);
            return null;
        }
    }

    /** Whether the wheel can remove and restore this control's Tool Pouch key, as it does for key mappings. */
    public static boolean canUnbind(String control) {
        String target = target(control);
        return target != null && slot(target) >= 0;
    }

    /** Changes Tool Pouch's saved key for a single-slot toggle; null removes it. */
    static void bind(String control, InputConstants.Key key) {
        try {
            Native.bind(slot(target(control)), key);
        } catch (LinkageError failure) {
            throw new IllegalStateException("Tool Pouch slot toggles are unavailable", failure);
        }
    }

    private static void unavailable(Throwable failure) {
        LoggerFactory.getLogger("hotkey-wheel").warn("Tool Pouch slot toggle failed", failure);
    }

    // Tool Pouch 1.2.0 classes are resolved only after supported() finds them.
    private static final class Native {
        private static String label(Minecraft client, String target) {
            ItemStack pouch = pouch(client);
            if (target.equals(ELYTRA)) {
                IntList slots = elytraSlots(pouch);
                if (slots.isEmpty()) return "Toggle pouch Elytra";
                return "Toggle pouch Elytra · " + (anyEnabled(pouch, slots) ? "On" : "Off");
            }
            int slot = slot(target);
            ItemStack stack = pouch.isEmpty() ? ItemStack.EMPTY : ToolPouchUtil.getItemInToolPouch(pouch, slot);
            String name = stack.isEmpty() ? "pouch slot " + (slot + 1) : stack.getHoverName().getString() + " (slot " + (slot + 1) + ")";
            if (pouch.isEmpty() || slot >= ToolPouchUtil.getToolPouchSize(pouch)) return "Toggle " + name;
            return "Toggle " + name + " · " + (ToolPouchUtil.isSlotDisabled(pouch, slot) ? "Off" : "On");
        }

        private static List<Choice> choices(Minecraft client) {
            List<Choice> choices = new ArrayList<>();
            choices.add(new Choice(ELYTRA, label(client, ELYTRA), "elytra slots"));
            ItemStack pouch = pouch(client);
            // Only slots of the active pouch can be toggled, so they are the ones worth listing.
            int size = pouch.isEmpty() ? 0 : ToolPouchUtil.getToolPouchSize(pouch);
            for (int slot = 0; slot < size; slot++) {
                InputConstants.Key key = SlotToggleKeys.getKey(slot);
                if (key == null && ToolPouchUtil.getItemInToolPouch(pouch, slot).isEmpty()) continue;
                String control = SLOT_PREFIX + slot;
                choices.add(new Choice(control, label(client, control), key == null ? "Not bound" : key.getDisplayName().getString()));
            }
            return choices;
        }

        private static void toggle(Minecraft client, String target) {
            LocalPlayer player = client.player;
            if (player == null) return;
            ItemStack pouch = pouch(client);
            if (pouch.isEmpty()) {
                player.sendSystemMessage(Component.literal("Hotkey Wheel: no active Tool Pouch to toggle."));
                return;
            }
            if (!ClientPlayNetworking.canSend(ModPayloads.C2SToggleSlotPayload.TYPE)) {
                player.sendSystemMessage(Component.literal("Hotkey Wheel: the server does not support Tool Pouch slot toggles."));
                return;
            }
            IntList slots = new IntArrayList();
            if (target.equals(ELYTRA)) {
                IntList elytra = elytraSlots(pouch);
                if (elytra.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Hotkey Wheel: no elytra in your active Tool Pouch."));
                    return;
                }
                // Several gliders switch together: off while any is on, otherwise all back on.
                boolean disable = anyEnabled(pouch, elytra);
                for (int slot : elytra) {
                    if (ToolPouchUtil.isSlotDisabled(pouch, slot) != disable) slots.add(slot);
                }
            } else {
                int slot = slot(target);
                if (slot >= ToolPouchUtil.getToolPouchSize(pouch)) {
                    player.sendSystemMessage(Component.literal("Hotkey Wheel: slot " + (slot + 1) + " is not part of your active Tool Pouch."));
                    return;
                }
                slots.add(slot);
            }
            // The same client update and payload Tool Pouch sends for its own toggle keys.
            for (int slot : slots) {
                ToolPouchUtil.toggleSlot(pouch, slot);
                MultiLoaderUtil.INSTANCE.sendToServer(new ModPayloads.C2SToggleSlotPayload(slot));
            }
            if (slots.size() == 1) {
                int slot = slots.getInt(0);
                ItemStack stack = ToolPouchUtil.getItemInToolPouch(pouch, slot);
                Component name = stack.isEmpty() ? Component.translatable("text.toolpouch.slot", slot + 1) : stack.getHoverName();
                player.sendOverlayMessage(Component.translatable(ToolPouchUtil.isSlotDisabled(pouch, slot)
                        ? "text.toolpouch.slot_disabled" : "text.toolpouch.slot_enabled", name));
            } else {
                player.sendOverlayMessage(Component.translatable("text.toolpouch.slots_toggled", slots.size()));
            }
        }

        private static InputConstants.Key boundKey(int slot) {
            return SlotToggleKeys.getKey(slot);
        }

        private static void bind(int slot, InputConstants.Key key) {
            // bind() saves Tool Pouch's client config; unlike unbind() it leaves the slot's on/off state alone.
            SlotToggleKeys.bind(slot, key);
        }

        private static ItemStack pouch(Minecraft client) {
            return client.player == null ? ItemStack.EMPTY : ToolPouchUtil.getToolPouch(client.player);
        }

        private static IntList elytraSlots(ItemStack pouch) {
            IntList slots = new IntArrayList();
            if (pouch.isEmpty()) return slots;
            var items = ToolPouchUtil.readItems(pouch);
            for (int slot = 0; slot < items.size(); slot++) {
                if (items.get(slot).has(DataComponents.GLIDER)) slots.add(slot);
            }
            return slots;
        }

        private static boolean anyEnabled(ItemStack pouch, IntList slots) {
            for (int slot : slots) {
                if (!ToolPouchUtil.isSlotDisabled(pouch, slot)) return true;
            }
            return false;
        }
    }
}
