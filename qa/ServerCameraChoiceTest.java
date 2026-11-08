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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Buildprint 0.1.38 asks which camera the editor should use the first time it opens on a server.
 * This joins a real dedicated server, started in this JVM by the client gametest API, so the question appears.
 */
public final class ServerCameraChoiceTest implements FabricClientGameTest {
    private static final String CAMERA_CHOICE = "com.voxelsprout.common.freecam.CameraChoice";
    private static final String EDITOR = "com.buildprint.api.editor.BuildprintEditor";
    private final List<String> checks = new ArrayList<>();
    private final List<String> scenarios = new ArrayList<>();
    private int nativeEditorKey;
    private InputConstants.Key originalEditorKey;

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        report.put("scenarios", scenarios);
        try {
            context.runOnClient(client -> {
                Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                        .getConstructor().newInstance();
                onboarding.getClass().getMethod("markDone").invoke(onboarding);
                client.gui.setScreen(null);
            });
            setup(context);
            try (var server = context.worldBuilder().createServer(); var connection = server.connect()) {
                connection.waitForChunksDownload();
                context.waitTicks(20);
                context.runOnClient(client -> {
                    check(client.getSingleplayerServer() == null && client.getCurrentServer() != null,
                            "Client is connected to a dedicated server, not a singleplayer world");
                    report.put("server", client.getCurrentServer().ip);
                });
                firstSessionFromWheel(context);
                secondSessionFromWheel(context);
                cancelledQuestion(context);
                openerClosesQuestion(context);
                walkingCameraAnswer(context);
                nativeOpeningAfterAnswer(context);
            }
            context.runOnClient(client -> {
                control("key.buildprint.editor").setKey(originalEditorKey);
                KeyMapping.resetMapping();
                WheelMenuReturn.clear();
            });
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Buildprint server camera question QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "server-camera-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void setup(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            WheelConfig config = new WheelConfig();
            config.closeBuildprintWithWheelKey = true;
            config.wheels.get(0).toggleOpen = false;
            config.wheels.get(0).entries.get(0).control = "key.buildprint.editor";
            check(WheelConfig.save(config), "Wheel 1 slot 1 holds Buildprint's editor control");
            HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
            originalEditorKey = InputConstants.getKey(control("key.buildprint.editor").saveString());
            int[] candidates = {InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8,
                    InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_Y, InputConstants.KEY_U};
            nativeEditorKey = java.util.Arrays.stream(candidates).filter(key -> java.util.Arrays.stream(client.options.keyMappings)
                    .noneMatch(mapping -> mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(key))))
                    .findFirst().orElseThrow(() -> new AssertionError("No unused native editor key"));
            control("key.buildprint.editor").setKey(InputConstants.Type.KEYBOARD.getOrCreate(nativeEditorKey));
            KeyMapping.resetMapping();
        });
    }

    private void firstSessionFromWheel(ClientGameTestContext context) throws Exception {
        scenarios.add("first wheel opening on a new server, free camera answer");
        forgetCameraAnswers(context);
        Object originalInput = context.computeOnClient(client -> client.player.input);
        selectEditorFromWheel(context);
        context.runOnClient(client -> {
            check(isCameraQuestion(client.gui.screen()), "First editor opening on a server shows Buildprint's camera question");
            check(!editorActive(), "The editor waits for the camera answer");
        });
        context.takeScreenshot("server-camera-question-from-wheel");
        answer(context, "free");
        context.runOnClient(client -> {
            check(editorActive() && client.gui.screen() == null, "Choosing the free camera starts the editor");
            check(client.getCameraEntity() != client.player, "The answered session uses freecam");
        });
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null,
                    "The wheel opener closes the editor started from the camera question");
            check(client.getCameraEntity() == client.player && client.player.input == originalInput,
                    "Closing the answered session restores the player camera and input");
            check(knownCamera(client).equals("FREE"), "Buildprint remembers the free camera answer for this server");
        });
    }

    private void secondSessionFromWheel(ClientGameTestContext context) throws Exception {
        scenarios.add("second wheel opening on the same server, no question");
        selectEditorFromWheel(context);
        context.runOnClient(client -> check(editorActive() && client.gui.screen() == null,
                "A later wheel opening on the server starts the editor without asking again"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive() && client.gui.screen() == null,
                "The wheel opener closes the second session"));
    }

    private void cancelledQuestion(ClientGameTestContext context) throws Exception {
        scenarios.add("question cancelled with Escape, then a native opening");
        forgetCameraAnswers(context);
        selectEditorFromWheel(context);
        context.runOnClient(client -> check(isCameraQuestion(client.gui.screen()), "The camera question appears again after forgetting the answer"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null && !editorActive(),
                "Escape cancels the question without starting the editor"));
        context.waitTicks(10);
        context.getInput().pressKey(nativeEditorKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(isCameraQuestion(client.gui.screen()),
                "Buildprint's own editor key asks the camera question too"));
        answer(context, "free");
        context.runOnClient(client -> check(editorActive(), "The natively opened editor starts after the answer"));
        assertNotClaimed(context, "after a cancelled wheel question");
    }

    private void openerClosesQuestion(ClientGameTestContext context) throws Exception {
        scenarios.add("question closed with the original wheel opener");
        forgetCameraAnswers(context);
        selectEditorFromWheel(context);
        context.runOnClient(client -> check(isCameraQuestion(client.gui.screen()), "The wheel opening shows the camera question"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() == null && !editorActive(),
                    "The original wheel opener closes the question without starting the editor");
            check(!HotkeyWheelClient.WHEEL_KEYS[0].isDown() && !HotkeyWheelClient.WHEEL_KEYS[0].consumeClick(),
                    "Closing the question does not leave the wheel key pressed or clicked");
            check(knownCamera(client) == null, "A closed question leaves the server's camera choice unanswered");
        });
        context.waitTicks(10);
        context.runOnClient(client -> check(client.gui.screen() == null && !editorActive(),
                "No editor starts later from the closed question"));
    }

    private void walkingCameraAnswer(ClientGameTestContext context) throws Exception {
        scenarios.add("first wheel opening answered with the walking camera");
        Object originalInput = context.computeOnClient(client -> client.player.input);
        selectEditorFromWheel(context);
        context.runOnClient(client -> check(isCameraQuestion(client.gui.screen()), "The unanswered server asks again"));
        answer(context, "walk");
        context.runOnClient(client -> check(editorActive() && client.getCameraEntity() == client.player,
                "Choosing the walking camera starts the editor on the player's own camera"));
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(!editorActive() && client.gui.screen() == null,
                    "The wheel opener closes the walking-camera session started from the question");
            check(client.player.input == originalInput, "Closing the walking-camera session restores player input");
            check(knownCamera(client).equals("WALK"), "Buildprint remembers the walking camera answer for this server");
        });
    }

    private void nativeOpeningAfterAnswer(ClientGameTestContext context) throws Exception {
        scenarios.add("native opening on an answered server");
        context.getInput().pressKey(nativeEditorKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(editorActive() && client.gui.screen() == null,
                "Buildprint's own key opens the editor directly once the server is answered"));
        assertNotClaimed(context, "on an answered server");
    }

    private void assertNotClaimed(ClientGameTestContext context, String when) throws Exception {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(3);
        context.runOnClient(client -> check(editorActive() && client.gui.screen() instanceof WheelScreen,
                "The wheel key opens a wheel instead of closing a natively opened editor " + when));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(editorActive() && client.gui.screen() == null,
                "Cancelling that wheel leaves the native editor open " + when));
        context.getInput().pressKey(nativeEditorKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(!editorActive(), "Buildprint's own key still closes its editor " + when));
    }

    private void selectEditorFromWheel(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> check(!editorActive() && client.gui.screen() == null,
                "Editor opening starts from ordinary gameplay"));
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "Holding G opens wheel 1"));
        pointTop(context);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
    }

    // The question's tiles run each choice's own action when clicked; this runs the same action.
    private void answer(ClientGameTestContext context, String view) throws Exception {
        context.runOnClient(client -> {
            Screen question = client.gui.screen();
            Field choicesField = question.getClass().getDeclaredField("choices");
            choicesField.setAccessible(true);
            Runnable pick = null;
            for (Object choice : (List<?>) choicesField.get(question)) {
                Component name = (Component) choice.getClass().getMethod("name").invoke(choice);
                if (name.getContents() instanceof TranslatableContents key
                        && key.getKey().equals("voxelsprout_controls.camera." + view)) {
                    pick = (Runnable) choice.getClass().getMethod("pick").invoke(choice);
                }
            }
            check(pick != null, "Camera question offers the " + view + " camera");
            pick.run();
        });
        context.waitTicks(3);
    }

    private void forgetCameraAnswers(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            Class.forName(CAMERA_CHOICE).getMethod("forgetAll").invoke(null);
            check(knownCamera(client) == null, "This server has no remembered camera answer");
        });
    }

    private static String knownCamera(Minecraft client) throws Exception {
        Object view = Class.forName(CAMERA_CHOICE).getMethod("known", Minecraft.class).invoke(null, client);
        return view == null ? null : view.toString();
    }

    private static boolean isCameraQuestion(Screen screen) {
        return screen != null && screen.getClass().getName().equals("com.voxelsprout.common.ui.settings.KitChoiceDialog")
                && screen.getTitle().getContents() instanceof TranslatableContents title
                && title.getKey().equals("voxelsprout_controls.camera.title");
    }

    private static boolean editorActive() throws Exception {
        return (boolean) Class.forName(EDITOR).getMethod("isActive").invoke(null);
    }

    private static KeyMapping control(String name) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().equals(name)) return mapping;
        }
        throw new AssertionError("Missing control " + name);
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
}
