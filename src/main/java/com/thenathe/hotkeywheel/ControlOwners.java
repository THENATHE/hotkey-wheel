package com.thenathe.hotkeywheel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Which mod each control belongs to, for the control picker's folders. A mod is credited with the controls its
 * own code creates: the mixin on KeyMapping's constructor records the first class outside KeyMapping that
 * created it, and that class belongs to the mod whose files hold it. Minecraft's own controls belong to
 * {@code minecraft}. A control whose creator cannot be traced falls back to the mod named by its category's
 * namespace, then by its translation key ({@code key.<modid>.…}); anything else is left unattributed.
 */
public final class ControlOwners {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    // Mods may create controls off the render thread while loading, so the record is synchronized.
    private static final Map<KeyMapping, Class<?>> creators = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Optional<String>> classOwners = new HashMap<>();

    private ControlOwners() {}

    /** Called by the KeyMapping constructor mixin. */
    public static void created(KeyMapping mapping) {
        WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> !KeyMapping.class.isAssignableFrom(type) && type != ControlOwners.class)
                .findFirst()).ifPresent(creator -> creators.put(mapping, creator));
    }

    /** The ID of the mod the control belongs to; empty when it cannot be told. */
    public static Optional<String> modId(KeyMapping mapping) {
        Class<?> creator = creators.get(mapping);
        if (creator != null) {
            Optional<String> owner = classOwners.computeIfAbsent(creator, ControlOwners::ownerOf);
            if (owner.isPresent()) return owner;
        }
        String namespace = mapping.getCategory().id().getNamespace();
        if (!namespace.equals(Identifier.DEFAULT_NAMESPACE) && FabricLoader.getInstance().isModLoaded(namespace)) return Optional.of(namespace);
        String[] parts = mapping.getName().split("\\.");
        if (parts.length > 2 && parts[0].equals("key") && FabricLoader.getInstance().isModLoaded(parts[1])) return Optional.of(parts[1]);
        return Optional.empty();
    }

    private static Optional<String> ownerOf(Class<?> type) {
        if (type.getName().startsWith("net.minecraft.") || type.getName().startsWith("com.mojang.")) return Optional.of("minecraft");
        String resource = type.getName().replace('.', '/') + ".class";
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            try {
                if (mod.findPath(resource).isPresent()) return Optional.of(mod.getMetadata().getId());
            } catch (RuntimeException ignored) {
                // A mod whose files cannot be searched simply does not own the control.
            }
        }
        return Optional.empty();
    }
}
