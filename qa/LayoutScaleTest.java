package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.MoveSlotScreen;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * GUI scale 3 at 1280×800: the Move to… subtitle fits the screen and says "go" for a folder, and the wheel's hovered
 * name and key line stay above the hearts and hotbar. At GUI scale 2 both look exactly as in 1.1.1.
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
                for (int scale : new int[] {2, 3}) {
                    context.runOnClient(client -> client.options.guiScale().set(scale));
                    context.waitTicks(2);
                    wheel(context, scale);
                }
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

    /** 1.1.1's radial placement, for the sizes where its text already cleared the HUD. */
    private void unchangedGeometry() {
        int[][] sizes = {{640, 400}, {640, 360}, {683, 384}, {960, 540}, {720, 450}};
        for (int[] size : sizes) {
            int centerY = (size[1] - 32) / 2;
            int radius = Math.max(36, Math.min(110, Math.min(size[0] / 2 - 22, (size[1] - 90) / 2)));
            RadialGeometry.Layout layout = RadialGeometry.layout(size[0], size[1]);
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

    private void wheel(ClientGameTestContext context, int scale) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(1, screen.slotCount());
            return new double[] {(screen.centerX() + Math.cos(angle) * screen.radius() * 0.76) * client.getWindow().getScreenWidth() / screen.width,
                    (screen.centerY() + Math.sin(angle) * screen.radius() * 0.76) * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(2);
        context.takeScreenshot("wheel-hover-scale-" + scale);
        context.runOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            check(screen.selection(client.mouseHandler.getScaledXPos(client.getWindow()), client.mouseHandler.getScaledYPos(client.getWindow())) == 1,
                    "A slot is hovered at GUI scale " + scale);
            int keyLineBottom = screen.centerY() + screen.radius() + 31 + client.font.lineHeight;
            int heartsTop = screen.height - HEARTS_TOP_FROM_BOTTOM;
            report.put("wheel_scale_" + scale, Map.of("gui", screen.width + "x" + screen.height, "centerY", screen.centerY(),
                    "radius", screen.radius(), "key_line_bottom", keyLineBottom, "hearts_top", heartsTop));
            check(keyLineBottom <= heartsTop, "At GUI scale " + scale + " the hovered name and key line end above the hearts ("
                    + keyLineBottom + " ≤ " + heartsTop + ")");
            check(screen.centerY() - screen.radius() - 24 >= 4, "At GUI scale " + scale + " the title above the wheel stays on screen");
            if (scale == 2) {
                check(screen.centerY() == (screen.height - 32) / 2 && screen.radius() == 110, "At GUI scale 2 the wheel is placed as before");
            }
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> client.gui.setScreen(null));
    }
}
