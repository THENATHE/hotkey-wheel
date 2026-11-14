package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.IconPickerScreen;
import com.thenathe.hotkeywheel.ModIcons;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelIcons;
import com.thenathe.hotkeywheel.WheelScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Hotkey Wheel's own icons and mods' icons in the icon picker: the order with no search, searching all three
 * sources, choosing them for slots and folders, Save and reload, a removed mod, and mods with unusable icons.
 * The saved settings and keys are left in {@code restart/} for RestartTest, which loads them in a new game.
 */
public final class IconPickerTest implements FabricClientGameTest {
    // Fixture mods that run.py adds to the profile, each with a declared icon that is hard to use, and one without.
    private static final String WIDE = "hkwqa_wide_icon";
    private static final List<String> UNUSABLE = List.of("hkwqa_broken_icon", "hkwqa_huge_icon", "hkwqa_missing_icon");
    private static final String NO_ICON = "hkwqa_no_icon";
    private static final String REMOVED = WheelIcons.modIcon("hkwqa_removed_mod");
    private static final int LIGHT = 0xF2F2F2;
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
                // An exact label wins over a longer one that starts the same, such as a tab named "Saved…".
                .sorted(java.util.Comparator.comparing(candidate -> !candidate.getMessage().getString().equals(prefix)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    private static EditBox search(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    /** The saved IDs the picker shows, in order, across every page. */
    @SuppressWarnings("unchecked")
    private static List<String> shown(Screen picker) throws Exception {
        Field filtered = IconPickerScreen.class.getDeclaredField("filtered");
        filtered.setAccessible(true);
        List<String> ids = new ArrayList<>();
        for (Object icon : (List<Object>) filtered.get(picker)) {
            var id = icon.getClass().getDeclaredMethod("id");
            id.setAccessible(true);
            ids.add((String) id.invoke(icon));
        }
        return ids;
    }

    private static InputConstants.Key[] keys(InputConstants.Key settings) {
        InputConstants.Key[] keys = new InputConstants.Key[6];
        for (int index = 0; index < 5; index++) keys[index] = InputConstants.getKey(HotkeyWheelClient.editorKey(index).saveString());
        keys[5] = settings;
        return keys;
    }

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
        InputConstants.Key[] originalKeys = {null, null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                originalKeys[0] = InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[0].saveString());
                originalKeys[1] = InputConstants.getKey(HotkeyWheelClient.SETTINGS_KEY.saveString());
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                for (KeyMapping mapping : client.options.keyMappings) {
                    if (!mapping.getName().startsWith("key.hotkey_wheel.")
                            && mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G))) mapping.setKey(InputConstants.UNKNOWN);
                }
                KeyMapping.resetMapping();
                modIcons(client);
                mainMenuPicker(client);
            });
            context.waitTicks(3);
            context.takeScreenshot("icons-picker-main-menu");
            context.runOnClient(client -> client.gui.setScreen(null));
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                pickerOrder(context);
                choose(context);
                wheel(context);
                persistence(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel icon QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    HotkeyWheelClient.WHEEL_KEYS[0].setKey(originalKeys[0]);
                    HotkeyWheelClient.SETTINGS_KEY.setKey(originalKeys[1]);
                    KeyMapping.resetMapping();
                    client.options.save();
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "icon-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    private void modIcons(Minecraft client) {
        List<String> listed = ModIcons.choices().stream().map(ModIcons.Choice::modId).toList();
        report.put("mod_icons", listed);
        check(listed.contains("hotkey_wheel") && listed.contains("fabric-api"), "Installed mods that declare an icon are listed");
        check(!listed.contains("minecraft") && !listed.contains("java"), "Built-in entries are left out");
        check(listed.stream().noneMatch(id -> FabricLoader.getInstance().getModContainer(id).orElseThrow().getContainingMod()
                        .filter(container -> container.getMetadata().getId().equals("fabric-api")).isPresent()),
                "Fabric API's modules, which repeat Fabric API's icon, are listed once as Fabric API");
        check(!listed.contains(NO_ICON) && !listed.contains("hotkey_wheel_qa"), "A mod without an icon is not listed");
        check(listed.contains(WIDE) && listed.containsAll(UNUSABLE), "Mods whose declared icon is unusable are still listed");
        if (FabricLoader.getInstance().isModLoaded("vpp_mod_suite")) {
            check(listed.contains("vpp_mod_suite") && listed.contains("toolpouch"),
                    "Vanilla++ and the Tool Pouch it bundles, which has its own icon, are both listed");
        }
        TextureManager textures = client.getTextureManager();
        Identifier wide = ModIcons.texture(WIDE).orElseThrow(() -> new AssertionError("The non-square icon loads"));
        var loaded = textures.getTexture(wide);
        var pixels = ((DynamicTexture) loaded).getPixels();
        check(pixels.getWidth() == 64 && pixels.getHeight() == 64, "Mod icons are fitted into a 64 by 64 texture");
        check(pixels.getPixel(32, 2) >>> 24 == 0 && pixels.getPixel(32, 32) >>> 24 == 255 && pixels.getPixel(2, 32) >>> 24 == 255,
                "A wide icon keeps its proportions, centred with transparent bands above and below");
        check(ModIcons.texture(WIDE).orElseThrow().equals(wide), "An icon is uploaded once and then reused");
        for (String modId : UNUSABLE) {
            check(ModIcons.texture(modId).isEmpty(), modId + ": an unusable icon is refused without a crash");
        }
        check(ModIcons.texture("hkwqa_removed_mod").isEmpty(), "A mod that is not installed has no icon texture");
        ModIcons.releaseExcept(Set.of());
        check(textures.getTexture(wide) != loaded, "Releasing frees the uploaded texture");
        check(ModIcons.texture(WIDE).isPresent() && textures.getTexture(wide) instanceof DynamicTexture,
                "A released icon loads again when it is next needed");
    }

    /** Before joining a world, Hotkey Wheel's and mods' icons can already be drawn; blocks and items show names. */
    private void mainMenuPicker(Minecraft client) throws Exception {
        String[] chosen = {null};
        IconPickerScreen picker = new IconPickerScreen(null, "minecraft:stone", icon -> chosen[0] = icon);
        client.gui.setScreen(picker);
        List<String> ids = shown(picker);
        check(ids.getFirst().equals(WheelIcons.GLYPH_PREFIX + "cog"), "Before joining a world the picker still starts with the cog");
        search(picker).setValue("check");
        press(button(picker, "Check mark"));
        check((WheelIcons.GLYPH_PREFIX + "check").equals(chosen[0]), "A Hotkey Wheel icon can be chosen before joining a world");
        client.gui.setScreen(new IconPickerScreen(null, "minecraft:stone", icon -> { }));
    }

    private void pickerOrder(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            IconPickerScreen picker = new IconPickerScreen(null, "minecraft:chest", icon -> { });
            client.gui.setScreen(picker);
            List<String> ids = shown(picker);
            List<String> glyphs = WheelIcons.GLYPHS.stream().map(WheelIcons.Glyph::id).toList();
            List<String> mods = ModIcons.choices().stream().map(choice -> WheelIcons.modIcon(choice.modId())).toList();
            report.put("glyphs", glyphs);
            check(glyphs.size() >= 39 && ids.subList(0, glyphs.size()).equals(glyphs),
                    "With no search, Hotkey Wheel's " + glyphs.size() + " icons come first, cog first");
            check(ids.subList(glyphs.size(), glyphs.size() + mods.size()).equals(mods), "Mods' icons follow, sorted by mod name");
            List<String> items = ids.subList(glyphs.size() + mods.size(), ids.size());
            check(items.size() > 1000 && items.stream().noneMatch(id -> id.startsWith("hotkey_wheel:")) && items.contains("minecraft:stone"),
                    "Blocks and items, modded ones included, come after them");
            for (String required : List.of("cog", "check", "close", "bulleted_list", "numbered_list", "plus", "minus", "star", "heart",
                    "home", "folder", "search", "pencil", "trash", "refresh", "arrow_up", "arrow_down", "arrow_left", "arrow_right",
                    "play", "pause", "lock", "eye", "info", "warning", "question", "bookmark", "flag", "map_pin", "clock", "sun",
                    "moon", "bell", "key", "lightning", "power", "person", "chat")) {
                check(glyphs.contains(WheelIcons.GLYPH_PREFIX + required) && client.getResourceManager()
                                .getResource(Identifier.fromNamespaceAndPath("hotkey_wheel", "textures/gui/sprites/icon/" + required + ".png")).isPresent(),
                        "Icon " + required + " is offered and its sprite is packaged");
            }
        });
        context.waitTicks(3);
        context.takeScreenshot("icons-picker-empty-search");
        Map<String, List<String>> searches = new LinkedHashMap<>();
        report.put("searches", searches);
        context.runOnClient(client -> {
            Screen picker = client.gui.screen();
            for (String query : List.of("settings", "gear", "x", "list", "book", "hotkey", "stone", "qa icon")) {
                search(picker).setValue(query);
                List<String> ids = shown(picker);
                searches.put(query, ids.subList(0, Math.min(8, ids.size())));
            }
            check(searches.get("settings").getFirst().equals(WheelIcons.GLYPH_PREFIX + "cog")
                    && searches.get("gear").getFirst().equals(WheelIcons.GLYPH_PREFIX + "cog"), "Aliases find the cog: settings, gear");
            check(searches.get("x").getFirst().equals(WheelIcons.GLYPH_PREFIX + "close"), "An exact alias ranks first: x is Close");
            check(searches.get("list").subList(0, 2).containsAll(List.of(WheelIcons.GLYPH_PREFIX + "bulleted_list", WheelIcons.GLYPH_PREFIX + "numbered_list")),
                    "list finds both list icons first");
            search(picker).setValue("book");
            List<String> book = shown(picker);
            int item = book.indexOf("minecraft:book");
            int bookmark = book.indexOf(WheelIcons.GLYPH_PREFIX + "bookmark");
            int shelf = book.indexOf("minecraft:bookshelf");
            int enchanted = book.indexOf("minecraft:enchanted_book");
            check(item == 0 && bookmark == 1 && shelf > bookmark && enchanted > shelf,
                    "A search covers all sources: the exact item Book, then names starting with book (Hotkey Wheel's first), then words");
            if (FabricLoader.getInstance().isModLoaded("brb")) {
                int recipeBook = book.indexOf(WheelIcons.modIcon("brb"));
                check(recipeBook > shelf && recipeBook < enchanted, "Among equal matches mods' icons come before items: " + book.subList(0, Math.min(12, book.size())));
            }
            search(picker).setValue("hotkey");
            check(shown(picker).getFirst().equals(WheelIcons.modIcon("hotkey_wheel")), "A mod's name finds its icon");
            search(picker).setValue("hotkey_wheel");
            check(shown(picker).contains(WheelIcons.modIcon("hotkey_wheel")), "A mod's ID finds its icon");
            search(picker).setValue("stone");
            check(shown(picker).getFirst().equals("minecraft:stone"), "An exact block name still comes first");
            search(picker).setValue("minecraft:iron_pickaxe");
            check(shown(picker).equals(List.of("minecraft:iron_pickaxe")), "Registry IDs still search");
            search(picker).setValue("qa icon");
        });
        context.waitTicks(3);
        context.takeScreenshot("icons-picker-broken-mod-icons");
        context.runOnClient(client -> {
            check(shown(client.gui.screen()).containsAll(List.of(WheelIcons.modIcon(WIDE), WheelIcons.modIcon(UNUSABLE.get(0)))),
                    "Mods with unusable icons are drawn with the fallback, and the picker keeps working");
            search(client.gui.screen()).setValue("");
        });
        context.waitTicks(3);
        context.takeScreenshot("icons-picker-generics-first");
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    /** Through the editor: a Hotkey Wheel icon and a mod icon for a slot, and the same for folders. */
    private void choose(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            WheelConfig configuration = new WheelConfig();
            configuration.wheelCount = 1;
            List<WheelConfig.Entry> entries = configuration.wheels.get(0).entries;
            entries.clear();
            entries.add(slot("key.inventory", "minecraft:chest"));
            entries.add(slot("key.drop", "minecraft:dropper"));
            WheelConfig.Entry tools = WheelConfig.Entry.folder("Tools");
            tools.entries.add(slot("key.inventory", "minecraft:book"));
            entries.add(tools);
            entries.add(WheelConfig.Entry.folder("Mods"));
            entries.add(slot("key.togglePerspective", REMOVED));
            check(WheelBindings.save(configuration, keys(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_U))).isEmpty(),
                    "The icon fixture saves");

            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            chooseFor(client, editor, "Slot 1", "cog", "Cog");
            chooseFor(client, editor, "Slot 2", "hotkey wheel", "Hotkey Wheel\nMod icon");
            chooseFor(client, editor, "Slot 3", "star", "Star");
            chooseFor(client, editor, "Slot 4", "fabric api", "Fabric API\nMod icon");
            press(button(editor, "Slot 3"));
            press(button(editor, "Open folder: Tools"));
            chooseFor(client, editor, "Slot 1", "warning", "Warning");
            press(button(editor, "Wheel 1"));
            press(button(editor, "Save"));
            List<WheelConfig.Entry> saved = WheelConfig.get().wheels.get(0).entries;
            check(saved.get(0).icon.equals(WheelIcons.GLYPH_PREFIX + "cog"), "A slot saves a Hotkey Wheel icon as hotkey_wheel:icon/cog");
            check(saved.get(1).icon.equals(WheelIcons.modIcon("hotkey_wheel")), "A slot saves a mod icon as hotkey_wheel:mod/hotkey_wheel");
            check(saved.get(2).icon.equals(WheelIcons.GLYPH_PREFIX + "star") && saved.get(2).isFolder(), "A folder takes a Hotkey Wheel icon");
            check(saved.get(3).icon.equals(WheelIcons.modIcon("fabric-api")) && saved.get(3).isFolder(), "A folder takes a mod icon");
            check(saved.get(2).entries.get(0).icon.equals(WheelIcons.GLYPH_PREFIX + "warning"), "Slots inside folders take the new icons");
            check(saved.get(4).icon.equals(REMOVED), "A removed mod's icon keeps its saved value through an editor Save");
        });
        context.waitTicks(2);
        context.runOnClient(client -> client.gui.setScreen(new WheelEditorScreen(null)));
        context.waitTicks(3);
        context.takeScreenshot("icons-editor-slots");
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private void chooseFor(Minecraft client, WheelEditorScreen editor, String slot, String query, String tile) {
        press(button(editor, slot));
        press(button(editor, "Choose icon"));
        Screen picker = client.gui.screen();
        check(picker instanceof IconPickerScreen, slot + " opens the icon picker");
        search(picker).setValue(query);
        press(button(picker, tile));
        check(client.gui.screen() == editor, "Choosing " + tile.replace('\n', ' ') + " returns to the editor");
    }

    /** The wheel draws each kind of icon: light glyph pixels where Hotkey Wheel's icons are, and the fallback for a removed mod. */
    private void wheel(ClientGameTestContext context) throws Exception {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.getInput().setCursorPos(640, 400);
        context.waitTicks(2);
        Path screenshot = context.takeScreenshot("icons-wheel");
        context.runOnClient(client -> {
            WheelScreen wheel = (WheelScreen) client.gui.screen();
            BufferedImage image = ImageIO.read(screenshot.toFile());
            double scale = (double) client.getWindow().getScreenWidth() / wheel.width;
            Map<String, Integer> light = new LinkedHashMap<>();
            for (int slot = 0; slot < wheel.slotCount(); slot++) {
                double angle = RadialGeometry.angle(slot, wheel.slotCount());
                int x = wheel.centerX() + (int) Math.round(Math.cos(angle) * wheel.radius() * 0.73);
                int y = wheel.centerY() + (int) Math.round(Math.sin(angle) * wheel.radius() * 0.73);
                light.put("slot " + (slot + 1), lightPixels(image, scale, x - 8, y - 8, x + 8, y + 8));
            }
            report.put("wheel_light_pixels", light);
            check(light.get("slot 1") >= 80, "The cog draws on the wheel: " + light);
            check(light.get("slot 3") >= 80, "A folder's star draws on the wheel");
            check(light.get("slot 5") >= 60, "A removed mod's slot shows the light puzzle-piece fallback");
            check(light.get("slot 2") < 80, "A mod icon draws the mod's own picture, not a fallback");
        });
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> client.gui.setScreen(null));
        // Open the Tools folder to see its icon inside.
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        double[] tools = context.computeOnClient(client -> {
            WheelScreen wheel = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(2, wheel.slotCount());
            return new double[] {(wheel.centerX() + Math.cos(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenWidth() / wheel.width,
                    (wheel.centerY() + Math.sin(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenHeight() / wheel.height};
        });
        context.getInput().setCursorPos(tools[0], tools[1]);
        context.waitTicks(2);
        context.takeScreenshot("icons-wheel-hover-folder");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.takeScreenshot("icons-wheel-in-folder");
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen wheel && wheel.folderDepth() == 1,
                "The folder with a Hotkey Wheel icon opens"));
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private static int lightPixels(BufferedImage image, double scale, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = (int) (top * scale); y < (int) (bottom * scale); y++) {
            for (int x = (int) (left * scale); x < (int) (right * scale); x++) {
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                if (Math.abs((rgb >> 16 & 0xFF) - (LIGHT >> 16 & 0xFF)) < 12 && Math.abs((rgb >> 8 & 0xFF) - (LIGHT >> 8 & 0xFF)) < 12
                        && Math.abs((rgb & 0xFF) - (LIGHT & 0xFF)) < 12) count++;
            }
        }
        return count;
    }

    /** Save and reload keep the new icons; the file and keys are left for RestartTest to load in a new game. */
    private void persistence(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            String before = Files.readString(WheelConfig.path());
            WheelConfig.load();
            List<WheelConfig.Entry> loaded = WheelConfig.get().wheels.get(0).entries;
            check(loaded.get(0).icon.equals(WheelIcons.GLYPH_PREFIX + "cog") && loaded.get(3).icon.equals(WheelIcons.modIcon("fabric-api"))
                            && loaded.get(4).icon.equals(REMOVED), "Reloading the file keeps Hotkey Wheel, mod and removed-mod icons");
            check(before.contains("\"icon\": \"hotkey_wheel:icon/cog\"") && before.contains("\"icon\": \"hotkey_wheel:mod/hotkey_wheel\""),
                    "The file stores the icons in the slot's existing icon field");
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            // A key that only works inside a wheel, so its persistence is checked after the restart too.
            press(button(editor, "General"));
            press(button(editor, "Settings from an open wheel"));
            editor.keyPressed(new KeyEvent(InputConstants.KEY_P, 0, 0));
            press(button(editor, "Save"));
            check(HotkeyWheelClient.SETTINGS_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_P)),
                    "The editor binds the settings key");
            check(Files.readString(WheelConfig.path()).equals(before), "An editor Save with no slot changes writes the same file");
            // The look and the elytra switch are saved the moment they change; the restart checks both come back.
            check(WheelConfig.update(config -> {
                config.setHouseLook(false);
                config.setElytraFlightOff(true);
            }), "The Minecraft look and elytra flight off are saved for the restart");
            Path restart = Path.of(System.getProperty("wheel.qa.root"), "restart");
            Files.createDirectories(restart);
            Files.copy(WheelConfig.path(), restart.resolve("hotkey-wheel.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<String> keyLines = new ArrayList<>();
            for (String line : Files.readAllLines(client.options.getFile().toPath())) {
                if (line.startsWith("key_key.hotkey_wheel.")) keyLines.add(line);
            }
            Files.write(restart.resolve("keys.txt"), keyLines);
            report.put("restart_keys", keyLines);
            check(keyLines.contains("key_key.hotkey_wheel.settings:key.keyboard.p"), "options.txt keeps the settings key for the restart");
            Set<String> modsInUse = new HashSet<>(WheelIcons.modsUsedBy(WheelConfig.get().allEntries()));
            check(modsInUse.equals(Set.of("hotkey_wheel", "fabric-api", "hkwqa_removed_mod")), "The textures in use are known: " + modsInUse);
        });
    }
}
