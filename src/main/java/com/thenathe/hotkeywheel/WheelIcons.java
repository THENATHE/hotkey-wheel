package com.thenathe.hotkeywheel;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a slot's saved icon shows. The icon field holds one of:
 * <ul>
 * <li>a block or item ID, such as {@code minecraft:chest}, as in every earlier release;</li>
 * <li>one of Hotkey Wheel's own icons, {@code hotkey_wheel:icon/<name>}, drawn from the GUI sprite of that name;</li>
 * <li>an installed mod's icon, {@code hotkey_wheel:mod/<modid>}.</li>
 * </ul>
 * Hotkey Wheel registers no items and Fabric reserves each mod ID as its namespace, so these never name an item.
 * An unknown item or icon name shows a barrier; a mod that is missing, or whose icon cannot be read, shows the
 * puzzle piece. The saved value is kept either way.
 */
public final class WheelIcons {
    public static final String GLYPH_PREFIX = "hotkey_wheel:icon/";
    public static final String MOD_PREFIX = "hotkey_wheel:mod/";
    private static final Identifier MOD_FALLBACK = Identifier.fromNamespaceAndPath("hotkey_wheel", "icon/puzzle");

    public record Glyph(String name, String label, List<String> aliases) {
        public String id() { return GLYPH_PREFIX + name; }
    }

    /** In the order the picker shows them when nothing is searched. */
    public static final List<Glyph> GLYPHS = List.of(
            glyph("cog", "Cog", "settings", "gear", "options", "config", "preferences"),
            glyph("check", "Check mark", "tick", "done", "yes", "ok", "confirm", "accept"),
            glyph("close", "Close", "x", "cross", "cancel", "no", "delete", "exit"),
            glyph("bulleted_list", "Bulleted list", "list", "bullets", "menu", "settings list", "items"),
            glyph("numbered_list", "Numbered list", "list", "ordered", "numbers", "steps"),
            glyph("plus", "Plus", "add", "new", "create", "more"),
            glyph("minus", "Minus", "remove", "less", "subtract"),
            glyph("star", "Star", "favourite", "favorite", "important"),
            glyph("heart", "Heart", "love", "health", "like", "favourite"),
            glyph("home", "Home", "house", "base", "spawn"),
            glyph("folder", "Folder", "directory", "group", "category"),
            glyph("search", "Search", "magnifier", "find", "zoom", "look"),
            glyph("pencil", "Pencil", "edit", "write", "rename", "draw"),
            glyph("trash", "Trash", "bin", "delete", "remove", "garbage"),
            glyph("refresh", "Refresh", "reload", "redo", "repeat", "cycle", "sync"),
            glyph("arrow_up", "Arrow up", "up", "north", "raise"),
            glyph("arrow_down", "Arrow down", "down", "south", "lower"),
            glyph("arrow_left", "Arrow left", "left", "west", "back", "previous"),
            glyph("arrow_right", "Arrow right", "right", "east", "forward", "next"),
            glyph("play", "Play", "start", "run", "go", "resume"),
            glyph("pause", "Pause", "stop", "wait", "hold"),
            glyph("lock", "Lock", "locked", "secure", "padlock", "private"),
            glyph("eye", "Eye", "view", "visible", "show", "look", "spectate"),
            glyph("info", "Info", "information", "about", "help", "details"),
            glyph("warning", "Warning", "alert", "caution", "danger", "attention"),
            glyph("question", "Question mark", "help", "unknown", "faq"),
            glyph("bookmark", "Bookmark", "save", "saved", "mark"),
            glyph("flag", "Flag", "marker", "goal", "report"),
            glyph("map_pin", "Map pin", "pin", "location", "waypoint", "marker", "place"),
            glyph("clock", "Clock", "time", "timer", "watch"),
            glyph("sun", "Sun", "day", "light", "bright", "weather"),
            glyph("moon", "Moon", "night", "dark", "sleep"),
            glyph("bell", "Bell", "notification", "alarm", "ring", "alert"),
            glyph("key", "Key", "unlock", "password", "keybind"),
            glyph("lightning", "Lightning", "bolt", "power", "energy", "fast", "flash"),
            glyph("power", "Power", "on", "off", "toggle", "shutdown", "switch"),
            glyph("person", "Person", "user", "player", "profile", "account", "skin"),
            glyph("chat", "Chat bubble", "chat", "message", "talk", "speech", "comment"),
            glyph("puzzle", "Puzzle piece", "mod", "plugin", "addon", "extension"));

    private static Glyph glyph(String name, String label, String... aliases) {
        return new Glyph(name, label, List.of(aliases));
    }

    private WheelIcons() {}

    public static Optional<Glyph> glyph(String id) {
        if (!id.startsWith(GLYPH_PREFIX)) return Optional.empty();
        String name = id.substring(GLYPH_PREFIX.length());
        return GLYPHS.stream().filter(glyph -> glyph.name().equals(name)).findFirst();
    }

    /** The mod a {@code hotkey_wheel:mod/<modid>} icon belongs to, or null for other icons. */
    public static String modId(String id) {
        return id.startsWith(MOD_PREFIX) && id.length() > MOD_PREFIX.length() ? id.substring(MOD_PREFIX.length()) : null;
    }

    public static String modIcon(String modId) { return MOD_PREFIX + modId; }

    public static Icon resolve(String id) {
        Optional<Glyph> glyph = glyph(id);
        if (glyph.isPresent()) return new Icon(null, glyph.get(), null);
        String modId = modId(id);
        if (modId != null) return new Icon(null, null, modId);
        return new Icon(WheelUi.item(id), null, null);
    }

    /** Mods whose icons the given slots use, so their textures stay loaded. */
    public static Set<String> modsUsedBy(List<WheelConfig.Entry> entries) {
        Set<String> mods = new HashSet<>();
        for (WheelConfig.Entry entry : entries) {
            String modId = modId(entry.icon);
            if (modId != null) mods.add(modId);
        }
        return mods;
    }

    /** One resolved icon. Exactly one of item, glyph and mod is set. */
    public static final class Icon {
        private final Item item;
        private final Glyph glyph;
        private final String mod;
        private ItemStack stack;

        private Icon(Item item, Glyph glyph, String mod) {
            this.item = item;
            this.glyph = glyph;
            this.mod = mod;
        }

        public Item item() { return item; }

        /** Item stacks need the data sent when joining a world; Hotkey Wheel's and mods' icons do not. */
        public boolean needsWorld() { return item != null; }

        public Component name() {
            if (item != null) return WheelUi.itemName(item);
            if (glyph != null) return Component.literal(glyph.label());
            return FabricLoader.getInstance().getModContainer(mod)
                    .map(container -> Component.literal(container.getMetadata().getName()))
                    .orElse(Component.literal(mod + " (not installed)"));
        }

        /** Draws the icon in the 16 by 16 square at x, y. */
        public void draw(GuiGraphicsExtractor graphics, int x, int y) {
            if (item != null) {
                if (Minecraft.getInstance().level == null) return;
                if (stack == null) stack = new ItemStack(item);
                graphics.fakeItem(stack, x, y);
            } else if (glyph != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("hotkey_wheel", "icon/" + glyph.name()), x, y, 16, 16);
            } else if (!ModIcons.draw(graphics, mod, x, y)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, MOD_FALLBACK, x, y, 16, 16);
            }
        }
    }
}
