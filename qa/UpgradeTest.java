package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelIcons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Settings files written by the 1.0.11 and 1.1.1 releases themselves (UpgradeSourceTest, run with those JARs)
 * load in this release, survive the new editor's Save byte for byte, and take the new icons and settings.
 */
public final class UpgradeTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                // An exact label wins over a longer one that starts the same, such as a tab named "Saved…".
                .sorted(java.util.Comparator.comparing(candidate -> !candidate.getMessage().getString().equals(prefix)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        WheelConfig[] original = {null};
        try {
            Path sources = Path.of(System.getProperty("wheel.qa.upgrade"));
            List<Path> files;
            try (Stream<Path> listing = Files.list(sources)) {
                files = listing.filter(path -> path.getFileName().toString().startsWith("settings-")).sorted().toList();
            }
            report.put("files", files.stream().map(path -> path.getFileName().toString()).toList());
            check(files.size() == 2, "Settings written by 1.0.11 and 1.1.1 are both present: " + files);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                client.options.guiScale().set(2);
                for (Path file : files) upgrade(client, file);
            });
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel upgrade QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    KeyMapping perspective = client.options.keyTogglePerspective;
                    perspective.setKey(perspective.getDefaultKey());
                    KeyMapping.resetMapping();
                    client.options.save();
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "upgrade-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    private void upgrade(Minecraft client, Path file) throws Exception {
        String release = file.getFileName().toString().replaceFirst("^settings-", "").replaceFirst("\\.json$", "");
        String written = Files.readString(file);
        Path unreadable = WheelConfig.path().resolveSibling("hotkey-wheel.json.unreadable");
        Files.deleteIfExists(unreadable);
        // As the older release left things: Unbind normal key had removed F5 from Toggle Perspective.
        KeyMapping perspective = client.options.keyTogglePerspective;
        perspective.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        client.options.save();
        Files.writeString(WheelConfig.path(), written);
        WheelConfig.load();
        WheelConfig loaded = WheelConfig.get();
        check(!Files.exists(unreadable), release + ": the file loads without being called unreadable");
        check(loaded.managedBindings.get("key.togglePerspective").equals("key.keyboard.f5"), release + ": the remembered key is kept");
        check(!loaded.oneModControlsFolder(), release + ": control picker folders start as one per mod");
        if (release.startsWith("1.0.")) {
            check(loaded.wheelCount == 2 && !loaded.safeMode && !loaded.toolPouchWheel && loaded.replaceToolPouchContextualKey
                            && !loaded.closeBuildprintWithWheelKey, release + ": general settings load unchanged");
            WheelConfig.Wheel builder = loaded.wheels.get(0);
            check(builder.name.equals("Builder") && builder.selectOutside && !builder.toggleOpen && loaded.wheels.get(1).toggleOpen,
                    release + ": wheel settings load unchanged");
            check(builder.entries.get(1).holdAction && builder.entries.get(2).control.equals("toolpouch:slot_toggle/3")
                            && builder.entries.get(2).icon.equals("minecraft:elytra") && builder.entries.get(2).label.equals("Wings"),
                    release + ": slots, icons, labels, Hold and pouch toggles load unchanged");
        } else {
            WheelConfig.Entry tools = loaded.wheels.get(0).entries.get(0);
            check(tools.isFolder() && tools.entries.get(1).entries.get(0).entries.get(0).control.equals("key.drop"),
                    release + ": folders three levels deep load unchanged");
            check(loaded.wheels.get(0).entries.get(1).control.equals("modmenu:config/hotkey_wheel")
                            && loaded.wheels.get(0).entries.get(2).control.equals("key.buildprint_toning.instamine"),
                    release + ": mod settings and instant-break slots keep their saved controls");
        }
        // Opening the restructured editor, visiting both kinds of tab and saving changes nothing in the file.
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Wheel 1"));
        press(button(editor, "Save"));
        check(Files.readString(WheelConfig.path()).equals(written), release + ": a Save in the new editor writes the file back byte for byte");
        check(perspective.isUnbound() && WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                release + ": Save keeps the removed key removed and remembered");
        // The new icons go in the existing icon field; nothing else moves.
        WheelConfig changed = WheelConfig.get().copy();
        changed.wheels.get(0).entries.get(0).icon = WheelIcons.GLYPH_PREFIX + "folder";
        changed.wheels.get(0).entries.get(1).icon = WheelIcons.modIcon("hotkey_wheel");
        check(WheelConfig.save(changed), release + ": settings with new icons save");
        JsonObject before = JsonParser.parseString(written).getAsJsonObject();
        JsonObject after = JsonParser.parseString(Files.readString(WheelConfig.path())).getAsJsonObject();
        JsonObject firstWheelBefore = before.getAsJsonArray("wheels").get(0).getAsJsonObject();
        firstWheelBefore.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("icon", WheelIcons.GLYPH_PREFIX + "folder");
        firstWheelBefore.getAsJsonArray("entries").get(1).getAsJsonObject().addProperty("icon", WheelIcons.modIcon("hotkey_wheel"));
        check(before.equals(after), release + ": only the two icon values change");
        // The control picker opens on the upgraded settings.
        client.gui.setScreen(new ControlPickerScreen(null, control -> { }));
        check(button(client.gui.screen(), ControlPickerScreen.MINECRAFT_FOLDER).active, release + ": the control picker opens on its folders");
        client.gui.setScreen(null);
        report.put(release, Map.of("bytes", written.length(), "fields", keys(before)));
    }

    private static List<String> keys(JsonElement element) {
        return new ArrayList<>(element.getAsJsonObject().keySet());
    }
}
