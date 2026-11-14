package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ElytraFlight;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.WheelActions;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The elytra flight switch: its key, /hotkeywheel elytra and the wheel action stop a glide from starting with a
 * vanilla elytra and with a Vanilla+ Features armored chestplate, the state is saved at once and survives a reload,
 * and with Vanilla++ installed none of it is offered and a saved Off is ignored. On a separate dedicated server
 * (wheel.qa.server) when one is given, otherwise in a singleplayer world.
 */
public final class ElytraFlightTest implements FabricClientGameTest {
    private static final int ELYTRA_SLOT = 0;
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private Consumer<String> command;
    // Where the player stands between flights, as "x y z" for /tp.
    private String ground;

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYBOARD.getOrCreate(code); }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        String server = System.getProperty("wheel.qa.server", "");
        boolean suite = !ElytraFlight.offered();
        report.put("server", server.isBlank() ? "singleplayer" : server);
        report.put("vanilla_plus_plus", suite);
        WheelConfig[] original = {null};
        InputConstants.Key[] originalElytraKey = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                if (HotkeyWheelClient.ELYTRA_KEY != null) originalElytraKey[0] = InputConstants.getKey(HotkeyWheelClient.ELYTRA_KEY.saveString());
                client.options.pauseOnLostFocus = false;
                fixture(client);
            });
            if (server.isBlank()) {
                try (TestSingleplayerContext world = context.worldBuilder().create()) {
                    world.getConnection().waitForChunksDownload();
                    command = text -> world.getServer().runCommand("execute as @a at @s run " + text);
                    context.waitTicks(20);
                    flights(context, suite, false);
                }
            } else {
                join(context, server);
                command = text -> context.runOnClient(client -> client.player.connection.sendCommand(text));
                flights(context, suite, true);
                leave(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel elytra flight QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    if (client.level != null && client.getSingleplayerServer() == null) {
                        client.level.disconnect(Component.literal("Hotkey Wheel QA finished"));
                        client.disconnectWithSavingScreen();
                    }
                    if (originalElytraKey[0] != null) {
                        HotkeyWheelClient.ELYTRA_KEY.setKey(originalElytraKey[0]);
                        KeyMapping.resetMapping();
                        client.options.save();
                    }
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "elytra-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** Wheel 1 on G with the elytra switch in its first slot; the switch's own key on K. */
    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        configuration.wheels.get(0).entries.clear();
        WheelConfig.Entry elytra = new WheelConfig.Entry();
        elytra.control = WheelActions.ELYTRA_FLIGHT;
        elytra.icon = "minecraft:elytra";
        configuration.wheels.get(0).entries.add(elytra);
        WheelConfig.Entry inventory = new WheelConfig.Entry();
        inventory.control = "key.inventory";
        configuration.wheels.get(0).entries.add(inventory);
        InputConstants.Key[] keys = {key(InputConstants.KEY_G), InputConstants.UNKNOWN, InputConstants.UNKNOWN,
                key(InputConstants.KEY_TAB), InputConstants.UNKNOWN, key(InputConstants.KEY_U)};
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.getName().startsWith("key.hotkey_wheel.")) continue;
            if (mapping.matches(keys[0]) || mapping.matches(key(InputConstants.KEY_K))) mapping.setKey(InputConstants.UNKNOWN);
        }
        if (HotkeyWheelClient.ELYTRA_KEY != null) HotkeyWheelClient.ELYTRA_KEY.setKey(key(InputConstants.KEY_K));
        KeyMapping.resetMapping();
        String error = WheelBindings.save(configuration, keys);
        check(error.isEmpty(), "The elytra fixture saves" + (error.isEmpty() ? "" : ": " + error));
    }

    private void flights(ClientGameTestContext context, boolean suite, boolean dedicated) throws IOException {
        command.accept("gamemode survival @s");
        command.accept("effect give @s minecraft:resistance infinite 4 true");
        command.accept("clear @s");
        command.accept("item replace entity @s armor.chest with minecraft:elytra");
        context.waitFor(client -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA)
                && client.player.onGround(), 200);
        ground = context.computeOnClient(client -> {
            BlockPos feet = client.player.blockPosition();
            return (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5);
        });
        report.put("ground", ground);
        if (suite) {
            suiteHidesTheSwitch(context);
            return;
        }
        context.runOnClient(client -> check(!WheelConfig.get().elytraFlightOff() && !ElytraFlight.blocksGliding(), "Elytra flight starts on"));
        check(tryToGlide(context), "With the switch on, a jump in the air with an elytra starts a glide");

        context.getInput().pressKey(InputConstants.KEY_K);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(WheelConfig.get().elytraFlightOff(), "The Toggle elytra flight key switches it off");
            check(overlay(client).equals("Elytra flight off"), "The key says so above the hotbar");
            check(savedText().contains("\"elytraFlightOff\": true"), "Off is saved to hotkey-wheel.json at once");
            clearOverlay(client);
        });
        check(!tryToGlide(context), "With the switch off, the same jump starts no glide");
        context.runOnClient(client -> check(overlay(client).equals("Elytra flight is off"),
                "A refused jump reminds the player: " + overlay(client)));
        context.runOnClient(client -> {
            WheelConfig.load();
            check(WheelConfig.get().elytraFlightOff() && ElytraFlight.blocksGliding(), "Off survives a reload of the settings");
        });

        clientCommand(context, "hotkeywheel elytra on");
        context.waitTicks(3);
        context.runOnClient(client -> check(!WheelConfig.get().elytraFlightOff() && !savedText().contains("elytraFlightOff"),
                "/hotkeywheel elytra on switches it back on and leaves the setting out of the file"));
        clientCommand(context, "hotkeywheel elytra off");
        context.waitTicks(3);
        context.runOnClient(client -> check(WheelConfig.get().elytraFlightOff(), "/hotkeywheel elytra off switches it off"));
        clientCommand(context, "hotkeywheel elytra");
        context.waitTicks(3);
        context.runOnClient(client -> check(!WheelConfig.get().elytraFlightOff(), "/hotkeywheel elytra on its own toggles"));

        context.runOnClient(client -> check(HotkeyWheelClient.controlLabel(WheelActions.ELYTRA_FLIGHT).equals("Elytra flight · On"),
                "The wheel slot shows the current state"));
        selectSlot(context, ELYTRA_SLOT);
        context.waitTicks(3);
        context.runOnClient(client -> check(WheelConfig.get().elytraFlightOff()
                        && HotkeyWheelClient.controlLabel(WheelActions.ELYTRA_FLIGHT).equals("Elytra flight · Off"),
                "The wheel action switches it off"));
        openWheel(context);
        context.takeScreenshot("elytra-wheel-off");
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
        check(!tryToGlide(context), "Switched off from the wheel, the elytra starts no glide");
        selectSlot(context, ELYTRA_SLOT);
        context.waitTicks(3);
        context.runOnClient(client -> check(!WheelConfig.get().elytraFlightOff(), "The wheel action switches it back on"));
        check(tryToGlide(context), "Back on, the elytra glides again");

        if (dedicated) armoredChestplate(context);
    }

    /**
     * A Vanilla+ Features armored chestplate, made in an anvil on the server from a diamond chestplate and an elytra,
     * glides through the vanilla glider component, so the switch stops it the same way.
     */
    private void armoredChestplate(ClientGameTestContext context) {
        command.accept("clear @s");
        command.accept("item replace entity @s armor.chest with minecraft:air");
        command.accept("item replace entity @s hotbar.0 with minecraft:diamond_chestplate");
        command.accept("item replace entity @s hotbar.1 with minecraft:elytra");
        command.accept("xp add @s 30 levels");
        context.waitFor(client -> client.player.onGround() && client.player.getInventory().getItem(1).is(Items.ELYTRA), 200);
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
        BlockPos anvil = context.computeOnClient(client -> client.player.blockPosition().east(2));
        command.accept("setblock ~2 ~ ~ minecraft:anvil");
        context.waitFor(client -> client.level.getBlockState(anvil).is(net.minecraft.world.level.block.Blocks.ANVIL), 100);
        context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(anvil), Direction.UP, anvil, false)));
        context.waitFor(client -> client.gui.screen() instanceof AnvilScreen, 100);
        // Anvil slots: 0 and 1 are the inputs, 2 the result, then the inventory, with the hotbar at 30 to 38.
        int[] clicks = {30, 0, 31, 1};
        for (int slot : clicks) {
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, 0,
                    ContainerInput.PICKUP, client.player));
            context.waitTicks(3);
        }
        context.waitFor(client -> !client.player.containerMenu.getSlot(2).getItem().isEmpty(), 100);
        context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 2, 0,
                ContainerInput.QUICK_MOVE, client.player));
        context.waitTicks(5);
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(5);
        int found = context.computeOnClient(client -> {
            for (int index = 0; index < 36; index++) {
                ItemStack stack = client.player.getInventory().getItem(index);
                if (stack.is(Items.DIAMOND_CHESTPLATE)) return index;
            }
            return -1;
        });
        check(found >= 0, "The anvil gives back one diamond chestplate");
        context.runOnClient(client -> {
            ItemStack armored = client.player.getInventory().getItem(found);
            CustomData data = armored.get(DataComponents.CUSTOM_DATA);
            check(armored.has(DataComponents.GLIDER) && data != null && data.copyTag().contains("vanilla_plus_features:armored_elytra"),
                    "It is a Vanilla+ Features armored chestplate: it has the glider component and keeps the elytra");
            check(client.player.getInventory().getItem(1).isEmpty(), "The elytra went into it");
        });
        command.accept("item replace entity @s armor.chest from entity @s " + (found < 9 ? "hotbar." + found : "inventory." + (found - 9)));
        command.accept("item replace entity @s " + (found < 9 ? "hotbar." + found : "inventory." + (found - 9)) + " with minecraft:air");
        context.waitFor(client -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 100);
        context.takeScreenshot("elytra-armored-chestplate-worn");
        check(tryToGlide(context), "With the switch on, the armored chestplate glides");
        context.getInput().pressKey(InputConstants.KEY_K);
        context.waitTicks(3);
        check(!tryToGlide(context), "With the switch off, the armored chestplate starts no glide");
        context.getInput().pressKey(InputConstants.KEY_K);
        context.waitTicks(3);
        context.runOnClient(client -> check(!WheelConfig.get().elytraFlightOff(), "The key switches it back on"));
    }

    /** Vanilla++ has its own pouch elytra toggle: no key, no command, no action, and a saved Off does nothing. */
    private void suiteHidesTheSwitch(ClientGameTestContext context) {
        context.runOnClient(client -> {
            check(HotkeyWheelClient.ELYTRA_KEY == null, "With Vanilla++ no Toggle elytra flight key is registered");
            check(List.of(client.options.keyMappings).stream().noneMatch(mapping -> mapping.getName().equals("key.hotkey_wheel.elytra_flight")),
                    "Key Binds has no Toggle elytra flight entry");
            check(ClientCommands.getActiveDispatcher().getRoot().getChild("hotkeywheel") == null, "/hotkeywheel is not registered");
            check(WheelActions.choices().stream().noneMatch(choice -> choice.control().equals(WheelActions.ELYTRA_FLIGHT)),
                    "The control chooser does not offer the elytra switch");
            check(!HotkeyWheelClient.controlAvailable(WheelActions.ELYTRA_FLIGHT)
                            && HotkeyWheelClient.controlLabel(WheelActions.ELYTRA_FLIGHT).endsWith("(Vanilla++ is installed; use its pouch elytra toggle)"),
                    "A slot that already has it shows as unavailable, with the reason");
            check(WheelConfig.update(config -> config.setElytraFlightOff(true)) && WheelConfig.get().elytraFlightOff(),
                    "A saved Off from before Vanilla++ is kept in the file");
            check(!ElytraFlight.blocksGliding(), "but the glide wrap ignores it");
        });
        check(tryToGlide(context), "With Vanilla++ and a saved Off, the elytra still glides");
        selectSlot(context, ELYTRA_SLOT);
        context.waitTicks(3);
        context.runOnClient(client -> check(overlay(client).equals("Vanilla++ is installed; use its pouch elytra toggle."),
                "Choosing the slot only points to Vanilla++'s toggle: " + overlay(client)));
    }

    /** Lifts the player 25 blocks, jumps once in the air and reports whether a glide started, then lands. */
    private boolean tryToGlide(ClientGameTestContext context) {
        double groundY = Double.parseDouble(ground.split(" ")[1]);
        String[] at = ground.split(" ");
        command.accept("tp @s " + at[0] + " " + (groundY + 25) + " " + at[2]);
        context.waitFor(client -> !client.player.onGround() && client.player.getY() > groundY + 15, 100);
        context.waitTicks(6);
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(3);
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(10);
        boolean gliding = context.computeOnClient(client -> client.player.isFallFlying());
        command.accept("tp @s " + ground);
        context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying(), 200);
        context.waitTicks(5);
        return gliding;
    }

    private static void openWheel(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitFor(client -> client.gui.screen() instanceof WheelScreen, 40);
        context.waitTicks(2);
    }

    private static void selectSlot(ClientGameTestContext context, int slot) {
        openWheel(context);
        double[] position = context.computeOnClient(client -> {
            WheelScreen screen = (WheelScreen) client.gui.screen();
            double angle = RadialGeometry.angle(slot, screen.slotCount());
            double x = screen.centerX() + Math.cos(angle) * screen.radius() * 0.76;
            double y = screen.centerY() + Math.sin(angle) * screen.radius() * 0.76;
            return new double[] {x * client.getWindow().getScreenWidth() / screen.width, y * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(2);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(3);
    }

    /** The text above the hotbar, where the switch reports its state. */
    private static String overlay(Minecraft client) {
        try {
            var field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Component message = (Component) field.get(client.gui.hud);
            return message == null ? "" : message.getString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read the overlay message", failure);
        }
    }

    private static void clearOverlay(Minecraft client) {
        try {
            var field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            field.set(client.gui.hud, null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot clear the overlay message", failure);
        }
    }

    /** /hotkeywheel is a client command: typed by the player, it never reaches the server. */
    private static void clientCommand(ClientGameTestContext context, String text) {
        context.runOnClient(client -> client.player.connection.sendCommand(text));
    }

    private static String savedText() {
        try {
            return Files.readString(WheelConfig.path());
        } catch (IOException failure) {
            throw new AssertionError("Cannot read hotkey-wheel.json", failure);
        }
    }

    private static void join(ClientGameTestContext context, String address) {
        context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                new ServerData("Hotkey Wheel QA", address, ServerData.Type.OTHER), false, null));
        context.waitFor(client -> client.level != null && client.player != null && client.gui.screen() == null, 1200);
        context.waitTicks(20);
    }

    private static void leave(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.level.disconnect(Component.literal("Hotkey Wheel QA finished"));
            client.disconnectWithSavingScreen();
        });
        context.waitFor(client -> client.level == null, 600);
        context.waitTicks(10);
        context.setScreen(TitleScreen::new);
    }
}
