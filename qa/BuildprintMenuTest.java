package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelMenuReturn;
import com.thenathe.hotkeywheel.WheelScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

public final class BuildprintMenuTest implements FabricClientGameTest {
    private static final String BUILDPRINT_SCREEN = "com.buildprint.client.gui.BuildprintScreen";
    private static final String INSTAMINE_SCREEN = "com.thenathe.buildprinttoning.client.InstamineSettingsScreen";
    private final List<String> checks = new ArrayList<>();
    private Object schematic;
    private Object loaded;
    private InputConstants.Key forwardKey;
    private int nativeOpenKey;

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        try {
            context.runOnClient(client -> {
                callStatic("com.buildprint.client.onboarding.OnboardingManager", "markComplete");
                client.gui.setScreen(null);
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                setup(context);
                cancelHeldWheel(context);
                rootClose(context);
                childClose(context);
                originalCycleOpener(context);
                nativeOpeningStaysIndependent(context);
                validatedAddonClose(context);
                disabledIntegration(context);
                unrelatedReplacement(context);
                nonMenuActionDoesNotClaimScreen(context);
                context.runOnClient(client -> {
                    WheelMenuReturn.clear();
                    client.gui.setScreen(null);
                    client.options.keyUp.setKey(forwardKey);
                    client.options.keyUp.setDown(false);
                    KeyMapping.resetMapping();
                    callStatic("com.buildprint.schematic.SchematicManager", "clear");
                });
                report.put("passed", true);
            }
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Buildprint wheel menu QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "buildprint-menu-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void setup(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            client.gui.setScreen(null);
            WheelMenuReturn.clear();
            WheelConfig config = new WheelConfig();
            config.wheelCount = 3;
            config.closeBuildprintWithWheelKey = true;
            for (WheelConfig.Wheel wheel : config.wheels) wheel.entries.get(0).control = "key.buildprint.open";
            check(WheelConfig.save(config), "Buildprint integration fixture saves");
            int[] keys = {InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_J};
            for (int index = 0; index < keys.length; index++) {
                HotkeyWheelClient.WHEEL_KEYS[index].setKey(InputConstants.Type.KEYBOARD.getOrCreate(keys[index]));
                HotkeyWheelClient.WHEEL_KEYS[index].setDown(false);
                while (HotkeyWheelClient.WHEEL_KEYS[index].consumeClick()) { }
            }
            forwardKey = InputConstants.getKey(client.options.keyUp.saveString());
            client.options.keyUp.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
            client.options.keyUp.setDown(false);
            while (client.options.keyUp.consumeClick()) { }
            HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
            int[] nativeKeys = {InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8,
                    InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_Y, InputConstants.KEY_U,
                    InputConstants.KEY_B, InputConstants.KEY_V, InputConstants.KEY_N, InputConstants.KEY_M};
            nativeOpenKey = java.util.Arrays.stream(nativeKeys).filter(key -> java.util.Arrays.stream(client.options.keyMappings)
                    .noneMatch(mapping -> mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(key))))
                    .findFirst().orElseThrow(() -> new AssertionError("No unused native menu fixture key"));
            control("key.buildprint.open").setKey(InputConstants.Type.KEYBOARD.getOrCreate(nativeOpenKey));
            KeyMapping.resetMapping();
            check(java.util.Arrays.stream(client.options.keyMappings)
                    .filter(mapping -> mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(nativeOpenKey))).count() == 1,
                    "Native Buildprint fixture key has no conflicts, including vanilla Friends");
            callStatic("com.buildprint.schematic.SchematicManager", "clear");
            Class<?> schematicType = Class.forName("com.buildprint.schematic.model.Schematic");
            schematic = schematicType.getConstructor(int.class, int.class, int.class).newInstance(1, 1, 1);
            Class<?> placementType = Class.forName("com.buildprint.schematic.Placement");
            Object placement = placementType.getConstructor(int.class, int.class, int.class).newInstance(13, 80, 17);
            loaded = Class.forName("com.buildprint.schematic.SchematicManager")
                    .getMethod("add", String.class, schematicType, placementType).invoke(null, "Wheel QA", schematic, placement);
            check(loaded != null, "Actual Buildprint schematic fixture registers");
        });
    }

    private void cancelHeldWheel(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "Hold wheel opens before Escape cancellation"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Escape cancels while the wheel opener remains physically held");
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !client.options.keyUp.isDown(),
                    "Escape cancellation cannot restore held opener or shared forward state through mouse capture");
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !client.options.keyUp.isDown(),
                "Releasing the canceled wheel opener leaves its ordinary shared control released"));
    }

    private void rootClose(ClientGameTestContext context) throws Exception {
        openFromWheel(context);
        context.runOnClient(client -> {
            Screen root = client.gui.screen();
            check(root.getClass().getName().equals(BUILDPRINT_SCREEN), "Hold release opens actual Buildprint menu");
            check(!WheelMenuReturn.handleInput(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G), InputConstants.RELEASE),
                    "Original selection release does not close the menu");
            Class<?> pane = Class.forName(BUILDPRINT_SCREEN + "$Pane");
            Object loadedPane = java.util.Arrays.stream(pane.getEnumConstants())
                    .filter(value -> value.toString().equals("LOADED")).findFirst().orElseThrow();
            Method select = root.getClass().getDeclaredMethod("selectPane", pane);
            select.setAccessible(true);
            select.invoke(root, loadedPane);
            Object list = field(root, "loadedList");
            list.getClass().getMethod("setSelectedIndex", int.class).invoke(list, 0);
            Method initLoaded = root.getClass().getDeclaredMethod("initLoaded");
            initLoaded.setAccessible(true);
            initLoaded.invoke(root);
            EditBox name = (EditBox) field(root, "nameBox");
            check(name != null, "Loaded schematic has editable native name field");
            name.setResponder(value -> { });
            name.setValue("Committed through wheel close");
            showPreview();
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                    "Wheel opener starts with no held state or pending click before menu closure");
            check(!client.options.keyUp.isDown() && !client.options.keyUp.consumeClick(),
                    "Shared forward control starts with no held state or pending click before menu closure");
        });
        context.takeScreenshot("buildprint-wheel-opened-menu");
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(1);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Holding the new opener press closes the menu immediately");
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                    "Closing while G remains physically held cannot reactivate the wheel key through mouse capture");
            check(!client.options.keyUp.isDown() && !client.options.keyUp.consumeClick(),
                    "Closing while G remains physically held cannot activate its shared forward control");
        });
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Pressing original wheel opener again closes the root menu");
            check(call(loaded, "displayName").equals("Committed through wheel close"), "Native Buildprint onClose commits pending schematic edits");
            check(callStatic("com.buildprint.client.render.SchematicPreview", "isActive").equals(false),
                    "Native Buildprint onClose hides its schematic preview");
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                    "Closing key press and release do not leak a gameplay keybinding click");
            check(!client.options.keyUp.isDown() && !client.options.keyUp.consumeClick(),
                    "Consumed closing-key release leaves its shared forward control released");
        });
    }

    private void childClose(ClientGameTestContext context) throws Exception {
        openFromWheel(context);
        context.runOnClient(client -> {
            Screen root = client.gui.screen();
            Screen settings = (Screen) Class.forName("com.buildprint.client.gui.SettingsScreen")
                    .getMethod("create", Screen.class).invoke(null, root);
            client.gui.setScreen(settings);
            check(settings.getClass().getName().equals("dev.isxander.yacl3.gui.YACLScreen"), "Actual Buildprint settings use YACL outside Buildprint package");
            Screen migration = (Screen) Class.forName("com.buildprint.client.gui.MigrationScreen")
                    .getConstructor(Screen.class).newInstance(settings);
            client.gui.setScreen(migration);
            showPreview();
            check(client.gui.screen() == migration, "Actual migration child retains YACL and Buildprint parent ancestry");
        });
        context.waitTicks(2);
        context.takeScreenshot("buildprint-wheel-owned-migration-child");
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Wheel opener unwinds migration, YACL settings, and Buildprint root");
            check(callStatic("com.buildprint.client.render.SchematicPreview", "isActive").equals(false),
                    "Unwinding descendants invokes root preview cleanup");
        });
    }

    private void originalCycleOpener(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.runOnClient(client -> check(((WheelScreen) client.gui.screen()).wheelIndex() == 1, "Cycle switches to second wheel while G remains the opener"));
        pointTop(context);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> check(client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN), "Cycled wheel opens Buildprint through original G opener"));
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(1);
        context.runOnClient(client -> check(client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN), "Second wheel's own key does not close a menu opened with G"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() == null, "Original G opener closes the menu after cycling"));
    }

    private void nativeOpeningStaysIndependent(ClientGameTestContext context) throws Exception {
        context.getInput().pressKey(nativeOpenKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() != null && client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN),
                "Native Buildprint hotkey opens its actual menu; observed "
                        + (client.gui.screen() == null ? "gameplay" : client.gui.screen().getClass().getName())));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(1);
        context.runOnClient(client -> {
            check(client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN), "Wheel opener does not take over a menu opened by native Buildprint hotkey");
            Screen parent = client.gui.screen();
            client.gui.setScreen((Screen) Class.forName("com.buildprint.client.gui.SettingsScreen")
                    .getMethod("create", Screen.class).invoke(null, parent));
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(1);
        context.runOnClient(client -> {
            check(client.gui.screen().getClass().getName().equals("dev.isxander.yacl3.gui.YACLScreen"),
                    "YACL under a directly opened Buildprint menu remains independent");
            client.gui.screen().onClose();
            client.gui.screen().onClose();
        });
    }

    private void validatedAddonClose(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).entries.get(0).control = "key.buildprint_toning.instamine_settings";
            check(WheelConfig.save(config), "Blocktone settings action can be assigned to a wheel");
        });
        openFromWheel(context);
        Screen[] opened = new Screen[1];
        context.runOnClient(client -> {
            opened[0] = client.gui.screen();
            check(opened[0].getClass().getName().equals(INSTAMINE_SCREEN), "Wheel opens actual Blocktone instamine settings");
            ((EditBox) field(opened[0], "reserve")).setValue("0");
            opened[0].setFocused((EditBox) field(opened[0], "reserve"));
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == opened[0], "Invalid Blocktone settings refuse closure through native validation");
            check(!((String) field(opened[0], "error")).isEmpty(), "Refused closure preserves Blocktone validation error");
            check(((EditBox) field(opened[0], "reserve")).getValue().equals("0"), "Closing opener is consumed while typing in settings");
            ((EditBox) field(opened[0], "reserve")).setValue("7");
        });
        context.takeScreenshot("buildprint-wheel-validation-refusal");
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Corrected Blocktone settings close on the next wheel opener press");
            Object settings = callStatic("com.thenathe.buildprinttoning.client.InstamineSettings", "get");
            check(field(settings, "durabilityReserve").equals(7), "Native Blocktone closure saves validated settings");
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).entries.get(0).control = "key.buildprint.open";
            check(WheelConfig.save(config), "Restore Buildprint menu action after addon validation");
        });
    }

    private void disabledIntegration(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.closeBuildprintWithWheelKey = false;
            check(WheelConfig.save(config), "Buildprint return integration can be disabled");
        });
        openFromWheel(context);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN), "Disabled integration leaves wheel-opened Buildprint menu alone");
            client.gui.screen().onClose();
            WheelConfig config = WheelConfig.get().copy();
            config.closeBuildprintWithWheelKey = true;
            check(WheelConfig.save(config), "Buildprint return integration can be enabled again");
        });
    }

    private void unrelatedReplacement(ClientGameTestContext context) {
        openFromWheel(context);
        context.runOnClient(client -> client.gui.setScreen(new WheelEditorScreen(null)));
        context.waitTicks(2);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelEditorScreen, "An unrelated replacement screen loses Buildprint opener ownership");
            client.gui.screen().onClose();
        });
    }

    private void nonMenuActionDoesNotClaimScreen(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            WheelMenuReturn.begin(control("key.buildprint.show_filter"), InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
            client.gui.setScreen((Screen) Class.forName(BUILDPRINT_SCREEN).getConstructor(Screen.class).newInstance((Screen) null));
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.runOnClient(client -> {
            check(client.gui.screen().getClass().getName().equals(BUILDPRINT_SCREEN), "A non-menu Buildprint action cannot claim an incidental menu");
            client.gui.screen().onClose();
        });
    }

    private void openFromWheel(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "Physical G opens the wheel before selecting Buildprint"));
        pointTop(context);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> check(client.gui.screen() != null && !(client.gui.screen() instanceof WheelScreen), "Wheel selection opens a native mod menu after release"));
    }

    private static void pointTop(ClientGameTestContext context) {
        double[] position = context.computeOnClient(client -> {
            WheelScreen wheel = (WheelScreen) client.gui.screen();
            int count = WheelConfig.get().wheels.get(wheel.wheelIndex()).entries.size();
            double angle = RadialGeometry.angle(0, count);
            return new double[] {
                    (wheel.centerX() + Math.cos(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenWidth() / wheel.width,
                    (wheel.centerY() + Math.sin(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenHeight() / wheel.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static KeyMapping control(String name) {
        KeyMapping mapping = HotkeyWheelClient.findControl(name);
        if (mapping == null) throw new AssertionError("Missing installed control " + name);
        return mapping;
    }

    private void showPreview() throws Exception {
        Class<?> schematicType = Class.forName("com.buildprint.schematic.model.Schematic");
        Class.forName("com.buildprint.client.render.SchematicPreview").getMethod("show", schematicType).invoke(null, schematic);
        Class.forName("com.buildprint.client.render.SchematicPreview").getMethod("setActive", boolean.class).invoke(null, true);
        check(callStatic("com.buildprint.client.render.SchematicPreview", "currentSchematic") == schematic, "Native schematic preview is present before closure");
        check(callStatic("com.buildprint.client.render.SchematicPreview", "isActive").equals(true), "Native schematic preview starts active before closure");
    }

    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static Object call(Object instance, String method) throws Exception {
        return instance.getClass().getMethod(method).invoke(instance);
    }

    private static Object callStatic(String className, String method) throws Exception {
        return Class.forName(className).getMethod(method).invoke(null);
    }
}
