package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.PngInfo;
import org.slf4j.LoggerFactory;

/**
 * Installed mods' own icons, as declared in their {@code fabric.mod.json}. Each is decoded the first time it is
 * drawn, fitted into a small square texture, uploaded once and kept until no screen or saved slot needs it.
 */
public final class ModIcons {
    // Drawn at 16 GUI pixels, which is 64 screen pixels at GUI scale 4; smaller scales filter it down smoothly.
    private static final int SIZE = 64;
    // Icons are usually 128–512 pixels. These limits keep a broken or oversized file from costing much memory.
    private static final int MAX_SIDE = 2048;
    private static final long MAX_BYTES = 4L << 20;
    private static final Map<String, Optional<Identifier>> textures = new HashMap<>();
    private static List<Choice> choices;

    public record Choice(String modId, String name) {}

    private ModIcons() {}

    /**
     * Every installed mod that declares an icon, sorted by name. Built-in entries such as Minecraft and Java are
     * left out, and so is a mod bundled inside another whose icon is the same picture, like each of Fabric API's
     * modules. A declared icon that cannot be read is still listed and shows the fallback.
     */
    public static List<Choice> choices() {
        if (choices != null) return choices;
        List<Choice> found = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getType().equals("builtin") || mod.getMetadata().getIconPath(SIZE).isEmpty()) continue;
            if (sameIconAsContainer(mod)) continue;
            found.add(new Choice(mod.getMetadata().getId(), mod.getMetadata().getName()));
        }
        found.sort(Comparator.comparing((Choice choice) -> choice.name().toLowerCase(Locale.ROOT)).thenComparing(Choice::modId));
        choices = List.copyOf(found);
        return choices;
    }

    private static boolean sameIconAsContainer(ModContainer mod) {
        Optional<ModContainer> container = mod.getContainingMod();
        if (container.isEmpty()) return false;
        byte[] own = iconBytes(mod);
        for (Optional<ModContainer> outer = container; outer.isPresent(); outer = outer.get().getContainingMod()) {
            byte[] outerIcon = iconBytes(outer.get());
            if (own != null && outerIcon != null && Arrays.equals(own, outerIcon)) return true;
        }
        return false;
    }

    private static byte[] iconBytes(ModContainer mod) {
        try {
            Optional<Path> path = iconPath(mod);
            return path.isPresent() && Files.size(path.get()) <= MAX_BYTES ? Files.readAllBytes(path.get()) : null;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private static Optional<Path> iconPath(ModContainer mod) {
        return mod.getMetadata().getIconPath(SIZE).flatMap(mod::findPath).filter(Files::isRegularFile);
    }

    /** The mod's icon texture; empty when the mod is not installed or its icon cannot be used. */
    public static Optional<Identifier> texture(String modId) {
        return textures.computeIfAbsent(modId, ModIcons::load);
    }

    /** Draws the mod's icon in a 16 by 16 square; returns false, drawing nothing, when there is none. */
    public static boolean draw(GuiGraphicsExtractor graphics, String modId, int x, int y) {
        Optional<Identifier> id = texture(modId);
        if (id.isEmpty()) return false;
        var texture = Minecraft.getInstance().getTextureManager().getTexture(id.get());
        graphics.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + 16, y + 16, 0, 1, 0, 1);
        return true;
    }

    /** Frees every loaded icon except those of the given mods; others load again when next drawn. */
    public static void releaseExcept(Collection<String> keptModIds) {
        textures.entrySet().removeIf(loaded -> {
            if (keptModIds.contains(loaded.getKey())) return false;
            loaded.getValue().ifPresent(Minecraft.getInstance().getTextureManager()::release);
            return true;
        });
    }

    private static Optional<Identifier> load(String modId) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (mod.isEmpty()) return Optional.empty();
        try {
            Optional<Path> path = iconPath(mod.get());
            if (path.isEmpty()) throw new IOException("Declared icon file is missing");
            if (Files.size(path.get()) > MAX_BYTES) throw new IOException("Icon file is larger than " + MAX_BYTES + " bytes");
            byte[] data = Files.readAllBytes(path.get());
            // Read the size from the PNG header before decoding, so a huge picture is refused without allocating it.
            PngInfo info = PngInfo.fromBytes(data);
            if (info.width() < 1 || info.height() < 1 || info.width() > MAX_SIDE || info.height() > MAX_SIDE) {
                throw new IOException("Icon is " + info.width() + "×" + info.height() + "; at most " + MAX_SIDE + " per side is used");
            }
            NativeImage square;
            try (NativeImage image = NativeImage.read(data)) {
                square = fitSquare(image);
            }
            Identifier id = Identifier.tryBuild("hotkey_wheel", "mod_icon/" + modId);
            if (id == null) id = Identifier.fromNamespaceAndPath("hotkey_wheel", "mod_icon/" + Integer.toHexString(modId.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Hotkey Wheel icon for " + modId, square));
            return Optional.of(id);
        } catch (Exception failure) {
            LoggerFactory.getLogger("hotkey-wheel").debug("No usable icon for {}", modId, failure);
            return Optional.empty();
        }
    }

    /**
     * Centres the picture in a transparent square, keeping its proportions. Small pixel art is enlarged by
     * repeating pixels; large pictures are reduced by averaging, weighted by transparency so edges stay clean.
     */
    private static NativeImage fitSquare(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = (double) SIZE / Math.max(width, height);
        int fittedWidth = Math.max(1, (int) Math.round(width * scale));
        int fittedHeight = Math.max(1, (int) Math.round(height * scale));
        int offsetX = (SIZE - fittedWidth) / 2;
        int offsetY = (SIZE - fittedHeight) / 2;
        NativeImage square = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < fittedHeight; y++) {
            int top = (int) (y / scale);
            int bottom = Math.max(top + 1, Math.min(height, (int) Math.ceil((y + 1) / scale)));
            for (int x = 0; x < fittedWidth; x++) {
                int left = (int) (x / scale);
                int right = Math.max(left + 1, Math.min(width, (int) Math.ceil((x + 1) / scale)));
                square.setPixel(offsetX + x, offsetY + y, average(source, left, top, right, bottom));
            }
        }
        return square;
    }

    private static int average(NativeImage source, int left, int top, int right, int bottom) {
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int pixel = source.getPixel(x, y);
                int pixelAlpha = pixel >>> 24;
                alpha += pixelAlpha;
                red += (long) (pixel >> 16 & 0xFF) * pixelAlpha;
                green += (long) (pixel >> 8 & 0xFF) * pixelAlpha;
                blue += (long) (pixel & 0xFF) * pixelAlpha;
            }
        }
        if (alpha == 0) return 0;
        int count = (right - left) * (bottom - top);
        return (int) (alpha / count) << 24 | (int) (red / alpha) << 16 | (int) (green / alpha) << 8 | (int) (blue / alpha);
    }
}
