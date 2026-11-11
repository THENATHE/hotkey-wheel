package com.thenathe.hotkeywheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class WheelConfig {
    public static final int MAX_SLOTS = 12;
    // A wheel, three folder levels below it and no deeper: each level costs a selection, and three keep the path on one line.
    public static final int MAX_FOLDER_DEPTH = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WheelConfig current = new WheelConfig();
    public int wheelCount = 1;
    public boolean safeMode = true;
    public boolean toolPouchWheel = true;
    public boolean replaceToolPouchContextualKey;
    public boolean closeBuildprintWithWheelKey = true;
    // The control picker's mod folders: null for one folder per mod, true for a single Mod controls folder.
    // The default is left out of the file, so settings from earlier releases save back unchanged.
    public Boolean modControlsInOneFolder;
    public Map<String, String> managedBindings = new LinkedHashMap<>();
    public List<Wheel> wheels = new ArrayList<>();

    public WheelConfig() {
        for (int index = 0; index < 3; index++) {
            Wheel wheel = new Wheel();
            wheel.name = "Wheel " + (index + 1);
            wheels.add(wheel);
        }
    }

    public static final class Wheel {
        public String name = "Wheel";
        public boolean toggleOpen;
        public boolean selectOutside;
        public List<Entry> entries = new ArrayList<>();

        public Wheel() {
            String[] controls = {"key.inventory", "key.swapOffhand", "key.drop", "key.togglePerspective",
                    "key.chat", "key.command", "key.screenshot", "key.advancements"};
            String[] icons = {"minecraft:chest", "minecraft:shield", "minecraft:dropper", "minecraft:spyglass",
                    "minecraft:oak_sign", "minecraft:command_block", "minecraft:glass", "minecraft:bookshelf"};
            for (int index = 0; index < controls.length; index++) {
                Entry entry = new Entry();
                entry.control = controls[index];
                entry.icon = icons[index];
                entries.add(entry);
            }
        }
    }

    public static final class Entry {
        public String control = "";
        public String icon = "minecraft:stone";
        public String label = "";
        public boolean holdAction;
        public boolean unbindKey;
        // Only folders have slots of their own. Gson leaves out the null list, so ordinary slots save as in 1.0.x.
        public List<Entry> entries;

        public static Entry folder(String name) {
            Entry folder = new Entry();
            folder.label = name;
            folder.icon = "minecraft:chest";
            folder.entries = new ArrayList<>();
            return folder;
        }

        public boolean isFolder() { return entries != null; }

        public String folderName() { return label.isBlank() ? "Folder" : label; }

        /** Slots inside this folder and its subfolders, folders included. */
        public int nestedSlotCount() {
            if (!isFolder()) return 0;
            int count = entries.size();
            for (Entry entry : entries) count += entry.nestedSlotCount();
            return count;
        }
    }

    /** Control slots of the active wheels, including those inside folders. */
    public List<Entry> activeControls() {
        List<Entry> slots = new ArrayList<>();
        for (int index = 0; index < Math.min(wheelCount, wheels.size()); index++) collectEntries(wheels.get(index).entries, slots);
        slots.removeIf(Entry::isFolder);
        return slots;
    }

    /** Every slot of every wheel, hidden wheels included, folders and their contents included. */
    public List<Entry> allEntries() {
        List<Entry> all = new ArrayList<>();
        for (Wheel wheel : wheels) collectEntries(wheel.entries, all);
        return all;
    }

    private static void collectEntries(List<Entry> entries, List<Entry> all) {
        for (Entry entry : entries) {
            all.add(entry);
            if (entry.isFolder()) collectEntries(entry.entries, all);
        }
    }

    public boolean oneModControlsFolder() { return Boolean.TRUE.equals(modControlsInOneFolder); }

    public static WheelConfig get() { return current; }

    public WheelConfig copy() { return GSON.fromJson(GSON.toJson(this), WheelConfig.class); }

    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("hotkey-wheel.json"); }

    public static void load() {
        if (!Files.isRegularFile(path())) return;
        try {
            WheelConfig loaded = GSON.fromJson(Files.readString(path()), WheelConfig.class);
            if (loaded == null) throw new IOException("Empty configuration");
            loaded.normalize();
            current = loaded;
        } catch (Exception failure) {
            // A later Save replaces the unreadable file, so keep a copy for manual recovery.
            Path backup = path().resolveSibling("hotkey-wheel.json.unreadable");
            try {
                Files.copy(path(), backup, StandardCopyOption.REPLACE_EXISTING);
                LoggerFactory.getLogger("hotkey-wheel").error("Cannot read hotkey-wheel.json; a copy is kept at {}", backup, failure);
            } catch (IOException copyFailure) {
                failure.addSuppressed(copyFailure);
                LoggerFactory.getLogger("hotkey-wheel").error("Cannot read hotkey-wheel.json; original file preserved", failure);
            }
        }
    }

    public static boolean save(WheelConfig replacement) {
        Path temporary = null;
        try {
            WheelConfig validated = replacement.copy();
            validated.normalize();
            Files.createDirectories(path().getParent());
            temporary = Files.createTempFile(path().getParent(), ".hotkey-wheel-", ".json");
            Files.writeString(temporary, GSON.toJson(validated));
            Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            current = validated;
            return true;
        } catch (Exception failure) {
            LoggerFactory.getLogger("hotkey-wheel").error("Cannot save Hotkey Wheel settings", failure);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException failure) { LoggerFactory.getLogger("hotkey-wheel").warn("Cannot remove temporary config", failure); }
            }
        }
    }

    public void normalize() {
        wheelCount = Math.clamp(wheelCount, 1, 3);
        if (!oneModControlsFolder()) modControlsInOneFolder = null;
        if (managedBindings == null) managedBindings = new LinkedHashMap<>();
        if (wheels == null) wheels = new ArrayList<>();
        while (wheels.size() < 3) wheels.add(new Wheel());
        if (wheels.size() > 3) wheels = new ArrayList<>(wheels.subList(0, 3));
        for (int index = 0; index < 3; index++) {
            if (wheels.get(index) == null) wheels.set(index, new Wheel());
            Wheel wheel = wheels.get(index);
            if (wheel.name == null || wheel.name.isBlank()) wheel.name = "Wheel " + (index + 1);
            wheel.name = wheel.name.substring(0, Math.min(wheel.name.length(), 32));
            if (wheel.entries == null) wheel.entries = new ArrayList<>();
            wheel.entries = normalizeEntries(wheel.entries, 0);
        }
    }

    private static List<Entry> normalizeEntries(List<Entry> entries, int depth) {
        List<Entry> normalized = new ArrayList<>(entries.subList(0, Math.min(entries.size(), MAX_SLOTS)));
        for (int slot = 0; slot < normalized.size(); slot++) {
            if (normalized.get(slot) == null) normalized.set(slot, new Entry());
            Entry entry = normalized.get(slot);
            if (entry.control == null) entry.control = "";
            if (entry.icon == null) entry.icon = "minecraft:stone";
            if (entry.label == null) entry.label = "";
            if (!entry.isFolder()) continue;
            if (depth >= MAX_FOLDER_DEPTH) {
                // The editor never nests this deep, so only a hand-edited file reaches here; keep the slot itself.
                LoggerFactory.getLogger("hotkey-wheel").warn("Folder \"{}\" is nested more than {} levels deep; its slots are ignored",
                        entry.label, MAX_FOLDER_DEPTH);
                entry.entries = null;
                continue;
            }
            entry.entries = normalizeEntries(entry.entries, depth + 1);
        }
        return normalized;
    }
}
