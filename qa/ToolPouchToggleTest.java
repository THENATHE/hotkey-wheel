package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.ToolPouchToggles;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelScreen;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import me.pajic.toolpouch.ToolPouch;
import me.pajic.toolpouch.component.ModDataComponents;
import me.pajic.toolpouch.item.ModItems;
import me.pajic.toolpouch.keybind.SlotToggleKeys;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/** Tool Pouch 1.2.0 slot toggles used as wheel actions, with Tool Pouch's own raw toggle keys. */
public final class ToolPouchToggleTest implements FabricClientGameTest {
    private static final String SLOT_ZERO = ToolPouchToggles.SLOT_PREFIX + 0;
    private static final String SPYGLASS_SLOT = ToolPouchToggles.SLOT_PREFIX + 2;
    private final List<String> checks = new ArrayList<>();
    private final boolean suite = FabricLoader.getInstance().isModLoaded("vpp_mod_suite");
    private int toggleKey;
    private boolean suiteElytraMapping;

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        try {
            report.put("toolpouch", FabricLoader.getInstance().getModContainer("toolpouch").orElseThrow().getMetadata().getVersion().getFriendlyString());
            report.put("suite", suite);
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                setup(context);
                report.put("suite_elytra_mapping", suiteElytraMapping);
                pickerLists(context);
                wheelTogglesSlot(context);
                wheelTogglesElytra(context);
                savedSuiteElytraSlot(context);
                rawKeysStillWork(context);
                unbindAndRestore(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Tool Pouch slot toggle QA", failure);
        } finally {
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "toolpouch-toggle-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void setup(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            client.gui.setScreen(null);
            check(ToolPouchToggles.supported(), "Tool Pouch slot toggles are detected");
            HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
            HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
            int[] candidates = {InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8, InputConstants.KEY_F9,
                    InputConstants.KEY_F10, InputConstants.KEY_Y, InputConstants.KEY_U, InputConstants.KEY_I};
            toggleKey = java.util.Arrays.stream(candidates).filter(key -> java.util.Arrays.stream(client.options.keyMappings)
                    .noneMatch(mapping -> mapping.matches(InputConstants.Type.KEYBOARD.getOrCreate(key))))
                    .findFirst().orElseThrow(() -> new AssertionError("No unused slot toggle key"));
            KeyMapping.resetMapping();
            ToolPouch.CONFIG.toolPouchRows.accept(3);
            ToolPouch.CONFIG.toolPouchColumns.accept(9);
            ToolPouch.CONFIG.canOpenFromInventory.accept(true);
            ToolPouch.CONFIG.allowUseFromInventory.accept(true);
            for (int slot = 0; slot < 27; slot++) SlotToggleKeys.bind(slot, null);
            SlotToggleKeys.bind(2, InputConstants.Type.KEYBOARD.getOrCreate(toggleKey));
            suiteElytraMapping = HotkeyWheelClient.findControl(ToolPouchToggles.SUITE_ELYTRA_MAPPING) != null;
            check(suiteElytraMapping == suite, "The suite's own elytra mapping is registered only when the suite registers it");
        });
        ItemStack pouch = new ItemStack(ModItems.TOOL_POUCH);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CUSTOM_NAME, Component.literal("Toggle box"));
        pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.ELYTRA), box, new ItemStack(Items.SPYGLASS))));
        onServer(context, player -> {
            player.getInventory().clearContent();
            player.getInventory().setItem(4, pouch.copy());
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
            return true;
        });
        context.waitTicks(5);
        context.runOnClient(client -> check(!ToolPouchUtil.getToolPouch(client.player).isEmpty(), "Client sees the active Tool Pouch"));
    }

    private void pickerLists(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            var choices = ToolPouchToggles.choices();
            var controls = choices.stream().map(ToolPouchToggles.Choice::control).toList();
            check(controls.equals(List.of(ToolPouchToggles.ELYTRA, SLOT_ZERO, ToolPouchToggles.SLOT_PREFIX + 1, SPYGLASS_SLOT)),
                    "Slot toggles list the elytra toggle and each filled pouch slot: " + controls);
            var spyglass = choices.get(3);
            check(spyglass.name().equals("Toggle Spyglass (slot 3) · On"), "Slot toggle is labelled with its item and state: " + spyglass.name());
            check(spyglass.key().equals(InputConstants.Type.KEYBOARD.getOrCreate(toggleKey).getDisplayName().getString()),
                    "Slot toggle shows Tool Pouch's own toggle key");
            check(choices.get(0).name().equals("Toggle pouch Elytra · On"), "Elytra toggle reports the elytra as on");
            client.gui.setScreen(new ControlPickerScreen(null, control -> { }));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            Field query = ControlPickerScreen.class.getDeclaredField("query");
            query.setAccessible(true);
            var search = client.gui.screen().children().stream()
                    .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            search.setValue("Toggle Spyglass");
            check(query.get(client.gui.screen()).equals("Toggle Spyglass"), "Picker search accepts the slot toggle name");
            boolean listed = client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .anyMatch(button -> button.getMessage().getString().startsWith("Toggle Spyglass (slot 3)"));
            check(listed, "The control picker lists the Tool Pouch slot toggle");
            client.gui.setScreen(null);
        });
    }

    private void wheelTogglesSlot(ClientGameTestContext context) throws Exception {
        wheelEntries(context, SPYGLASS_SLOT, ToolPouchToggles.ELYTRA, ToolPouchToggles.SUITE_ELYTRA_MAPPING);
        select(context, 0);
        check(disabled(context).equals(List.of(2)), "Selecting the spyglass toggle disables pouch slot 3 on the server");
        context.runOnClient(client -> check(ToolPouchUtil.isSlotDisabled(ToolPouchUtil.getToolPouch(client.player), 2),
                "The client pouch shows slot 3 disabled"));
        select(context, 0);
        check(disabled(context).isEmpty(), "Selecting it again enables slot 3");
    }

    private void wheelTogglesElytra(ClientGameTestContext context) throws Exception {
        select(context, 1);
        check(disabled(context).equals(List.of(0)), "The elytra toggle disables the elytra slot on the server");
        context.runOnClient(client -> check(ToolPouchToggles.label(ToolPouchToggles.ELYTRA).equals("Toggle pouch Elytra · Off"),
                "The elytra toggle label reports Off"));
        select(context, 1);
        check(disabled(context).isEmpty(), "The elytra toggle enables the elytra slot again");
    }

    // Wheel slots saved by 1.0.10 can name the suite's elytra mapping, which Vanilla++ 1.5.2 removes.
    private void savedSuiteElytraSlot(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            WheelConfig saved = WheelConfig.get().copy();
            check(WheelConfig.save(saved), "A wheel slot naming the suite's elytra mapping saves");
            WheelConfig.load();
            check(WheelConfig.get().wheels.get(0).entries.get(2).control.equals(ToolPouchToggles.SUITE_ELYTRA_MAPPING),
                    "The saved slot keeps the suite's mapping name, so an older suite still finds it");
            check(ToolPouchToggles.handles(ToolPouchToggles.SUITE_ELYTRA_MAPPING) == !suiteElytraMapping,
                    "The slot becomes the pouch elytra toggle only when the suite's mapping is gone");
        });
        select(context, 2);
        if (suiteElytraMapping) {
            check(disabled(context).isEmpty(), "With the suite's mapping present, the slot uses the suite's own toggle");
            select(context, 2);
        } else {
            check(disabled(context).equals(List.of(0)), "Without the suite's mapping, the slot toggles the pouch elytra");
            context.runOnClient(client -> check(ToolPouchToggles.label(ToolPouchToggles.SUITE_ELYTRA_MAPPING).startsWith("Toggle pouch Elytra"),
                    "The editor labels the old slot as the pouch elytra toggle"));
            select(context, 2);
            check(disabled(context).isEmpty(), "Selecting the old slot again enables the elytra");
            context.runOnClient(client -> {
                // 1.0.10 remembered the mapping's key when its slot had Unbind normal key on.
                WheelConfig config = WheelConfig.get().copy();
                config.wheels.get(0).entries.get(2).unbindKey = true;
                config.managedBindings.put(ToolPouchToggles.SUITE_ELYTRA_MAPPING, "key.keyboard.x");
                check(WheelBindings.save(config, wheelKeys()).isEmpty(), "A 1.0.10 slot with a remembered suite elytra key saves");
                check("key.keyboard.x".equals(WheelConfig.get().managedBindings.get(ToolPouchToggles.SUITE_ELYTRA_MAPPING)),
                        "The removed mapping's remembered key is kept for when the mapping returns");
                config = WheelConfig.get().copy();
                config.wheels.get(0).entries.get(2).unbindKey = false;
                config.managedBindings.remove(ToolPouchToggles.SUITE_ELYTRA_MAPPING);
                check(WheelConfig.save(config), "Remembered suite key fixture cleared");
            });
        }
    }

    private void rawKeysStillWork(ClientGameTestContext context) throws Exception {
        context.getInput().pressKey(toggleKey);
        context.waitTicks(5);
        check(disabled(context).equals(List.of(2)), "Tool Pouch's own toggle key still works outside the wheel");
        context.getInput().pressKey(toggleKey);
        context.waitTicks(5);
        check(disabled(context).isEmpty(), "Tool Pouch's toggle key switches the slot back");
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.wheelCount = 2;
            check(WheelConfig.save(config), "Second wheel enabled for the shared-key check");
            HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.Type.KEYBOARD.getOrCreate(toggleKey));
            KeyMapping.resetMapping();
        });
        context.getInput().holdKey(toggleKey);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "A wheel on the same key opens first"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.getInput().releaseKey(toggleKey);
        context.waitTicks(5);
        check(disabled(context).isEmpty(), "The wheel key takes precedence over Tool Pouch's toggle key, as for key mappings");
        context.runOnClient(client -> {
            HotkeyWheelClient.WHEEL_KEYS[1].setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            WheelConfig config = WheelConfig.get().copy();
            config.wheelCount = 1;
            check(WheelConfig.save(config), "Second wheel disabled again");
        });
    }

    private void unbindAndRestore(ClientGameTestContext context) throws Exception {
        InputConstants.Key original = InputConstants.Type.KEYBOARD.getOrCreate(toggleKey);
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).entries.get(0).unbindKey = true;
            check(ToolPouchToggles.canUnbind(SPYGLASS_SLOT) && !ToolPouchToggles.canUnbind(ToolPouchToggles.ELYTRA),
                    "Single-slot toggles can be unbound; the elytra toggle covers several slots and cannot");
            check(WheelBindings.save(config, wheelKeys()).isEmpty(), "Saving with Unbind normal key on succeeds");
            check(SlotToggleKeys.getKey(2) == null, "Save removes the slot's Tool Pouch toggle key");
            check(original.getName().equals(WheelConfig.get().managedBindings.get(SPYGLASS_SLOT)),
                    "The removed Tool Pouch key is remembered for restoration");
        });
        context.getInput().pressKey(toggleKey);
        context.waitTicks(5);
        check(disabled(context).isEmpty(), "The removed raw key no longer toggles the slot");
        select(context, 0);
        check(disabled(context).equals(List.of(2)), "The wheel still toggles the slot after its key is removed");
        select(context, 0);
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.wheels.get(0).entries.get(0).unbindKey = false;
            check(WheelBindings.save(config, wheelKeys()).isEmpty(), "Saving with Unbind normal key off succeeds");
            check(original.equals(SlotToggleKeys.getKey(2)), "Save restores the remembered Tool Pouch key");
            check(!WheelConfig.get().managedBindings.containsKey(SPYGLASS_SLOT), "The restored key is no longer remembered");
        });
    }

    private static InputConstants.Key[] wheelKeys() {
        InputConstants.Key[] keys = new InputConstants.Key[5];
        for (int index = 0; index < 3; index++) keys[index] = InputConstants.getKey(HotkeyWheelClient.WHEEL_KEYS[index].saveString());
        keys[3] = InputConstants.getKey(HotkeyWheelClient.CYCLE_KEY.saveString());
        keys[4] = InputConstants.getKey(HotkeyWheelClient.CONFIGURE_KEY.saveString());
        return keys;
    }

    private void wheelEntries(ClientGameTestContext context, String... controls) {
        context.runOnClient(client -> {
            WheelConfig config = new WheelConfig();
            config.wheels.get(0).entries.clear();
            for (String control : controls) {
                WheelConfig.Entry entry = new WheelConfig.Entry();
                entry.control = control;
                entry.icon = "minecraft:elytra";
                config.wheels.get(0).entries.add(entry);
            }
            check(WheelConfig.save(config), "Slot toggle wheel fixture saves");
        });
    }

    private void select(ClientGameTestContext context, int slot) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        double[] position = context.computeOnClient(client -> {
            WheelScreen wheel = (WheelScreen) client.gui.screen();
            int count = WheelConfig.get().wheels.get(wheel.wheelIndex()).entries.size();
            double angle = RadialGeometry.angle(slot, count);
            return new double[] {
                    (wheel.centerX() + Math.cos(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenWidth() / wheel.width,
                    (wheel.centerY() + Math.sin(angle) * wheel.radius() * 0.76) * client.getWindow().getScreenHeight() / wheel.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(6);
    }

    private static List<Integer> disabled(ClientGameTestContext context) throws Exception {
        return onServer(context, player -> List.copyOf(ToolPouchUtil.getToolPouch(player).getOrDefault(ModDataComponents.DISABLED_SLOTS, List.of())));
    }

    private static <T> T onServer(ClientGameTestContext context, Function<ServerPlayer, T> action) throws Exception {
        UUID uuid = context.computeOnClient(client -> client.player.getUUID());
        var server = context.computeOnClient(Minecraft::getSingleplayerServer);
        var result = server.submit(() -> action.apply(server.getPlayerList().getPlayer(uuid)));
        context.waitFor(client -> result.isDone(), 200);
        return result.get();
    }
}
