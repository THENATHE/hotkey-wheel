package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ContainerProtocol;
import com.thenathe.hotkeywheel.ContainerRadialScreen;
import com.thenathe.hotkeywheel.ContainerRequests;
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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Open ender chest and Open shulker box… against real dedicated servers in separate processes: one with
 * Vanilla+ Features (wheel.qa.server) and one without it (wheel.qa.plain_server), joined in separate passes. The
 * profile decides whether Vanilla+ Features is also on this client, which makes the two mods share the protocol's
 * payload types.
 */
public final class ContainerActionsTest implements FabricClientGameTest {
    private static final int SHULKER_SLOT = 0;
    private static final int ENDER_CHEST_SLOT = 1;
    private static final String[] INVENTORY_SHULKERS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray"};
    private static final String[] ENDER_CHEST_SHULKERS = {"red", "green", "blue", "black", "brown"};
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();

    private record Expected(int kind, List<ContainerProtocol.Step> path) {}

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    private static ContainerProtocol.Step step(int container, int index) {
        return new ContainerProtocol.Step(container, index);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        boolean sharedTypes = FabricLoader.getInstance().isModLoaded("vanilla_plus_features");
        report.put("vanilla_plus_features_on_client", sharedTypes);
        report.put("sorted_on_client", FabricLoader.getInstance().isModLoaded("sorted"));
        WheelConfig[] original = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                fixture(client);
            });
            // Only one QA server runs at a time, so each pass of this profile joins the one that is up.
            String plain = System.getProperty("wheel.qa.plain_server", "");
            String vanillaPlus = System.getProperty("wheel.qa.server", "");
            report.put("servers", Map.of("plain", plain, "vanilla_plus_features", vanillaPlus));
            check(!plain.isBlank() || !vanillaPlus.isBlank(), "A QA server address is given");
            if (!plain.isBlank()) withoutVanillaPlus(context, plain);
            if (!vanillaPlus.isBlank()) withVanillaPlus(context, vanillaPlus, sharedTypes);
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel container actions QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    if (client.level != null) leaveNow(client);
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "container-actions-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** Wheel 1 on G: Open shulker box…, Open ender chest, then the inventory. */
    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 1;
        List<WheelConfig.Entry> entries = configuration.wheels.get(0).entries;
        entries.clear();
        entries.add(slot(WheelActions.OPEN_SHULKER_BOX, "minecraft:shulker_box"));
        entries.add(slot(WheelActions.OPEN_ENDER_CHEST, "minecraft:ender_chest"));
        entries.add(slot("key.inventory", "minecraft:chest"));
        InputConstants.Key[] keys = {key(InputConstants.KEY_G), InputConstants.UNKNOWN, InputConstants.UNKNOWN,
                key(InputConstants.KEY_TAB), InputConstants.UNKNOWN, key(InputConstants.KEY_U)};
        for (KeyMapping mapping : client.options.keyMappings) {
            if (!mapping.getName().startsWith("key.hotkey_wheel.") && mapping.matches(keys[0])) mapping.setKey(InputConstants.UNKNOWN);
        }
        KeyMapping.resetMapping();
        String error = WheelBindings.save(configuration, keys);
        check(error.isEmpty(), "The container fixture saves" + (error.isEmpty() ? "" : ": " + error));
    }

    private static WheelConfig.Entry slot(String control, String icon) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        return entry;
    }

    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYBOARD.getOrCreate(code); }

    /** A server without Vanilla+ Features: both actions stay in their slots, unavailable, and say why. */
    private void withoutVanillaPlus(ClientGameTestContext context, String address) {
        join(context, address);
        context.waitTicks(40);
        context.runOnClient(client -> {
            String reason = "needs Vanilla+ Features on the server";
            check(reason.equals(ContainerRequests.chooserUnavailable()) && reason.equals(ContainerRequests.enderChestUnavailable()),
                    "Without Vanilla+ Features on the server both actions are unavailable: " + ContainerRequests.chooserUnavailable());
            check(HotkeyWheelClient.controlLabel(WheelActions.OPEN_SHULKER_BOX).equals("Open shulker box… (" + reason + ")")
                            && HotkeyWheelClient.controlLabel(WheelActions.OPEN_ENDER_CHEST).equals("Open ender chest (" + reason + ")"),
                    "Their slots are labelled with the reason");
            check(!HotkeyWheelClient.controlAvailable(WheelActions.OPEN_SHULKER_BOX) && !HotkeyWheelClient.controlAvailable(WheelActions.OPEN_ENDER_CHEST),
                    "Their slots show as unavailable on the wheel");
            WheelActions.Choice chooser = WheelActions.choices().stream()
                    .filter(choice -> choice.control().equals(WheelActions.OPEN_SHULKER_BOX)).findFirst().orElseThrow();
            check(chooser.note().equals(reason), "The control chooser lists the action with the reason");
        });
        openWheel(context);
        context.takeScreenshot("containers-unavailable-wheel");
        selectSlot(context, SHULKER_SLOT);
        context.waitTicks(10);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "Choosing Open shulker box… on that server opens nothing");
            check(overlay(client).equals("Open shulker box: needs Vanilla+ Features on the server."),
                    "It says why above the hotbar: " + overlay(client));
        });
        selectSlot(context, ENDER_CHEST_SLOT);
        context.waitTicks(10);
        context.runOnClient(client -> check(client.gui.screen() == null
                        && overlay(client).equals("Open ender chest: needs Vanilla+ Features on the server."),
                "Open ender chest opens nothing there either and says why: " + overlay(client)));
        leave(context);
    }

    private void withVanillaPlus(ClientGameTestContext context, String address, boolean sharedTypes) {
        join(context, address);
        context.waitFor(client -> ContainerRequests.chooserUnavailable() == null, 200);
        context.runOnClient(client -> {
            check(ContainerRequests.chooserUnavailable() == null && ContainerRequests.enderChestUnavailable() == null,
                    "With Vanilla+ Features on the server both actions become available after its hello"
                            + (sharedTypes ? ", with Vanilla+ Features also on this client" : ""));
            check(HotkeyWheelClient.controlLabel(WheelActions.OPEN_SHULKER_BOX).equals("Open shulker box…"),
                    "The slot drops its reason");
        });
        if (sharedTypes) {
            // Vanilla+ Features' own client must still hear the server: its tooltip hint only shows after the hello.
            context.runOnClient(client -> {
                String hint = Component.translatable("vanilla_plus_features.tooltip.open").getString();
                ItemStack box = new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("shulker_box")));
                List<String> lines = box.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
                        .stream().map(Component::getString).toList();
                report.put("shulker_tooltip_with_vanilla_plus_client", lines);
                check(lines.contains(hint), "Vanilla+ Features' own client still receives the server's hello (its tooltip hint shows)");
            });
        }
        setUpInventory(context);
        List<Expected> expected = expectedEntries();
        listing(context, expected);
        openEach(context, expected);
        openEnderChestAction(context);
        closesOnChange(context);
        staleEntry(context);
        nothingToOpen(context);
        leave(context);
    }

    /**
     * Eight shulker boxes and an ender chest in the hotbar, a purple box holding an ender chest, a bundle holding an
     * ender chest, and five boxes in the ender chest: 17 entries, so two pages.
     */
    private void setUpInventory(ClientGameTestContext context) {
        List<String> commands = new ArrayList<>(List.of("gamemode survival @s", "clear @s",
                "effect give @s minecraft:resistance infinite 4 true",
                "item replace entity @s hotbar.0 with minecraft:ender_chest"));
        for (int index = 0; index < INVENTORY_SHULKERS.length; index++) {
            commands.add("item replace entity @s hotbar." + (index + 1) + " with minecraft:" + INVENTORY_SHULKERS[index] + "_shulker_box");
        }
        commands.add("item replace entity @s inventory.0 with minecraft:purple_shulker_box[minecraft:container=[{slot:0,item:{id:\"minecraft:ender_chest\",count:1}}]]");
        commands.add("item replace entity @s inventory.1 with minecraft:bundle[minecraft:bundle_contents=[{id:\"minecraft:ender_chest\",count:1}]]");
        for (int slot = 0; slot < 27; slot++) {
            String item = slot < ENDER_CHEST_SHULKERS.length ? "minecraft:" + ENDER_CHEST_SHULKERS[slot] + "_shulker_box" : "minecraft:air";
            commands.add("item replace entity @s enderchest." + slot + " with " + item);
        }
        for (String command : commands) {
            context.runOnClient(client -> client.player.connection.sendCommand(command));
            context.waitTick();
        }
        context.waitFor(client -> client.player.getInventory().getItem(10).is(net.minecraft.world.item.Items.BUNDLE), 100);
        context.waitTicks(10);
        context.runOnClient(client -> check(itemId(client.player.getInventory().getItem(9)).equals("purple_shulker_box")
                && client.player.getInventory().getItem(0).is(net.minecraft.world.item.Items.ENDER_CHEST), "The inventory fixture arrives"));
    }

    /** In the protocol's order: inventory slots by index, nested items right after their holder, then the ender chest. */
    private static List<Expected> expectedEntries() {
        List<Expected> expected = new ArrayList<>();
        expected.add(new Expected(ContainerProtocol.KIND_ENDER_CHEST, List.of(step(0, 0))));
        for (int index = 1; index <= INVENTORY_SHULKERS.length; index++) {
            expected.add(new Expected(ContainerProtocol.KIND_SHULKER_BOX, List.of(step(0, index))));
        }
        expected.add(new Expected(ContainerProtocol.KIND_SHULKER_BOX, List.of(step(0, 9))));
        expected.add(new Expected(ContainerProtocol.KIND_ENDER_CHEST, List.of(step(0, 9), step(3, 0))));
        expected.add(new Expected(ContainerProtocol.KIND_ENDER_CHEST, List.of(step(0, 10), step(2, 0))));
        for (int slot = 0; slot < ENDER_CHEST_SHULKERS.length; slot++) {
            expected.add(new Expected(ContainerProtocol.KIND_SHULKER_BOX, List.of(step(1, slot))));
        }
        return expected;
    }

    private void listing(ClientGameTestContext context, List<Expected> expected) {
        ContainerRadialScreen radial = openChooser(context);
        context.takeScreenshot("containers-chooser-page-1");
        context.runOnClient(client -> {
            List<ContainerProtocol.Entry> entries = entries(radial);
            List<Expected> listed = entries.stream().map(entry -> new Expected(entry.kind(), entry.path())).toList();
            report.put("listed", listed.stream().map(entry -> entry.kind() + " " + entry.path()).toList());
            check(listed.size() == expected.size() && listed.containsAll(expected) && expected.containsAll(listed),
                    "The chooser lists the 9 inventory shulker boxes, the 5 in the ender chest, the carried ender chest, "
                            + "and the ender chests inside a shulker box and a bundle");
            report.put("listed_in_protocol_order", listed.equals(expected));
            check(listed.equals(expected), "Entries come in the protocol's order, nested ones after their holder");
            check(radial.pageCount() == 2 && visibleCount(radial) == 12, "17 entries make two pages, twelve on the first");
            check(entries.get(10).display().is(net.minecraft.world.item.Items.ENDER_CHEST)
                            && itemId(entries.get(9).display()).equals("purple_shulker_box"),
                    "Each entry carries its item for drawing");
        });
        context.getInput().pressKey(InputConstants.KEY_TAB);
        context.waitTicks(3);
        context.takeScreenshot("containers-chooser-page-2");
        context.runOnClient(client -> check(page(radial) == 1 && visibleCount(radial) == 5, "The cycle key turns to the second page of five"));
        context.getInput().scroll(1);
        context.waitTicks(2);
        context.runOnClient(client -> check(page(radial) == 0, "Scrolling turns back to the first page"));
        context.getInput().pressKey(InputConstants.KEY_RIGHT);
        context.waitTicks(2);
        context.runOnClient(client -> check(page(radial) == 1, "The right arrow turns the page"));
        context.getInput().pressKey(InputConstants.KEY_LEFT);
        context.waitTicks(2);
        context.runOnClient(client -> check(page(radial) == 0, "The left arrow turns it back"));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        context.runOnClient(client -> check(client.gui.screen() == null, "Escape closes the chooser"));
        context.waitTicks(25);
    }

    /** Opens every listed entry through the chooser and checks the screen that comes back. */
    private void openEach(ClientGameTestContext context, List<Expected> expected) {
        for (int index = 0; index < expected.size(); index++) {
            Expected target = expected.get(index);
            ContainerRadialScreen radial = openChooser(context);
            int position = index;
            int listedAt = context.computeOnClient(client -> entries(radial).stream().map(entry -> new Expected(entry.kind(), entry.path()))
                    .toList().indexOf(target));
            check(listedAt >= 0, "Entry " + position + " is listed again");
            for (int page = 0; page < listedAt / 12; page++) {
                context.getInput().pressKey(InputConstants.KEY_TAB);
                context.waitTicks(2);
            }
            pointAt(context, radial, listedAt % 12);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitFor(client -> client.gui.screen() instanceof ShulkerBoxScreen || client.gui.screen() instanceof ContainerScreen, 100);
            if (position == 0) context.takeScreenshot("containers-opened-ender-chest");
            if (position == 9) context.takeScreenshot("containers-opened-purple-shulker");
            context.runOnClient(client -> {
                String where = target.path().toString();
                if (target.kind() == ContainerProtocol.KIND_SHULKER_BOX) {
                    check(client.gui.screen() instanceof ShulkerBoxScreen, "Choosing the shulker box at " + where + " opens it");
                    if (target.path().equals(List.of(step(0, 9)))) {
                        check(client.player.containerMenu.getSlot(0).getItem().is(net.minecraft.world.item.Items.ENDER_CHEST),
                                "The purple box opens with its own contents");
                    }
                    if (target.path().getFirst().container() == 1) {
                        check(client.player.containerMenu.getSlot(0).getItem().isEmpty(), "A box from the ender chest opens empty, as it was stored");
                    }
                } else {
                    check(client.gui.screen() instanceof ContainerScreen chest
                                    && chest.getTitle().getString().equals(Component.translatable("container.enderchest").getString()),
                            "Choosing the ender chest at " + where + " opens the ender chest");
                    check(itemId(client.player.containerMenu.getSlot(0).getItem()).equals("red_shulker_box"),
                            "The ender chest shows the player's ender chest contents");
                }
                client.player.closeContainer();
            });
            // Two requests per opening; the server allows five a second and Hotkey Wheel sends at most four.
            context.waitTicks(15);
        }
    }

    private void openEnderChestAction(ClientGameTestContext context) {
        selectSlot(context, ENDER_CHEST_SLOT);
        context.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 100);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof ContainerScreen chest
                            && chest.getTitle().getString().equals(Component.translatable("container.enderchest").getString()),
                    "Open ender chest opens the ender chest straight from the wheel");
            client.player.closeContainer();
        });
        context.waitTicks(15);
    }

    /** The chooser closes as soon as the inventory changes, since its list may no longer match. */
    private void closesOnChange(ClientGameTestContext context) {
        openChooser(context);
        context.runOnClient(client -> client.player.connection.sendCommand("give @s minecraft:stone 1"));
        context.waitFor(client -> !(client.gui.screen() instanceof ContainerRadialScreen), 100);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "The chooser closes when the inventory changes");
            client.player.connection.sendCommand("clear @s minecraft:stone");
        });
        context.waitTicks(15);
        openChooser(context);
        // Moving a box within the inventory, as a player would with the inventory screen.
        context.runOnClient(client -> {
            client.player.connection.sendCommand("item replace entity @s inventory.20 from entity @s hotbar.1");
        });
        context.waitFor(client -> !(client.gui.screen() instanceof ContainerRadialScreen), 100);
        context.runOnClient(client -> {
            check(client.gui.screen() == null, "The chooser closes when an item is copied into another slot");
            client.player.connection.sendCommand("item replace entity @s inventory.20 with minecraft:air");
        });
        context.waitTicks(25);
    }

    /**
     * A box moved where this client cannot see it, inside the ender chest: the open chooser still lists it at its old
     * place, the server refuses it as stale, the player is told, and a new chooser finds it at the new place.
     */
    private void staleEntry(ClientGameTestContext context) {
        ContainerRadialScreen radial = openChooser(context);
        context.runOnClient(client -> {
            client.player.connection.sendCommand("item replace entity @s enderchest.20 from entity @s enderchest.0");
            client.player.connection.sendCommand("item replace entity @s enderchest.0 with minecraft:air");
        });
        context.waitTicks(20);
        check(context.computeOnClient(client -> client.gui.screen() == radial),
                "A change inside the ender chest, which the client cannot see, leaves the chooser open");
        int red = context.computeOnClient(client -> entries(radial).stream().map(ContainerProtocol.Entry::path).toList()
                .indexOf(List.of(step(1, 0))));
        for (int page = 0; page < red / 12; page++) {
            context.getInput().pressKey(InputConstants.KEY_TAB);
            context.waitTicks(2);
        }
        pointAt(context, radial, red % 12);
        context.runOnClient(client -> clearOverlay(client));
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> !overlay(client).isEmpty(), 100);
        context.waitTicks(10);
        context.runOnClient(client -> {
            report.put("stale_message", overlay(client));
            check(client.gui.screen() == null, "The server refuses the moved box and nothing opens");
            check(!overlay(client).isEmpty(), "The player is told the box moved: " + overlay(client));
        });
        context.waitTicks(25);
        ContainerRadialScreen fresh = openChooser(context);
        context.runOnClient(client -> {
            List<List<ContainerProtocol.Step>> paths = entries(fresh).stream().map(ContainerProtocol.Entry::path).toList();
            check(paths.contains(List.of(step(1, 20))) && !paths.contains(List.of(step(1, 0))), "A new chooser lists the box at its new place");
            fresh.onClose();
        });
        context.waitTicks(25);
    }

    private void nothingToOpen(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.player.connection.sendCommand("clear @s");
            for (int slot = 0; slot < 27; slot++) client.player.connection.sendCommand("item replace entity @s enderchest." + slot + " with minecraft:air");
        });
        context.waitFor(client -> client.player.getInventory().isEmpty(), 100);
        context.waitTicks(20);
        context.runOnClient(client -> clearOverlay(client));
        selectSlot(context, SHULKER_SLOT);
        context.waitFor(client -> !overlay(client).isEmpty(), 100);
        context.runOnClient(client -> check(client.gui.screen() == null && overlay(client).equals("No shulker boxes or ender chests within reach."),
                "With nothing to open the chooser says so: " + overlay(client)));
        context.waitTicks(25);
        context.runOnClient(client -> clearOverlay(client));
        selectSlot(context, ENDER_CHEST_SLOT);
        context.waitFor(client -> !overlay(client).isEmpty(), 100);
        context.runOnClient(client -> check(client.gui.screen() == null && overlay(client).equals("No ender chest within reach."),
                "Open ender chest without one says so: " + overlay(client)));
    }

    private ContainerRadialScreen openChooser(ClientGameTestContext context) {
        selectSlot(context, SHULKER_SLOT);
        context.waitFor(client -> client.gui.screen() instanceof ContainerRadialScreen, 100);
        context.waitTicks(2);
        return context.computeOnClient(client -> (ContainerRadialScreen) client.gui.screen());
    }

    private static void openWheel(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_G);
        context.waitFor(client -> client.gui.screen() instanceof WheelScreen, 40);
        context.waitTicks(2);
    }

    /** Holds G, points at a slot of wheel 1 and releases, as a player does in Hold mode. */
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

    private static void pointAt(ClientGameTestContext context, ContainerRadialScreen radial, int sector) {
        double[] position = context.computeOnClient(client -> {
            int radius = intField(radial, "radius");
            double angle = RadialGeometry.angle(sector, visibleCount(radial));
            double x = radial.centerX() + Math.cos(angle) * radius * 0.75;
            double y = radial.centerY() + Math.sin(angle) * radius * 0.75;
            return new double[] {x * client.getWindow().getScreenWidth() / radial.width, y * client.getWindow().getScreenHeight() / radial.height};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(2);
    }

    private static void join(ClientGameTestContext context, String address) {
        if (address == null || address.isBlank()) throw new AssertionError("Set the server addresses for this fixture");
        context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                new ServerData("Hotkey Wheel QA", address, ServerData.Type.OTHER), false, null));
        context.waitFor(client -> client.level != null && client.player != null && client.gui.screen() == null, 1200);
        context.waitTicks(20);
    }

    private static void leave(ClientGameTestContext context) {
        context.runOnClient(ContainerActionsTest::leaveNow);
        context.waitFor(client -> client.level == null, 600);
        context.waitTicks(10);
        context.setScreen(TitleScreen::new);
    }

    private static void leaveNow(Minecraft client) {
        client.level.disconnect(Component.literal("Hotkey Wheel QA finished"));
        client.disconnectWithSavingScreen();
    }

    @SuppressWarnings("unchecked")
    private static List<ContainerProtocol.Entry> entries(ContainerRadialScreen radial) {
        try {
            var field = ContainerRadialScreen.class.getDeclaredField("entries");
            field.setAccessible(true);
            return (List<ContainerProtocol.Entry>) field.get(radial);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read the chooser's entries", failure);
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static int page(ContainerRadialScreen radial) { return intField(radial, "page"); }

    private static int visibleCount(ContainerRadialScreen radial) {
        return Math.min(12, entries(radial).size() - page(radial) * 12);
    }

    private static int intField(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read " + name, failure);
        }
    }

    /** The text above the hotbar, where the actions report what happened. */
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
}
