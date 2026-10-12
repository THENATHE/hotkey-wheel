package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.RadialGeometry;
import com.thenathe.hotkeywheel.ToolPouchIntegration;
import com.thenathe.hotkeywheel.ToolPouchRadialScreen;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import me.pajic.toolpouch.ToolPouch;
import me.pajic.toolpouch.ToolPouchClient;
import me.pajic.toolpouch.hud.ContextualSelectionWidget;
import me.pajic.toolpouch.item.ModItems;
import me.pajic.toolpouch.keybind.ModKeybinds;
import me.pajic.toolpouch.keybind.ScrollHandler;
import me.pajic.toolpouch.util.PlayerExtension;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

public final class ToolPouchClientTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        report.put("diagnostics", diagnostics);
        try {
            context.getInput().resizeWindow(1280, 800);
            report.put("suite", FabricLoader.getInstance().getModContainer("thenathe_mod_suite").orElseThrow().getMetadata().getVersion().getFriendlyString());
            report.put("toolpouch", FabricLoader.getInstance().getModContainer("toolpouch").orElseThrow().getMetadata().getVersion().getFriendlyString());
            check(report.get("suite").equals("1.1.6+26.3"), "Tests use the exact active Vanilla++ Suite 1.1.6 developer track");
            check(report.get("toolpouch").equals("1.1.10"), "Tests use official Tool Pouch 1.1.10 nested in the suite");
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                initialize(context);
                nestedWheels(context);
                nativeReplacement(context);
                nativeUnchanged(context);
                ammunition(context);
                staleAndEmpty(context);
                paging(context);
                pouchLookup(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Tool Pouch radial integration QA", failure);
        } finally {
            report.put("check_count", checks.size());
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "toolpouch-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void initialize(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            client.gui.setScreen(null);
            HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
            HotkeyWheelClient.CYCLE_KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_TAB));
            ModKeybinds.OPEN_WIDGET.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_X));
            KeyMapping.resetMapping();
            WheelConfig config = new WheelConfig();
            config.wheels.get(0).entries.get(0).control = ToolPouchIntegration.CONTROL_NAME;
            config.wheels.get(0).entries.get(0).icon = "minecraft:shulker_box";
            check(WheelConfig.save(config), "Contextual action fixture is saved through normal wheel configuration");
            // Capacity must cover the pagination fixture when original shulker close writes back.
            ToolPouch.CONFIG.toolPouchRows.accept(3);
            ToolPouch.CONFIG.toolPouchColumns.accept(9);
            ToolPouch.CONFIG.canOpenFromInventory.accept(true);
        });
    }

    private static ItemStack shulker(String name, Item marker, int count) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(marker, count))));
        return stack;
    }

    private static ItemStack pouch(List<ItemStack> contents) {
        ItemStack stack = new ItemStack(ModItems.TOOL_POUCH);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return stack;
    }

    private static List<ItemStack> sparseShulkers() {
        return List.of(new ItemStack(Items.IRON_PICKAXE), ItemStack.EMPTY,
                shulker("First duplicate", Items.DIAMOND, 3), ItemStack.EMPTY,
                shulker("Second duplicate", Items.EMERALD, 7));
    }

    private static <T> T onServer(ClientGameTestContext context, Function<ServerPlayer, T> action) throws Exception {
        UUID uuid = context.computeOnClient(client -> client.player.getUUID());
        var server = context.computeOnClient(Minecraft::getSingleplayerServer);
        var result = server.submit(() -> action.apply(server.getPlayerList().getPlayer(uuid)));
        // Client gametests advance both game threads only while their context pumps ticks.
        context.waitFor(client -> result.isDone(), 200);
        return result.get();
    }

    private void fixture(ClientGameTestContext context, List<ItemStack> contents, boolean leggings,
            boolean allowInventory, ItemStack weapon) throws Exception {
        context.runOnClient(client -> {
            if (client.player.containerMenu != client.player.inventoryMenu) client.player.closeContainer();
            client.gui.setScreen(null);
            ToolPouchIntegration.clear();
            ContextualSelectionWidget.widgetOpen = false;
            ModKeybinds.OPEN_WIDGET.setDown(false);
            while (ModKeybinds.OPEN_WIDGET.consumeClick()) {}
            ScrollHandler.selectedShulkerSlot = 0;
            ScrollHandler.selectedArrowSlot = 0;
            ToolPouch.CONFIG.allowUseFromInventory.accept(allowInventory);
        });
        context.waitTicks(3);
        ItemStack inventoryPouch = pouch(contents);
        ItemStack legs = leggings ? new ItemStack(Items.IRON_LEGGINGS) : ItemStack.EMPTY;
        if (leggings) legs.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        onServer(context, player -> {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, weapon.copy());
            player.getInventory().setItem(4, inventoryPouch.copy());
            player.setItemSlot(EquipmentSlot.LEGS, legs.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ((PlayerExtension) player).toolpouch$setArrowSlot(0);
            ((PlayerExtension) player).toolpouch$setShulkerSlot(0);
            player.inventoryMenu.broadcastChanges();
            return true;
        });
        context.runOnClient(client -> {
            client.player.getInventory().clearContent();
            client.player.getInventory().setSelectedSlot(0);
            client.player.getInventory().setItem(0, weapon.copy());
            client.player.getInventory().setItem(4, inventoryPouch.copy());
            client.player.setItemSlot(EquipmentSlot.LEGS, legs.copy());
            client.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        });
        context.waitTicks(5);
    }

    private void settings(ClientGameTestContext context, boolean enabled, boolean replace, boolean toggle, boolean quick) {
        context.runOnClient(client -> {
            WheelConfig config = WheelConfig.get().copy();
            config.toolPouchWheel = enabled;
            config.replaceToolPouchContextualKey = replace;
            config.wheels.get(0).toggleOpen = toggle;
            check(WheelConfig.save(config), "Settings fixture commits enabled=" + enabled + ", replace=" + replace + ", toggle=" + toggle + ", quick=" + quick);
            ToolPouchClient.CONFIG.quickSelect.accept(quick);
        });
    }

    private static ToolPouchRadialScreen radial(Minecraft client) {
        return (ToolPouchRadialScreen) client.gui.screen();
    }

    private static void point(ClientGameTestContext context, boolean parent, int slot) {
        double[] position = context.computeOnClient(client -> {
            int centerX;
            int centerY;
            int radius;
            int count;
            if (parent) {
                WheelScreen screen = (WheelScreen) client.gui.screen();
                centerX = screen.centerX();
                centerY = screen.centerY();
                radius = screen.radius();
                count = WheelConfig.get().wheels.get(screen.wheelIndex()).entries.size();
            } else {
                ToolPouchRadialScreen screen = radial(client);
                centerX = screen.centerX();
                centerY = screen.centerY();
                radius = screen.radius();
                count = Math.min(12, screen.snapshot().choices().size() - screen.page() * 12);
            }
            double angle = RadialGeometry.angle(slot, count);
            double x = centerX + Math.cos(angle) * radius * 0.76;
            double y = centerY + Math.sin(angle) * radius * 0.76;
            return new double[] {x * client.getWindow().getScreenWidth() / client.gui.screen().width,
                    y * client.getWindow().getScreenHeight() / client.gui.screen().height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private void launchParent(ClientGameTestContext context, boolean toggle, boolean clickWhileHeld) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitTicks(2);
        context.runOnClient(client -> check(client.gui.screen() instanceof WheelScreen, "Actual G input opens the configured parent wheel"));
        point(context, true, 0);
        if (toggle || clickWhileHeld) context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        else context.getInput().releaseKey(InputConstants.KEY_G);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof ToolPouchRadialScreen) checkNaturalCenter(client, "Nested G opening");
        });
    }

    private void checkNaturalCenter(Minecraft client, String description) {
        ToolPouchRadialScreen screen = radial(client);
        double x = client.mouseHandler.getScaledXPos(client.getWindow());
        double y = client.mouseHandler.getScaledYPos(client.getWindow());
        double distance = Math.hypot(x - screen.centerX(), y - screen.centerY());
        check(distance <= 2, description + " naturally places the actual cursor at center Cancel: x=" + x
                + ", y=" + y + ", center=" + screen.centerX() + "," + screen.centerY() + ", distance=" + distance);
    }

    private void verifyShulker(ClientGameTestContext context, String name, Item marker, int count) throws Exception {
        context.waitTicks(8);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof ShulkerBoxScreen, "Original server menu opens a native shulker screen for " + name);
            check(client.gui.screen().getTitle().getString().equals(name), "Custom shulker name identifies the chosen duplicate: " + name);
            check(client.player.containerMenu.getSlot(0).getItem().is(marker)
                    && client.player.containerMenu.getSlot(0).getItem().getCount() == count, "Client receives exact contents for " + name);
            check(!client.options.keyAttack.isDown() && !client.options.keyAttack.consumeClick(), "Item-selection click does not leak into Attack");
        });
        check(onServer(context, player -> player.containerMenu instanceof ShulkerBoxMenu
                && player.containerMenu.getSlot(0).getItem().is(marker)
                && player.containerMenu.getSlot(0).getItem().getCount() == count), "Actual server opens the expected shulker and marker for " + name);
    }

    private void nestedWheels(ClientGameTestContext context) throws Exception {
        for (boolean toggle : new boolean[] {false, true}) {
            fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
            settings(context, true, false, toggle, true);
            launchParent(context, toggle, true);
            context.runOnClient(client -> {
                check(client.gui.screen() instanceof ToolPouchRadialScreen, "Selecting contextual action automatically opens nested item wheel in " + (toggle ? "Toggle" : "Hold") + " mode");
                check(radial(client).snapshot().choices().size() == 2, "Only two native interactable shulkers appear; empty slots and pickaxe are excluded");
                check(radial(client).snapshot().choices().get(1).index() == 1, "Sparse physical slots retain filtered native shulker indexes");
                check(radial(client).selection(radial(client).centerX(), radial(client).centerY()) == -1, "Nested wheel begins inside center Cancel");
            });
            point(context, false, 1);
            context.getInput().releaseKey(InputConstants.KEY_G);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() instanceof ToolPouchRadialScreen, "Releasing parent opener over an item leaves the click-only nested wheel open"));
            check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Parent source-key release sends no contextual open packet");
            context.takeScreenshot(toggle ? "toolpouch-toggle-nested" : "toolpouch-hold-nested");
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            verifyShulker(context, "Second duplicate", Items.EMERALD, 7);
        }
        fixture(context, List.of(shulker("Single item", Items.GOLD_INGOT, 2)), false, true, ItemStack.EMPTY);
        settings(context, true, false, false, true);
        launchParent(context, false, false);
        context.runOnClient(client -> check(client.gui.screen() instanceof ToolPouchRadialScreen && radial(client).snapshot().choices().size() == 1,
                "One shulker still opens an item wheel and never uses the native auto-open shortcut"));
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu),
                "Single-item nested wheel sends no auto-open packet before the Cancel click");
        context.runOnClient(client -> {
            ToolPouchRadialScreen screen = radial(client);
            double x = client.mouseHandler.getScaledXPos(client.getWindow());
            double y = client.mouseHandler.getScaledYPos(client.getWindow());
            diagnostics.add("Single-item Cancel pointer before simulator sync: x=" + x + ", y=" + y
                    + ", center=" + screen.centerX() + "," + screen.centerY() + ", selection=" + screen.selection(x, y));
        });
        context.runOnClient(client -> {
            ToolPouchRadialScreen screen = radial(client);
            double x = client.mouseHandler.getScaledXPos(client.getWindow());
            double y = client.mouseHandler.getScaledYPos(client.getWindow());
            checkNaturalCenter(client, "Single-item Cancel fixture");
            check(Math.hypot(x - screen.centerX(), y - screen.centerY()) < screen.radius() * 0.42,
                    "Single-item Cancel click naturally lies within the geometric dead zone");
        });
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Clicking center Cancel sends no shulker open packet");
    }

    private void nativeReplacement(ClientGameTestContext context) throws Exception {
        for (boolean quick : new boolean[] {false, true}) {
            fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
            settings(context, true, true, false, quick);
            context.getInput().holdKey(InputConstants.KEY_X);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(client.gui.screen() instanceof ToolPouchRadialScreen, "Native X opens radial when replacement is enabled with quickSelect=" + quick);
                checkNaturalCenter(client, "Native X replacement with quickSelect=" + quick);
                check(!ContextualSelectionWidget.widgetOpen && !ModKeybinds.OPEN_WIDGET.isDown(), "Native widget stays closed beneath replacement radial");
            });
            point(context, false, 0);
            if (!quick) {
                context.getInput().releaseKey(InputConstants.KEY_X);
                context.waitTicks(3);
                context.runOnClient(client -> check(client.gui.screen() instanceof ToolPouchRadialScreen, "Native X release requires a separate item click in toggle-style native mode"));
            }
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            verifyShulker(context, "First duplicate", Items.DIAMOND, 3);
            int menu = onServer(context, player -> player.containerMenu.containerId);
            if (quick) context.getInput().releaseKey(InputConstants.KEY_X);
            context.waitTicks(4);
            check(onServer(context, player -> player.containerMenu.containerId) == menu, "Releasing native opener cannot send a duplicate shulker open packet");
        }
        fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
        settings(context, true, true, false, true);
        context.getInput().holdKey(InputConstants.KEY_X);
        context.waitTicks(2);
        context.runOnClient(client -> checkNaturalCenter(client, "Native X cancel opening"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.getInput().releaseKey(InputConstants.KEY_X);
        context.waitTicks(4);
        context.runOnClient(client -> check(client.gui.screen() == null && !ContextualSelectionWidget.widgetOpen, "Cancel plus native opener release leaves both menus closed"));
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Cancelling native replacement sends no contextual open packet");
    }

    private void nativeUnchanged(ClientGameTestContext context) throws Exception {
        for (boolean quick : new boolean[] {false, true}) {
            fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
            settings(context, true, false, false, quick);
            context.getInput().holdKey(InputConstants.KEY_X);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.gui.screen() == null && ContextualSelectionWidget.widgetOpen,
                    "Replacement OFF preserves original native widget with quickSelect=" + quick));
            context.getInput().releaseKey(InputConstants.KEY_X);
            if (!quick) context.getInput().pressKey(InputConstants.KEY_X);
            verifyShulker(context, "First duplicate", Items.DIAMOND, 3);
        }
        fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
        settings(context, false, true, false, false);
        context.getInput().pressKey(InputConstants.KEY_X);
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(client.gui.screen() == null && ContextualSelectionWidget.widgetOpen, "Turning Tool Pouch wheel OFF preserves native widget even if replacement preference remains saved");
            check(!ToolPouchIntegration.suppressNativeWidget(), "Disabled master integration does not suppress original rendering");
        });
        context.getInput().pressKey(InputConstants.KEY_X);
        verifyShulker(context, "First duplicate", Items.DIAMOND, 3);
    }

    private void ammunition(ClientGameTestContext context) throws Exception {
        List<ItemStack> contents = List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.ARROW, 4), ItemStack.EMPTY,
                new ItemStack(Items.SPECTRAL_ARROW, 5), shulker("Excluded while bow held", Items.DIAMOND, 1), new ItemStack(Items.ARROW, 9));
        fixture(context, contents, false, true, new ItemStack(Items.BOW));
        settings(context, true, false, false, true);
        launchParent(context, false, false);
        context.runOnClient(client -> {
            check(radial(client).snapshot().context() == ToolPouchIntegration.Context.AMMO, "Holding a bow uses native supported ammunition context");
            check(radial(client).snapshot().choices().size() == 3, "Ammo wheel includes supported duplicates and excludes shulkers and tools");
        });
        point(context, false, 2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        check(onServer(context, player -> ((PlayerExtension) player).toolpouch$getArrowSlot()) == 2, "Original ammo sync packet updates actual server filtered arrow index");
        context.runOnClient(client -> check(client.gui.screen() == null && ScrollHandler.selectedArrowSlot == 2, "Ammo click updates native client selection and closes without a container menu"));
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Choosing ammunition never opens a shulker container");
        fixture(context, contents, false, true, new ItemStack(Items.BOW));
        settings(context, true, true, false, true);
        context.getInput().holdKey(InputConstants.KEY_X);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() instanceof ToolPouchRadialScreen
                && radial(client).snapshot().context() == ToolPouchIntegration.Context.AMMO,
                "Native contextual hotkey replacement also opens the supported-ammo radial"));
        context.runOnClient(client -> checkNaturalCenter(client, "Native X ammunition opening"));
        point(context, false, 1);
        context.getInput().releaseKey(InputConstants.KEY_X);
        context.waitTicks(3);
        check(onServer(context, player -> ((PlayerExtension) player).toolpouch$getArrowSlot()) == 0,
                "Native opener release over ammunition sends no premature selection packet");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        check(onServer(context, player -> ((PlayerExtension) player).toolpouch$getArrowSlot()) == 1,
                "Native replacement ammo click updates the exact server selection");
        fixture(context, contents, false, true, ItemStack.EMPTY);
        settings(context, true, false, false, true);
        onServer(context, player -> { player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW)); return true; });
        context.runOnClient(client -> client.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW)));
        context.waitTicks(3);
        launchParent(context, false, false);
        context.runOnClient(client -> check(radial(client).snapshot().context() == ToolPouchIntegration.Context.AMMO
                && radial(client).snapshot().choices().size() == 3, "A projectile weapon in the offhand also exposes original ammunition choices"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
    }

    private void staleAndEmpty(ClientGameTestContext context) throws Exception {
        fixture(context, List.of(new ItemStack(Items.IRON_PICKAXE)), false, true, ItemStack.EMPTY);
        settings(context, true, false, false, true);
        launchParent(context, false, false);
        context.runOnClient(client -> check(client.gui.screen() == null, "No native interactable items leaves the item wheel closed"));
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Empty contextual list sends no server open packet");
        fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
        launchParent(context, false, false);
        ItemStack changed = pouch(List.of(shulker("Changed contents", Items.REDSTONE, 11)));
        onServer(context, player -> { player.getInventory().setItem(4, changed.copy()); player.inventoryMenu.broadcastChanges(); return true; });
        context.runOnClient(client -> client.player.getInventory().setItem(4, changed.copy()));
        context.waitTicks(3);
        point(context, false, 0);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Changed list rejects stale item snapshot instead of opening a replacement item");
        context.runOnClient(client -> check(client.gui.screen() == null, "Stale-list click closes safely without opening a container"));
        fixture(context, sparseShulkers(), false, true, ItemStack.EMPTY);
        launchParent(context, false, false);
        onServer(context, player -> { player.getInventory().setItem(0, new ItemStack(Items.BOW)); return true; });
        context.runOnClient(client -> client.player.getInventory().setItem(0, new ItemStack(Items.BOW)));
        point(context, false, 0);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Changing to projectile context rejects stale shulker selection");
    }

    private void paging(ClientGameTestContext context) throws Exception {
        List<ItemStack> contents = new ArrayList<>();
        for (int index = 0; index < 15; index++) contents.add(shulker("Paged shulker " + index, Items.LAPIS_LAZULI, index + 1));
        fixture(context, contents, false, true, ItemStack.EMPTY);
        settings(context, true, false, false, true);
        launchParent(context, false, false);
        context.runOnClient(client -> check(radial(client).snapshot().choices().size() == 15 && radial(client).pageCount() == 2,
                "All 15 native contextual items are retained across pages of at most 12 icons"));
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(radial(client).page() == 1, "Configured cycle key advances the item page");
            checkNaturalCenter(client, "Cycle-key page change");
        });
        context.getInput().scroll(1);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(radial(client).page() == 0, "Actual mouse scrolling selects the previous item page");
            checkNaturalCenter(client, "Mouse-scroll page change");
        });
        context.getInput().pressKey(InputConstants.KEY_RIGHT);
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(radial(client).page() == 1, "Arrow key advances the item page independently of the cycle binding");
            checkNaturalCenter(client, "Arrow-key page change");
        });
        point(context, false, 1);
        context.takeScreenshot("toolpouch-page-two-floating-icons");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        verifyShulker(context, "Paged shulker 13", Items.LAPIS_LAZULI, 14);
        check(onServer(context, player -> ((PlayerExtension) player).toolpouch$getShulkerSlot()) == 13, "Second-page click preserves the global native filtered index");
    }

    private void pouchLookup(ClientGameTestContext context) throws Exception {
        fixture(context, sparseShulkers(), false, false, ItemStack.EMPTY);
        settings(context, true, false, false, true);
        launchParent(context, false, false);
        context.runOnClient(client -> check(client.gui.screen() == null, "Native allowUseFromInventory=false prevents contextual inventory-pouch use"));
        check(onServer(context, player -> player.containerMenu == player.inventoryMenu), "Inventory-use restriction sends no shulker open packet");
        fixture(context, List.of(shulker("Leggings attachment", Items.IRON_INGOT, 6)), true, false, ItemStack.EMPTY);
        ItemStack other = pouch(sparseShulkers());
        onServer(context, player -> { player.getInventory().setItem(4, other.copy()); player.inventoryMenu.broadcastChanges(); return true; });
        context.runOnClient(client -> client.player.getInventory().setItem(4, other.copy()));
        context.waitTicks(3);
        launchParent(context, false, false);
        context.runOnClient(client -> check(radial(client).snapshot().choices().size() == 1
                && radial(client).snapshot().choices().get(0).stack().getHoverName().getString().equals("Leggings attachment"),
                "Attached leggings pouch takes priority over inventory pouch even with inventory use disabled"));
        point(context, false, 0);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        verifyShulker(context, "Leggings attachment", Items.IRON_INGOT, 6);
    }
}
