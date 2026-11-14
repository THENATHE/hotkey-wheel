package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ElytraFlight;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelIcons;
import com.thenathe.hotkeywheel.WheelScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Runs in a fresh game started with the settings file IconPickerTest saved in the previous run, which run.py put in
 * place before launch: it loads at startup, the wheel opens, draws the icons, uses a control, and the editor opens
 * from the wheel and saves the same file again. The saved keys are loaded through Minecraft's own options loading.
 */
public final class RestartTest implements FabricClientGameTest {
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

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        try {
            Path saved = Path.of(System.getProperty("wheel.qa.root"), "restart", "hotkey-wheel.json");
            String expected = Files.readString(saved);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                check(Files.readString(WheelConfig.path()).equals(expected), "The previous game's settings file is in place");
                List<WheelConfig.Entry> slots = WheelConfig.get().wheels.get(0).entries;
                check(slots.get(0).icon.equals(WheelIcons.GLYPH_PREFIX + "cog") && slots.get(1).icon.equals(WheelIcons.modIcon("hotkey_wheel"))
                                && slots.get(2).icon.equals(WheelIcons.GLYPH_PREFIX + "star") && slots.get(3).icon.equals(WheelIcons.modIcon("fabric-api"))
                                && slots.get(4).icon.equals(WheelIcons.modIcon("hkwqa_removed_mod"))
                                && slots.get(2).entries.get(0).icon.equals(WheelIcons.GLYPH_PREFIX + "warning"),
                        "After a restart every slot and folder keeps its Hotkey Wheel, mod or removed-mod icon");
                check(!WheelConfig.get().houseLook() && WheelConfig.get().elytraFlightOff(),
                        "After a restart the Minecraft look and elytra flight off are still set");
                check(ElytraFlight.blocksGliding() == ElytraFlight.offered(),
                        "After a restart the saved Off stops gliding, unless Vanilla++ is installed");
                // The client gametest harness starts every game with default options, so the keys the previous game
                // saved in options.txt are read back here with Minecraft's own Options.load, as at a normal startup.
                Path options = client.options.getFile().toPath();
                List<String> lines = new ArrayList<>(Files.readAllLines(options));
                lines.removeIf(line -> line.startsWith("key_key.hotkey_wheel."));
                lines.addAll(Files.readAllLines(saved.resolveSibling("keys.txt")));
                Files.write(options, lines);
                client.options.load();
                net.minecraft.client.KeyMapping.resetMapping();
                check(HotkeyWheelClient.SETTINGS_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_P))
                                && HotkeyWheelClient.WHEEL_KEYS[0].matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G)),
                        "The settings key's new binding loads from the saved options.txt");
                for (var mapping : client.options.keyMappings) {
                    if (!mapping.getName().startsWith("key.hotkey_wheel.") && mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G))) {
                        mapping.setKey(InputConstants.UNKNOWN);
                    }
                }
                net.minecraft.client.KeyMapping.resetMapping();
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("buildprint")) {
                    // A new game shows Buildprint's welcome screen once; mark it seen, as the other fixtures do.
                    context.runOnClient(client -> {
                        Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                                .getConstructor().newInstance();
                        onboarding.getClass().getMethod("markDone").invoke(onboarding);
                        client.gui.setScreen(null);
                    });
                    context.waitTicks(2);
                }
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.getInput().setCursorPos(640, 400);
                context.waitTicks(2);
                Path screenshot = context.takeScreenshot("restart-wheel-icons");
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof WheelScreen, "After a restart the wheel opens");
                    WheelScreen wheel = (WheelScreen) client.gui.screen();
                    BufferedImage image = ImageIO.read(screenshot.toFile());
                    double scale = (double) client.getWindow().getScreenWidth() / wheel.width;
                    double angle = RadialGeometry.angle(0, wheel.slotCount());
                    int x = wheel.centerX() + (int) Math.round(Math.cos(angle) * wheel.radius() * 0.73);
                    int y = wheel.centerY() + (int) Math.round(Math.sin(angle) * wheel.radius() * 0.73);
                    int light = 0;
                    for (int py = (int) ((y - 8) * scale); py < (int) ((y + 8) * scale); py++) {
                        for (int px = (int) ((x - 8) * scale); px < (int) ((x + 8) * scale); px++) {
                            int rgb = image.getRGB(px, py) & 0xFFFFFF;
                            if ((rgb >> 16 & 0xFF) > 230 && (rgb >> 8 & 0xFF) > 230 && (rgb & 0xFF) > 230) light++;
                        }
                    }
                    report.put("cog_light_pixels", light);
                    check(light >= 80, "After a restart the cog draws on the wheel (" + light + " light pixels)");
                });
                // Select the cog's slot, Open/Close Inventory, by releasing over it.
                double[] position = context.computeOnClient(client -> {
                    WheelScreen wheel = (WheelScreen) client.gui.screen();
                    double angle = RadialGeometry.angle(0, wheel.slotCount());
                    return new double[] {(wheel.centerX() + Math.cos(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenWidth() / wheel.width,
                            (wheel.centerY() + Math.sin(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenHeight() / wheel.height};
                });
                context.getInput().setCursorPos(position[0], position[1]);
                context.waitTicks(2);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(4);
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof InventoryScreen, "After a restart selecting a slot runs its control");
                    client.gui.setScreen(null);
                });
                context.waitTicks(2);
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.getInput().pressKey(InputConstants.KEY_P);
                context.waitTicks(2);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(2);
                context.takeScreenshot("restart-editor");
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof WheelEditorScreen, "After a restart the settings key opens the editor from the wheel");
                    check(client.gui.screen().children().stream().anyMatch(child -> child instanceof Button button
                                    && button.getMessage().getString().equals("Look: Minecraft")),
                            "After a restart the editor opens in the Minecraft look");
                    button(client.gui.screen(), "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    check(client.gui.screen() == null && Files.readString(WheelConfig.path()).equals(expected),
                            "After a restart Save writes the same settings again");
                });
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel restart QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "restart-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (IOException failure) { throw new RuntimeException(failure); }
        }
    }
}
