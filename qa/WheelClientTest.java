package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class WheelClientTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static WheelScreen wheel(Minecraft client) { return (WheelScreen) client.gui.screen(); }

    private static void point(ClientGameTestContext context, int slot) {
        point(context, slot, 0.76);
    }

    private static void point(ClientGameTestContext context, int slot, double radialScale) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = wheel(client);
            int count = WheelConfig.get().wheels.get(screen.wheelIndex()).entries.size();
            double angle = RadialGeometry.angle(slot, count);
            double x = screen.centerX() + Math.cos(angle) * screen.radius() * radialScale;
            double y = screen.centerY() + Math.sin(angle) * screen.radius() * radialScale;
            return new double[] {x * client.getWindow().getScreenWidth() / screen.width,
                    y * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static void configureTop(Minecraft client, String control, boolean hold) {
        WheelConfig config = WheelConfig.get().copy();
        config.wheels.get(0).entries.get(0).control = control;
        config.wheels.get(0).entries.get(0).holdAction = hold;
        if (!WheelConfig.save(config)) throw new AssertionError("Save fixture");
        client.gui.setScreen(null);
    }

    private void selectTop(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        point(context, 0);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        try {
            mainMenuSettings(context);
            gameplay(context, report);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel client QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void gameplay(ClientGameTestContext context, LinkedHashMap<String, Object> report) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.waitTicks(20);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                client.gui.setScreen(null);
                WheelConfig config = new WheelConfig();
                config.wheelCount = 3;
                config.wheels.get(1).entries.get(0).control = client.options.keyTogglePerspective.getName();
                config.wheels.get(2).entries.get(0).control = client.options.keyTogglePerspective.getName();
                check(WheelConfig.save(config), "Configuration commits atomically");
                WheelConfig.load();
                check(WheelConfig.get().wheelCount == 3, "All three wheels reload from disk");
                HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_H));
                HotkeyWheelClient.WHEEL_KEYS[2].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J));
                KeyMapping.resetMapping();
                for (int count = 1; count <= 12; count++) {
                    check(RadialGeometry.selection(0, 0, 20, 100, count) == -1, "Center cancels for " + count + " slots");
                    for (int slot = 0; slot < count; slot++) {
                        double angle = RadialGeometry.angle(slot, count);
                        check(RadialGeometry.selection(Math.cos(angle) * 70, Math.sin(angle) * 70, 20, 100, count) == slot,
                                "Sector " + slot + " of " + count + " matches its icon direction");
                    }
                }
            });
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(4);
            context.runOnClient(client -> {
                check(client.gui.screen() instanceof WheelScreen, "Actual keyboard opener opens wheel 1");
                WheelScreen screen = wheel(client);
                double distance = Math.hypot(client.mouseHandler.getScaledXPos(client.getWindow()) - screen.centerX(),
                        client.mouseHandler.getScaledYPos(client.getWindow()) - screen.centerY());
                check(distance <= 1, "Cursor begins at center Cancel after native mouse synchronization: " + distance);
            });
            context.takeScreenshot("wheel-center-cancel");
            point(context, 2);
            context.waitTicks(2);
            context.takeScreenshot("wheel-hover-pop-and-line");
            for (int expected = 1; expected <= 3; expected++) {
                context.getInput().pressKey(InputConstants.KEY_TAB);
                int index = expected % 3;
                context.runOnClient(client -> check(wheel(client).wheelIndex() == index, "Cycle advances to wheel " + (index + 1)));
            }
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() == null, "Escape cancels without opening the hovered control"));

            outsideSelection(context);

            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() == null, "Releasing inside center cancels"));

            context.runOnClient(client -> {
                client.options.keyInventory.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
            selectTop(context);
            context.runOnClient(client -> {
                check(client.gui.screen() instanceof InventoryScreen, "Release activates Inventory even when its ordinary key is unbound");
                client.gui.setScreen(null);
                client.options.keyInventory.setKey(client.options.keyChat.getDefaultKey());
                KeyMapping.resetMapping();
            });
            selectTop(context);
            context.runOnClient(client -> {
                check(client.gui.screen() instanceof InventoryScreen, "Duplicate ordinary keys activate only the chosen control");
                client.gui.setScreen(null);
                client.options.keyInventory.setKey(client.options.keyInventory.getDefaultKey());
                KeyMapping.resetMapping();
            });

            var initialCamera = context.computeOnClient(client -> client.options.getCameraType());
            context.getInput().holdKey(InputConstants.KEY_H);
            context.waitTicks(2);
            context.runOnClient(client -> check(wheel(client).wheelIndex() == 1, "Wheel 2 opens using its own key"));
            point(context, 0);
            context.getInput().releaseKey(InputConstants.KEY_H);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.options.getCameraType() != initialCamera, "Wheel 2 release activates perspective control"));

            context.runOnClient(client -> {
                WheelConfig config = WheelConfig.get().copy();
                config.wheels.get(2).toggleOpen = true;
                check(WheelConfig.save(config), "Toggle opening persists per wheel");
            });
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(3);
            context.runOnClient(client -> check(wheel(client).wheelIndex() == 2, "Toggle wheel remains open after opener release"));
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() == null, "Second toggle press in center cancels"));
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(2);
            point(context, 0);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(client.gui.screen() == null, "Toggle wheel click activates and closes");
                check(!client.options.keyAttack.isDown() && !client.options.keyAttack.consumeClick(),
                        "Selection mouse click does not leak into Attack");
            });

            context.runOnClient(client -> configureTop(client, client.options.keyUp.getName(), true));
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(2);
            point(context, 0);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            context.runOnClient(client -> check(client.gui.screen() == null && client.options.keyUp.isDown(),
                    "Hold action stays pressed after click while wheel opener is held"));
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(!client.options.keyUp.isDown(), "Releasing opener releases the held action"));

            context.getInput().holdKey(InputConstants.KEY_W);
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(2);
            point(context, 0);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(client -> check(client.options.keyUp.isDown(), "Wheel Hold combines with the control's physical key"));
            context.getInput().releaseKey(InputConstants.KEY_W);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.options.keyUp.isDown(), "Physical control-key release leaves the wheel Hold active"));
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(!client.options.keyUp.isDown(), "Releasing both sources cannot restore stale movement state"));

            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(2);
            point(context, 0);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(client -> client.gui.setScreen(new WheelEditorScreen(null)));
            context.waitTicks(2);
            context.runOnClient(client -> {
                check(!client.options.keyUp.isDown(), "Another screen clears a held action");
                client.gui.setScreen(null);
            });
            context.getInput().releaseKey(InputConstants.KEY_G);

            context.runOnClient(client -> {
                configureTop(client, client.options.keyShift.getName(), true);
                client.options.toggleCrouch().set(true);
            });
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(2);
            point(context, 0);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.options.keyShift.isDown(), "Hold action presses crouch with vanilla toggle crouch enabled"));
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(!client.options.keyShift.isDown(), "Hold action releases crouch even with vanilla toggle crouch enabled");
                configureTop(client, client.options.keyShift.getName(), false);
            });
            selectTop(context);
            context.runOnClient(client -> check(client.options.keyShift.isDown(), "Tap action preserves vanilla toggle crouch semantics"));
            selectTop(context);
            context.runOnClient(client -> {
                check(!client.options.keyShift.isDown(), "Second tap toggles crouch off");
                client.options.toggleCrouch().set(false);
            });

            context.runOnClient(client -> {
                HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_4));
                HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_5));
                KeyMapping.resetMapping();
            });
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_4);
            context.waitTicks(3);
            context.runOnClient(client -> check(wheel(client).wheelIndex() == 1, "Mouse button opens its assigned wheel"));
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_5);
            context.runOnClient(client -> check(wheel(client).wheelIndex() == 2, "Mouse cycle key advances while opener is held"));
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_4);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(client.gui.screen() == null, "Original hold opener still closes after cycling to a toggle-configured wheel");
                HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
                KeyMapping.resetMapping();
                configureTop(client, client.options.keyDebugOverlay.getName(), false);
            });
            boolean overlay = context.computeOnClient(client -> client.debugEntries.isOverlayVisible());
            selectTop(context);
            context.runOnClient(client -> {
                check(client.debugEntries.isOverlayVisible() != overlay, "Debug overlay follows its native special input path");
                client.debugEntries.setOverlayVisible(false);
                configureTop(client, client.options.keyDebugShowAdvancedTooltips.getName(), false);
                client.options.keyDebugShowAdvancedTooltips.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
            boolean advanced = context.computeOnClient(client -> client.options.advancedItemTooltips);
            selectTop(context);
            context.runOnClient(client -> {
                check(client.options.advancedItemTooltips != advanced, "Unbound debug control dispatches without its ordinary F3 chord");
                configureTop(client, client.options.keyScreenshot.getName(), false);
            });
            selectTop(context);
            context.waitTicks(8);
            context.runOnClient(client -> {
                Path screenshots = FabricLoader.getInstance().getGameDir().resolve("screenshots");
                try (var files = Files.list(screenshots)) {
                    check(files.anyMatch(file -> file.getFileName().toString().endsWith(".png")), "Screenshot control produces an actual screenshot");
                }
                configureTop(client, client.options.keyInventory.getName(), false);
                client.gui.setScreen(new WheelEditorScreen(null));
            });
            context.takeScreenshot("wheel-editor");
            context.runOnClient(client -> {
                Screen editor = client.gui.screen();
                EditBox name = editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                name.setValue("Saved from UI");
                button(editor, "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(WheelConfig.get().wheels.get(0).name.equals("Saved from UI"), "Editor Save persists edited wheel name");
                client.gui.setScreen(new WheelEditorScreen(null));
                EditBox second = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                second.setValue("Discard this");
                client.gui.screen().onClose();
                check(WheelConfig.get().wheels.get(0).name.equals("Saved from UI"), "Editor Cancel leaves saved settings intact");
                client.gui.setScreen(new WheelEditorScreen(null));
                Screen keyEditor = client.gui.screen();
                button(keyEditor, "Cycle while open").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                keyEditor.keyPressed(new KeyEvent(InputConstants.KEY_G, 0, 0));
                button(keyEditor, "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(client.gui.screen() == keyEditor && !HotkeyWheelClient.CYCLE_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G)),
                        "Editor rejects conflicting wheel and cycle keys before applying settings");
                keyEditor.onClose();
                client.gui.setScreen(new WheelEditorScreen(null));
                Screen failedEditor = client.gui.screen();
                EditBox retryName = failedEditor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                retryName.setValue("Retry saved");
                byte[] original = Files.readAllBytes(WheelConfig.path());
                Files.delete(WheelConfig.path());
                Files.createDirectory(WheelConfig.path());
                Files.writeString(WheelConfig.path().resolve("preserve"), "keep");
                button(failedEditor, "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(client.gui.screen() == failedEditor && WheelConfig.get().wheels.get(0).name.equals("Saved from UI"),
                        "Save failure keeps draft open and leaves current configuration unchanged");
                check(Files.readString(WheelConfig.path().resolve("preserve")).equals("keep"), "Save failure preserves destination contents");
                Files.delete(WheelConfig.path().resolve("preserve"));
                Files.delete(WheelConfig.path());
                Files.write(WheelConfig.path(), original);
                button(failedEditor, "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(WheelConfig.get().wheels.get(0).name.equals("Retry saved"), "Retry commits the preserved draft");
                client.gui.setScreen(new ControlPickerScreen(null, selected -> {}));
                EditBox search = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                search.setValue("inventory");
                check(button(client.gui.screen(), Component.translatable(client.options.keyInventory.getName()).getString()) != null,
                        "Control picker searches standard control names");
                client.gui.setScreen(new IconPickerScreen(null, "minecraft:stone", selected -> {}));
                EditBox icons = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                icons.setValue("minecraft:stone");
                check(client.gui.screen().children().size() > 4, "Icon picker searches registry IDs");
            });
            context.takeScreenshot("wheel-icon-picker");
            context.runOnClient(client -> {
                KeyBindsScreen screen = new KeyBindsScreen(null, client.options);
                client.gui.setScreen(screen);
                check(button(client.gui.screen(), "Wheels") != null, "Controls screen provides a wheel-editor shortcut");
            });
            if (FabricLoader.getInstance().isModLoaded("controlling")) controlling(context);
            if (FabricLoader.getInstance().isModLoaded("buildprint")) modControl(context);
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                configureTop(client, client.options.keyInventory.getName(), false);
            });
            context.getInput().resizeWindow(854, 480);
            context.getInput().holdKey(InputConstants.KEY_G);
            context.waitTicks(4);
            point(context, 0);
            context.takeScreenshot("wheel-small-window");
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() instanceof InventoryScreen, "Small-window wheel still selects correctly"));
            report.put("passed", true);
        }
    }

    private void outsideSelection(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        point(context, 0, 1.45);
        context.runOnClient(client -> {
            WheelScreen screen = wheel(client);
            check(screen.selection(client.mouseHandler.getScaledXPos(client.getWindow()),
                    client.mouseHandler.getScaledYPos(client.getWindow())) == -1,
                    "Outside selection defaults to Cancel");
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Outside release cancels with the option disabled");
            client.gui.setScreen(new WheelEditorScreen(null));
            button(client.gui.screen(), "Outside wheel:").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            button(client.gui.screen(), "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            WheelConfig.load();
            check(WheelConfig.get().wheels.get(0).selectOutside && !WheelConfig.get().wheels.get(1).selectOutside,
                    "Outside selection saves and reloads independently per wheel");
        });
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(wheel(client).selection(wheel(client).centerX(), wheel(client).centerY()) == -1,
                "Center still cancels with outside selection enabled"));
        point(context, 0, 1.45);
        context.runOnClient(client -> {
            WheelScreen screen = wheel(client);
            check(screen.selection(client.mouseHandler.getScaledXPos(client.getWindow()),
                    client.mouseHandler.getScaledYPos(client.getWindow())) == 0,
                    "Cursor beyond the edge selects the entry under its line");
        });
        context.takeScreenshot("wheel-outside-direction-selection");
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.runOnClient(client -> {
            WheelScreen screen = wheel(client);
            check(screen.selection(client.mouseHandler.getScaledXPos(client.getWindow()),
                    client.mouseHandler.getScaledYPos(client.getWindow())) == -1,
                    "Cycling uses the next wheel's outside selection setting");
            screen.switchWheel(0);
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen, "Hold release beyond the edge activates the directional entry");
            client.gui.setScreen(null);
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).toggleOpen = true;
            check(WheelConfig.save(config), "Outside selection can be combined with Toggle opening");
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        point(context, 0, 1.45);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof InventoryScreen, "Toggle click beyond the edge activates the directional entry");
            client.gui.setScreen(null);
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).toggleOpen = false;
            config.wheels.get(0).selectOutside = false;
            if (!WheelConfig.save(config)) throw new AssertionError("Reset outside-selection fixture");
        });
    }

    private void mainMenuSettings(ClientGameTestContext context) throws Exception {
        context.getInput().resizeWindow(1280, 1000);
        context.runOnClient(client -> {
            check(client.level == null && client.player == null, "Settings regression runs before joining any world");
            client.options.guiScale().set(2);
            client.gui.setScreen(new KeyBindsScreen(null, client.options));
            button(client.gui.screen(), "Wheels").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(client.gui.screen() instanceof WheelEditorScreen, "Controls shortcut opens editor from main menu");
        });
        context.waitTicks(5);
        context.takeScreenshot("main-menu-wheel-settings");
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelEditorScreen,
                "Main-menu editor renders visible icon slots without a crash"));
        String[] selectedIcon = {""};
        context.runOnClient(client -> client.gui.setScreen(new IconPickerScreen(client.gui.screen(), "minecraft:glass", selected -> selectedIcon[0] = selected)));
        context.waitTicks(5);
        context.takeScreenshot("main-menu-icon-picker");
        context.runOnClient(client -> {
            EditBox search = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
            search.setValue("minecraft:stone");
            check(client.gui.screen().children().size() > 4, "Icon picker searches blocks before joining a world");
            button(client.gui.screen(), "Stone").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(selectedIcon[0].equals("minecraft:stone") && client.gui.screen() instanceof WheelEditorScreen,
                    "Main-menu icon choice accepts the block and returns to editor");
            EditBox name = (EditBox) client.gui.screen().children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
            name.setValue("Configured before world");
            button(client.gui.screen(), "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(WheelConfig.get().wheels.get(0).name.equals("Configured before world"), "Settings save before joining a world");
            check(client.gui.screen() instanceof KeyBindsScreen, "Main-menu editor returns to Controls");
            if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                Class<?> integration = Class.forName("com.thenathe.hotkeywheel.ModMenuIntegration");
                Object factory = integration.getMethod("getModConfigScreenFactory").invoke(integration.getConstructor().newInstance());
                Screen editor = (Screen) Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory")
                        .getMethod("create", Screen.class).invoke(factory, client.gui.screen());
                client.gui.setScreen(editor);
                check(client.gui.screen() instanceof WheelEditorScreen, "Mod Menu settings factory opens before joining a world");
            }
        });
        context.waitTicks(3);
        context.runOnClient(client -> {
            client.gui.setScreen(null);
        });
        context.getInput().resizeWindow(1280, 800);
    }

    private void controlling(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            Class<?> type = Class.forName("com.blamejared.controlling.client.NewKeyBindsScreen");
            Screen screen = (Screen) type.getConstructor(Screen.class, net.minecraft.client.Options.class).newInstance(null, client.options);
            client.gui.setScreen(screen);
            EditBox search = (EditBox) screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
            search.setValue("hotkey wheel");
            Object list = type.getMethod("getKeyBindsList").invoke(screen);
            List<?> rows = (List<?>) list.getClass().getMethod("children").invoke(list);
            long found = rows.stream().filter(row -> {
                try { return ((KeyMapping) row.getClass().getMethod("getKey").invoke(row)).getName().startsWith("key.hotkey_wheel."); }
                catch (Exception ignored) { return false; }
            }).count();
            check(found == 5, "Controlling name search finds all three wheel keys, cycle, and configure");
            check(button(screen, "Wheels") != null, "Editor shortcut survives Controlling replacement screen");
        });
        context.takeScreenshot("controlling-hotkey-wheel-search");
    }

    private void modControl(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            Class<?> type = Class.forName("com.buildprint.client.BuildprintClient");
            KeyMapping mapping = (KeyMapping) type.getMethod("openMenuKey").invoke(null);
            configureTop(client, mapping.getName(), false);
            mapping.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        });
        selectTop(context);
        context.runOnClient(client -> {
            check(client.gui.screen() != null && client.gui.screen().getClass().getName().contains("buildprint"),
                    "Wheel activates an unbound Buildprint menu control with the addon installed");
            client.gui.setScreen(null);
        });
    }
}
