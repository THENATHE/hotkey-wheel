package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The editor's General and wheel tabs, with Save and Cancel, and the key that opens the editor from an open wheel
 * or folder: its default, the hint under the wheel, Safe mode and the editor's key checks.
 */
public final class EditorLayoutTest implements FabricClientGameTest {
    private static final List<String> GENERAL = List.of("Active wheels:", "Cycle while open:", "Configure wheels:",
            "Settings from an open wheel:", "Safe mode:", "Restore keys unbound", "Mod controls:", "Tool Pouch contextual wheel:",
            "Native contextual key uses wheel:", "Wheel key closes menus it opened:");
    private static final List<String> WHEEL = List.of("Opening key:", "Open mode:", "Outside wheel:", "Add", "Add folder", "Remove");
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    private static boolean hasButton(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(candidate -> candidate.getMessage().getString().startsWith(prefix));
    }

    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYBOARD.getOrCreate(code); }

    private static WheelConfig.Entry slot(String control, String icon) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        return entry;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        WheelConfig[] original = {null};
        InputConstants.Key[] originalKeys = new InputConstants.Key[6];
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                for (int index = 0; index < 6; index++) originalKeys[index] = InputConstants.getKey(HotkeyWheelClient.editorKey(index).saveString());
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                defaultKey(client);
                fixture(client);
                tabs(client);
            });
            context.waitTicks(3);
            context.takeScreenshot("editor-general");
            context.runOnClient(client -> press(button(client.gui.screen(), "Wheel 2")));
            context.waitTicks(3);
            context.takeScreenshot("editor-wheel-settings");
            context.runOnClient(client -> {
                saveAndCancel(client);
                keyChecks(client);
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                fromOpenWheel(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel editor layout QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    for (int index = 0; index < 6; index++) HotkeyWheelClient.editorKey(index).setKey(originalKeys[index]);
                    KeyMapping.resetMapping();
                    client.options.save();
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "editor-layout-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** The settings key works out of the box and its default takes no other control's default key. */
    private void defaultKey(Minecraft client) {
        KeyMapping settings = HotkeyWheelClient.SETTINGS_KEY;
        check(settings.getDefaultKey().equals(key(InputConstants.KEY_U)), "Settings from an open wheel defaults to U");
        List<String> sharing = new ArrayList<>();
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping != settings && mapping.getDefaultKey().equals(settings.getDefaultKey())) sharing.add(mapping.getName());
        }
        report.put("default_u_shared_with", sharing);
        check(sharing.isEmpty(), "No other control in this profile defaults to U: " + sharing);
        check(!WheelBindings.reservedKey(settings.getDefaultKey()), "The default is not a key Safe mode reserves");
    }

    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 2;
        List<WheelConfig.Entry> first = configuration.wheels.get(0).entries;
        first.clear();
        first.add(slot("key.inventory", "minecraft:chest"));
        WheelConfig.Entry tools = WheelConfig.Entry.folder("Tools");
        WheelConfig.Entry deep = WheelConfig.Entry.folder("Deep");
        deep.entries.add(slot("key.drop", "minecraft:dropper"));
        tools.entries.add(slot("key.togglePerspective", "minecraft:spyglass"));
        tools.entries.add(deep);
        first.add(tools);
        configuration.wheels.get(1).toggleOpen = true;
        configuration.wheels.get(1).entries.clear();
        configuration.wheels.get(1).entries.add(slot("key.inventory", "hotkey_wheel:icon/cog"));
        InputConstants.Key[] keys = new InputConstants.Key[6];
        keys[0] = key(InputConstants.KEY_G);
        keys[1] = key(InputConstants.KEY_H);
        keys[2] = InputConstants.UNKNOWN;
        keys[3] = key(InputConstants.KEY_TAB);
        keys[4] = InputConstants.UNKNOWN;
        keys[5] = key(InputConstants.KEY_U);
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.getName().startsWith("key.hotkey_wheel.")) continue;
            if (mapping.matches(key(InputConstants.KEY_G)) || mapping.matches(key(InputConstants.KEY_H)) || mapping.matches(key(InputConstants.KEY_U))) {
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
        KeyMapping.resetMapping();
        String error = WheelBindings.save(configuration, keys);
        check(error.isEmpty(), "The editor fixture saves" + (error.isEmpty() ? "" : ": " + error));
    }

    private void tabs(Minecraft client) {
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        check(!editor.showingGeneral() && editor.editedWheel() == 0 && hasButton(editor, "● Wheel 1"),
                "The editor opens on Wheel 1's tab");
        check(hasButton(editor, "General") && hasButton(editor, "Wheel 2") && !hasButton(editor, "Wheel 3"),
                "Tabs: General, then each active wheel");
        for (String label : WHEEL) check(hasButton(editor, label), "A wheel tab shows " + label);
        for (String label : GENERAL) check(!hasButton(editor, label), "A wheel tab leaves " + label + " to General");
        check(button(editor, "Opening key:").getMessage().getString().endsWith(": G"), "The wheel's own opening key is in its settings");
        press(button(editor, "General"));
        check(editor.showingGeneral() && hasButton(editor, "● General"), "General is a tab of its own");
        for (String label : GENERAL) check(hasButton(editor, label), "General shows " + label);
        for (String label : WHEEL) check(!hasButton(editor, label), "General leaves " + label + " to the wheel tabs");
        check(button(editor, "Settings from an open wheel:").getMessage().getString().endsWith(": U"), "General shows the settings key");
    }

    /** Nothing applies until Save, and Cancel discards edits on both tabs. */
    private void saveAndCancel(Minecraft client) throws Exception {
        String before = Files.readString(WheelConfig.path());
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Safe mode: ON"));
        press(button(editor, "Wheel 2"));
        editBox(editor, "Wheel 2").setValue("Renamed");
        press(button(editor, "Open mode: Toggle"));
        check(WheelConfig.get().safeMode && WheelConfig.get().wheels.get(1).name.equals("Wheel 2"), "Edits wait for Save");
        press(button(editor, "Cancel"));
        check(Files.readString(WheelConfig.path()).equals(before), "Cancel discards edits on General and a wheel tab");
        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "Wheel 2"));
        editBox(editor, "Wheel 2").setValue("Renamed");
        press(button(editor, "Outside wheel: Cancel"));
        press(button(editor, "General"));
        check(hasButton(editor, "Active wheels: 2"), "Switching tabs keeps the draft");
        press(button(editor, "Wheel 2"));
        check(editBox(editor, "Renamed") != null && hasButton(editor, "Outside wheel: Select by direction"),
                "Wheel edits survive a visit to General");
        press(button(editor, "Save"));
        WheelConfig.Wheel saved = WheelConfig.get().wheels.get(1);
        check(saved.name.equals("Renamed") && saved.selectOutside && saved.toggleOpen, "Save applies the wheel tab's settings");
        WheelConfig.load();
        check(WheelConfig.get().wheels.get(1).name.equals("Renamed"), "They survive a reload");
    }

    private static EditBox editBox(Screen screen, String value) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getValue().equals(value)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing text field holding \"" + value + "\""));
    }

    private void keyChecks(Minecraft client) {
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Settings from an open wheel"));
        editor.keyPressed(new KeyEvent(InputConstants.KEY_TAB, 0, 0));
        press(button(editor, "Save"));
        check(client.gui.screen() == editor && HotkeyWheelClient.SETTINGS_KEY.matches(key(InputConstants.KEY_U)),
                "The settings key may not share the cycle key");
        press(button(editor, "Settings from an open wheel"));
        editor.keyPressed(new KeyEvent(InputConstants.KEY_H, 0, 0));
        press(button(editor, "Save"));
        check(client.gui.screen() == editor && HotkeyWheelClient.SETTINGS_KEY.matches(key(InputConstants.KEY_U)),
                "The settings key may not share another wheel's opening key");
        press(button(editor, "Settings from an open wheel"));
        editor.mouseClicked(new MouseButtonEvent(0, 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
        press(button(editor, "Save"));
        check(client.gui.screen() == editor && HotkeyWheelClient.SETTINGS_KEY.matches(key(InputConstants.KEY_U)),
                "Safe mode reserves the primary mouse buttons for the settings key too");
        press(button(editor, "Configure wheels"));
        editor.keyPressed(new KeyEvent(InputConstants.KEY_Y, 0, 0));
        press(button(editor, "Settings from an open wheel"));
        editor.keyPressed(new KeyEvent(InputConstants.KEY_Y, 0, 0));
        press(button(editor, "Save"));
        check(client.gui.screen() != editor && HotkeyWheelClient.SETTINGS_KEY.matches(key(InputConstants.KEY_Y))
                        && HotkeyWheelClient.CONFIGURE_KEY.matches(key(InputConstants.KEY_Y)),
                "The settings key may share the configure key, which never acts inside a wheel");
        WheelEditorScreen reset = new WheelEditorScreen(null);
        client.gui.setScreen(reset);
        press(button(reset, "General"));
        press(button(reset, "Configure wheels"));
        reset.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
        press(button(reset, "Settings from an open wheel"));
        reset.keyPressed(new KeyEvent(InputConstants.KEY_U, 0, 0));
        press(button(reset, "Save"));
        check(HotkeyWheelClient.SETTINGS_KEY.matches(key(InputConstants.KEY_U)) && HotkeyWheelClient.CONFIGURE_KEY.isUnbound(),
                "The keys go back to their defaults");
    }

    private static void point(ClientGameTestContext context, int slot) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(slot, screen.slotCount());
            return new double[] {(screen.centerX() + Math.cos(angle) * screen.radius() * 0.76) * client.getWindow().getScreenWidth() / screen.width,
                    (screen.centerY() + Math.sin(angle) * screen.radius() * 0.76) * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private void fromOpenWheel(ClientGameTestContext context) {
        // Hold wheel 1, open Tools and then Deep, and press the settings key.
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.takeScreenshot("editor-key-hint-under-wheel");
        point(context, 1);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        point(context, 1);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen wheel && wheel.folderDepth() == 2,
                "Wheel 1 is open two folders deep"));
        context.getInput().pressKey(InputConstants.KEY_U);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelEditorScreen editor && !editor.showingGeneral()
                        && editor.editedWheel() == 0 && editor.openFolderNames().equals(List.of("Tools", "Deep")),
                "The settings key opens the editor on the open wheel, inside the open folder"));
        context.takeScreenshot("editor-opened-from-wheel");
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelEditorScreen, "Releasing the held wheel key leaves the editor open");
            check(!client.options.keyDrop.isDown() && !client.options.keyInventory.isDown(), "Opening the editor uses no slot");
            press(button(client.gui.screen(), "Cancel"));
            check(client.gui.screen() == null, "Cancel returns to the game");
        });
        // Wheel 2 opens with a toggle press; the settings key then edits wheel 2.
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen wheel && wheel.wheelIndex() == 1, "Wheel 2 opens"));
        context.getInput().pressKey(InputConstants.KEY_U);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelEditorScreen editor && editor.editedWheel() == 1 && editor.openFolderNames().isEmpty(),
                    "From wheel 2 the editor opens on wheel 2");
            client.gui.screen().onClose();
        });
        context.waitTicks(2);
        // Outside a wheel the settings key does nothing, so its default never takes a key from play.
        context.getInput().pressKey(InputConstants.KEY_U);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() == null, "Outside a wheel the settings key does nothing"));
    }
}
