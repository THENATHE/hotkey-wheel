package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.MoveSlotScreen;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Moving slots and folders between levels in the editor: into, out, between, refusals, Save and Cancel. */
public final class MoveSlotTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static String json(Object value) { return new GsonBuilder().create().toJson(value); }

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

    private static WheelConfig.Entry slot(String control, String icon, String label) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        entry.label = label;
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

    private static WheelEditorScreen editor(Minecraft client) {
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        return editor;
    }

    /** Selects a slot of the level being edited by its icon button's description. */
    private static void select(Screen editor, int slot) {
        Button icon = editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith("Slot " + (slot + 1) + ":"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing slot " + (slot + 1)));
        press(icon);
    }

    private static MoveSlotScreen openMove(Minecraft client, Screen editor) {
        press(button(editor, "Move to…"));
        return (MoveSlotScreen) client.gui.screen();
    }

    private static MoveSlotScreen.Destination destination(MoveSlotScreen screen, String name) {
        return screen.destinations().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing destination " + name));
    }

    private static WheelConfig.Entry find(List<WheelConfig.Entry> entries, String label) {
        for (WheelConfig.Entry entry : entries) {
            if (entry.label.equals(label)) return entry;
            if (entry.isFolder()) {
                WheelConfig.Entry inside = find(entry.entries, label);
                if (inside != null) return inside;
            }
        }
        return null;
    }

    private static List<WheelConfig.Entry> top() { return WheelConfig.get().wheels.get(0).entries; }

    private static List<String> labels(List<WheelConfig.Entry> entries) {
        return entries.stream().map(entry -> entry.label).toList();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        WheelConfig[] original = {null};
        Map<KeyMapping, InputConstants.Key> originalKeys = new LinkedHashMap<>();
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                for (KeyMapping mapping : client.options.keyMappings) originalKeys.put(mapping, InputConstants.getKey(mapping.saveString()));
                client.options.guiScale().set(2);
                fixture(client);
                moveIntoFolder(client);
                moveOutAndBetween(client);
                refusals(client);
            });
            context.runOnClient(client -> {
                Screen editor = editor(client);
                select(editor, 0);
                openMove(client, editor);
            });
            context.waitTicks(2);
            context.takeScreenshot("move-slot-picker");
            context.runOnClient(client -> client.gui.screen().onClose());
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel move QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    originalKeys.forEach(KeyMapping::setKey);
                    KeyMapping.resetMapping();
                    client.options.save();
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "move-slot-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /**
     * Wheel 1: Camera (Hold, unbinds its key), Tools [Bag, Deep [Chat]], Full [12 slots],
     * Outer [Middle [Inner []]], Drop.
     */
    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        for (WheelConfig.Wheel wheel : configuration.wheels) wheel.entries.clear();
        WheelConfig.Entry camera = slot(client.options.keyTogglePerspective.getName(), "minecraft:spyglass", "Camera");
        camera.holdAction = true;
        camera.unbindKey = true;
        WheelConfig.Entry full = folder("Full");
        for (int index = 0; index < WheelConfig.MAX_SLOTS; index++) full.entries.add(slot("key.inventory", "minecraft:chest", "Full " + index));
        configuration.wheels.get(0).entries.addAll(List.of(camera,
                folder("Tools", slot("key.inventory", "minecraft:chest", "Bag"), folder("Deep", slot("key.chat", "minecraft:oak_sign", "Chat"))),
                full, folder("Outer", folder("Middle", folder("Inner"))), slot("key.drop", "minecraft:dropper", "Drop")));
        client.options.keyTogglePerspective.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F5));
        KeyMapping.resetMapping();
        String error = WheelBindings.save(configuration, wheelKeys());
        check(error.isEmpty(), "Move fixture saves" + (error.isEmpty() ? "" : ": " + error));
        check(client.options.keyTogglePerspective.isUnbound()
                        && "key.keyboard.f5".equals(WheelConfig.get().managedBindings.get(client.options.keyTogglePerspective.getName())),
                "The fixture's Camera slot has removed and remembered its key");
        report.put("managed_bindings", WheelConfig.get().managedBindings);
    }

    private void moveIntoFolder(Minecraft client) {
        String before = json(WheelConfig.get());
        String camera = json(find(top(), "Camera"));
        Screen editor = editor(client);
        select(editor, 0);
        MoveSlotScreen move = openMove(client, editor);
        check(move.destinations().size() == 7, "The picker lists the wheel and all six folders: " + move.destinations().size());
        check(destination(move, "Wheel 1").refusal() != null && destination(move, "Wheel 1").refusal().contains("already here"),
                "The level the slot is in is refused as already here");
        check(destination(move, "Full").refusal() != null && destination(move, "Full").refusal().startsWith("Full"),
                "A folder with 12 slots is refused as full");
        check(destination(move, "Inner").refusal() == null,
                "A slot can go into a folder three levels deep");
        move.choose(destination(move, "Tools"));
        check(client.gui.screen() == editor, "Choosing a destination returns to the editor");
        check(json(WheelConfig.get()).equals(before), "A move changes nothing before Save");
        press(button(editor, "Cancel"));
        check(json(WheelConfig.get()).equals(before) && find(top(), "Camera") == top().getFirst(),
                "Cancel discards the move");

        editor = editor(client);
        select(editor, 0);
        openMove(client, editor).choose(destination((MoveSlotScreen) client.gui.screen(), "Tools"));
        press(button(editor, "Save"));
        WheelConfig.Entry tools = find(top(), "Tools");
        check(!labels(top()).contains("Camera") && tools.entries.getLast().label.equals("Camera"),
                "Save keeps the slot moved into the folder, after its last slot");
        check(json(tools.entries.getLast()).equals(camera),
                "The moved slot keeps its control, icon, label, Hold action and Unbind normal key");
        check(client.options.keyTogglePerspective.isUnbound()
                        && "key.keyboard.f5".equals(WheelConfig.get().managedBindings.get(client.options.keyTogglePerspective.getName())),
                "Moving keeps the removed key removed and its recovery key remembered");
    }

    private void moveOutAndBetween(Minecraft client) {
        String camera = json(find(top(), "Camera"));
        Screen editor = editor(client);
        select(editor, labels(top()).indexOf("Tools"));
        press(button(editor, "Open folder: Tools"));
        select(editor, 2);
        MoveSlotScreen move = openMove(client, editor);
        move.choose(destination(move, "Wheel 1"));
        press(button(editor, "Save"));
        check(top().getLast().label.equals("Camera") && json(top().getLast()).equals(camera),
                "A slot moves out of a folder to the wheel's top level with its settings");

        String deep = json(find(top(), "Deep"));
        editor = editor(client);
        select(editor, labels(top()).indexOf("Tools"));
        press(button(editor, "Open folder: Tools"));
        select(editor, 1);
        move = openMove(client, editor);
        check(destination(move, "Middle").refusal() == null, "A folder of slots fits two levels down");
        check(destination(move, "Inner").refusal() != null && destination(move, "Inner").refusal().startsWith("Too deep"),
                "A folder cannot go where its slots would sit four levels deep");
        move.choose(destination(move, "Outer"));
        press(button(editor, "Save"));
        WheelConfig.Entry outer = find(top(), "Outer");
        check(outer.entries.getLast().label.equals("Deep") && json(outer.entries.getLast()).equals(deep)
                        && outer.entries.getLast().entries.getFirst().label.equals("Chat"),
                "A folder moves into another folder with everything in it");
        check(find(top(), "Tools").entries.stream().noneMatch(entry -> entry.label.equals("Deep")),
                "The moved folder leaves its old level");
    }

    private void refusals(Minecraft client) {
        // Outer now holds Middle [Inner] and Deep [Chat]: three levels of folders.
        Screen editor = editor(client);
        int outer = labels(top()).indexOf("Outer");
        select(editor, outer);
        MoveSlotScreen move = openMove(client, editor);
        for (String inside : List.of("Outer", "Middle", "Inner", "Deep")) {
            String refusal = destination(move, inside).refusal();
            check(refusal != null && refusal.contains("inside itself"), "A folder cannot go into itself or its folders: " + inside);
        }
        check(destination(move, "Tools").refusal() != null && destination(move, "Tools").refusal().startsWith("Too deep"),
                "A three-level folder cannot go one level down");
        move.choose(destination(move, "Inner"));
        check(client.gui.screen() == move, "Choosing a refused destination does nothing");
        move.onClose();

        WheelConfig.Wheel wheel = new WheelConfig.Wheel();
        wheel.entries.clear();
        for (int index = 0; index < WheelConfig.MAX_SLOTS - 1; index++) wheel.entries.add(slot("key.inventory", "minecraft:chest", "Top " + index));
        WheelConfig.Entry inside = slot("key.inventory", "minecraft:chest", "Inside");
        wheel.entries.add(folder("Holder", inside));
        MoveSlotScreen full = new MoveSlotScreen(null, wheel, wheel.entries.getLast().entries, inside, (entry, destination, name) -> {
            throw new AssertionError("A refused move ran");
        });
        check(full.destinations().getFirst().refusal() != null && full.destinations().getFirst().refusal().startsWith("Full"),
                "Moving out to a wheel level that already has 12 slots is refused");
        press(button(editor, "Cancel"));
    }
}
