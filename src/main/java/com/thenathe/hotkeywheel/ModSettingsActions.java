package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/**
 * Wheel slots that open a mod's settings screen, the one Mod Menu opens. They are saved as
 * {@code modmenu:config/<modid>} and need no key. Screens come from Mod Menu's public {@code modmenu}
 * entrypoints, so without Mod Menu the slots stay saved and show as unavailable.
 */
public final class ModSettingsActions {
    public static final String PREFIX = "modmenu:config/";
    private static Map<String, ConfigScreenFactory<?>> factories;

    public record Choice(String control, String modId, String name) {}

    private ModSettingsActions() {}

    public static boolean handles(String control) {
        return control.startsWith(PREFIX) && !modId(control).isEmpty();
    }

    public static String control(String modId) { return PREFIX + modId; }

    static String modId(String control) { return control.substring(PREFIX.length()); }

    public static boolean modMenuPresent() { return FabricLoader.getInstance().isModLoaded("modmenu"); }

    /** Whether selecting the slot can open its screen right now. */
    public static boolean available(String control) {
        return handles(control) && modMenuPresent() && factories().containsKey(modId(control));
    }

    public static String label(String control) {
        String modId = modId(control);
        String name = FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getName()).orElse(modId);
        String label = name + " settings";
        if (!modMenuPresent()) return label + " (needs Mod Menu)";
        if (FabricLoader.getInstance().getModContainer(modId).isEmpty()) return label + " (mod not installed)";
        if (!factories().containsKey(modId)) return label + " (no settings screen)";
        return label;
    }

    /** Every installed mod with a settings screen, sorted by name. */
    public static List<Choice> choices() {
        List<Choice> choices = new ArrayList<>();
        if (!modMenuPresent()) return choices;
        for (String modId : factories().keySet()) {
            FabricLoader.getInstance().getModContainer(modId).ifPresent(mod ->
                    choices.add(new Choice(control(modId), modId, mod.getMetadata().getName())));
        }
        return choices;
    }

    /**
     * Opens the screen with no parent, so closing it returns to the game, and lets the original
     * wheel opener close it again like the Buildprint menus.
     */
    public static void open(Minecraft client, String control, InputConstants.Key opener) {
        if (!modMenuPresent()) {
            message(client, "Hotkey Wheel: this slot needs Mod Menu");
            return;
        }
        ConfigScreenFactory<?> factory = factories().get(modId(control));
        if (factory == null) {
            message(client, "Hotkey Wheel: " + label(control));
            return;
        }
        Screen screen;
        try {
            screen = factory.create(null);
        } catch (RuntimeException | LinkageError failure) {
            LoggerFactory.getLogger("hotkey-wheel").error("Cannot open the settings of {}", modId(control), failure);
            message(client, "Hotkey Wheel: the settings screen failed to open; see the game log.");
            return;
        }
        if (screen == null) return;
        client.gui.setScreen(screen);
        if (client.gui.screen() == screen) WheelMenuReturn.own(screen, opener);
    }

    private static void message(Minecraft client, String text) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(text));
    }

    /**
     * Mod Menu's own order: screens one mod provides for others first, then each mod's own
     * screen, which wins. Read once, since the entrypoints do not change while the game runs.
     */
    private static Map<String, ConfigScreenFactory<?>> factories() {
        if (factories != null) return factories;
        Map<String, ConfigScreenFactory<?>> provided = new LinkedHashMap<>();
        Map<String, ConfigScreenFactory<?>> own = new LinkedHashMap<>();
        try {
            for (var entrypoint : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
                String modId = entrypoint.getProvider().getMetadata().getId();
                try {
                    ModMenuApi api = entrypoint.getEntrypoint();
                    Map<String, ConfigScreenFactory<?>> forOthers = api.getProvidedConfigScreenFactories();
                    if (forOthers != null) forOthers.forEach((id, factory) -> {
                        if (factory != null) provided.put(id, factory);
                    });
                    ConfigScreenFactory<?> factory = api.getModConfigScreenFactory();
                    if (factory != null) own.put(modId, factory);
                } catch (RuntimeException | LinkageError failure) {
                    LoggerFactory.getLogger("hotkey-wheel").warn("Cannot read the Mod Menu entrypoint of {}", modId, failure);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            LoggerFactory.getLogger("hotkey-wheel").warn("Cannot read Mod Menu entrypoints", failure);
        }
        provided.putAll(own);
        provided.keySet().removeIf(modId -> FabricLoader.getInstance().getModContainer(modId).isEmpty());
        factories = provided;
        return factories;
    }
}
