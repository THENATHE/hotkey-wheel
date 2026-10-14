package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.HotkeyWheelClient;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.Config;
import marsh.town.brb.interfaces.PinnedRecipeBook;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exercises BRBR's screen-level pin binding alongside real wheel input and all integrations. */
public final class BrbrIntegrationTest implements FabricClientGameTest {
    private final List<String> checks = new ArrayList<>();

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var report = new LinkedHashMap<String, Object>();
        report.put("checks", checks);
        var originalPins = new HashSet<>(BetterRecipeBook.pinnedRecipeManager.pinned);
        var originalKey = InputConstants.getKey(BetterRecipeBook.PIN_MAPPING.saveString());
        var originalMode = BetterRecipeBook.config.recipeDisplayMode;
        boolean originalBook = BetterRecipeBook.config.enableBook;
        boolean originalPinning = BetterRecipeBook.config.enablePinning;
        boolean originalCentered = BetterRecipeBook.config.keepCentered;
        try {
            String version = FabricLoader.getInstance().getModContainer("brb").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            report.put("brbr", version);
            check(version.equals(System.getProperty("wheel.qa.brbr.version", "1.11.7+26.3")),
                    "All-four profile uses the exact candidate BRBR version " + version);
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 8));
                    player.awardRecipes(server.getRecipeManager().getRecipes());
                    player.inventoryMenu.broadcastFullState();
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> {
                    client.options.pauseOnLostFocus = false;
                    client.options.guiScale().set(2);
                    client.gui.setScreen(null);
                    check(HotkeyWheelClient.findControl("key.brb.pin") == BetterRecipeBook.PIN_MAPPING,
                            "Wheel discovers BRBR's actual registered pin binding");
                    WheelConfig config = new WheelConfig();
                    config.wheels.get(0).entries.get(0).control = client.options.keyInventory.getName();
                    check(WheelConfig.save(config), "Combined inventory fixture saves without replacing BRBR settings");
                    HotkeyWheelClient.WHEEL_KEYS[0].setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                    BetterRecipeBook.PIN_MAPPING.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G));
                    KeyMapping.resetMapping();
                    BetterRecipeBook.config.enableBook = true;
                    BetterRecipeBook.config.enablePinning = true;
                    BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
                    BetterRecipeBook.pinnedRecipeManager.pinned.clear();
                });
                context.getInput().resizeWindow(1280, 800);
                context.getInput().holdKey(InputConstants.KEY_G);
                context.waitTicks(3);
                double[] sector = context.computeOnClient(client -> {
                    check(client.gui.screen() instanceof WheelScreen,
                            "Wheel opener takes precedence over a BRBR pin binding sharing G during gameplay");
                    WheelScreen wheel = (WheelScreen) client.gui.screen();
                    return new double[] {wheel.centerX() * client.getWindow().getScreenWidth() / wheel.width,
                            (wheel.centerY() - wheel.radius() * 0.76) * client.getWindow().getScreenHeight() / wheel.height};
                });
                context.getInput().setCursorPos(sector[0], sector[1]);
                context.getInput().releaseKey(InputConstants.KEY_G);
                context.waitTicks(4);
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof InventoryScreen,
                            "Inventory selected from the wheel opens with latest BRBR and Vanilla++ installed");
                    var book = book(client);
                    if (!book.isVisible()) book.toggleVisibility();
                    book.recipesUpdated();
                    check(book instanceof PinnedRecipeBook && ((PinnedRecipeBook) book).brb$getPinnedTab() != null,
                            "Wheel-opened inventory retains BRBR's real recipe book and pinned-tab integration");
                    check(BetterRecipeBook.pinnedRecipeManager.pinned.isEmpty(),
                            "Wheel opening and selection never leak into BRBR's native pin handler");
                    check(!BetterRecipeBook.PIN_MAPPING.isDown(), "Conflicting pin mapping is released after wheel selection");
                    ((RecipeBookComponentAccessor) book).getSearchBox().setFocused(false);
                });
                context.waitTicks(3);
                double[] recipe = context.computeOnClient(client -> {
                    var page = (RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book(client)).getRecipeBookPage();
                    var button = page.getButtons().stream().filter(candidate -> candidate.visible).findFirst().orElseThrow();
                    return new double[] {(button.getX() + 10) * client.getWindow().getScreenWidth() / client.gui.screen().width,
                            (button.getY() + 10) * client.getWindow().getScreenHeight() / client.gui.screen().height};
                });
                context.getInput().setCursorPos(recipe[0], recipe[1]);
                context.waitTicks(3);
                context.getInput().pressKey(InputConstants.KEY_G);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    check(client.gui.screen() instanceof InventoryScreen,
                            "Shared G key stays inside the inventory and does not reopen a wheel");
                    check(BetterRecipeBook.pinnedRecipeManager.pinned.size() == 1,
                            "BRBR's actual pin handler remains functional when its key shares the wheel opener");
                });
                context.takeScreenshot("all-four-brbr-wheel-inventory-pin");
                context.getInput().pressKey(InputConstants.KEY_G);
                context.waitTicks(2);
                context.runOnClient(client -> check(BetterRecipeBook.pinnedRecipeManager.pinned.isEmpty(),
                        "BRBR's normal pin removal still works with all four mods present"));
                settings(context);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("BRBR and Hotkey Wheel combined QA", failure);
        } finally {
            context.runOnClient(client -> {
                BetterRecipeBook.PIN_MAPPING.setKey(originalKey);
                KeyMapping.resetMapping();
                BetterRecipeBook.config.recipeDisplayMode = originalMode;
                BetterRecipeBook.config.enableBook = originalBook;
                BetterRecipeBook.config.enablePinning = originalPinning;
                BetterRecipeBook.config.keepCentered = originalCentered;
                BetterRecipeBook.configHolder.save();
                BetterRecipeBook.pinnedRecipeManager.pinned.clear();
                BetterRecipeBook.pinnedRecipeManager.pinned.addAll(originalPins);
                client.gui.setScreen(null);
            });
            try {
                Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "brbr-integration-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    private void settings(ClientGameTestContext context) {
        Screen parent = context.computeOnClient(client -> client.gui.screen());
        var gson = new GsonBuilder().create();
        String original = gson.toJson(BetterRecipeBook.config);
        Config expected = gson.fromJson(original, Config.class);
        expected.keepCentered = !expected.keepCentered;
        openSettings(context, parent);
        toggleCentered(context);
        context.runOnClient(client -> {
            check(gson.toJson(BetterRecipeBook.config).equals(original),
                    "BRBR settings draft leaves committed preferences intact before Save");
            click(client.gui.screen(), footer(client.gui.screen(), "text.cloth-config.save_and_done"));
        });
        context.waitTicks(2);
        String saved = gson.toJson(expected);
        context.runOnClient(client -> {
            check(client.gui.screen() == parent, "BRBR native Save returns to the original wheel-opened inventory");
            check(gson.toJson(BetterRecipeBook.config).equals(saved) && gson.toJson(readSaved()).equals(saved),
                    "BRBR native Save commits the selected preference and preserves every unrelated setting");
        });
        openSettings(context, parent);
        toggleCentered(context);
        context.runOnClient(client -> {
            check(gson.toJson(BetterRecipeBook.config).equals(saved),
                    "BRBR's second draft remains separate from saved settings");
            click(client.gui.screen(), footer(client.gui.screen(), "gui.cancel", "text.cloth-config.cancel_discard"));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof ConfirmScreen, "BRBR Cancel retains the native discard confirmation");
            click(client.gui.screen(), footer(client.gui.screen(), "text.cloth-config.quit_discard"));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() == parent, "BRBR native Cancel/discard returns to the original inventory parent");
            check(gson.toJson(BetterRecipeBook.config).equals(saved) && gson.toJson(readSaved()).equals(saved),
                    "BRBR Cancel/discard preserves committed settings in memory and on disk");
        });
    }

    private void openSettings(ClientGameTestContext context, Screen parent) {
        context.runOnClient(client -> {
            Screen settings = com.terraformersmc.modmenu.ModMenu.getConfigScreen("brb", parent);
            client.gui.setScreen(settings);
            check(settings instanceof ClothConfigScreen, "BRBR Mod Menu settings open normally after wheel inventory selection");
        });
        // Cloth computes the Save label and enabled state during rendering.
        context.waitTicks(2);
    }

    private void toggleCentered(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ClothConfigScreen settings = (ClothConfigScreen) client.gui.screen();
            AbstractConfigEntry<?> entry = settings.getCategorizedEntries().values().stream().flatMap(List::stream)
                    .filter(candidate -> candidate.getFieldName().equals(Component.translatable("text.autoconfig.brb.option.keepCentered")))
                    .findFirst().orElseThrow();
            int index = settings.listWidget.children().indexOf(entry);
            int row = settings.listWidget.getRowTop(index);
            settings.listWidget.scrollTo(settings.listWidget.getScroll() + row - settings.listWidget.top - 8, false);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            ClothConfigScreen settings = (ClothConfigScreen) client.gui.screen();
            AbstractConfigEntry<?> entry = settings.getCategorizedEntries().values().stream().flatMap(List::stream)
                    .filter(candidate -> candidate.getFieldName().equals(Component.translatable("text.autoconfig.brb.option.keepCentered")))
                    .findFirst().orElseThrow();
            click(settings, (Button) entry.children().getFirst());
            check(settings.isEdited(), "Native BRBR value-button input creates a real pending settings edit");
        });
        context.waitTicks(2);
    }

    private void click(Screen screen, Button button) {
        var click = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        check(screen.mouseClicked(click, false), "BRBR screen accepts native input for " + button.getMessage().getString());
        screen.mouseReleased(click);
    }

    private static Button footer(Screen screen, String... translations) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> java.util.Arrays.stream(translations)
                        .anyMatch(key -> button.getMessage().equals(Component.translatable(key))))
                .findFirst().orElseThrow(() -> new AssertionError("Missing BRBR footer: " + String.join(", ", translations)));
    }

    private static Config readSaved() {
        try {
            return new Toml4jConfigSerializer<>(Config.class.getAnnotation(me.shedaniel.autoconfig.annotation.Config.class), Config.class).deserialize();
        } catch (Exception failure) {
            throw new AssertionError("Cannot read BRBR saved settings", failure);
        }
    }

    private static RecipeBookComponent<?> book(Minecraft client) {
        return client.gui.screen().children().stream().filter(RecipeBookComponent.class::isInstance)
                .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
    }
}
