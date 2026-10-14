package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelMenuReturn;
import com.thenathe.hotkeywheel.WheelScreen;
import java.lang.reflect.Field;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/** Exercises the user's side-mouse opener through native callbacks, including Buildprint's mouse mixin. */
public final class SideMouseEditorTest implements FabricClientGameTest {
    private static final int OPENER = InputConstants.MOUSE_BUTTON_4;
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String, Object>> scenarios = new ArrayList<>();
    private Object originalInput;
    private Object region;
    private int nativeEditorKey;
    private WheelConfig originalConfig;
    private final Map<KeyMapping, InputConstants.Key> originalKeys = new LinkedHashMap<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        report.put("scenarios", scenarios);
        report.put("input_path", "Fabric client gametest native mouse callbacks, index3; no direct wheel handler calls");
        report.put("opener", "key.mouse.4");
        report.put("native_editor_key", "key.keyboard.semicolon");
        try {
            context.runOnClient(client -> {
                Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                        .getConstructor().newInstance();
                onboarding.getClass().getMethod("markDone").invoke(onboarding);
                client.gui.setScreen(null);
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                setup(context);
                openEditor(context, false, false, false);
                closeEditor(context, "hold-release");
                openEditor(context, false, true, false);
                closeEditor(context, "click-while-held");
                openEditor(context, true, false, false);
                closeEditor(context, "toggle");
                openEditor(context, false, false, true);
                closeEditor(context, "cycled-original-opener");

                openEditor(context, false, false, false);
                openSave(context);
                context.clickScreenButton("Cancel");
                context.waitTicks(3);
                context.runOnClient(client -> check(active() && client.gui.screen() == null,
                        "Native Save Cancel returns to the side-mouse-owned freecam"));
                closeEditor(context, "save-cancel");

                openEditor(context, false, false, false);
                openSave(context);
                closeEditor(context, "save-child-close");

                openEditor(context, false, false, false);
                openSave(context);
                Path output = context.computeOnClient(client -> {
                    Screen save = client.gui.screen();
                    ((EditBox) field(save, "nameBox")).setValue("side-mouse-qa-" + Long.toUnsignedString(System.nanoTime()));
                    var fileName = save.getClass().getDeclaredMethod("fileName");
                    fileName.setAccessible(true);
                    return ((Path) call("com.buildprint.schematic.SchematicLibrary", "rootDir"))
                            .resolve((String) fileName.invoke(save));
                });
                String saveLabel = context.computeOnClient(client -> net.minecraft.network.chat.Component
                        .translatable("buildprint.save.quick").getString());
                context.clickScreenButton(saveLabel);
                context.waitFor(client -> Files.isRegularFile(output), 200);
                context.runOnClient(client -> {
                    check(Files.size(output) > 0 && active(), "Native Save writes a real blueprint and retains the editor");
                    check((boolean) call("com.buildprint.client.edit.EditorMode", "selection").getClass()
                            .getMethod("isEmpty").invoke(call("com.buildprint.client.edit.EditorMode", "selection")),
                            "Native successful Save clears the selected region");
                });
                closeEditor(context, "native-save");

                context.getInput().pressKey(nativeEditorKey);
                context.waitTicks(4);
                context.runOnClient(client -> check(active(), "An independent native key opens the editor"));
                context.getInput().holdMouse(OPENER);
                context.waitTicks(2);
                context.getInput().releaseMouse(OPENER);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    check(active(), "Side-mouse opener does not exit an independently opened native session");
                    if (client.gui.screen() instanceof WheelScreen wheel) wheel.onClose();
                });
                context.getInput().pressKey(nativeEditorKey);
                context.waitTicks(4);
                context.runOnClient(client -> check(!active(), "Independent editor still exits using its native key"));
                report.put("passed", true);
            }
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Side-mouse Buildprint editor QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    WheelMenuReturn.clear();
                    for (var entry : originalKeys.entrySet()) {
                        entry.getKey().setKey(entry.getValue());
                        entry.getKey().setDown(false);
                        while (entry.getKey().consumeClick()) { }
                    }
                    KeyMapping.resetMapping();
                    if (originalConfig != null) WheelConfig.save(originalConfig);
                });
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "side-mouse-editor-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        }
    }

    private void setup(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            WheelMenuReturn.clear();
            originalConfig = WheelConfig.get().copy();
            for (var mapping : client.options.keyMappings) originalKeys.put(mapping, InputConstants.getKey(mapping.saveString()));
            WheelConfig config = new WheelConfig();
            config.wheelCount = 2;
            config.closeBuildprintWithWheelKey = true;
            for (var wheel : config.wheels) wheel.entries.get(0).control = "key.buildprint.editor";
            check(WheelConfig.save(config), "Side-mouse fixture saves isolated wheel preferences");
            for (var mapping : client.options.keyMappings) {
                if (mapping.matches(InputConstants.Type.MOUSE.getOrCreate(OPENER))) mapping.setKey(InputConstants.UNKNOWN);
            }
            HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.MOUSE.getOrCreate(OPENER));
            HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.UNKNOWN);
            HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
            nativeEditorKey = InputConstants.KEY_SEMICOLON;
            for (var mapping : client.options.keyMappings) {
                if (mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(nativeEditorKey))) mapping.setKey(InputConstants.UNKNOWN);
            }
            HotkeyWheelClient.findControl("key.buildprint.editor")
                    .setKey(InputConstants.Type.KEYBOARD.getOrCreate(nativeEditorKey));
            KeyMapping.resetMapping();
            originalInput = client.player.input;
            Object selection = call("com.buildprint.client.edit.EditorMode", "selection");
            selection.getClass().getMethod("clear").invoke(selection);
            Class<?> type = Class.forName("com.buildprint.schematic.selection.RegionSelection");
            region = type.getMethod("single", BlockPos.class).invoke(null, client.player.blockPosition());
            selection.getClass().getMethod("add", type).invoke(selection, region);
            check(HotkeyWheelClient.WHEEL_KEYS[0].saveString().equals("key.mouse.4"),
                    "Fixture uses the user's mouse button index3 mapping");
        });
    }

    private void openEditor(ClientGameTestContext context, boolean toggle, boolean click, boolean cycle) throws Exception {
        context.runOnClient(client -> {
            check(!active() && client.gui.screen() == null, "Editor scenario begins in ordinary gameplay");
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).toggleOpen = toggle;
            check(WheelConfig.save(config), "Scenario opening mode saves");
        });
        if (toggle) context.getInput().pressMouse(OPENER);
        else context.getInput().holdMouse(OPENER);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen,
                "Actual side-mouse input opens the radial wheel"));
        if (cycle) context.getInput().pressKey(InputConstants.KEY_TAB);
        double[] position = context.computeOnClient(client -> {
            WheelScreen wheel = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(0, WheelConfig.get().wheels.get(wheel.wheelIndex()).entries.size());
            return new double[]{(wheel.centerX() + Math.cos(angle) * wheel.radius() * 0.76)
                    * client.getWindow().getScreenWidth() / wheel.width,
                    (wheel.centerY() + Math.sin(angle) * wheel.radius() * 0.76)
                    * client.getWindow().getScreenHeight() / wheel.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        if (toggle || click) context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        else context.getInput().releaseMouse(OPENER);
        context.waitTicks(5);
        context.runOnClient(client -> check(active() && client.gui.screen() == null
                        && client.getCameraEntity() != client.player,
                "Native side-mouse selection enters Buildprint freecam"));
        if (click && !toggle) {
            context.getInput().releaseMouse(OPENER);
            context.waitTicks(3);
            context.runOnClient(client -> check(active(), "Original opener release after clicking does not prematurely exit freecam"));
        }
    }

    private void closeEditor(ClientGameTestContext context, String scenario) throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("scenario", scenario);
        row.put("before", context.computeOnClient(SideMouseEditorTest::snapshot));
        scenarios.add(row);
        context.takeScreenshot("side-mouse-editor-" + scenario + "-before-close");
        context.getInput().pressMouse(OPENER);
        context.waitTicks(4);
        row.put("after", context.computeOnClient(SideMouseEditorTest::snapshot));
        if (context.computeOnClient(client -> active())) {
            context.takeScreenshot("side-mouse-editor-" + scenario + "-blocked-close");
            context.getInput().pressKey(nativeEditorKey);
            context.waitTicks(4);
            row.put("after_native_semicolon", context.computeOnClient(SideMouseEditorTest::snapshot));
            context.runOnClient(client -> check(!active(), "Native semicolon still closes the editor after blocked side-mouse input"));
            throw new AssertionError(scenario + ": original side-mouse opener failed to exit the editor; native semicolon succeeds");
        }
        context.runOnClient(client -> {
            check(!active() && client.gui.screen() == null, scenario + ": original side-mouse opener exits the whole editor");
            check(client.getCameraEntity() == client.player && client.player.input == originalInput,
                    scenario + ": native camera and player input are restored");
            if (!scenario.equals("native-save")) {
                Object selection = call("com.buildprint.client.edit.EditorMode", "selection");
                check(selection.getClass().getMethod("active").invoke(selection) == region,
                        scenario + ": side-mouse closure preserves the exact selected region");
            }
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                    scenario + ": closing leaves no side-mouse held state or queued native click");
        });
    }

    private void openSave(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_3);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() != null && client.gui.screen().getClass().getName()
                .equals("com.buildprint.client.gui.EditorSaveScreen"), "Native toolbar mouse click opens Save dialog"));
    }

    private static Map<String, Object> snapshot(Minecraft client) throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("editor_active", active());
        row.put("screen", client.gui.screen() == null ? "gameplay" : client.gui.screen().getClass().getName());
        row.put("player_camera", client.getCameraEntity() == client.player);
        row.put("mouse_grabbed", client.mouseHandler.isMouseGrabbed());
        Class<?> ownership = Class.forName("com.thenathe.hotkeywheel.BuildprintEditorReturn");
        row.put("wheel_owned_session", field(ownership, "session") != null);
        row.put("owned_opener", String.valueOf(field(ownership, "opener")));
        return row;
    }

    private static boolean active() throws Exception {
        return (boolean) call("com.buildprint.api.editor.BuildprintEditor", "isActive");
    }

    private static Object call(String type, String method) throws Exception {
        return Class.forName(type).getMethod(method).invoke(null);
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target instanceof Class<?> actual ? actual : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target instanceof Class<?> ? null : target);
    }
}
