package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Plans key changes from the editor draft; loading settings never changes Minecraft bindings. */
public final class WheelBindings {
    private WheelBindings() {}

    public record Plan(WheelConfig configuration, Map<KeyMapping, InputConstants.Key> changes) {}

    public static boolean protectedControl(KeyMapping control) {
        var options = Minecraft.getInstance().options;
        List<KeyMapping> essential = List.of(options.keyInventory, options.keyAttack, options.keyUse,
                options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                options.keyJump, options.keyShift, options.keySprint, options.keyPickItem,
                options.keyChat, options.keyCommand);
        if (essential.contains(control) || control.getName().startsWith("key.hotkey_wheel.")) return true;
        for (KeyMapping slot : options.keyHotbarSlots) {
            if (control == slot) return true;
        }
        return false;
    }

    public static boolean reservedKey(InputConstants.Key key) {
        if (key.equals(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE))) return true;
        return key.getType() == InputConstants.Type.MOUSE
                && (key.getValue() == InputConstants.MOUSE_BUTTON_LEFT || key.getValue() == InputConstants.MOUSE_BUTTON_RIGHT);
    }

    public static Plan plan(WheelConfig draft) {
        WheelConfig configuration = draft.copy();
        configuration.normalize();
        Set<KeyMapping> wanted = new LinkedHashSet<>();
        for (int index = 0; index < configuration.wheelCount; index++) {
            for (WheelConfig.Entry entry : configuration.wheels.get(index).entries) {
                KeyMapping control = HotkeyWheelClient.findControl(entry.control);
                if (entry.unbindKey && control != null && !control.getName().startsWith("key.hotkey_wheel.")
                        && (!configuration.safeMode || !protectedControl(control))) {
                    wanted.add(control);
                }
            }
        }

        Map<KeyMapping, InputConstants.Key> changes = new LinkedHashMap<>();
        for (String name : new ArrayList<>(configuration.managedBindings.keySet())) {
            KeyMapping control = HotkeyWheelClient.findControl(name);
            if (control == null || wanted.contains(control)) continue;
            String previous = configuration.managedBindings.get(name);
            if (control.isUnbound() && previous != null) {
                InputConstants.Key original = InputConstants.getKey(previous);
                if (!original.equals(InputConstants.UNKNOWN)) changes.put(control, original);
            }
            // A manual rebind wins over the saved backup; absent mods retain their backup for later.
            configuration.managedBindings.remove(name);
        }
        for (KeyMapping control : wanted) {
            if (control.isUnbound()) continue;
            configuration.managedBindings.putIfAbsent(control.getName(), control.saveString());
            changes.put(control, InputConstants.UNKNOWN);
        }
        return new Plan(configuration, changes);
    }

    public static String save(WheelConfig draft, InputConstants.Key[] wheelKeys) {
        Minecraft client = Minecraft.getInstance();
        for (int index = 0; index < wheelKeys.length; index++) {
            if (index < 3 && index >= draft.wheelCount) continue;
            if (draft.safeMode && reservedKey(wheelKeys[index])) {
                return "Safe mode reserves Escape and primary mouse buttons.";
            }
        }
        WheelConfig previousConfiguration = WheelConfig.get().copy();
        Path optionsPath = client.gameDirectory.toPath().resolve("options.txt");
        byte[] originalOptions;
        Plan plan;
        try {
            originalOptions = Files.isRegularFile(optionsPath) ? Files.readAllBytes(optionsPath) : null;
            plan = plan(draft);
        } catch (Exception failure) {
            LoggerFactory.getLogger("hotkey-wheel").error("Cannot prepare key changes", failure);
            return "Could not prepare key changes. Your draft is kept here.";
        }
        Map<KeyMapping, InputConstants.Key> changes = new LinkedHashMap<>(plan.changes());
        for (int index = 0; index < wheelKeys.length; index++) changes.put(wheelKey(index), wheelKeys[index]);
        Map<KeyMapping, InputConstants.Key> previousKeys = new LinkedHashMap<>();
        for (KeyMapping mapping : changes.keySet()) previousKeys.put(mapping, InputConstants.getKey(mapping.saveString()));

        // Persist recovery bindings before removing ordinary controls from options.txt.
        if (!WheelConfig.save(plan.configuration())) return "Could not save. Your changes are kept here; check the game log.";
        try {
            changes.forEach(KeyMapping::setKey);
            KeyMapping.resetMapping();
            client.options.save();
            List<String> saved = Files.readAllLines(optionsPath);
            for (KeyMapping mapping : changes.keySet()) {
                if (!saved.contains("key_" + mapping.getName() + ":" + mapping.saveString())) {
                    throw new IOException("Key binding was not saved: " + mapping.getName());
                }
            }
            return "";
        } catch (Exception failure) {
            previousKeys.forEach(KeyMapping::setKey);
            KeyMapping.resetMapping();
            boolean restoredConfiguration = WheelConfig.save(previousConfiguration);
            try { restoreOptions(optionsPath, originalOptions); }
            catch (IOException restoreFailure) {
                LoggerFactory.getLogger("hotkey-wheel").error("Cannot restore options.txt after a failed save", restoreFailure);
            }
            LoggerFactory.getLogger("hotkey-wheel").error("Minecraft key save failed; live key bindings restored", failure);
            return restoredConfiguration ? "Key save failed. Keys restored; your draft is kept here."
                    : "Key save failed. Keys restored; recovery bindings kept in the config.";
        }
    }

    private static KeyMapping wheelKey(int index) {
        if (index < 3) return HotkeyWheelClient.WHEEL_KEYS[index];
        return index == 3 ? HotkeyWheelClient.CYCLE_KEY : HotkeyWheelClient.CONFIGURE_KEY;
    }

    private static void restoreOptions(Path path, byte[] original) throws IOException {
        if (original == null) {
            if (Files.isRegularFile(path)) Files.delete(path);
            return;
        }
        Path temporary = Files.createTempFile(path.getParent(), ".hotkey-wheel-options-", ".txt");
        try {
            Files.write(temporary, original);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
