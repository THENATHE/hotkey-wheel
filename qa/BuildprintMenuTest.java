package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.ToolPouchIntegration;
import com.thenathe.hotkeywheel.ToolPouchRadialScreen;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.WheelMenuReturn;
import com.thenathe.hotkeywheel.WheelScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BuildprintMenuTest implements FabricClientGameTest {
    private static final String BUILDPRINT_SCREEN = "com.buildprint.client.gui.BuildprintScreen";
    private static final String INSTAMINE_SCREEN = "com.thenathe.buildprinttoning.client.InstamineSettingsScreen";
    private final List<String> checks = new ArrayList<>();
    private Object schematic;
    private Object loaded;
    private InputConstants.Key forwardKey;
    private int nativeOpenKey;
    private boolean editorApi;
    private int editorEnters;
    private int editorExits;

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
                setupEditorApi(context);
                report.put("public_editor_api", editorApi);
                cancelHeldWheel(context);
                selectionEditor(context);
                rootClose(context);
                childClose(context);
                originalCycleOpener(context);
                nativeOpeningStaysIndependent(context);
                validatedAddonClose(context);
                disabledIntegration(context);
                unrelatedReplacement(context);
                nonMenuActionDoesNotClaimScreen(context);
                context.runOnClient(client -> {
                    if (editorApi) {
                        check(editorEnters > 0 && editorEnters == editorExits,
                                "Public editor lifecycle delivers balanced enter and exit events across wheel and native sessions");
                        Method usingApi = Class.forName("com.thenathe.hotkeywheel.BuildprintApiIntegration")
                                .getDeclaredMethod("hasEditorApi");
                        usingApi.setAccessible(true);
                        check((boolean) usingApi.invoke(null),
                                "Hotkey Wheel uses the public editor bridge throughout modern Buildprint scenarios");
                        Class<?> legacy = Class.forName("com.thenathe.hotkeywheel.BuildprintEditorReturn$NativeEditor");
                        Field active = legacy.getDeclaredField("active");
                        Field freecam = legacy.getDeclaredField("freecam");
                        active.setAccessible(true);
                        freecam.setAccessible(true);
                        check(active.get(null) == null && freecam.get(null) == null,
                                "Modern editor lifecycle tracking never resolves legacy active or freecam reflection");
                        report.put("editor_enter_events", editorEnters);
                        report.put("editor_exit_events", editorExits);
                    }
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

    private void setupEditorApi(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            Class<?> api;
            try {
                api = Class.forName("com.buildprint.api.editor.BuildprintEditor");
            } catch (ClassNotFoundException legacy) {
                return;
            }
            editorApi = true;
            Class<?> listenerType = Class.forName("com.buildprint.api.editor.EditorListener");
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "onEnter" -> editorEnters++;
                            case "onExit" -> editorExits++;
                            case "equals" -> { return proxy == args[0]; }
                            case "hashCode" -> { return System.identityHashCode(proxy); }
                            case "toString" -> { return "Hotkey Wheel QA lifecycle observer"; }
                            default -> { }
                        }
                        return null;
                    });
            api.getMethod("addListener", listenerType).invoke(null, listener);
            check(!callStatic("com.buildprint.api.editor.BuildprintEditor", "isActive").equals(true),
                    "Public editor API starts inactive before any wheel action");
            check(!hudHidden(), "Public HUD starts visible outside radial menus");
            // This is a HUD predicate fixture only; native Tool Pouch behavior has its own suite tests.
            var snapshot = new ToolPouchIntegration.Snapshot(ToolPouchIntegration.Context.SHULKERS,
                    List.of(new ToolPouchIntegration.ItemChoice(0, new ItemStack(Items.SHULKER_BOX))));
            client.gui.setScreen(new ToolPouchRadialScreen(snapshot));
            check(hudHidden(), "Public HUD predicate also recognizes the Tool Pouch item radial");
            client.gui.screen().onClose();
            check(!hudHidden(), "Closing the Tool Pouch radial releases its public HUD suppression");
        });
    }

    private void apiOwnedDialogs(ClientGameTestContext context, Object originalInput) throws Exception {
        int entersBefore = editorEnters;
        int exitsBefore = editorExits;
        openSelectionEditor(context, false, false);
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() != null
                        && client.gui.screen().getClass().getName().equals("com.buildprint.client.gui.ToolPickerScreen"),
                "Wheel-owned editor opens Buildprint's native API tool picker"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null,
                    "Original wheel opener closes the API tool picker and its editor session");
            check(client.getCameraEntity() == client.player && client.player.input == originalInput,
                    "Closing the API tool picker restores native camera and player input");
            check(editorEnters == entersBefore + 1 && editorExits == exitsBefore + 1,
                    "Wheel-owned native editor open and close each deliver exactly one public lifecycle event");
        });

        openSelectionEditor(context, false, false);
        openSplineSettings(context);
        double previousWidth = context.computeOnClient(client ->
                Class.forName("com.thenathe.buildprinttoning.AddonSession").getField("halfWidth").getDouble(null));
        context.runOnClient(client -> ((EditBox) field(client.gui.screen(), "halfWidth")).setValue("-1"));
        context.clickScreenButton("Done");
        context.waitTicks(2);
        context.runOnClient(client -> check(!((String) field(client.gui.screen(), "error")).isEmpty(),
                "API addon spline settings keep native validation when Done is pressed"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null,
                    "Original opener exits API addon spline settings and the whole wheel-owned editor");
            check(Class.forName("com.thenathe.buildprinttoning.AddonSession").getField("halfWidth").getDouble(null) == previousWidth,
                    "Wheel closure respects spline settings native Cancel semantics and preserves saved geometry");
        });
    }

    private void apiSessionReplacement(ClientGameTestContext context) throws Exception {
        openSelectionEditor(context, false, false);
        int entersBefore = editorEnters;
        int exitsBefore = editorExits;
        context.runOnClient(client -> {
            // Both transitions happen before another wheel tick, exercising event-based session identity.
            Method toggle = Class.forName("com.buildprint.client.edit.EditorController")
                    .getDeclaredMethod("toggle", Minecraft.class);
            toggle.setAccessible(true);
            toggle.invoke(null, client);
            toggle.invoke(null, client);
            check(editorActive() && editorEnters == entersBefore + 1 && editorExits == exitsBefore + 1,
                    "Public lifecycle observes native exit and re-entry within the same client callback");
            check(!WheelMenuReturn.handleInput(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G), InputConstants.PRESS),
                    "Original wheel opener cannot claim the replacement native session before the next tick");
            check(editorActive(), "Replacement native session survives the stale wheel opener");
            toggle.invoke(null, client);
        });
        context.waitTicks(2);
    }

    private void openSplineSettings(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> check((boolean) Class.forName("com.buildprint.api.editor.BuildprintEditor")
                        .getMethod("selectTool", String.class).invoke(null, "buildprint_toning:spline_settings"),
                "Public editor API selects Blocktone spline settings by its registered tool ID"));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() != null && client.gui.screen().getClass().getName()
                        .equals("com.thenathe.buildprinttoning.client.SplineSettingsScreen"),
                "Clicking the selected API addon tool opens its actual spline settings"));
    }

    private static boolean hudHidden() throws Exception {
        return (boolean) callStatic("com.buildprint.api.BuildprintHud", "isHidden");
    }

    private void selectionEditor(ClientGameTestContext context) throws Exception {
        InputConstants.Key originalEditorKey = context.computeOnClient(client -> InputConstants.getKey(control("key.buildprint.editor").saveString()));
        Object[] originalInput = new Object[1];
        Object[] region = new Object[1];
        int editorKey = context.computeOnClient(BuildprintMenuTest::unusedKey);
        context.runOnClient(client -> {
            originalInput[0] = client.player.input;
            Object selection = callStatic("com.buildprint.client.edit.EditorMode", "selection");
            call(selection, "clear");
            Class<?> regionType = Class.forName("com.buildprint.schematic.selection.RegionSelection");
            region[0] = regionType.getMethod("single", BlockPos.class).invoke(null, client.player.blockPosition());
            selection.getClass().getMethod("add", regionType).invoke(selection, region[0]);
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).entries.get(0).control = "key.buildprint.editor";
            config.wheels.get(1).entries.get(0).control = "key.buildprint.editor";
            check(WheelConfig.save(config), "Selection editor can be assigned to an actual wheel slot");
            control("key.buildprint.editor").setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        });
        openSelectionEditor(context, false, false);
        context.takeScreenshot("buildprint-wheel-selection-editor-before-close");
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null, "Original wheel opener exits the Buildprint selection editor instead of reopening a wheel");
            check(client.getCameraEntity() == client.player && callStatic("com.buildprint.client.edit.EditorMode", "freecam") == null,
                    "Selection editor closure restores the player camera through native exit");
            check(client.player.input == originalInput[0], "Selection editor closure restores the original player input");
            check(call(callStatic("com.buildprint.client.edit.EditorMode", "selection"), "active") == region[0],
                    "Selection editor closure preserves the existing selected region");
        });

        if (editorApi) {
            apiOwnedDialogs(context, originalInput[0]);
            apiSessionReplacement(context);
        }

        openSelectionEditor(context, true, false);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive() && client.gui.screen() == null, "Toggle wheel's original opener exits the selection editor"));

        openSelectionEditor(context, false, true);
        context.getInput().pressKey(InputConstants.KEY_H);
        context.waitTicks(2);
        context.runOnClient(client -> check(editorActive(), "Another wheel's key does not exit a selection editor opened with G"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive() && client.gui.screen() == null, "Original opener exits selection editor after cycling wheels"));

        openSelectionEditor(context, false, false);
        screenshotFromOtherWheel(context);
        openSelectionSave(context);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null,
                    "Original opener exits the whole editor and Save dialog after another wheel takes a screenshot");
            check(client.getCameraEntity() == client.player && client.player.input == originalInput[0],
                    "Exit after another wheel action restores the native camera and player input");
        });

        openSelectionEditor(context, false, false);
        openSelectionSave(context);
        context.clickScreenButton("Cancel");
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(editorActive() && client.gui.screen() == null, "Native selection Save dialog Cancel returns to the active editor");
            check(call(callStatic("com.buildprint.client.edit.EditorMode", "selection"), "active") == region[0],
                    "Native selection Save dialog Cancel preserves the selected region");
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive(), "Wheel opener still exits editor after native Save dialog cancellation"));

        openSelectionEditor(context, false, false);
        openSelectionSave(context);
        context.takeScreenshot("buildprint-wheel-selection-editor-save-dialog");
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive() && client.gui.screen() == null, "Wheel opener closes an owned Save dialog and exits its selection editor"));

        openSelectionEditor(context, false, false);
        openSelectionSave(context);
        Path output = context.computeOnClient(client -> {
            Screen save = client.gui.screen();
            ((EditBox) field(save, "nameBox")).setValue("wheel-qa-selection-" + Long.toUnsignedString(System.nanoTime()));
            Method fileName = save.getClass().getDeclaredMethod("fileName");
            fileName.setAccessible(true);
            return ((Path) callStatic("com.buildprint.schematic.SchematicLibrary", "rootDir")).resolve((String) fileName.invoke(save));
        });
        String saveButton = context.computeOnClient(client -> net.minecraft.network.chat.Component.translatable("buildprint.save.quick").getString());
        context.clickScreenButton(saveButton);
        context.waitFor(client -> Files.isRegularFile(output), 200);
        context.runOnClient(client -> {
            check(Files.size(output) > 0, "Native selection Save still writes an actual blueprint file");
            check(editorActive() && client.gui.screen() == null, "Native selection Save returns to the active editor");
            check(call(callStatic("com.buildprint.client.edit.EditorMode", "selection"), "isEmpty").equals(true),
                    "Successful native selection Save clears the selection through Buildprint's normal callback");
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive(), "Wheel opener still exits editor after native blueprint saving"));

        context.runOnClient(client -> {
            control("key.buildprint.editor").setKey(InputConstants.Type.KEYBOARD.getOrCreate(editorKey));
            KeyMapping.resetMapping();
        });
        context.getInput().pressKey(editorKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(editorActive(), "The editor still opens through its ordinary native binding"));
        screenshotFromOtherWheel(context);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(editorActive(), "Wheel opener does not exit an independently opened native selection editor");
            Object selection = callStatic("com.buildprint.client.edit.EditorMode", "selection");
            selection.getClass().getMethod("add", region[0].getClass()).invoke(selection, region[0]);
        });
        if (editorApi) {
            String previousTool = context.computeOnClient(client ->
                    (String) callStatic("com.buildprint.api.editor.BuildprintEditor", "activeTool"));
            openSplineSettings(context);
            Screen nativeSpline = context.computeOnClient(client -> client.gui.screen());
            context.getInput().pressKey(InputConstants.KEY_G);
            context.waitTicks(2);
            context.runOnClient(client -> check(client.gui.screen() == nativeSpline && editorActive(),
                    "Wheel opener leaves API addon settings in an independently opened editor alone"));
            context.clickScreenButton("Cancel");
            context.waitTicks(2);
            context.runOnClient(client -> check((boolean) Class.forName("com.buildprint.api.editor.BuildprintEditor")
                            .getMethod("selectTool", String.class).invoke(null, previousTool),
                    "Native editor tool selection is restored after independent addon settings cancellation"));
        }
        openSelectionSave(context);
        Screen nativeSave = context.computeOnClient(client -> client.gui.screen());
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == nativeSave && editorActive(),
                "Wheel opener leaves a native Save dialog open after an earlier wheel-owned editor session closed"));
        context.takeScreenshot("buildprint-native-editor-save-not-owned-by-wheel");
        context.clickScreenButton("Cancel");
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == null && editorActive(), "Native Save dialog Cancel still returns to its independently opened editor");
            check(call(callStatic("com.buildprint.client.edit.EditorMode", "selection"), "active") == region[0],
                    "Canceling an independently opened Save dialog keeps its selection intact");
        });
        context.getInput().pressKey(editorKey);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive(), "Native editor binding still exits an independently opened selection editor");
            WheelConfig config = WheelConfig.get().copy();
            config.closeBuildprintWithWheelKey = false;
            check(WheelConfig.save(config), "Selection editor return integration can be disabled");
        });
        openSelectionEditor(context, false, false);
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(editorActive(), "Disabled integration does not take over selection editor closure"));
        context.getInput().pressKey(editorKey);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive(), "Native editor exit works when wheel return integration is disabled");
            control("key.buildprint.editor").setKey(originalEditorKey);
            KeyMapping.resetMapping();
            WheelConfig config = WheelConfig.get().copy();
            config.closeBuildprintWithWheelKey = true;
            config.wheels.get(0).toggleOpen = false;
            config.wheels.get(0).entries.get(0).control = "key.buildprint.open";
            config.wheels.get(1).entries.get(0).control = "key.buildprint.open";
            check(WheelConfig.save(config), "Restore ordinary Buildprint menu fixture after selection editor regression");
        });
    }

    private void openSelectionEditor(ClientGameTestContext context, boolean toggle, boolean cycle) throws Exception {
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).toggleOpen = toggle;
            check(WheelConfig.save(config), "Selection editor opening mode fixture saves");
            check(!editorActive() && client.gui.screen() == null, "Selection editor activation starts from ordinary gameplay");
        });
        if (toggle) context.getInput().pressKey(InputConstants.KEY_G);
        else context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        if (cycle) context.getInput().pressKey(InputConstants.KEY_TAB);
        pointTop(context);
        if (toggle) context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        else context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> {
            check(editorActive() && client.gui.screen() == null, "Wheel selection opens actual Buildprint freecam without a Screen");
            if (editorApi) check((boolean) callStatic("com.buildprint.api.editor.BuildprintEditor", "isActive"),
                    "Public editor API confirms the wheel-opened editor is active");
            check(client.getCameraEntity() != client.player, "Selection editor activation switches to the native freecam camera");
        });
    }

    private void openSelectionSave(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_3);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() != null
                && client.gui.screen().getClass().getName().equals("com.buildprint.client.gui.EditorSaveScreen"),
                "Native selection toolbar opens its actual Save dialog"));
    }

    private void screenshotFromOtherWheel(ClientGameTestContext context) throws Exception {
        Object session = context.computeOnClient(client -> callStatic("com.buildprint.client.edit.EditorMode", "freecam"));
        InputConstants.Key previousKey = context.computeOnClient(client -> InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[1].saveString()));
        int secondWheelKey = context.computeOnClient(BuildprintMenuTest::unusedKey);
        context.runOnClient(client -> {
            // Buildprint reserves H for its controls card before wheel input reaches our handler.
            HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.Type.KEYBOARD.getOrCreate(secondWheelKey));
            KeyMapping.resetMapping();
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(1).toggleOpen = false;
            config.wheels.get(1).entries.get(0).control = client.options.keyScreenshot.getName();
            check(WheelConfig.save(config), "Second wheel can run a screenshot during selection editing");
        });
        context.getInput().holdKey(secondWheelKey);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof WheelScreen wheel && wheel.wheelIndex() == 1,
                    "The second wheel's physical key opens its radial menu inside the active selection editor");
            if (editorApi) check(hudHidden(), "Public HUD suppression hides editor panels while a physical wheel is open");
        });
        pointTop(context);
        context.getInput().releaseKey(secondWheelKey);
        context.waitTicks(4);
        context.runOnClient(client -> {
            check(editorActive() && client.gui.screen() == null
                            && callStatic("com.buildprint.client.edit.EditorMode", "freecam") == session,
                    "Selecting Screenshot through another wheel keeps the exact native editor session active");
            if (editorApi) check(!hudHidden(), "Public HUD suppression releases editor panels after wheel selection");
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(1).entries.get(0).control = "key.buildprint.editor";
            check(WheelConfig.save(config), "Restore second wheel's selection editor action after screenshot");
            HotkeyWheelClient.WHEEL_KEYS[1].setKey(previousKey);
            KeyMapping.resetMapping();
        });
    }

    private static boolean editorActive() throws Exception {
        return (boolean) callStatic("com.buildprint.client.edit.EditorMode", "isActive");
    }

    private static int unusedKey(Minecraft client) {
        int[] candidates = {InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8,
                InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_Y, InputConstants.KEY_U,
                InputConstants.KEY_B, InputConstants.KEY_V, InputConstants.KEY_N, InputConstants.KEY_M};
        return java.util.Arrays.stream(candidates).filter(key -> java.util.Arrays.stream(client.options.keyMappings)
                .noneMatch(mapping -> mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(key))))
                .findFirst().orElseThrow(() -> new AssertionError("No unused editor fixture key"));
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
        InputConstants.Key originalKey = context.computeOnClient(client -> InputConstants.getKey(control("key.buildprint_toning.instamine_settings").saveString()));
        int nativeKey = context.computeOnClient(BuildprintMenuTest::unusedKey);
        context.runOnClient(client -> {
            control("key.buildprint_toning.instamine_settings").setKey(InputConstants.Type.KEYBOARD.getOrCreate(nativeKey));
            KeyMapping.resetMapping();
        });
        context.getInput().pressKey(nativeKey);
        context.waitTicks(3);
        Screen nativeSettings = context.computeOnClient(client -> client.gui.screen());
        context.runOnClient(client -> check(nativeSettings != null && nativeSettings.getClass().getName().equals(INSTAMINE_SCREEN),
                "Blocktone settings still open through their registered native hotkey"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == nativeSettings, "Wheel opener leaves independently opened native Blocktone settings open");
            nativeSettings.onClose();
            check(client.gui.screen() == null, "Independently opened Blocktone settings retain their normal validated closure");
            control("key.buildprint_toning.instamine_settings").setKey(originalKey);
            KeyMapping.resetMapping();
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
