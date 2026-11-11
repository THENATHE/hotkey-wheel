package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * Wheel slots that open a mod's settings screen, the one Mod Menu opens. They are saved as
 * {@code modmenu:config/<modid>} and need no key. Screens come from Mod Menu's public {@code modmenu}
 * entrypoints, so without Mod Menu the slots stay saved and show as unavailable.
 */
public final class ModSettingsActions {
    public static final String PREFIX = "modmenu:config/";
    private static final Map<String, Optional<Identifier>> icons = new HashMap<>();
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

    /** The mod's own icon as a texture, loaded the first time it is drawn; empty when it has none. */
    public static Optional<Identifier> icon(String modId) {
        return icons.computeIfAbsent(modId, ModSettingsActions::loadIcon);
    }

    private static Optional<Identifier> loadIcon(String modId) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        Optional<String> iconPath = mod.flatMap(container -> container.getMetadata().getIconPath(16));
        if (iconPath.isEmpty()) return Optional.empty();
        var path = mod.get().findPath(iconPath.get());
        if (path.isEmpty() || !Files.isRegularFile(path.get())) return Optional.empty();
        try (InputStream input = Files.newInputStream(path.get())) {
            NativeImage image = NativeImage.read(input);
            Identifier id = Identifier.fromNamespaceAndPath("hotkey_wheel", "mod_icon/" + Integer.toHexString(modId.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Hotkey Wheel icon for " + modId, image));
            return Optional.of(id);
        } catch (Exception failure) {
            LoggerFactory.getLogger("hotkey-wheel").debug("No usable icon for {}", modId, failure);
            return Optional.empty();
        }
    }

    /** Icon textures are drawn at their own size, so the picker needs it. */
    public static int[] iconSize(Identifier id) {
        if (Minecraft.getInstance().getTextureManager().getTexture(id) instanceof DynamicTexture texture && texture.getPixels() != null) {
            return new int[] {texture.getPixels().getWidth(), texture.getPixels().getHeight()};
        }
        return new int[] {16, 16};
    }
}
