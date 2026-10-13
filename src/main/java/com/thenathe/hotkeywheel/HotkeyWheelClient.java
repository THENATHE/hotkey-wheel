package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.mixin.KeyMappingAccess;
import com.thenathe.hotkeywheel.mixin.ToggleKeyMappingAccess;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class HotkeyWheelClient implements ClientModInitializer {
    public static final KeyMapping[] WHEEL_KEYS = new KeyMapping[3];
    public static KeyMapping CYCLE_KEY;
    public static KeyMapping CONFIGURE_KEY;
    private static final Set<InputConstants.Key> physicalKeys = new HashSet<>();
    private static final Set<InputConstants.Key> consumedKeys = new HashSet<>();
    private static PendingAction pending;
    private static ActiveAction active;
    private static long ticks;

    private record PendingAction(WheelConfig.Entry entry, InputConstants.Key opener, boolean hold) {}
    private record ActiveAction(KeyMapping control, InputConstants.Key opener, long releaseTick, boolean hold, boolean restoreToggleDown) {}

    @Override
    public void onInitializeClient() {
        WheelConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hotkey_wheel", "wheels"));
        for (int index = 0; index < 3; index++) {
            WHEEL_KEYS[index] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hotkey_wheel.wheel" + (index + 1),
                    index == 0 ? InputConstants.KEY_G : InputConstants.UNKNOWN.getValue(), category));
        }
        CYCLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hotkey_wheel.cycle", InputConstants.KEY_TAB, category));
        CONFIGURE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hotkey_wheel.configure", InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.START_CLIENT_TICK.register(HotkeyWheelClient::tick);
        BuildprintApiIntegration.initialize();
        WheelMenuReturn.initialize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearInput();
            WheelMenuReturn.clear();
            ToolPouchIntegration.clear();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof KeyBindsScreen) {
                // Controls remains the normal searchable keybinding screen; editing wheels is separate.
                Button editor = Button.builder(Component.literal("Wheels…"), button -> client.gui.setScreen(new WheelEditorScreen(screen)))
                        .bounds(width - 86, 6, 80, 20).build();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(editor);
            }
        });
    }

    public static boolean handleInput(InputConstants.Key key, int action) {
        Minecraft client = Minecraft.getInstance();
        boolean press = action == InputConstants.PRESS;
        boolean release = action == InputConstants.RELEASE;
        if (press) physicalKeys.add(key);
        if (release) physicalKeys.remove(key);
        if (WheelMenuReturn.handleInput(key, action)) return true;
        if (release && ToolPouchIntegration.handleNativeKey(key, action)) {
            consumedKeys.remove(key);
            return true;
        }

        if (release && active != null && active.hold() && active.opener().equals(key)) releaseActive();

        if (client.gui.screen() instanceof ToolPouchRadialScreen pouch) {
            if (press && CYCLE_KEY.matches(key)) {
                pouch.cyclePage(1);
                consumedKeys.add(key);
                return true;
            }
            if (press && key.getType() == InputConstants.Type.MOUSE) {
                if (key.getValue() == InputConstants.MOUSE_BUTTON_LEFT) pouch.confirm();
                else if (key.getValue() == InputConstants.MOUSE_BUTTON_RIGHT) pouch.onClose();
                consumedKeys.add(key);
                return true;
            }
        } else if (client.gui.screen() instanceof WheelScreen wheel) {
            if (release && wheel.opener().equals(key)) {
                if (!wheel.toggleOpen()) wheel.confirm(false);
                consumedKeys.remove(key);
                return true;
            }
            if (press && CYCLE_KEY.matches(key)) {
                wheel.cycle();
                consumedKeys.add(key);
                return true;
            }
            if (press && wheel.opener().equals(key)) {
                if (wheel.toggleOpen()) wheel.confirm(false);
                return true;
            }
            for (int index = 0; index < WheelConfig.get().wheelCount; index++) {
                if (press && WHEEL_KEYS[index].matches(key)) {
                    wheel.switchWheel(index);
                    consumedKeys.add(key);
                    return true;
                }
            }
            if (press && key.getType() == InputConstants.Type.MOUSE) {
                if (key.getValue() == InputConstants.MOUSE_BUTTON_LEFT) wheel.confirm(true);
                else if (key.getValue() == InputConstants.MOUSE_BUTTON_RIGHT) wheel.onClose();
                consumedKeys.add(key);
                return true;
            }
        } else if (client.gui.screen() == null && client.player != null && client.isWindowActive()) {
            if (press && CONFIGURE_KEY.matches(key)) {
                clearActions();
                client.gui.setScreen(new WheelEditorScreen(null));
                consumedKeys.add(key);
                return true;
            }
            for (int index = 0; index < WheelConfig.get().wheelCount; index++) {
                if (press && WHEEL_KEYS[index].matches(key)) {
                    clearActions();
                    client.gui.setScreen(new WheelScreen(index, key, WheelConfig.get().wheels.get(index).toggleOpen));
                    consumedKeys.add(key);
                    return true;
                }
            }
            if (ToolPouchIntegration.handleNativeKey(key, action)) {
                consumedKeys.add(key);
                return true;
            }
        }
        if (consumedKeys.contains(key)) {
            if (release) consumedKeys.remove(key);
            return true;
        }
        return false;
    }

    public static void queueAction(WheelConfig.Entry entry, InputConstants.Key opener, boolean hold) {
        pending = new PendingAction(entry, opener, hold && physicalKeys.contains(opener));
    }

    private static void tick(Minecraft client) {
        ticks++;
        ToolPouchIntegration.tick(client);
        if (!client.isWindowActive() || client.player == null) {
            if (client.gui.screen() instanceof WheelScreen wheel) wheel.onClose();
            clearInput();
            WheelMenuReturn.clear();
            ToolPouchIntegration.clear();
            return;
        }
        if (client.gui.screen() != null) {
            clearActions();
            return;
        }
        // Escape or a replacement screen can also restore a still-held wheel opener.
        for (InputConstants.Key key : consumedKeys) KeyMapping.set(key, false);
        if (active != null && (!active.hold() && ticks >= active.releaseTick()
                || active.hold() && !physicalKeys.contains(active.opener()))) releaseActive();
        if (active != null && active.hold()) {
            // An ordinary release of the control's own key must not end the wheel's held action.
            ((KeyMappingAccess) active.control()).hotkeyWheel$setDownDirect(true);
        }
        if (pending == null) return;
        PendingAction action = pending;
        pending = null;
        KeyMapping control = findControl(action.entry().control);
        if (control == null) {
            client.player.sendSystemMessage(Component.literal("Hotkey Wheel: control is unavailable"));
            return;
        }
        releaseActive();
        if (control.getName().equals(ToolPouchIntegration.CONTROL_NAME) && ToolPouchIntegration.open(client)) return;
        WheelMenuReturn.begin(control, action.opener());
        if (BuildprintApiIntegration.activateEditor(control)) return;
        if (VanillaControlActions.activate(client, control)) return;
        KeyMappingAccess access = (KeyMappingAccess) control;
        boolean restoreToggleDown = control instanceof ToggleKeyMapping
                && ((ToggleKeyMappingAccess) control).hotkeyWheel$needsToggle().getAsBoolean() && control.isDown();
        if (action.hold()) access.hotkeyWheel$setDownDirect(true);
        else control.setDown(true);
        access.hotkeyWheel$setClicks(access.hotkeyWheel$getClicks() + 1);
        active = new ActiveAction(control, action.opener(), ticks + 1, action.hold(), restoreToggleDown);
    }

    public static KeyMapping findControl(String name) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().equals(name) && !name.startsWith("key.hotkey_wheel.")) return mapping;
        }
        return null;
    }

    private static void releaseActive() {
        if (active == null) return;
        KeyMapping control = active.control();
        boolean physicallyDown = physicalKeys.contains(InputConstants.getKey(control.saveString()));
        if (active.hold()) {
            ((KeyMappingAccess) control).hotkeyWheel$setDownDirect(active.restoreToggleDown() || physicallyDown);
        } else if (!physicallyDown) {
            control.setDown(false);
        }
        active = null;
    }

    private static void clearActions() {
        pending = null;
        releaseActive();
    }

    private static void clearInput() {
        physicalKeys.clear();
        consumedKeys.clear();
        clearActions();
    }
}
