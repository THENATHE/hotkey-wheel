package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlsMarker;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.IconPickerScreen;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelMenuReturn;
import com.thenathe.hotkeywheel.WheelScreen;
import com.thenathe.hotkeywheel.look.LookDialog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Folders in wheels and the editor, settings compatibility, unbound controls and the Controls screen marker. */
public final class FolderWheelTest implements FabricClientGameTest {
    private static final String BUILDPRINT_SCREEN = "com.buildprint.client.gui.BuildprintScreen";
    private static final String INSTAMINE_SCREEN = "com.thenathe.buildprinttoning.client.InstamineSettingsScreen";
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final boolean buildprint = FabricLoader.getInstance().isModLoaded("buildprint");
    private final boolean blocktone = FabricLoader.getInstance().isModLoaded("buildprint_toning");
    // Blocktone 0.1.39 and earlier have instant-break; 0.1.39.1 replaced it with Buildprint's excavator.
    private final boolean instantBreak = blocktone && classExists(INSTAMINE_SCREEN);
    private final boolean controlling = FabricLoader.getInstance().isModLoaded("controlling");

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
                // An exact label wins over a longer one that starts the same, such as a tab named "Saved…".
                .sorted(java.util.Comparator.comparing(candidate -> !candidate.getMessage().getString().equals(prefix)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    /** A breadcrumb above the slots. The wheel's own crumb has the same label as its tab, which sits left of the panel. */
    private static Button breadcrumb(Screen screen, String label) {
        Button general = button(screen, "General");
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().equals(label))
                .filter(candidate -> candidate.getX() > general.getX() + general.getWidth())
                .findFirst().orElseThrow(() -> new AssertionError("Missing breadcrumb " + label));
    }

    private static boolean hasButton(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(candidate -> candidate.getMessage().getString().startsWith(prefix));
    }

    private static EditBox editBox(Screen screen, String value) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getValue().equals(value)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing text field holding \"" + value + "\""));
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static WheelConfig.Entry slot(String control, String icon) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        return entry;
    }

    private static WheelConfig.Entry folder(String name, WheelConfig.Entry... entries) {
        WheelConfig.Entry folder = WheelConfig.Entry.folder(name);
        folder.entries.addAll(List.of(entries));
        return folder;
    }

    private static InputConstants.Key[] wheelKeys() {
        InputConstants.Key[] keys = new InputConstants.Key[5];
        for (int index = 0; index < 3; index++) keys[index] = InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[index].saveString());
        keys[3] = InputConstants.getKey(HotkeyWheelClient.CYCLE_KEY.saveString());
        keys[4] = InputConstants.getKey(HotkeyWheelClient.CONFIGURE_KEY.saveString());
        return keys;
    }

    private void saveWithKeys(WheelConfig configuration, String description) {
        String error = WheelBindings.save(configuration, wheelKeys());
        check(error.isEmpty(), description + (error.isEmpty() ? "" : ": " + error));
    }

    private static WheelScreen wheel(Minecraft client) { return (WheelScreen) client.gui.screen(); }

    private static void point(ClientGameTestContext context, int slot) {
        point(context, slot, 0.76);
    }

    private static void point(ClientGameTestContext context, int slot, double radialScale) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = wheel(client);
            double angle = RadialGeometry.angle(slot, screen.slotCount());
            double x = screen.centerX() + Math.cos(angle) * screen.radius() * radialScale;
            double y = screen.centerY() + Math.sin(angle) * screen.radius() * radialScale;
            return new double[] {x * client.getWindow().getScreenWidth() / screen.width,
                    y * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static void pointCenter(ClientGameTestContext context) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = wheel(client);
            return new double[] {screen.centerX() * (double) client.getWindow().getScreenWidth() / screen.width,
                    screen.centerY() * (double) client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static void click(ClientGameTestContext context) {
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        report.put("buildprint", buildprint);
        report.put("blocktone", blocktone);
        report.put("blocktone_instant_break", instantBreak);
        report.put("controlling", controlling);
        ClientState[] original = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = new ClientState(client);
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                client.gui.setScreen(null);
                HotkeyWheelClient.WHEEL_KEYS[0].setKey(key(InputConstants.KEY_G));
                HotkeyWheelClient.WHEEL_KEYS[1].setKey(key(InputConstants.KEY_H));
                HotkeyWheelClient.WHEEL_KEYS[2].setKey(key(InputConstants.KEY_J));
                HotkeyWheelClient.CYCLE_KEY.setKey(key(InputConstants.KEY_TAB));
                HotkeyWheelClient.CONFIGURE_KEY.setKey(InputConstants.UNKNOWN);
                for (KeyMapping mapping : client.options.keyMappings) {
                    if (!mapping.getName().startsWith("key.hotkey_wheel.") && (mapping.matches(key(InputConstants.KEY_H))
                            || mapping.matches(key(InputConstants.KEY_J)))) mapping.setKey(InputConstants.UNKNOWN);
                }
                KeyMapping.resetMapping();
                configuration(client);
                editor(client);
                editorKeys(client);
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                if (buildprint) {
                    context.runOnClient(client -> {
                        Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                                .getConstructor().newInstance();
                        onboarding.getClass().getMethod("markDone").invoke(onboarding);
                    });
                }
                runtimeConfiguration(context);
                editorScreenshot(context);
                holdNavigation(context);
                toggleNavigation(context);
                unboundActivation(context);
                if (buildprint) buildprintInFolders(context);
                controlsMarker(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel folder QA", failure);
        } finally {
            try {
                if (original[0] != null) {
                    context.runOnClient(client -> {
                        try { original[0].restore(client); }
                        catch (IOException failure) { throw new RuntimeException(failure); }
                    });
                }
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "folder-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    // Written by 1.0.11 with Tool Pouch slot toggles, the suite's old elytra mapping and remembered keys.
    private static final String RELEASE_1_0_11_CONFIG = """
            {
              "wheelCount": 3,
              "safeMode": true,
              "toolPouchWheel": true,
              "replaceToolPouchContextualKey": true,
              "closeBuildprintWithWheelKey": false,
              "managedBindings": {
                "toolpouch:slot_toggle/4": "key.keyboard.y",
                "key.toolpouch.toggle_elytra": "key.keyboard.k",
                "key.absent_mod.action": "key.mouse.4"
              },
              "wheels": [
                {
                  "name": "Pouch",
                  "toggleOpen": false,
                  "selectOutside": true,
                  "entries": [
                    {"control": "toolpouch:slot_toggle/4", "icon": "minecraft:spyglass", "label": "", "holdAction": false, "unbindKey": true},
                    {"control": "toolpouch:slot_toggle/elytra", "icon": "minecraft:elytra", "label": "Glide", "holdAction": false, "unbindKey": false},
                    {"control": "key.toolpouch.toggle_elytra", "icon": "minecraft:elytra", "label": "", "holdAction": false, "unbindKey": true},
                    {"control": "key.toolpouch.open_widget", "icon": "minecraft:shulker_box", "label": "", "holdAction": false, "unbindKey": false},
                    {"control": "key.absent_mod.action", "icon": "absent_mod:widget", "label": "Absent", "holdAction": true, "unbindKey": true}
                  ]
                },
                {
                  "name": "Second",
                  "toggleOpen": true,
                  "selectOutside": false,
                  "entries": [
                    {"control": "key.inventory", "icon": "minecraft:chest", "label": "", "holdAction": false, "unbindKey": false}
                  ]
                },
                {
                  "name": "Third",
                  "toggleOpen": false,
                  "selectOutside": false,
                  "entries": []
                }
              ]
            }
            """;

    // DowngradeTest loads this same file into 1.0.11.
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

    private void configuration(Minecraft client) throws IOException {
        Map<KeyMapping, InputConstants.Key> keysBefore = new LinkedHashMap<>();
        for (KeyMapping mapping : client.options.keyMappings) keysBefore.put(mapping, InputConstants.getKey(mapping.saveString()));
        JsonElement expected = JsonParser.parseString(RELEASE_1_0_11_CONFIG);
        Files.writeString(WheelConfig.path(), RELEASE_1_0_11_CONFIG);
        WheelConfig.load();
        check(JsonParser.parseString(json(WheelConfig.get())).equals(expected),
                "A 1.0.11 configuration loads with every wheel, slot, slot toggle and remembered key unchanged");
        check(WheelConfig.get().allEntries().stream().noneMatch(WheelConfig.Entry::isFolder),
                "1.0.11 slots load as ordinary slots, not folders");
        boolean keysUnchanged = true;
        for (var entry : keysBefore.entrySet()) {
            keysUnchanged &= InputConstants.getKey(entry.getKey().saveString()).equals(entry.getValue());
        }
        check(keysUnchanged, "Loading a 1.0.11 configuration leaves every Minecraft key binding unchanged");
        check(WheelConfig.save(WheelConfig.get()) && JsonParser.parseString(Files.readString(WheelConfig.path())).equals(expected),
                "Saving a loaded 1.0.11 configuration writes the same settings, with no folder fields added");

        JsonElement withFolders = JsonParser.parseString(RELEASE_1_1_0_CONFIG);
        Files.writeString(WheelConfig.path(), RELEASE_1_1_0_CONFIG);
        WheelConfig.load();
        check(JsonParser.parseString(json(WheelConfig.get())).equals(withFolders) && WheelConfig.save(WheelConfig.get())
                        && JsonParser.parseString(Files.readString(WheelConfig.path())).equals(withFolders),
                "The settings file the downgrade check feeds to 1.0.11 is exactly what this release loads and saves");

        WheelConfig nested = new WheelConfig();
        WheelConfig.Entry deepest = folder("Deepest", slot("key.inventory", "minecraft:chest"));
        WheelConfig.Entry tools = folder("Tools", slot("key.inventory", "minecraft:chest"),
                folder("Deep", slot("key.togglePerspective", "minecraft:spyglass"), deepest));
        tools.icon = "minecraft:iron_pickaxe";
        tools.entries.get(0).label = "Bag";
        tools.entries.get(0).unbindKey = true;
        nested.wheels.get(0).entries.add(0, tools);
        nested.wheels.get(2).entries.add(folder("Hidden folder", slot("key.drop", "minecraft:dropper")));
        check(WheelConfig.save(nested), "Nested folders save");
        JsonObject saved = JsonParser.parseString(Files.readString(WheelConfig.path())).getAsJsonObject();
        JsonArray topLevel = saved.getAsJsonArray("wheels").get(0).getAsJsonObject().getAsJsonArray("entries");
        check(topLevel.get(0).getAsJsonObject().has("entries") && !topLevel.get(1).getAsJsonObject().has("entries"),
                "Only folders carry an entries list; ordinary slots save in the 1.0.x shape");
        WheelConfig.load();
        check(json(WheelConfig.get()).equals(json(nested)), "Folders, their names, icons and nested slots reload unchanged");
        WheelConfig.Entry reloaded = WheelConfig.get().wheels.get(0).entries.get(0);
        check(reloaded.isFolder() && reloaded.label.equals("Tools") && reloaded.icon.equals("minecraft:iron_pickaxe")
                        && reloaded.entries.get(1).entries.get(1).entries.get(0).control.equals("key.inventory"),
                "A folder three levels deep reloads with its slot");
        check(WheelConfig.get().allEntries().stream().anyMatch(entry -> entry.label.equals("Hidden folder")),
                "Folders on a hidden wheel are kept");

        String tooDeep = """
                {"wheels": [{"name": "Deep", "entries": [
                  {"label": "One", "icon": "minecraft:chest", "entries": [
                    {"label": "Two", "icon": "minecraft:chest", "entries": [
                      {"label": "Three", "icon": "minecraft:chest", "entries": [
                        {"label": "Four", "icon": "minecraft:barrel", "entries": [
                          {"control": "key.inventory", "icon": "minecraft:chest"}
                        ]}
                      ]}
                    ]}
                  ]}
                ]}]}
                """;
        Files.writeString(WheelConfig.path(), tooDeep);
        WheelConfig.load();
        WheelConfig.Entry three = WheelConfig.get().wheels.get(0).entries.get(0).entries.get(0).entries.get(0);
        check(three.isFolder() && three.label.equals("Three"), "Three levels of folders load from a hand-edited file");
        WheelConfig.Entry four = three.entries.get(0);
        check(!four.isFolder() && four.label.equals("Four") && four.icon.equals("minecraft:barrel"),
                "A fourth level is kept as a plain slot with its name and icon; its contents are past the depth cap");

        StringBuilder oversized = new StringBuilder("{\"wheels\": [{\"name\": \"Big\", \"entries\": [{\"label\": \"Full\", \"entries\": [");
        for (int index = 0; index < 15; index++) oversized.append(index == 0 ? "" : ",").append("{\"control\": \"key.inventory\"}");
        oversized.append("]}]}]}");
        Files.writeString(WheelConfig.path(), oversized.toString());
        WheelConfig.load();
        check(WheelConfig.get().wheels.get(0).entries.get(0).entries.size() == WheelConfig.MAX_SLOTS,
                "A folder keeps at most 12 slots, like a wheel");

        Path backup = WheelConfig.path().resolveSibling("hotkey-wheel.json.unreadable");
        Files.deleteIfExists(backup);
        String before = json(WheelConfig.get());
        String malformed = "{\"wheels\": [{\"name\": \"Bad\", \"entries\": [{\"label\": \"Folder\", \"entries\": \"not a list\"}]}]}";
        Files.writeString(WheelConfig.path(), malformed);
        WheelConfig.load();
        check(json(WheelConfig.get()).equals(before) && Files.readString(backup).equals(malformed)
                        && Files.readString(WheelConfig.path()).equals(malformed),
                "A malformed folder is kept as an unreadable copy and the file stays in place");
        Files.delete(backup);
        StringBuilder runaway = new StringBuilder("{\"wheels\": [{\"entries\": [");
        for (int depth = 0; depth < 200; depth++) runaway.append("{\"entries\": [");
        for (int depth = 0; depth < 200; depth++) runaway.append("]}");
        runaway.append("]}]}");
        Files.writeString(WheelConfig.path(), runaway.toString());
        WheelConfig.load();
        // Gson's nesting limit refuses the file; without one, loading caps the depth instead. Either way nothing overflows.
        boolean refused = Files.isRegularFile(backup);
        report.put("runaway_nesting", refused ? "refused as unreadable" : "loaded with the depth capped");
        check(refused ? json(WheelConfig.get()).equals(before) : folderDepth(WheelConfig.get().wheels.get(0).entries) <= WheelConfig.MAX_FOLDER_DEPTH,
                "Runaway nesting neither overflows the stack nor loads past the depth cap");
        Files.deleteIfExists(backup);
    }

    private static int folderDepth(List<WheelConfig.Entry> entries) {
        int depth = 0;
        for (WheelConfig.Entry entry : entries) {
            if (entry.isFolder()) depth = Math.max(depth, 1 + folderDepth(entry.entries));
        }
        return depth;
    }

    private void editor(Minecraft client) throws IOException {
        KeyMapping perspective = client.options.keyTogglePerspective;
        perspective.setKey(perspective.getDefaultKey());
        KeyMapping.resetMapping();
        WheelConfig base = new WheelConfig();
        base.wheelCount = 1;
        base.wheels.get(0).entries.clear();
        base.wheels.get(0).entries.add(slot("key.inventory", "minecraft:chest"));
        saveWithKeys(base, "Editor fixture saves");

        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        check(button(editor, "Add folder").active, "The editor offers Add folder");
        press(button(editor, "Add folder"));
        check(hasButton(editor, "Open folder: Folder"), "Add folder creates a selected folder named Folder");
        check(!hasButton(editor, "Unbind normal key") && !hasButton(editor, "Action:"),
                "A folder has a name and icon but no control options");
        editBox(editor, "Folder").setValue("Tools");
        press(button(editor, "Choose icon"));
        check(client.gui.screen() instanceof IconPickerScreen, "A folder icon is chosen with the existing icon picker");
        EditBox search = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
        search.setValue("minecraft:iron_pickaxe");
        press(button(client.gui.screen(), "Iron Pickaxe"));
        check(client.gui.screen() == editor && hasButton(editor, "Open folder: Tools"),
                "The renamed folder keeps its new name after the icon picker returns");

        press(button(editor, "Open folder: Tools"));
        check(breadcrumb(editor, "Wheel 1").active && !button(editor, "● Tools").active,
                "Opening a folder shows the path back as breadcrumbs");
        check(!hasButton(editor, "Control:"), "A new folder starts with no slots");
        press(button(editor, "Add"));
        press(button(editor, "Control: Choose a control"));
        check(client.gui.screen() instanceof ControlPickerScreen, "A folder slot uses the existing control picker");
        EditBox controls = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
        controls.setValue("key.togglePerspective");
        press(button(client.gui.screen(), Component.translatable(perspective.getName()).getString()));
        check(client.gui.screen() == editor && hasButton(editor, "Control: " + Component.translatable(perspective.getName()).getString()),
                "The chosen control appears on the folder slot");
        press(button(editor, "Unbind normal key: OFF"));
        editBox(editor, "").setValue("Camera");
        check(hasButton(editor, "Action: Tap"), "Folder slots have the Tap/Hold option");

        press(button(editor, "Add folder"));
        editBox(editor, "Folder").setValue("Deep");
        press(button(editor, "Open folder: Deep"));
        press(button(editor, "Add folder"));
        editBox(editor, "Folder").setValue("Deepest");
        press(button(editor, "Open folder: Deepest"));
        check(!button(editor, "Add folder").active && button(editor, "Add").active,
                "At three folder levels Add folder is unavailable and ordinary slots can still be added");
        check(button(editor, "› Tools").active && button(editor, "› Deep").active && !button(editor, "● Deepest").active,
                "Breadcrumbs list every level on the way back");
        press(button(editor, "Add"));
        press(button(editor, "› Tools"));
        check(!button(editor, "● Tools").active && hasButton(editor, "Open folder: Deep"),
                "A breadcrumb returns to its level with the folder just left selected");
        press(breadcrumb(editor, "Wheel 1"));
        check(hasButton(editor, "Open folder: Tools") && !hasButton(editor, "● Tools"),
                "The wheel breadcrumb returns to the top level");
        press(button(editor, "Save"));
        WheelConfig.Entry tools = WheelConfig.get().wheels.get(0).entries.get(1);
        check(tools.isFolder() && tools.label.equals("Tools") && tools.icon.equals("minecraft:iron_pickaxe"),
                "Save stores the folder's name and icon");
        WheelConfig.Entry camera = tools.entries.get(0);
        check(camera.control.equals(perspective.getName()) && camera.label.equals("Camera") && camera.unbindKey,
                "Save stores the folder slot's control, label and Unbind normal key option");
        check(tools.entries.get(1).entries.get(0).label.equals("Deepest")
                        && tools.entries.get(1).entries.get(0).entries.size() == 1,
                "Save stores folders nested three levels deep");
        check(perspective.isUnbound() && WheelConfig.get().managedBindings.get(perspective.getName()).equals(perspective.getDefaultKey().getName()),
                "Unbind normal key inside a folder removes and remembers the control's key on Save");

        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "Slot 2: Folder Tools"));
        press(button(editor, "Remove"));
        check(client.gui.screen() instanceof LookDialog question && question.getTitle().getString().equals("Remove the folder \"Tools\"?"),
                "Removing a folder that holds slots asks first, naming the folder");
        press(button(client.gui.screen(), "Keep"));
        check(client.gui.screen() == editor && hasButton(editor, "Open folder: Tools"), "Keep leaves the folder in place");
        press(button(editor, "Remove"));
        client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
        check(client.gui.screen() == editor && hasButton(editor, "Open folder: Tools"), "Escape on the question keeps the folder");
        press(button(editor, "Remove"));
        press(button(client.gui.screen(), "Remove folder"));
        check(client.gui.screen() == editor && !hasButton(editor, "Open folder: Tools"), "Confirming removes the folder from the draft");
        press(button(editor, "Cancel"));
        check(WheelConfig.get().wheels.get(0).entries.get(1).isFolder() && perspective.isUnbound(),
                "Cancel after removing a folder keeps the saved folder and its unbound key");

        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "Add folder"));
        press(button(editor, "Remove"));
        check(client.gui.screen() == editor && !hasButton(editor, "Open folder: Folder"),
                "An empty folder is removed without a question");
        press(button(editor, "Slot 2: Folder Tools"));
        press(button(editor, "Remove"));
        press(button(client.gui.screen(), "Remove folder"));
        press(button(editor, "Save"));
        check(WheelConfig.get().wheels.get(0).entries.size() == 1, "Save applies the folder removal");
        check(perspective.matches(perspective.getDefaultKey()) && WheelConfig.get().managedBindings.isEmpty(),
                "Removing the folder that held the last unbinding use restores the remembered key");
    }

    private void editorKeys(Minecraft client) throws IOException {
        KeyMapping perspective = client.options.keyTogglePerspective;
        InputConstants.Key original = perspective.getDefaultKey();
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        configuration.wheels.get(0).entries.clear();
        WheelConfig.Entry nested = slot(perspective.getName(), "minecraft:spyglass");
        nested.unbindKey = true;
        configuration.wheels.get(0).entries.add(folder("Tools", folder("Deep", nested)));
        saveWithKeys(configuration, "A two-level folder slot opts into unbinding");
        check(perspective.isUnbound(), "Unbinding counts a slot two folders deep");
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Restore keys unbound"));
        press(button(editor, "Save"));
        check(perspective.matches(original) && WheelConfig.get().managedBindings.isEmpty()
                        && !WheelConfig.get().wheels.get(0).entries.get(0).entries.get(0).entries.get(0).unbindKey,
                "Restore keys unbound by Hotkey Wheel reaches slots inside folders");

        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.get(0).entries.get(0).entries.get(0).unbindKey = true;
        WheelConfig.Entry top = slot(perspective.getName(), "minecraft:spyglass");
        top.unbindKey = true;
        configuration.wheels.get(0).entries.add(top);
        saveWithKeys(configuration, "The same control is unbound from the top level and from a folder");
        check(WheelConfig.get().managedBindings.size() == 1, "A control used in and out of folders keeps one recovery key");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.remove(1);
        saveWithKeys(configuration, "Remove the top-level use");
        check(perspective.isUnbound(), "A remaining use inside a folder keeps the control unbound");
        configuration = WheelConfig.get().copy();
        configuration.wheels.get(0).entries.get(0).entries.get(0).entries.get(0).unbindKey = false;
        saveWithKeys(configuration, "Turn the folder slot's option off");
        check(perspective.matches(original), "Turning the last folder use off restores the key");

        configuration = WheelConfig.get().copy();
        configuration.wheelCount = 2;
        WheelConfig.Entry hidden = slot(perspective.getName(), "minecraft:spyglass");
        hidden.unbindKey = true;
        configuration.wheels.get(1).entries.clear();
        configuration.wheels.get(1).entries.add(folder("Second wheel folder", hidden));
        saveWithKeys(configuration, "A second wheel's folder unbinds its control");
        check(perspective.isUnbound(), "Folders on every active wheel count");
        configuration = WheelConfig.get().copy();
        configuration.wheelCount = 1;
        saveWithKeys(configuration, "Hide the second wheel");
        check(perspective.matches(original), "Hiding the wheel that holds the folder restores the key");

        KeyMapping jump = client.options.keyJump;
        configuration = WheelConfig.get().copy();
        WheelConfig.Entry protectedSlot = slot(jump.getName(), "minecraft:slime_block");
        protectedSlot.unbindKey = true;
        configuration.wheels.get(0).entries.get(0).entries.add(protectedSlot);
        saveWithKeys(configuration, "A protected control inside a folder saves");
        check(jump.matches(jump.getDefaultKey()) && !WheelConfig.get().managedBindings.containsKey(jump.getName()),
                "Safe mode protects controls inside folders");
        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "Open folder: Tools"));
        press(button(editor, "Slot 2: "));
        check(!button(editor, "Unbind normal key: Protected").active, "The protected folder slot's unbinding option is disabled");
        press(button(editor, "Cancel"));
        client.gui.setScreen(null);
    }

    private void runtimeConfiguration(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WheelConfig configuration = new WheelConfig();
            configuration.wheelCount = 3;
            for (WheelConfig.Wheel wheel : configuration.wheels) wheel.entries.clear();
            WheelConfig.Entry bag = slot("key.inventory", "minecraft:chest");
            bag.label = "Bag";
            WheelConfig.Entry forward = slot(client.options.keyUp.getName(), "minecraft:leather_boots");
            forward.holdAction = true;
            WheelConfig.Entry players = slot(client.options.keyPlayerList.getName(), "minecraft:player_head");
            players.holdAction = true;
            WheelConfig.Entry tools = folder("Tools", bag, folder("Deep", slot(client.options.keyTogglePerspective.getName(), "minecraft:spyglass")),
                    forward, players);
            if (buildprint) {
                tools.entries.add(slot("key.buildprint.open", "minecraft:oak_planks"));
                tools.entries.add(slot("key.buildprint.editor", "minecraft:spyglass"));
                if (blocktone) tools.entries.add(slot(instantBreak ? "key.buildprint_toning.instamine_settings"
                        : "key.buildprint_toning.instamine", "minecraft:diamond_pickaxe"));
            }
            WheelConfig.Wheel first = configuration.wheels.get(0);
            first.name = "Wheel 1";
            first.entries.addAll(List.of(tools, slot(client.options.keyTogglePerspective.getName(), "minecraft:spyglass"),
                    slot("key.inventory", "minecraft:chest")));
            WheelConfig.Wheel second = configuration.wheels.get(1);
            second.name = "Toggle";
            second.toggleOpen = true;
            second.entries.addAll(List.of(folder("Toggle tools", slot("key.inventory", "minecraft:chest"),
                    folder("Inner", slot("key.inventory", "minecraft:chest"))), slot("key.inventory", "minecraft:chest")));
            WheelConfig.Wheel third = configuration.wheels.get(2);
            third.name = "Third";
            WheelConfig.Entry thirdPlayers = slot(client.options.keyPlayerList.getName(), "minecraft:player_head");
            thirdPlayers.holdAction = true;
            third.entries.addAll(List.of(slot("key.inventory", "minecraft:chest"), thirdPlayers,
                    slot(buildprint ? "key.buildprint.open" : "key.inventory", "minecraft:oak_planks")));
            saveWithKeys(configuration, "Runtime folder fixture saves");
        });
    }

    private void editorScreenshot(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Open folder: Tools"));
            press(button(editor, "Slot 2: Folder Deep"));
            editor.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            check(breadcrumb(editor, "Wheel 1").active && hasButton(editor, "Open folder: Deep"), "The in-game editor opens the Tools folder");
        });
        context.waitTicks(3);
        context.takeScreenshot("folder-editor-tools");
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private void holdNavigation(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        context.waitTicks(1);
        context.takeScreenshot("folder-hover-tools");
        click(context);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelScreen && wheel(client).folderDepth() == 1,
                    "Clicking a folder while holding the opener opens it as a radial");
            check(wheel(client).location().equals("Wheel 1 › Tools") && wheel(client).slotCount() == (buildprint ? (blocktone ? 7 : 6) : 4),
                    "The folder radial is named by its path and shows the folder's slots");
            WheelScreen screen = wheel(client);
            double distance = Math.hypot(client.mouseHandler.getScaledXPos(client.getWindow()) - screen.centerX(),
                    client.mouseHandler.getScaledYPos(client.getWindow()) - screen.centerY());
            check(distance <= 1, "Opening a folder starts the cursor on Back in the center: " + distance);
            check(!screen.toggleOpen(), "A click-opened folder keeps the held opener in charge");
        });
        context.takeScreenshot("folder-open-tools-hold");
        point(context, 0);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen, "Releasing the opener over a folder slot uses it");
            client.gui.setScreen(null);
        });

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelScreen && wheel(client).folderDepth() == 1,
                    "Releasing the opener over a folder opens it and keeps it open");
            check(wheel(client).toggleOpen(), "After a release opened a folder, clicks choose, as in the Tool Pouch radial");
        });
        context.takeScreenshot("folder-open-after-release");
        point(context, 0);
        click(context);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen, "A click then uses the folder slot");
            client.gui.setScreen(null);
        });

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 1);
        click(context);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 2 && wheel(client).location().equals("Wheel 1 › Tools › Deep"),
                "A folder inside a folder opens one level deeper"));
        context.takeScreenshot("folder-nested-deep");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 1, "Escape goes back one level"));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 0 && client.gui.screen() instanceof WheelScreen,
                "Right click goes back one level"));
        point(context, 2);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen, "After going back, releasing the held opener selects in the parent");
            client.gui.setScreen(null);
        });

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(1);
        context.runOnClient(client -> check(client.gui.screen() == null, "Escape at the top level closes the wheel"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null, "The opener's later release does nothing"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        pointCenter(context);
        click(context);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 0, "Clicking the center of a folder goes Back"));
        point(context, 0);
        click(context);
        pointCenter(context);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null && !client.options.keyUp.isDown(),
                "Releasing the opener in a folder's center closes the wheel without an action"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 0, 1.45);
        context.runOnClient(client -> check(wheel(client).selection(client.mouseHandler.getScaledXPos(client.getWindow()),
                client.mouseHandler.getScaledYPos(client.getWindow())) == -1, "Outside the folder ring selects nothing with Outside wheel: Cancel"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null, "Releasing outside a folder ring cancels"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 2);
        click(context);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() == null && client.options.keyUp.isDown(),
                "A Hold action inside a folder stays pressed while the opener is held"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!client.options.keyUp.isDown(), "Releasing the opener releases the folder's Hold action"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 2);
        click(context);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null && !client.options.keyUp.isDown(),
                "With the opener already released, a Hold slot taps, as in Toggle mode"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.runOnClient(client -> check(wheel(client).wheelIndex() == 1 && wheel(client).folderDepth() == 0,
                "Cycling from a folder opens the next wheel at its top level"));
        point(context, 0);
        click(context);
        context.getInput().pressKey(InputConstants.KEY_J);
        context.runOnClient(client -> check(wheel(client).wheelIndex() == 2 && wheel(client).folderDepth() == 0,
                "Another wheel's key switches to that wheel at its top level"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null, "The original Hold opener still closes after switching from a folder"));
    }

    private void toggleNavigation(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 1 && wheel(client).location().equals("Toggle › Toggle tools"),
                "Clicking a folder in a Toggle wheel opens it"));
        context.takeScreenshot("folder-open-toggle");
        pointCenter(context);
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 0, "In Toggle mode the opener over the center goes Back"));
        point(context, 0);
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 1, "In Toggle mode the opener over a folder opens it"));
        point(context, 1);
        click(context);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 2, "Toggle folders nest"));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 1, "Right click goes back in Toggle mode"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(1);
        context.runOnClient(client -> check(wheel(client).folderDepth() == 0, "Escape goes back in Toggle mode"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() == null, "Escape at the top of a Toggle wheel closes it"));
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 0);
        click(context);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen && !client.options.keyAttack.isDown() && !client.options.keyAttack.consumeClick(),
                    "A Toggle folder slot activates on click without leaking into Attack");
            client.gui.setScreen(null);
        });
    }

    private void unboundActivation(ClientGameTestContext context) {
        for (boolean byWheel : new boolean[] {false, true}) {
            String how = byWheel ? " after Unbind normal key" : " after unbinding in Controls";
            context.runOnClient(client -> {
                WheelConfig configuration = WheelConfig.get().copy();
                KeyMapping perspective = client.options.keyTogglePerspective;
                KeyMapping players = client.options.keyPlayerList;
                perspective.setKey(perspective.getDefaultKey());
                players.setKey(key(InputConstants.KEY_F8));
                KeyMapping.resetMapping();
                for (WheelConfig.Entry entry : configuration.allEntries()) {
                    entry.unbindKey = byWheel && (entry.control.equals(perspective.getName()) || entry.control.equals(players.getName()));
                }
                saveWithKeys(configuration, "Unbound-control fixture saves" + how);
                if (!byWheel) {
                    perspective.setKey(InputConstants.UNKNOWN);
                    players.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    client.options.save();
                }
                check(perspective.isUnbound() && players.isUnbound(), "Perspective and Player List have no key" + how);
                check(byWheel == WheelConfig.get().managedBindings.containsKey(perspective.getName()),
                        "Only Unbind normal key leaves a remembered key" + how);
            });
            var camera = context.computeOnClient(client -> client.options.getCameraType());
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            point(context, 1);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.options.getCameraType() != camera,
                    "A top-level wheel slot changes the camera with no Perspective key" + how));
            var cameraAgain = context.computeOnClient(client -> client.options.getCameraType());
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            point(context, 0);
            click(context);
            point(context, 1);
            click(context);
            point(context, 0);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() == null && client.options.getCameraType() != cameraAgain,
                    "A slot two folders deep changes the camera with no Perspective key" + how));

            context.getInput().holdKey(InputConstants.KEY_J);
            context.waitTicks(3);
            point(context, 1);
            click(context);
            context.waitTicks(2);
            context.runOnClient(client -> check(client.gui.screen() == null && client.options.keyPlayerList.isDown(),
                    "A top-level Hold action holds Player List with no key" + how));
            context.getInput().releaseKey(InputConstants.KEY_J);
            context.waitTicks(3);
            context.runOnClient(client -> check(!client.options.keyPlayerList.isDown(), "Releasing the opener releases the unbound Hold action" + how));
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            point(context, 0);
            click(context);
            point(context, 3);
            click(context);
            context.waitTicks(2);
            context.runOnClient(client -> check(client.gui.screen() == null && client.options.keyPlayerList.isDown(),
                    "A folder Hold action holds Player List with no key" + how));
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(!client.options.keyPlayerList.isDown(), "Releasing the opener releases the folder's unbound Hold action" + how));

            if (buildprint) {
                context.runOnClient(client -> {
                    KeyMapping open = HotkeyWheelClient.findControl("key.buildprint.open");
                    open.setKey(key(InputConstants.KEY_F7));
                    KeyMapping.resetMapping();
                    WheelConfig configuration = WheelConfig.get().copy();
                    for (WheelConfig.Entry entry : configuration.allEntries()) {
                        if (entry.control.equals(open.getName())) entry.unbindKey = byWheel;
                    }
                    saveWithKeys(configuration, "Buildprint fixture saves" + how);
                    if (!byWheel) {
                        open.setKey(InputConstants.UNKNOWN);
                        KeyMapping.resetMapping();
                        client.options.save();
                    }
                    check(open.isUnbound(), "Buildprint's menu control has no key" + how);
                });
                context.getInput().holdKey(InputConstants.KEY_J);
                context.waitTicks(3);
                point(context, 2);
                context.getInput().releaseKey(InputConstants.KEY_J);
                context.waitTicks(4);
                context.runOnClient(client -> {
                    check(client.gui.screen() != null && client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN),
                            "A top-level slot opens Buildprint's menu with no key" + how);
                    client.gui.setScreen(null);
                    WheelMenuReturn.clear();
                });
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                point(context, 0);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(3);
                point(context, 4);
                click(context);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    check(client.gui.screen() != null && client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN),
                            "A folder slot opens Buildprint's menu with no key" + how);
                    client.gui.setScreen(null);
                    WheelMenuReturn.clear();
                });
            }
        }
    }

    private void buildprintInFolders(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            check(WheelConfig.get().closeBuildprintWithWheelKey, "Closing Buildprint with the wheel key is on");
            client.gui.setScreen(null);
        });
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 4);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> check(client.gui.screen() != null && client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN),
                "Releasing the opener over Buildprint inside a folder opens its menu"));
        context.takeScreenshot("folder-buildprint-menu");
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(1);
        context.runOnClient(client -> check(client.gui.screen() == null, "The original opener closes a menu opened from a folder"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() == null && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                "Closing the folder-opened menu leaves no wheel click behind"));

        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        point(context, 0);
        click(context);
        point(context, 5);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> check(editorActive() && client.gui.screen() == null,
                "A folder slot opens Buildprint's selection editor"));
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive() && client.getCameraEntity() == client.player,
                "The original opener closes the selection editor opened from a folder"));

        if (blocktone && !instantBreak) {
            for (boolean on : new boolean[] {true, false}) {
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                point(context, 0);
                click(context);
                point(context, 6);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(4);
                context.runOnClient(client -> check(client.gui.screen() == null && excavatorOn() == on,
                        "An old instant-break slot in a folder turns Buildprint's excavator " + (on ? "on" : "off")));
            }
        }
        if (instantBreak) {
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            point(context, 0);
            click(context);
            point(context, 6);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(4);
            context.runOnClient(client -> check(client.gui.screen() != null && client.gui.screen().getClass().getName().equals(INSTAMINE_SCREEN),
                    "A folder slot opens Blocktone's instant-break settings"));
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(1);
            context.runOnClient(client -> check(client.gui.screen() == null, "The original opener closes Blocktone's settings opened from a folder"));
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(2);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, FolderWheelTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static boolean excavatorOn() throws Exception {
        Object config = Class.forName("com.buildprint.config.ConfigManager").getMethod("get").invoke(null);
        return config.getClass().getField("excavatorMode").getBoolean(config);
    }

    private static boolean editorActive() throws Exception {
        return (boolean) Class.forName("com.buildprint.api.editor.BuildprintEditor").getMethod("isActive").invoke(null);
    }

    private record Row(Object entry, KeyMapping control, AbstractWidget keyButton, AbstractWidget resetButton) {}

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static List<Row> vanillaRows(KeyBindsScreen screen) throws Exception {
        KeyBindsList list = (KeyBindsList) field(KeyBindsScreen.class, "keyBindsList").get(screen);
        List<Row> rows = new ArrayList<>();
        for (Object entry : list.children()) {
            if (!(entry instanceof KeyBindsList.KeyEntry)) continue;
            rows.add(new Row(entry, (KeyMapping) field(KeyBindsList.KeyEntry.class, "key").get(entry),
                    (AbstractWidget) field(KeyBindsList.KeyEntry.class, "changeButton").get(entry),
                    (AbstractWidget) field(KeyBindsList.KeyEntry.class, "resetButton").get(entry)));
        }
        return rows;
    }

    private static List<Row> controllingRows(Screen screen) throws Exception {
        Object list = screen.getClass().getMethod("getKeyBindsList").invoke(screen);
        List<Row> rows = new ArrayList<>();
        Class<?> keyEntry = Class.forName("com.blamejared.controlling.api.entries.IKeyEntry");
        for (Object entry : ((AbstractSelectionList<?>) list).children()) {
            if (!keyEntry.isInstance(entry)) continue;
            rows.add(new Row(entry, (KeyMapping) keyEntry.getMethod("getKey").invoke(entry),
                    (AbstractWidget) keyEntry.getMethod("getBtnChangeKeyBinding").invoke(entry),
                    (AbstractWidget) keyEntry.getMethod("getBtnResetKeyBinding").invoke(entry)));
        }
        return rows;
    }

    private static Row row(List<Row> rows, KeyMapping control) {
        return rows.stream().filter(row -> row.control() == control).findFirst()
                .orElseThrow(() -> new AssertionError("No key list row for " + control.getName()));
    }

    private static AbstractSelectionList<?> list(Screen screen) throws Exception {
        // Controlling's screen extends the vanilla one but keeps its own list.
        if (screen.getClass() == KeyBindsScreen.class) return (KeyBindsList) field(KeyBindsScreen.class, "keyBindsList").get(screen);
        return (AbstractSelectionList<?>) screen.getClass().getMethod("getKeyBindsList").invoke(screen);
    }

    private static void scrollTo(Screen screen, Object entry) throws Exception {
        Method center = AbstractSelectionList.class.getDeclaredMethod("centerScrollOn",
                Class.forName("net.minecraft.client.gui.components.AbstractSelectionList$Entry"));
        center.setAccessible(true);
        center.invoke(list(screen), entry);
    }

    /** Pixels in a GUI-space box of a screenshot that are within {@code tolerance} of an RGB colour. */
    private static int matching(BufferedImage image, double scale, int left, int top, int right, int bottom, int rgb, int tolerance) {
        int count = 0;
        for (int y = (int) Math.floor(top * scale); y < (int) Math.ceil(bottom * scale); y++) {
            for (int x = (int) Math.floor(left * scale); x < (int) Math.ceil(right * scale); x++) {
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
                int pixel = image.getRGB(x, y);
                if (Math.abs((pixel >> 16 & 0xff) - (rgb >> 16 & 0xff)) <= tolerance
                        && Math.abs((pixel >> 8 & 0xff) - (rgb >> 8 & 0xff)) <= tolerance
                        && Math.abs((pixel & 0xff) - (rgb & 0xff)) <= tolerance) count++;
            }
        }
        return count;
    }

    private static int differing(BufferedImage first, BufferedImage second, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = Math.max(0, top); y < Math.min(bottom, first.getHeight()); y++) {
            for (int x = Math.max(0, left); x < Math.min(right, first.getWidth()); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
            }
        }
        return count;
    }

    private void controlsMarker(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            KeyMapping perspective = client.options.keyTogglePerspective;
            perspective.setKey(perspective.getDefaultKey());
            client.options.keyInventory.setKey(client.options.keyInventory.getDefaultKey());
            KeyMapping.resetMapping();
            WheelConfig configuration = new WheelConfig();
            configuration.wheelCount = 1;
            configuration.wheels.get(0).entries.clear();
            WheelConfig.Entry bag = slot("key.inventory", "minecraft:chest");
            bag.label = "Bag";
            WheelConfig.Entry camera = slot(perspective.getName(), "minecraft:spyglass");
            camera.unbindKey = true;
            configuration.wheels.get(0).entries.addAll(List.of(folder("Tools", bag, folder("Deep", camera)),
                    slot("key.inventory", "minecraft:chest")));
            // A hidden wheel cannot be opened, so its slots are not marked.
            configuration.wheels.get(1).entries.clear();
            configuration.wheels.get(1).entries.add(slot(client.options.keyDrop.getName(), "minecraft:dropper"));
            saveWithKeys(configuration, "Controls marker fixture saves");

            List<String> inventory = ControlsMarker.tooltip(client.options.keyInventory).stream().map(Component::getString).toList();
            report.put("inventory_tooltip", inventory);
            check(inventory.equals(List.of("Used by Hotkey Wheel:", "Wheel 1 › Tools › Bag", "Wheel 1 › Slot 2")),
                    "The tooltip names every place a control is used, inside folders too: " + inventory);
            List<String> cameraTooltip = ControlsMarker.tooltip(perspective).stream().map(Component::getString).toList();
            report.put("perspective_tooltip", cameraTooltip);
            check(cameraTooltip.size() == 4 && cameraTooltip.get(1).equals("Wheel 1 › Tools › Deep › Slot 1")
                            && cameraTooltip.get(2).equals(Component.translatable("hotkey_wheel.controls.unbound_by_wheel").getString())
                            && !cameraTooltip.get(2).startsWith("hotkey_wheel."),
                    "The tooltip says when Hotkey Wheel removed the key, through a translation: " + cameraTooltip);
            check(ControlsMarker.tooltip(client.options.keyJump).isEmpty() && ControlsMarker.tooltip(client.options.keyDrop).isEmpty(),
                    "Controls no active wheel uses have no marker");
        });

        // Controlling swaps in its own list whenever the vanilla Key Binds screen opens, so each profile checks the list it shows.
        Screen[] shown = {null};
        context.runOnClient(client -> {
            client.gui.setScreen(new KeyBindsScreen(null, client.options));
            shown[0] = client.gui.screen();
            check(controlling ? shown[0].getClass().getName().equals("com.blamejared.controlling.client.NewKeyBindsScreen")
                            : shown[0].getClass() == KeyBindsScreen.class,
                    "Key Binds opens " + (controlling ? "Controlling's key list" : "the vanilla key list"));
        });
        context.waitTicks(2);
        if (!controlling) {
            markerOnScreen(context, shown[0], "vanilla", screen -> vanillaRows((KeyBindsScreen) screen));
        } else {
            report.put("vanilla_note", "Controlling replaces the vanilla key list whenever it opens; the minimal profile checks the vanilla list");
            markerOnScreen(context, shown[0], "controlling", this::controllingRowsOf);
            context.runOnClient(client -> {
                search(shown[0]).setValue("inventory");
            });
            context.waitTicks(2);
            context.runOnClient(client -> scrollTo(shown[0], row(controllingRows(shown[0]), client.options.keyInventory).entry()));
            context.waitTicks(2);
            Path searched = context.takeScreenshot("controls-marker-controlling-search");
            context.runOnClient(client -> {
                Row inventory = row(controllingRows(shown[0]), client.options.keyInventory);
                BufferedImage image = ImageIO.read(searched.toFile());
                double scale = (double) client.getWindow().getScreenWidth() / shown[0].width;
                int x = inventory.keyButton().getX() - ControlsMarker.GAP - ControlsMarker.SIZE;
                int y = inventory.keyButton().getY() + (inventory.keyButton().getHeight() - ControlsMarker.SIZE) / 2;
                check(matching(image, scale, x, y, x + ControlsMarker.SIZE, y + ControlsMarker.SIZE, 0xF1C56C, 40) >= 80,
                        "Controlling's search results keep the marker");
                search(shown[0]).setValue("hotkey wheel");
                long wheelKeys = controllingRows(shown[0]).stream().filter(row -> row.control().getName().startsWith("key.hotkey_wheel.")).count();
                long ownKeys = HotkeyWheelClient.ELYTRA_KEY == null ? 6 : 7;
                check(wheelKeys == ownKeys, "Controlling's search still finds the wheel's own " + ownKeys + " keys");
            });
        }
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private List<Row> controllingRowsOf(Screen screen) throws Exception { return controllingRows(screen); }

    private static EditBox search(Screen screen) {
        return (EditBox) screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
    }

    private interface RowReader {
        List<Row> read(Screen screen) throws Exception;
    }

    private void markerOnScreen(ClientGameTestContext context, Screen screen, String name, RowReader reader) throws Exception {
        context.runOnClient(client -> scrollTo(screen, row(reader.read(screen), client.options.keyInventory).entry()));
        // Away from the list, so no row is hovered in the first screenshot.
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        Path plain = context.takeScreenshot("controls-marker-" + name);
        int[] cursor = context.computeOnClient(client -> {
            List<Row> rows = reader.read(screen);
            Row inventory = row(rows, client.options.keyInventory);
            int index = rows.indexOf(inventory);
            Row neighbour = rows.get(index + 1 < rows.size() ? index + 1 : index - 1);
            check(ControlsMarker.tooltip(neighbour.control()).isEmpty(), "The neighbouring row is a control no wheel uses");
            BufferedImage image = ImageIO.read(plain.toFile());
            double scale = (double) client.getWindow().getScreenWidth() / screen.width;
            int x = inventory.keyButton().getX() - ControlsMarker.GAP - ControlsMarker.SIZE;
            int y = inventory.keyButton().getY() + (inventory.keyButton().getHeight() - ControlsMarker.SIZE) / 2;
            int gold = matching(image, scale, x, y, x + ControlsMarker.SIZE, y + ControlsMarker.SIZE, 0xF1C56C, 40);
            report.put(name + "_marker_pixels", gold);
            check(gold >= 80, name + ": a used control shows the wheel marker beside its key button (" + gold + " pixels)");
            int neighbourY = neighbour.keyButton().getY() + (neighbour.keyButton().getHeight() - ControlsMarker.SIZE) / 2;
            check(matching(image, scale, x, neighbourY, x + ControlsMarker.SIZE, neighbourY + ControlsMarker.SIZE, 0xF1C56C, 40) == 0,
                    name + ": an unused control has no marker");
            check(inventory.keyButton().getX() == neighbour.keyButton().getX() && inventory.resetButton().getX() == neighbour.resetButton().getX()
                            && inventory.resetButton().getX() == inventory.keyButton().getX() + inventory.keyButton().getWidth() + 5,
                    name + ": the marker leaves the key and Reset buttons where the list put them");
            return new int[] {(int) Math.round((x + 4) * scale), (int) Math.round((y + 4) * scale), (int) Math.round(scale)};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(2);
        Path hovered = context.takeScreenshot("controls-marker-" + name + "-tooltip");
        context.runOnClient(client -> {
            int changed = differing(ImageIO.read(plain.toFile()), ImageIO.read(hovered.toFile()),
                    cursor[0], cursor[1] - 40 * cursor[2], cursor[0] + 160 * cursor[2], cursor[1] + 40 * cursor[2]);
            report.put(name + "_tooltip_pixels", changed);
            check(changed > 2000, name + ": hovering the marker shows its tooltip (" + changed + " pixels changed)");
        });

        context.runOnClient(client -> {
            client.options.keyDrop.setKey(client.options.keyDrop.getDefaultKey());
            client.options.keyInventory.setKey(client.options.keyDrop.getDefaultKey());
            ((KeyBindsList) list(screen)).resetMappingAndUpdateButtons();
        });
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(2);
        Path conflict = context.takeScreenshot("controls-marker-" + name + "-conflict");
        context.runOnClient(client -> {
            Row inventory = row(reader.read(screen), client.options.keyInventory);
            BufferedImage image = ImageIO.read(conflict.toFile());
            double scale = (double) client.getWindow().getScreenWidth() / screen.width;
            int stripe = inventory.keyButton().getX() - 6;
            int top = inventory.keyButton().getY() + 1;
            int yellow = matching(image, scale, stripe, top, stripe + 3, top + 20, 0xFFFF00, 30);
            int x = inventory.keyButton().getX() - ControlsMarker.GAP - ControlsMarker.SIZE;
            int y = inventory.keyButton().getY() + (inventory.keyButton().getHeight() - ControlsMarker.SIZE) / 2;
            int gold = matching(image, scale, x, y, x + ControlsMarker.SIZE, y + ControlsMarker.SIZE, 0xF1C56C, 40);
            check(yellow >= 60 && gold >= 80, name + ": the conflict stripe and the marker both show, side by side ("
                    + yellow + " stripe, " + gold + " marker pixels)");
            ((Button) inventory.resetButton()).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(client.options.keyInventory.isDefault(), name + ": Reset still works on a marked row");
            KeyMapping.resetMapping();
        });
    }

    private static final class ClientState {
        private final Map<KeyMapping, InputConstants.Key> keys = new LinkedHashMap<>();
        private final WheelConfig configuration;
        private final net.minecraft.client.CameraType camera;

        private ClientState(Minecraft client) {
            for (KeyMapping mapping : client.options.keyMappings) keys.put(mapping, InputConstants.getKey(mapping.saveString()));
            configuration = WheelConfig.get().copy();
            camera = client.options.getCameraType();
        }

        private void restore(Minecraft client) throws IOException {
            client.gui.setScreen(null);
            WheelMenuReturn.clear();
            keys.forEach(KeyMapping::setKey);
            KeyMapping.resetMapping();
            client.options.setCameraType(camera);
            client.options.save();
            if (!WheelConfig.save(configuration)) throw new IOException("Cannot restore the configuration after the folder QA");
        }
    }
}
