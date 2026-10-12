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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WheelConfig current = new WheelConfig();
    public int wheelCount = 1;
    public boolean safeMode = true;
    public boolean toolPouchWheel = true;
    public boolean replaceToolPouchContextualKey;
    public boolean closeBuildprintWithWheelKey = true;
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
    }

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
            LoggerFactory.getLogger("hotkey-wheel").error("Cannot read hotkey-wheel.json; original file preserved", failure);
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
            if (wheel.entries.size() > 12) wheel.entries = new ArrayList<>(wheel.entries.subList(0, 12));
            for (int slot = 0; slot < wheel.entries.size(); slot++) {
                if (wheel.entries.get(slot) == null) wheel.entries.set(slot, new Entry());
                Entry entry = wheel.entries.get(slot);
                if (entry.control == null) entry.control = "";
                if (entry.icon == null) entry.icon = "minecraft:stone";
                if (entry.label == null) entry.label = "";
            }
        }
    }
}
