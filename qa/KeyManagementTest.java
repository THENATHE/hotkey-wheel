package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Tests editor commits and recovery against the real client and options.txt. */
public final class KeyManagementTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private static final InputConstants.Key[] WHEEL_KEYS = {
            key(InputConstants.KEY_G), InputConstants.UNKNOWN, InputConstants.UNKNOWN,
            key(InputConstants.KEY_TAB), InputConstants.UNKNOWN};

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static InputConstants.Key key(int code) {
        return InputConstants.Type.KEYBOARD.getOrCreate(code);
    }

    private static String json(WheelConfig configuration) {
        return new GsonBuilder().create().toJson(configuration);
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow();
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private void save(WheelConfig configuration, String description) {
        String error = WheelBindings.save(configuration, WHEEL_KEYS.clone());
        check(error.isEmpty(), description + (error.isEmpty() ? "" : ": " + error));
    }

    private static WheelConfig.Entry entry(KeyMapping control, boolean unbind) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control.getName();
        entry.unbindKey = unbind;
        return entry;
    }

    private static WheelConfig empty() {
        WheelConfig configuration = new WheelConfig();
        for (WheelConfig.Wheel wheel : configuration.wheels) wheel.entries.clear();
        return configuration;
    }

    private static List<KeyMapping> essentials(Minecraft client) {
        var options = client.options;
        List<KeyMapping> controls = new ArrayList<>(List.of(options.keyInventory, options.keyAttack,
                options.keyUse, options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                options.keyJump, options.keyShift, options.keySprint, options.keyPickItem,
                options.keyChat, options.keyCommand));
        controls.addAll(Arrays.asList(options.keyHotbarSlots));
        return controls;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", false);
        report.put("checks", checks);
        ClientState[] original = {null};
        try {
            context.runOnClient(client -> {
                try {
                    original[0] = new ClientState(client);
                    editorAndBindings(client);
                    failures(client);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            activation(context);
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel key-management QA", failure);
        } finally {
            try {
                if (original[0] != null) {
                    context.runOnClient(client -> {
                        try { original[0].restore(client); }
                        catch (IOException failure) { throw new RuntimeException(failure); }
                    });
                }
            } catch (Throwable failure) {
                report.put("passed", false);
                report.put("cleanupFailure", failure.toString());
                throw failure;
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "key-management-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    private void editorAndBindings(Minecraft client) throws IOException {
        KeyMapping perspective = client.options.keyTogglePerspective;
        InputConstants.Key original = perspective.getDefaultKey();
        essentials(client).forEach(control -> control.setKey(control.getDefaultKey()));
        perspective.setKey(original);
        KeyMapping.resetMapping();
        WheelConfig configuration = empty();
        configuration.wheels.get(0).entries.add(entry(perspective, false));
        check(configuration.safeMode && !configuration.wheels.get(0).entries.get(0).unbindKey,
                "Safe mode defaults ON and per-slot unbinding defaults OFF");
        save(configuration, "Ordinary save leaves a selected control's normal key enabled");
        check(perspective.matches(original), "Selecting a control without opt-in retains its normal key");

        String before = json(WheelConfig.get());
        client.gui.setScreen(new WheelEditorScreen(null));
        press(button(client.gui.screen(), "Unbind normal key:"));
        check(perspective.matches(original), "Editor opt-in does not unbind before Save");
        press(button(client.gui.screen(), "Cancel"));
        check(perspective.matches(original) && json(WheelConfig.get()).equals(before),
                "Cancel discards unbinding changes and preserves committed settings");

        client.gui.setScreen(new WheelEditorScreen(null));
        press(button(client.gui.screen(), "Unbind normal key:"));
        press(button(client.gui.screen(), "Save"));
        check(perspective.isUnbound(), "Editor Save removes an opted-in control's ordinary key");
        check(WheelConfig.get().managedBindings.get(perspective.getName()).equals(original.getName()),
                "Editor Save remembers the original ordinary key");
        check(Files.readAllLines(client.options.getFile().toPath())
                        .contains("key_" + perspective.getName() + ":" + InputConstants.UNKNOWN.getName()),
                "The unbound control is persisted in Minecraft options.txt");
        WheelConfig.load();
        check(WheelConfig.get().wheels.get(0).entries.get(0).unbindKey
                        && WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                "Opt-in and recovery binding survive configuration reload");
        client.gui.setScreen(new WheelEditorScreen(null));
        press(button(client.gui.screen(), "Restore keys unbound"));
        check(perspective.isUnbound(), "Restore request leaves live bindings unchanged before Save");
        press(button(client.gui.screen(), "Cancel"));
        check(perspective.isUnbound() && WheelConfig.get().wheels.get(0).entries.get(0).unbindKey,
                "Cancel discards the bulk restore request");
        client.gui.setScreen(new WheelEditorScreen(null));
        press(button(client.gui.screen(), "Restore keys unbound"));
        press(button(client.gui.screen(), "Save"));
        check(perspective.matches(original) && WheelConfig.get().managedBindings.isEmpty()
                        && !WheelConfig.get().wheels.get(0).entries.get(0).unbindKey,
                "Bulk restore Save restores the original key and clears slot unbinding");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.get(0).unbindKey = true;
        save(configuration, "Re-enable unbinding before testing a later manual rebind");
        perspective.setKey(key(InputConstants.KEY_Q));
        KeyMapping.resetMapping();
        WheelConfig.load();
        check(perspective.matches(key(InputConstants.KEY_Q)), "Loading settings never reapplies automatic unbinding");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.get(0).unbindKey = false;
        save(configuration, "Manual rebinding can be retained while turning automatic unbinding off");
        check(perspective.matches(key(InputConstants.KEY_Q))
                        && !WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                "Restoration preserves a newer manual binding and clears its managed backup");

        perspective.setKey(original);
        KeyMapping.resetMapping();
        configuration = empty();
        for (KeyMapping control : essentials(client)) configuration.wheels.get(0).entries.add(entry(control, true));
        // The editor allows twelve slots per wheel, so spread all protected controls across two enabled wheels.
        List<WheelConfig.Entry> protectedEntries = new ArrayList<>(configuration.wheels.get(0).entries);
        configuration.wheelCount = 2;
        configuration.wheels.get(0).entries = new ArrayList<>(protectedEntries.subList(0, 12));
        configuration.wheels.get(1).entries = new ArrayList<>(protectedEntries.subList(12, protectedEntries.size()));
        save(configuration, "Safe mode accepts wheels containing protected control entries");
        check(essentials(client).size() == 22, "Protected control test covers thirteen core controls and nine hotbar slots");
        for (KeyMapping control : essentials(client)) {
            check(control.matches(control.getDefaultKey())
                            && !WheelConfig.get().managedBindings.containsKey(control.getName()),
                    "Safe mode preserves " + control.getName());
        }
        client.gui.setScreen(new WheelEditorScreen(null));
        check(!button(client.gui.screen(), "Unbind normal key: Protected").active,
                "Protected control's automatic-unbinding editor button is disabled");
        press(button(client.gui.screen(), "Cancel"));

        configuration = empty();
        configuration.wheels.get(0).entries.add(entry(perspective, true));
        perspective.setKey(client.options.keyInventory.getDefaultKey());
        KeyMapping.resetMapping();
        save(configuration, "A permitted control can be unbound while sharing an essential control's physical key");
        check(perspective.isUnbound() && client.options.keyInventory.matches(client.options.keyInventory.getDefaultKey()),
                "Unbinding an individual control leaves other controls sharing its key enabled");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.get(0).unbindKey = false;
        save(configuration, "Turning slot unbinding off restores its normal key");
        check(perspective.matches(client.options.keyInventory.getDefaultKey()),
                "Flag OFF restores the remembered custom key rather than the default");

        perspective.setKey(original);
        KeyMapping.resetMapping();
        configuration = empty();
        configuration.wheelCount = 2;
        configuration.wheels.get(0).entries.add(entry(perspective, true));
        configuration.wheels.get(1).entries.add(entry(perspective, true));
        save(configuration, "Duplicate selected controls across two wheels unbind once");
        check(WheelConfig.get().managedBindings.size() == 1, "Duplicate slots retain a single recovery binding");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.clear();
        save(configuration, "Removing one duplicate keeps the remaining enabled wheel's ownership");
        check(perspective.isUnbound(), "A duplicate still present on another enabled wheel remains unbound");
        configuration = WheelConfig.get().copy();
        configuration.wheelCount = 1;
        save(configuration, "Disabling the last owning wheel restores its control");
        check(perspective.matches(original), "Disabled wheel no longer owns the automatic unbinding");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.add(entry(perspective, true));
        save(configuration, "Reintroduced slot can opt into automatic unbinding");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.clear();
        save(configuration, "Removing the last opted-in slot restores its control");
        check(perspective.matches(original), "Slot removal restores the remembered binding");

        configuration = empty();
        configuration.safeMode = false;
        configuration.wheels.get(0).entries.add(entry(client.options.keyInventory, true));
        save(configuration, "Safe mode OFF permits explicit inventory-key unbinding");
        check(client.options.keyInventory.isUnbound(), "Unsafe opt-in can remove Inventory's ordinary key");
        configuration = WheelConfig.get().copy();
        configuration.safeMode = true;
        save(configuration, "Re-enabling safe mode restores an essential binding previously removed by this mod");
        check(client.options.keyInventory.matches(client.options.keyInventory.getDefaultKey()),
                "Safe mode ON restores Inventory while preserving its wheel assignment");

        configuration = empty();
        configuration.safeMode = false;
        configuration.wheels.get(0).entries.add(entry(HotkeyWheelClient.WHEEL_KEYS[0], true));
        configuration.wheels.get(0).entries.add(entry(HotkeyWheelClient.CYCLE_KEY, true));
        save(configuration, "Wheel opener and cycle controls resist self-unbinding even with safe mode OFF");
        check(HotkeyWheelClient.WHEEL_KEYS[0].matches(WHEEL_KEYS[0])
                        && HotkeyWheelClient.CYCLE_KEY.matches(WHEEL_KEYS[3])
                        && WheelConfig.get().managedBindings.isEmpty(),
                "Hotkey Wheel's own mappings never create automatic-unbinding ownership");

        configuration = empty();
        save(configuration, "Reset safe-mode fixture before reserved-key checks");
        before = json(WheelConfig.get());
        for (InputConstants.Key reserved : List.of(key(InputConstants.KEY_ESCAPE),
                InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT),
                InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT))) {
            InputConstants.Key[] invalidKeys = WHEEL_KEYS.clone();
            invalidKeys[0] = reserved;
            check(!WheelBindings.save(configuration, invalidKeys).isEmpty()
                            && HotkeyWheelClient.WHEEL_KEYS[0].matches(WHEEL_KEYS[0])
                            && json(WheelConfig.get()).equals(before),
                    "Safe mode rejects wheel opener " + reserved.getName() + " without modifying keys or settings");
        }
    }

    private void failures(Minecraft client) throws IOException {
        KeyMapping perspective = client.options.keyTogglePerspective;
        perspective.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        WheelConfig configuration = empty();
        save(configuration, "Set up invalid recovery-key test without managed bindings");
        String before = json(WheelConfig.get());
        configuration.managedBindings.put(perspective.getName(), "not-a-minecraft-key");
        check(!WheelBindings.save(configuration, WHEEL_KEYS.clone()).isEmpty()
                        && perspective.isUnbound() && json(WheelConfig.get()).equals(before),
                "Malformed recovery key rejects Save without live or committed configuration changes");

        perspective.setKey(perspective.getDefaultKey());
        KeyMapping.resetMapping();
        save(empty(), "Reset binding and configuration before filesystem failures");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.add(entry(perspective, true));
        before = json(WheelConfig.get());
        byte[] optionsBefore = Files.readAllBytes(client.options.getFile().toPath());
        Path configPath = WheelConfig.path();
        Path configOriginal = configPath.resolveSibling(".key-management-config-original");
        Files.move(configPath, configOriginal);
        Files.createDirectory(configPath);
        Path marker = configPath.resolve("keep-this-file");
        Files.writeString(marker, "configuration blocker");
        try {
            check(!WheelBindings.save(configuration, WHEEL_KEYS.clone()).isEmpty(),
                    "Blocked configuration destination reports Save failure");
            check(perspective.matches(perspective.getDefaultKey()) && json(WheelConfig.get()).equals(before)
                            && Arrays.equals(optionsBefore, Files.readAllBytes(client.options.getFile().toPath()))
                            && Files.readString(marker).equals("configuration blocker"),
                    "Configuration failure preserves live keys, prior options, prior settings, and the blocker");
        } finally {
            Files.deleteIfExists(marker);
            Files.delete(configPath);
            Files.move(configOriginal, configPath);
        }

        Path optionsPath = client.options.getFile().toPath();
        Path optionsOriginal = optionsPath.resolveSibling(".key-management-options-original");
        Files.move(optionsPath, optionsOriginal);
        Files.createDirectory(optionsPath);
        marker = optionsPath.resolve("keep-this-file");
        Files.writeString(marker, "options blocker");
        try {
            check(!WheelBindings.save(configuration, WHEEL_KEYS.clone()).isEmpty(),
                    "Blocked options destination reports Save failure after configuration preparation");
            check(perspective.matches(perspective.getDefaultKey()) && json(WheelConfig.get()).equals(before)
                            && Files.readString(marker).equals("options blocker"),
                    "Options failure rolls back live bindings and committed settings while preserving the blocker");
        } finally {
            Files.deleteIfExists(marker);
            Files.delete(optionsPath);
            Files.move(optionsOriginal, optionsPath);
        }
        save(configuration, "Retry succeeds after the options blocker is removed");
        check(perspective.isUnbound()
                        && WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                "Retry retains the requested unbinding and its recovery binding");
    }

    private void activation(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.waitTicks(10);
            var cameraBefore = context.computeOnClient(client -> {
                client.gui.setScreen(null);
                client.options.pauseOnLostFocus = false;
                WheelConfig configuration = WheelConfig.get().copy();
                configuration.wheelCount = 1;
                configuration.wheels.get(0).entries.clear();
                configuration.wheels.get(0).entries.add(entry(client.options.keyTogglePerspective, true));
                save(configuration, "Save a usable wheel containing the automatically unbound Perspective control");
                check(client.options.keyTogglePerspective.isUnbound(), "Perspective stays unbound before gameplay activation");
                return client.options.getCameraType();
            });
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            double[] cursor = context.computeOnClient(client -> {
                check(client.gui.screen() instanceof WheelScreen, "The real wheel opener opens the saved wheel");
                WheelScreen screen = (WheelScreen) client.gui.screen();
                return new double[] {screen.centerX() * client.getWindow().getScreenWidth() / screen.width,
                        (screen.centerY() - screen.radius() * 0.75) * client.getWindow().getScreenHeight() / screen.height};
            });
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(2);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(4);
            context.runOnClient(client -> check(client.gui.screen() == null
                            && client.options.getCameraType() != cameraBefore
                            && client.options.keyTogglePerspective.isUnbound(),
                    "Wheel selection activates Perspective despite its persisted ordinary binding being unbound"));
        }
    }

    private static final class ClientState {
        private final Map<KeyMapping, InputConstants.Key> keys = new LinkedHashMap<>();
        private final WheelConfig configuration;
        private final Path optionsPath;
        private final byte[] optionsBytes;
        private final byte[] configBytes;
        private final boolean pauseOnLostFocus;
        private final net.minecraft.client.CameraType camera;

        private ClientState(Minecraft client) throws IOException {
            for (KeyMapping mapping : client.options.keyMappings) keys.put(mapping, InputConstants.getKey(mapping.saveString()));
            configuration = WheelConfig.get().copy();
            optionsPath = client.options.getFile().toPath();
            optionsBytes = Files.isRegularFile(optionsPath) ? Files.readAllBytes(optionsPath) : null;
            configBytes = Files.isRegularFile(WheelConfig.path()) ? Files.readAllBytes(WheelConfig.path()) : null;
            pauseOnLostFocus = client.options.pauseOnLostFocus;
            camera = client.options.getCameraType();
        }

        private void restore(Minecraft client) throws IOException {
            client.gui.setScreen(null);
            keys.forEach(KeyMapping::setKey);
            KeyMapping.resetMapping();
            client.options.pauseOnLostFocus = pauseOnLostFocus;
            client.options.setCameraType(camera);
            if (!WheelConfig.save(configuration)) throw new IOException("Cannot restore prior QA configuration");
            restoreFile(WheelConfig.path(), configBytes);
            restoreFile(optionsPath, optionsBytes);
        }

        private static void restoreFile(Path path, byte[] bytes) throws IOException {
            if (bytes == null) {
                Files.deleteIfExists(path);
                return;
            }
            Path temporary = Files.createTempFile(path.getParent(), ".key-management-restore-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
    }
}
