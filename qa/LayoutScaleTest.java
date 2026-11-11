package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.MoveSlotScreen;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.ToolPouchIntegration;
import com.thenathe.hotkeywheel.ToolPouchRadialScreen;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Layout at GUI scales 2, 3 and 4 and in small windows: the Move to… subtitle fits the screen and says "go" for a
 * folder; under the wheel, its folders and the Tool Pouch item radial, the hovered name, the key line and the hint
 * stay together above the hearts and hotbar, and the title stays clear of the radial.
 */
public final class LayoutScaleTest implements FabricClientGameTest {
    // The hotbar is 22 GUI pixels tall and the hearts row sits 17 above it.
    private static final int HEARTS_TOP_FROM_BOTTOM = 39;
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
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

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        WheelConfig[] original = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                client.options.pauseOnLostFocus = false;
                unchangedGeometry();
                fixture(client);
            });
            for (int scale : new int[] {2, 3}) {
                context.runOnClient(client -> client.options.guiScale().set(scale));
                context.waitTicks(2);
                moveTo(context, scale);
            }
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                if (FabricLoader.getInstance().isModLoaded("buildprint")) {
                    // A new game shows Buildprint's welcome screen once; mark it seen, as the other fixtures do.
                    context.runOnClient(client -> {
                        Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                                .getConstructor().newInstance();
                        onboarding.getClass().getMethod("markDone").invoke(onboarding);
                        client.gui.setScreen(null);
                    });
                    context.waitTicks(2);
                }
                // Window width, height and GUI scale; 0 is Minecraft's automatic scale, 1 for these small windows.
                // Minecraft caps the scale by window size, so GUI scale 4 is checked at 1920×1080.
                // 640×480 at scale 2 is 320×240, the smallest GUI Minecraft allows; every hint still fits on one line there.
                int[][] screens = {{1280, 800, 2}, {1280, 800, 3}, {1920, 1080, 4}, {640, 360, 0}, {683, 384, 0}, {640, 480, 2}};
                for (int[] screen : screens) {
                    context.getInput().resizeWindow(screen[0], screen[1]);
                    context.runOnClient(client -> client.options.guiScale().set(screen[2]));
                    context.waitTicks(3);
                    String name = screen[2] == 0 || screen[0] == 640 ? "window-" + screen[0] + "x" + screen[1] : "scale-" + screen[2];
                    int expectedScale = screen[2] == 0 ? 1 : screen[2];
                    context.runOnClient(client -> check(client.getWindow().getGuiScale() == expectedScale,
                            name + " renders at GUI scale " + expectedScale + " (" + client.getWindow().getGuiScale() + ")"));
                    wheel(context, name);
                }
                context.getInput().resizeWindow(1280, 800);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel GUI scale QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    client.options.guiScale().set(2);
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "layout-scale-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** 1.1.1's radial placement, for the sizes where the name, key line and hint still fit above the hearts. */
    private void unchangedGeometry() {
        int[][] sizes = {{640, 400}, {683, 384}, {720, 450}, {960, 540}};
        for (int[] size : sizes) {
            int centerY = (size[1] - 32) / 2;
            int radius = Math.max(36, Math.min(110, Math.min(size[0] / 2 - 22, (size[1] - 90) / 2)));
            RadialGeometry.Layout layout = RadialGeometry.layout(size[0], size[1], 1);
            check(layout.centerY() == centerY && layout.radius() == radius,
                    "A " + size[0] + "×" + size[1] + " GUI keeps 1.1.1's wheel geometry");
        }
    }

    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        List<WheelConfig.Entry> entries = configuration.wheels.get(0).entries;
        entries.clear();
        for (String control : List.of("key.inventory", "key.drop", "key.chat", "key.command", "key.screenshot", "key.advancements", "key.swapOffhand")) {
            entries.add(slot(control, "minecraft:chest"));
        }
        WheelConfig.Entry tools = WheelConfig.Entry.folder("Tools");
        tools.entries.add(WheelConfig.Entry.folder("Gemstones and other shiny things"));
        entries.add(tools);
        InputConstants.Key[] keys = new InputConstants.Key[6];
        for (int index = 0; index < 6; index++) keys[index] = InputConstants.getKey(HotkeyWheelClient.editorKey(index).saveString());
        keys[0] = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G);
        for (KeyMapping mapping : client.options.keyMappings) {
            if (!mapping.getName().startsWith("key.hotkey_wheel.") && mapping.matches(keys[0])) mapping.setKey(InputConstants.UNKNOWN);
        }
        KeyMapping.resetMapping();
        check(WheelBindings.save(configuration, keys).isEmpty(), "The layout fixture saves");
    }

    private void moveTo(ClientGameTestContext context, int scale) {
        for (boolean folder : new boolean[] {false, true}) {
            context.runOnClient(client -> {
                WheelEditorScreen editor = new WheelEditorScreen(null);
                client.gui.setScreen(editor);
                if (folder) {
                    press(button(editor, "Slot 8"));
                    press(button(editor, "Open folder: Tools"));
                }
                press(button(editor, folder ? "Slot 1" : "Slot 2"));
                press(button(editor, "Move to…"));
                check(client.gui.screen() instanceof MoveSlotScreen, "Move to… opens at GUI scale " + scale);
            });
            context.waitTicks(3);
            context.takeScreenshot("move-to-subtitle-scale-" + scale + (folder ? "-folder" : "-slot"));
            context.runOnClient(client -> {
                MoveSlotScreen picker = (MoveSlotScreen) client.gui.screen();
                List<String> lines = picker.subtitleLines();
                report.put("move_to_scale_" + scale + (folder ? "_folder" : "_slot"), lines);
                String joined = String.join(" ", lines);
                int widest = lines.stream().mapToInt(client.font::width).max().orElse(0);
                check(widest <= Math.min(560, picker.width - 40),
                        "At GUI scale " + scale + " every subtitle line fits inside the screen (" + widest + " of " + picker.width + ")");
                if (folder) {
                    check(joined.contains("the folder Gemstones and other shiny things and its slots go ·") && !joined.contains(" goes"),
                            "A folder and its slots \"go\"");
                } else {
                    check(joined.contains("this slot goes ·"), "A single slot \"goes\"");
                }
                if (scale == 2 && !folder) check(lines.size() == 1, "At GUI scale 2 the slot's subtitle is one line, as before");
                int lowest = 30 + lines.size() * 10;
                int firstRow = picker.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .mapToInt(Button::getY).min().orElseThrow();
                check(firstRow >= lowest + 2, "The levels start below the subtitle at GUI scale " + scale);
                if (scale == 2) check(firstRow == 52, "At GUI scale 2 the levels start where they did");
                client.gui.setScreen(null);
            });
        }
    }

    private static double[] towards(Minecraft client, Screen screen, int centerY, int radius, int slot, int count) {
        double angle = RadialGeometry.angle(slot, count);
        return new double[] {(screen.width / 2 + Math.cos(angle) * radius * 0.76) * client.getWindow().getScreenWidth() / screen.width,
                (centerY + Math.sin(angle) * radius * 0.76) * client.getWindow().getScreenHeight() / screen.height};
    }

    /** The text block under a radial ends above the hearts, its lines fit, and the title clears a popped-out top sector. */
    private void textClear(Minecraft client, Screen screen, int centerY, int radius, int hintLines, String keyLine, String where) {
        RadialGeometry.Layout layout = new RadialGeometry.Layout(centerY, radius);
        int heartsTop = screen.height - HEARTS_TOP_FROM_BOTTOM;
        int textBottom = layout.textBottom(hintLines);
        int titleBottom = Math.max(8, centerY - radius - 24) + client.font.lineHeight;
        report.put(where, Map.of("gui", screen.width + "x" + screen.height, "centerY", centerY, "radius", radius,
                "hint_lines", hintLines, "hint_top", centerY + radius + RadialGeometry.HINT_OFFSET, "text_bottom", textBottom,
                "hearts_top", heartsTop, "title_bottom", titleBottom));
        check(textBottom + 2 <= heartsTop, where + ": the name, key line and hint end at least 2 pixels above the hearts (" + textBottom + " ≤ " + heartsTop + ")");
        check(RadialGeometry.HINT_OFFSET >= RadialGeometry.KEY_LINE_OFFSET + client.font.lineHeight + 4,
                where + ": the hint sits just under the key line");
        check(client.font.width(keyLine) <= screen.width - 8, where + ": the key line fits the screen");
        check(titleBottom <= centerY - radius - 7, where + ": the title stays clear of a hovered top sector");
    }

    private void wheel(ClientGameTestContext context, String name) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            return towards(client, screen, screen.centerY(), screen.radius(), 1, screen.slotCount());
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(2);
        context.takeScreenshot("hint-wheel-" + name);
        int[] topLevel = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            check(screen.selection(client.mouseHandler.getScaledXPos(client.getWindow()), client.mouseHandler.getScaledYPos(client.getWindow())) == 1,
                    name + ": a slot is hovered");
            String keys = "Cycle wheel: " + HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString()
                    + " · Settings: " + HotkeyWheelClient.SETTINGS_KEY.getTranslatedKeyMessage().getString();
            textClear(client, screen, screen.centerY(), screen.radius(), screen.hintLines(), keys, "wheel " + name);
            if (name.equals("window-640x480")) check(screen.hintLines() == 1, name + ": at the narrowest GUI the hint still fits on one line");
            if (name.equals("scale-2")) {
                check(screen.centerY() == (screen.height - 32) / 2 && screen.radius() == 110, "At GUI scale 2 the wheel is placed as before");
            }
            return new int[] {screen.centerY(), screen.radius()};
        });
        // Open the Tools folder with a click while the key is held; the folder radial uses the same placement.
        double[] folder = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            return towards(client, screen, screen.centerY(), screen.radius(), 7, screen.slotCount());
        });
        context.getInput().setCursorPos(folder[0], folder[1]);
        context.waitTicks(1);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.takeScreenshot("hint-folder-" + name);
        context.runOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            check(screen.folderDepth() == 1, name + ": the folder opens");
            check(screen.centerY() == topLevel[0] && screen.radius() == topLevel[1], name + ": the folder radial sits where the wheel does");
            textClear(client, screen, screen.centerY(), screen.radius(), screen.hintLines(), "Cycle wheel: Tab · Settings: U", "folder " + name);
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> client.gui.setScreen(null));
        if (!FabricLoader.getInstance().isModLoaded("toolpouch")) return;
        // The Tool Pouch item radial, with two pages of arrows so its page line is the longer one.
        context.runOnClient(client -> {
            List<ToolPouchIntegration.ItemChoice> arrows = new ArrayList<>();
            for (int index = 0; index < 14; index++) arrows.add(new ToolPouchIntegration.ItemChoice(index, new ItemStack(Items.ARROW, index + 1)));
            client.gui.setScreen(new ToolPouchRadialScreen(new ToolPouchIntegration.Snapshot(ToolPouchIntegration.Context.AMMO, arrows)));
        });
        context.waitTicks(2);
        double[] item = context.computeOnClient(client -> {
            ToolPouchRadialScreen screen = (ToolPouchRadialScreen) client.gui.screen();
            return towards(client, screen, screen.centerY(), screen.radius(), 2, 12);
        });
        context.getInput().setCursorPos(item[0], item[1]);
        context.waitTicks(2);
        context.takeScreenshot("hint-toolpouch-" + name);
        context.runOnClient(client -> {
            ToolPouchRadialScreen screen = (ToolPouchRadialScreen) client.gui.screen();
            RadialGeometry.Layout expected = RadialGeometry.layout(screen.width, screen.height, screen.hintLines());
            check(screen.centerY() == expected.centerY() && screen.radius() == expected.radius(),
                    name + ": the Tool Pouch radial follows the same placement rule as the wheel");
            String pages = "Cycle page: " + HotkeyWheelClient.CYCLE_KEY.getTranslatedKeyMessage().getString() + " · arrows or scroll";
            textClear(client, screen, screen.centerY(), screen.radius(), screen.hintLines(), pages, "toolpouch " + name);
            client.gui.setScreen(null);
        });
    }
}
