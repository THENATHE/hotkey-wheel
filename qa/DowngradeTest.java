package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Settings saved by 1.1.0, with folders, loaded by a release without folders. Run it against the 1.0.11 JAR; it only
 * uses API that 1.0.11 already has.
 */
public final class DowngradeTest implements FabricClientGameTest {
    // FolderWheelTest checks that 1.1.0 loads this file and saves it back unchanged.
    private static final String RELEASE_1_1_0_CONFIG = """
            {
              "wheelCount": 1,
              "safeMode": true,
              "toolPouchWheel": true,
              "replaceToolPouchContextualKey": false,
              "closeBuildprintWithWheelKey": true,
              "managedBindings": {
                "key.togglePerspective": "key.keyboard.f5"
              },
              "wheels": [
                {
                  "name": "Wheel 1",
                  "toggleOpen": false,
                  "selectOutside": false,
                  "entries": [
                    {
                      "control": "",
                      "icon": "minecraft:iron_pickaxe",
                      "label": "Tools",
                      "holdAction": false,
                      "unbindKey": false,
                      "entries": [
                        {"control": "key.togglePerspective", "icon": "minecraft:spyglass", "label": "Camera", "holdAction": false, "unbindKey": true},
                        {"control": "", "icon": "minecraft:chest", "label": "Deep", "holdAction": false, "unbindKey": false, "entries": [
                          {"control": "key.inventory", "icon": "minecraft:chest", "label": "", "holdAction": false, "unbindKey": false}
                        ]}
                      ]
                    },
                    {"control": "key.inventory", "icon": "minecraft:chest", "label": "", "holdAction": false, "unbindKey": false},
                    {"control": "key.drop", "icon": "minecraft:dropper", "label": "", "holdAction": false, "unbindKey": false}
                  ]
                },
                {"name": "Wheel 2", "toggleOpen": false, "selectOutside": false, "entries": []},
                {"name": "Wheel 3", "toggleOpen": false, "selectOutside": false, "entries": []}
              ]
            }
            """;
    private final List<String> checks = new ArrayList<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static void point(ClientGameTestContext context, int slot) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            int count = WheelConfig.get().wheels.get(screen.wheelIndex()).entries.size();
            double angle = RadialGeometry.angle(slot, count);
            return new double[] {(screen.centerX() + Math.cos(angle) * screen.radius() * 0.76) * client.getWindow().getScreenWidth() / screen.width,
                    (screen.centerY() + Math.sin(angle) * screen.radius() * 0.76) * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checks", checks);
        try {
            report.put("wheel_version", FabricLoader.getInstance().getModContainer("hotkey_wheel").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString());
            Path backup = WheelConfig.path().resolveSibling("hotkey-wheel.json.unreadable");
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                KeyMapping perspective = client.options.keyTogglePerspective;
                // As 1.1.0 leaves it: the folder slot's Unbind normal key removed F5 and remembered it.
                perspective.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                client.options.save();
                Files.deleteIfExists(backup);
                Files.writeString(WheelConfig.path(), RELEASE_1_1_0_CONFIG);
                WheelConfig.load();
                check(!Files.exists(backup), "The older release reads a 1.1.0 file with folders without calling it unreadable");
                check(Files.readString(WheelConfig.path()).equals(RELEASE_1_1_0_CONFIG), "Loading leaves the 1.1.0 file on disk untouched");
                List<WheelConfig.Entry> slots = WheelConfig.get().wheels.get(0).entries;
                check(slots.size() == 3 && slots.get(0).label.equals("Tools") && slots.get(0).control.isEmpty()
                                && slots.get(0).icon.equals("minecraft:iron_pickaxe"),
                        "A folder appears as an unassigned slot with the folder's name and icon");
                check(slots.get(1).control.equals("key.inventory") && slots.get(2).control.equals("key.drop"),
                        "Top-level slots after the folder load unchanged");
                check(perspective.isUnbound() && WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                        "Loading keeps the key the folder slot removed, and its remembered copy");
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(10);
                var camera = context.computeOnClient(client -> client.options.getCameraType());
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "The wheel opens"));
                point(context, 0);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.runOnClient(client -> check(client.gui.screen() == null && client.options.getCameraType() == camera,
                        "Selecting the folder's slot closes the wheel and does nothing"));
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                point(context, 1);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof InventoryScreen, "Ordinary top-level slots still work");
                    client.gui.setScreen(null);
                });
            }
            context.runOnClient(client -> {
                InputConstants.Key[] keys = new InputConstants.Key[5];
                for (int index = 0; index < 3; index++) keys[index] = InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[index].saveString());
                keys[3] = InputConstants.getKey(HotkeyWheelClient.CYCLE_KEY.saveString());
                keys[4] = InputConstants.getKey(HotkeyWheelClient.CONFIGURE_KEY.saveString());
                String error = WheelBindings.save(WheelConfig.get(), keys);
                check(error.isEmpty(), "Saving in the older editor succeeds" + (error.isEmpty() ? "" : ": " + error));
                JsonObject saved = JsonParser.parseString(Files.readString(WheelConfig.path())).getAsJsonObject();
                report.put("saved_after_downgrade", saved);
                JsonArray top = saved.getAsJsonArray("wheels").get(0).getAsJsonObject().getAsJsonArray("entries");
                boolean folderKept = false;
                for (JsonElement slot : top) folderKept |= slot.getAsJsonObject().has("entries");
                check(!folderKept && top.size() == 3 && top.get(0).getAsJsonObject().get("label").getAsString().equals("Tools"),
                        "A Save in the older release keeps the folder's slot but drops the slots inside it");
                check(client.options.keyTogglePerspective.matches(client.options.keyTogglePerspective.getDefaultKey())
                                && WheelConfig.get().managedBindings.isEmpty(),
                        "That Save gives back the keys only folder slots had removed, so no key is lost");
                client.options.keyTogglePerspective.setKey(client.options.keyTogglePerspective.getDefaultKey());
                KeyMapping.resetMapping();
            });
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel downgrade QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "downgrade-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (IOException failure) { throw new RuntimeException(failure); }
        }
    }
}
