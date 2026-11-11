package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.thenathe.hotkeywheel.WheelConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Run with an earlier release's JAR: has that release write a settings file of its own, for UpgradeTest to load in
 * this one. Uses only API that 1.0.11 and 1.1.1 both have.
 */
public final class UpgradeSourceTest implements FabricClientGameTest {
    // Every 1.0.11 setting: two wheels, Toggle and Select by direction, a label, Hold, a pouch slot toggle,
    // Unbind normal key with its remembered key, and a Configure key left unbound.
    static final String RELEASE_1_0_11 = """
            {
              "wheelCount": 2,
              "safeMode": false,
              "toolPouchWheel": false,
              "replaceToolPouchContextualKey": true,
              "closeBuildprintWithWheelKey": false,
              "managedBindings": {"key.togglePerspective": "key.keyboard.f5"},
              "wheels": [
                {"name": "Builder", "toggleOpen": false, "selectOutside": true, "entries": [
                  {"control": "key.togglePerspective", "icon": "minecraft:spyglass", "label": "Camera", "holdAction": false, "unbindKey": true},
                  {"control": "key.playerlist", "icon": "minecraft:player_head", "label": "", "holdAction": true, "unbindKey": false},
                  {"control": "toolpouch:slot_toggle/3", "icon": "minecraft:elytra", "label": "Wings", "holdAction": false, "unbindKey": false}
                ]},
                {"name": "Second", "toggleOpen": true, "selectOutside": false, "entries": [
                  {"control": "key.inventory", "icon": "minecraft:chest", "label": "", "holdAction": false, "unbindKey": false}
                ]},
                {"name": "Wheel 3", "toggleOpen": false, "selectOutside": false, "entries": []}
              ]
            }
            """;
    // 1.1.1 adds folders three levels deep, mod settings actions and Blocktone's old instant-break control.
    static final String RELEASE_1_1_1 = """
            {
              "wheelCount": 1,
              "safeMode": true,
              "toolPouchWheel": true,
              "replaceToolPouchContextualKey": false,
              "closeBuildprintWithWheelKey": true,
              "managedBindings": {"key.togglePerspective": "key.keyboard.f5"},
              "wheels": [
                {"name": "Wheel 1", "toggleOpen": false, "selectOutside": false, "entries": [
                  {"control": "", "icon": "minecraft:iron_pickaxe", "label": "Tools", "holdAction": false, "unbindKey": false, "entries": [
                    {"control": "key.togglePerspective", "icon": "minecraft:spyglass", "label": "Camera", "holdAction": false, "unbindKey": true},
                    {"control": "", "icon": "minecraft:chest", "label": "Deep", "holdAction": false, "unbindKey": false, "entries": [
                      {"control": "", "icon": "minecraft:barrel", "label": "Deepest", "holdAction": false, "unbindKey": false, "entries": [
                        {"control": "key.drop", "icon": "minecraft:dropper", "label": "", "holdAction": false, "unbindKey": false}
                      ]}
                    ]}
                  ]},
                  {"control": "modmenu:config/hotkey_wheel", "icon": "minecraft:comparator", "label": "", "holdAction": false, "unbindKey": false},
                  {"control": "key.buildprint_toning.instamine", "icon": "minecraft:diamond_pickaxe", "label": "Dig", "holdAction": false, "unbindKey": false},
                  {"control": "key.inventory", "icon": "minecraft:chest", "label": "", "holdAction": false, "unbindKey": false}
                ]},
                {"name": "Wheel 2", "toggleOpen": false, "selectOutside": false, "entries": []},
                {"name": "Wheel 3", "toggleOpen": false, "selectOutside": false, "entries": []}
              ]
            }
            """;

    @Override
    public void runTest(ClientGameTestContext context) {
        String version = FabricLoader.getInstance().getModContainer("hotkey_wheel").orElseThrow().getMetadata().getVersion().getFriendlyString();
        String fixture = version.startsWith("1.0.") ? RELEASE_1_0_11 : RELEASE_1_1_1;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("wheel_version", version);
        try {
            context.runOnClient(client -> {
                Files.writeString(WheelConfig.path(), fixture);
                WheelConfig.load();
                if (!WheelConfig.save(WheelConfig.get())) throw new AssertionError("The older release could not save its settings");
                Path output = Path.of(System.getProperty("wheel.qa.root"), "upgrade", "settings-" + version + ".json");
                Files.createDirectories(output.getParent());
                Files.copy(WheelConfig.path(), output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                report.put("written", output.toString());
                report.put("passed", true);
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "upgrade-source-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            });
        } catch (Exception failure) {
            throw new AssertionError("Writing the older release's settings", failure);
        }
    }
}
