package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlOwners;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.ModSettingsActions;
import com.thenathe.hotkeywheel.ToolPouchToggles;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The control picker's folders, which mod each control is credited to, search across folders, and one Mod controls folder. */
public final class ControlFolderTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static List<Button> buttons(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static Button button(Screen screen, String prefix) {
        return buttons(screen).stream().filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    private static EditBox search(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static Object call(Object target, String method) throws Exception {
        Method accessor = target.getClass().getDeclaredMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }

    /** Folder name to the controls it holds, in the picker's order. */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> folders(ControlPickerScreen picker) throws Exception {
        Field field = ControlPickerScreen.class.getDeclaredField("folders");
        field.setAccessible(true);
        Map<String, List<String>> folders = new LinkedHashMap<>();
        for (Object folder : (List<Object>) field.get(picker)) {
            List<String> controls = new ArrayList<>();
            for (Object option : (List<Object>) call(folder, "options")) controls.add((String) call(option, "control"));
            folders.put((String) call(folder, "name"), controls);
        }
        return folders;
    }

    private static String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).orElseThrow().getMetadata().getName();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        WheelConfig[] original = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                client.options.guiScale().set(2);
                attribution(client);
                separateFolders(client);
            });
            context.waitTicks(3);
            context.takeScreenshot("controls-folders");
            context.runOnClient(client -> press(button(client.gui.screen(), ControlPickerScreen.MINECRAFT_FOLDER)));
            context.waitTicks(3);
            context.takeScreenshot("controls-folder-minecraft");
            context.runOnClient(client -> {
                Screen picker = client.gui.screen();
                check(button(picker, "‹ All folders").active, "Inside a folder the first row leads back");
                picker.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                check(client.gui.screen() == picker && hasButton(picker, ControlPickerScreen.MINECRAFT_FOLDER),
                        "Escape inside a folder goes back to the folder list instead of closing");
                String folder = FabricLoader.getInstance().isModLoaded("toolpouch") ? modName("toolpouch")
                        : FabricLoader.getInstance().isModLoaded("buildprint") ? modName("buildprint") : null;
                if (folder != null) press(button(picker, folder));
            });
            context.waitTicks(3);
            context.takeScreenshot("controls-folder-mod");
            context.runOnClient(client -> {
                search(client.gui.screen()).setValue("inventory");
            });
            context.waitTicks(3);
            context.takeScreenshot("controls-search-all-folders");
            context.runOnClient(client -> {
                searchAcrossFolders(client);
                oneModFolder(client);
            });
            context.waitTicks(3);
            context.takeScreenshot("controls-one-mod-folder");
            context.runOnClient(this::editorSetting);
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel control folders QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "control-folder-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** The settings file, or nothing when this run has not saved one yet. */
    private static String savedText() throws IOException {
        return Files.isRegularFile(WheelConfig.path()) ? Files.readString(WheelConfig.path()) : "";
    }

    private static boolean hasButton(Screen screen, String prefix) {
        return buttons(screen).stream().anyMatch(candidate -> candidate.getMessage().getString().startsWith(prefix));
    }

    private void attribution(Minecraft client) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (KeyMapping mapping : client.options.keyMappings) owners.put(mapping.getName(), ControlOwners.modId(mapping).orElse("(other)"));
        report.put("owners", owners);
        check(owners.get("key.inventory").equals("minecraft") && owners.get("key.attack").equals("minecraft")
                && owners.get("key.hotbar.1").equals("minecraft"), "Minecraft's own controls belong to Minecraft");
        check(owners.get("key.hotkey_wheel.wheel1").equals("hotkey_wheel"), "Hotkey Wheel's keys belong to Hotkey Wheel");
        check(owners.values().stream().noneMatch(owner -> owner.equals("fabric-key-mapping-api-v1")),
                "Fabric API's registration helper is never credited with other mods' controls");
        for (Map.Entry<String, String> owner : owners.entrySet()) {
            String name = owner.getKey();
            if (name.startsWith("key.buildprint.")) check(owner.getValue().equals("buildprint"), name + " belongs to Buildprint");
            if (name.startsWith("key.buildprint_toning.")) check(owner.getValue().equals("buildprint_toning"), name + " belongs to Blocktone");
            if (name.startsWith("key.toolpouch.")) check(owner.getValue().equals("toolpouch"), name + " belongs to Tool Pouch");
            if (name.startsWith("key.controlling.")) check(owner.getValue().equals("controlling"), name + " belongs to Controlling");
        }
    }

    private void separateFolders(Minecraft client) throws Exception {
        ControlPickerScreen picker = new ControlPickerScreen(null, control -> { }, false);
        client.gui.setScreen(picker);
        Map<String, List<String>> folders = folders(picker);
        report.put("folders", folders.keySet());
        List<String> names = new ArrayList<>(folders.keySet());
        check(names.getFirst().equals(ControlPickerScreen.MINECRAFT_FOLDER), "Minecraft controls is the first folder");
        int settingsAt = names.indexOf(ControlPickerScreen.MOD_SETTINGS_FOLDER);
        check(modMenu ? settingsAt == 1 : settingsAt < 0, modMenu ? "Mod settings is the second folder" : "Without Mod Menu there is no Mod settings folder");
        List<String> modFolders = names.subList(modMenu ? 2 : 1, names.size() - (names.getLast().equals(ControlPickerScreen.OTHER_FOLDER) ? 1 : 0));
        List<String> sorted = new ArrayList<>(modFolders);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        boolean modControls = FabricLoader.getInstance().isModLoaded("buildprint");
        check(modFolders.isEmpty() != modControls && modFolders.equals(sorted), "A folder for each mod follows, sorted by name: " + modFolders);
        check(!names.contains(ControlPickerScreen.MOD_CONTROLS_FOLDER), "By default each mod has its own folder");
        List<String> minecraft = folders.get(ControlPickerScreen.MINECRAFT_FOLDER);
        check(minecraft.contains("key.inventory") && minecraft.contains("key.attack") && minecraft.stream().allMatch(name ->
                        ControlOwners.modId(HotkeyWheelClient.findControl(name)).orElse("").equals("minecraft")),
                "Minecraft controls holds exactly Minecraft's own controls");
        if (modMenu) {
            check(folders.get(ControlPickerScreen.MOD_SETTINGS_FOLDER).contains(ModSettingsActions.control("hotkey_wheel")),
                    "Mod settings holds the Open mod settings actions");
        }
        if (FabricLoader.getInstance().isModLoaded("buildprint")) {
            List<String> buildprint = folders.get(modName("buildprint"));
            check(buildprint != null && buildprint.contains("key.buildprint.excavator"), "Buildprint's folder holds its controls");
        }
        if (FabricLoader.getInstance().isModLoaded("buildprint_toning") && folders.containsKey(modName("buildprint_toning"))) {
            check(folders.get(modName("buildprint_toning")).stream().allMatch(name -> name.startsWith("key.buildprint_toning.")),
                    "Blocktone's folder holds only Blocktone's controls");
        }
        if (!ToolPouchToggles.choices().isEmpty()) {
            List<String> pouch = folders.get(modName("toolpouch"));
            check(pouch != null && pouch.containsAll(ToolPouchToggles.choices().stream().map(ToolPouchToggles.Choice::control).toList())
                    && pouch.stream().anyMatch(name -> name.startsWith("key.toolpouch.")),
                    "Tool Pouch's folder holds its controls and its slot toggles");
        }
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (List<String> controls : folders.values()) {
            total += controls.size();
            seen.addAll(controls);
        }
        long mappings = List.of(client.options.keyMappings).stream().filter(mapping -> !mapping.getName().contains("hotkey_wheel")).count();
        int expected = (int) mappings + ToolPouchToggles.choices().size() + ModSettingsActions.choices().size();
        check(total == seen.size() && total == expected, "Every control is in exactly one folder (" + total + " of " + expected + ")");
        int minecraftCount = minecraft.size();
        check(hasButton(picker, ControlPickerScreen.MINECRAFT_FOLDER + " · " + minecraftCount + " controls"),
                "The picker opens on the list of folders, each with its control count");
        report.put("other_controls", folders.getOrDefault(ControlPickerScreen.OTHER_FOLDER, List.of()));
    }

    private void searchAcrossFolders(Minecraft client) {
        Screen picker = client.gui.screen();
        search(picker).setValue("inventory");
        String inventory = Component.translatable("key.inventory").getString();
        check(button(picker, inventory).getMessage().getString().endsWith(" · " + ControlPickerScreen.MINECRAFT_FOLDER),
                "A search lists matches from every folder with their folder name");
        if (FabricLoader.getInstance().isModLoaded("buildprint")) {
            search(picker).setValue("excavator");
            check(buttons(picker).stream().anyMatch(button -> button.getMessage().getString().endsWith(" · " + modName("buildprint"))),
                    "A search finds a mod's control from outside its folder");
        }
        String[] chosen = {null};
        ControlPickerScreen choosing = new ControlPickerScreen(null, control -> chosen[0] = control, false);
        client.gui.setScreen(choosing);
        press(button(choosing, ControlPickerScreen.MINECRAFT_FOLDER));
        // Page through the folder, as a player would, to the Inventory category.
        for (int page = 0; page < 10 && !hasButton(choosing, inventory); page++) {
            choosing.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
        }
        check(button(choosing, "‹ All folders").active, "Every page of a folder leads back");
        press(button(choosing, inventory));
        check("key.inventory".equals(chosen[0]), "Choosing a control from inside a folder saves its name");
    }

    private void oneModFolder(Minecraft client) throws Exception {
        ControlPickerScreen separate = new ControlPickerScreen(null, control -> { }, false);
        client.gui.setScreen(separate);
        Map<String, List<String>> perModFolders = folders(separate);
        ControlPickerScreen picker = new ControlPickerScreen(null, control -> { }, true);
        client.gui.setScreen(picker);
        Map<String, List<String>> merged = folders(picker);
        report.put("merged_folders", merged.keySet());
        List<String> names = new ArrayList<>(merged.keySet());
        Set<String> expected = new java.util.LinkedHashSet<>(List.of(ControlPickerScreen.MINECRAFT_FOLDER));
        if (modMenu) expected.add(ControlPickerScreen.MOD_SETTINGS_FOLDER);
        Set<String> shared = Set.of(ControlPickerScreen.MINECRAFT_FOLDER, ControlPickerScreen.MOD_SETTINGS_FOLDER, ControlPickerScreen.OTHER_FOLDER);
        int perMod = perModFolders.entrySet().stream().filter(entry -> !shared.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue().size()).sum();
        if (perMod > 0) expected.add(ControlPickerScreen.MOD_CONTROLS_FOLDER);
        if (merged.containsKey(ControlPickerScreen.OTHER_FOLDER)) expected.add(ControlPickerScreen.OTHER_FOLDER);
        check(names.equals(new ArrayList<>(expected)), "One Mod controls folder replaces the per-mod folders: " + names);
        if (perMod == 0) return;
        check(merged.get(ControlPickerScreen.MOD_CONTROLS_FOLDER).size() == perMod, "Mod controls holds every mod's controls");
        press(button(picker, ControlPickerScreen.MOD_CONTROLS_FOLDER));
    }

    /** The general setting reaches the picker opened from a slot, is saved only when on, and Cancel discards it. */
    private void editorSetting(Minecraft client) throws Exception {
        WheelEditorScreen editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Mod controls: A folder for each mod"));
        check(hasButton(editor, "Mod controls: One Mod controls folder"), "General settings switch the picker to one Mod controls folder");
        press(button(editor, "Wheel 1"));
        press(button(editor, "Control:"));
        Field oneFolder = ControlPickerScreen.class.getDeclaredField("oneModFolder");
        oneFolder.setAccessible(true);
        check(client.gui.screen() instanceof ControlPickerScreen picker && oneFolder.getBoolean(picker)
                        && (!FabricLoader.getInstance().isModLoaded("buildprint") || folders(picker).containsKey(ControlPickerScreen.MOD_CONTROLS_FOLDER)),
                "The picker opened from a slot follows the unsaved setting");
        client.gui.screen().onClose();
        press(button(editor, "Cancel"));
        check(!WheelConfig.get().oneModControlsFolder() && !savedText().contains("modControlsInOneFolder"),
                "Cancel discards the setting");
        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Mod controls:"));
        press(button(editor, "Save"));
        check(WheelConfig.get().oneModControlsFolder() && savedText().contains("\"modControlsInOneFolder\": true"),
                "Save keeps one Mod controls folder");
        WheelConfig.load();
        check(WheelConfig.get().oneModControlsFolder(), "The setting survives a reload");
        editor = new WheelEditorScreen(null);
        client.gui.setScreen(editor);
        press(button(editor, "General"));
        press(button(editor, "Mod controls:"));
        press(button(editor, "Save"));
        check(!savedText().contains("modControlsInOneFolder"),
                "Back at the default, the setting is left out of the file, as in earlier releases");
    }
}
