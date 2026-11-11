package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.ModSettingsActions;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelMenuReturn;
import com.thenathe.hotkeywheel.WheelScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** The "Open mod settings" action: picker entries, opening from a wheel and a folder, closing, and no Mod Menu. */
public final class ModSettingsTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
    private final boolean buildprint = FabricLoader.getInstance().isModLoaded("buildprint");
    private final boolean suite = FabricLoader.getInstance().isModLoaded("vpp_mod_suite");
    private final Map<String, String> opened = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static WheelConfig.Entry slot(String control, String icon) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        return entry;
    }

    /** The mods whose settings the wheel opens in this profile, in slot order. */
    private List<String> targets() {
        List<String> targets = new ArrayList<>(List.of("hotkey_wheel"));
        if (buildprint) targets.add("buildprint");
        if (suite) targets.add("vpp_mod_suite");
        return targets;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        report.put("modmenu", modMenu);
        report.put("opened", opened);
        WheelConfig[] original = {null};
        InputConstants.Key[] originalWheelKey = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                originalWheelKey[0] = InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[0].saveString());
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                for (KeyMapping mapping : client.options.keyMappings) {
                    if (!mapping.getName().startsWith("key.hotkey_wheel.")
                            && mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G))) mapping.setKey(InputConstants.UNKNOWN);
                }
                KeyMapping.resetMapping();
                choices(client);
                fixture();
            });
            if (modMenu) {
                context.runOnClient(client -> client.gui.setScreen(new ControlPickerScreen(null, control -> { })));
                context.runOnClient(client -> {
                    EditBox search = client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                            .map(EditBox.class::cast).findFirst().orElseThrow();
                    search.setValue("Mod settings");
                });
                context.waitTicks(3);
                context.takeScreenshot("mod-settings-picker");
                context.runOnClient(client -> client.gui.setScreen(null));
            }
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                if (buildprint) {
                    context.runOnClient(client -> {
                        Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                                .getConstructor().newInstance();
                        onboarding.getClass().getMethod("markDone").invoke(onboarding);
                        client.gui.setScreen(null);
                    });
                }
                List<String> targets = targets();
                for (int slot = 0; slot < targets.size(); slot++) openAndClose(context, slot, targets.get(slot), false);
                openAndClose(context, targets.size(), "hotkey_wheel", true);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel mod settings QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    WheelMenuReturn.clear();
                    if (originalWheelKey[0] != null) HotkeyWheelClient.WHEEL_KEYS[0].setKey(originalWheelKey[0]);
                    KeyMapping.resetMapping();
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "mod-settings-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    private void choices(Minecraft client) throws Exception {
        List<String> listed = ModSettingsActions.choices().stream().map(ModSettingsActions.Choice::modId).toList();
        report.put("listed", listed);
        String own = ModSettingsActions.control("hotkey_wheel");
        if (!modMenu) {
            check(listed.isEmpty(), "Without Mod Menu no mod settings are offered");
            check(!ModSettingsActions.available(own) && !HotkeyWheelClient.controlAvailable(own),
                    "Without Mod Menu a saved mod settings slot is unavailable");
            check(HotkeyWheelClient.controlLabel(own).equals("Hotkey Wheel settings (needs Mod Menu)"),
                    "Without Mod Menu the slot says it needs Mod Menu: " + HotkeyWheelClient.controlLabel(own));
            return;
        }
        for (String target : targets()) check(listed.contains(target), "Mod settings lists " + target);
        // Mod Menu itself offers Minecraft's options as Minecraft's settings, so Minecraft is listed like any mod.
        for (String without : List.of("fabricloader", "java", "fabric-api", "searchables", "controlling")) {
            if (FabricLoader.getInstance().isModLoaded(without)) {
                check(!listed.contains(without), "A mod without a settings screen is not listed: " + without);
            }
        }
        check(HotkeyWheelClient.controlLabel(own).equals("Hotkey Wheel settings") && HotkeyWheelClient.controlAvailable(own),
                "A mod settings slot shows the mod's name");
        check(HotkeyWheelClient.controlLabel(ModSettingsActions.control("not_installed_mod")).endsWith("(mod not installed)"),
                "A slot for a removed mod says so");
        String[] chosen = {null};
        ControlPickerScreen picker = new ControlPickerScreen(null, control -> chosen[0] = control);
        client.gui.setScreen(picker);
        EditBox search = picker.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("Hotkey Wheel settings");
        Button result = picker.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith("Hotkey Wheel settings · no key needed"))
                .findFirst().orElseThrow(() -> new AssertionError("The control picker lacks Hotkey Wheel's settings"));
        result.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        check(own.equals(chosen[0]), "Choosing a mod's settings saves the namespaced control " + own);
        client.gui.setScreen(null);
    }

    /** Wheel 1: each target's settings, then a folder holding Hotkey Wheel's settings again. */
    private void fixture() throws IOException {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        configuration.closeBuildprintWithWheelKey = true;
        WheelConfig.Wheel wheel = configuration.wheels.get(0);
        wheel.entries.clear();
        for (String target : targets()) wheel.entries.add(slot(ModSettingsActions.control(target), "minecraft:comparator"));
        WheelConfig.Entry folder = WheelConfig.Entry.folder("Settings");
        WheelConfig.Entry inside = slot(ModSettingsActions.control("hotkey_wheel"), "minecraft:book");
        inside.holdAction = true;
        folder.entries.add(inside);
        wheel.entries.add(folder);
        check(WheelConfig.save(configuration), "A wheel of mod settings slots saves");
        String saved = Files.readString(WheelConfig.path());
        WheelConfig.load();
        check(saved.contains("\"control\": \"modmenu:config/hotkey_wheel\"")
                        && WheelConfig.get().wheels.get(0).entries.get(0).control.equals("modmenu:config/hotkey_wheel"),
                "Mod settings slots are saved and loaded as modmenu:config/<modid>");
    }

    private static void point(ClientGameTestContext context, int slot) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(slot, screen.slotCount());
            double x = screen.centerX() + Math.cos(angle) * screen.radius() * 0.76;
            double y = screen.centerY() + Math.sin(angle) * screen.radius() * 0.76;
            return new double[] {x * client.getWindow().getScreenWidth() / screen.width,
                    y * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private void openAndClose(ClientGameTestContext context, int slot, String modId, boolean inFolder) {
        String where = modId + (inFolder ? " from a folder" : "");
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, slot);
        if (inFolder) {
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            point(context, 0);
        }
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        if (!modMenu) {
            context.runOnClient(client -> check(client.gui.screen() == null,
                    "Without Mod Menu selecting " + where + " opens nothing and does not crash"));
            return;
        }
        Screen[] screen = {null};
        context.runOnClient(client -> {
            screen[0] = client.gui.screen();
            check(screen[0] != null && !(screen[0] instanceof WheelScreen), "The wheel opens the settings of " + where);
            if (modId.equals("hotkey_wheel")) check(screen[0] instanceof WheelEditorScreen, "Hotkey Wheel's settings are its wheel editor");
            opened.put(where, screen[0].getClass().getName());
        });
        context.takeScreenshot("mod-settings-" + modId + (inFolder ? "-folder" : ""));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            Screen after = client.gui.screen();
            check(after == null, "The original opener closes the settings of " + where + " and returns to the game"
                    + (after == null ? "" : ": " + after.getClass().getName()));
        });
    }
}
