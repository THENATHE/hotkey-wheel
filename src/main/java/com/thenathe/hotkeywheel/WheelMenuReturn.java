package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Returns from menus opened by a wheel without taking over menus opened elsewhere. */
public final class WheelMenuReturn {
    private static final int OPEN_TIMEOUT_TICKS = 4;
    private static final int MAX_PARENT_DEPTH = 32;
    private static final String MATERIAL_LIST_SCREEN = "com.buildprint.client.gui.MaterialListScreen";
    private static final ClassValue<List<Field>> PARENT_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> screenClass) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> type = screenClass; type != null && Screen.class.isAssignableFrom(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && Screen.class.isAssignableFrom(field.getType())
                            && field.trySetAccessible()) fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
    };

    private static PendingMenu pending;
    private static Screen root;
    private static Screen originParent;
    private static InputConstants.Key opener;
    private static InputConstants.Key consumedKey;
    private static long ticks;

    private record PendingMenu(String control, InputConstants.Key opener, Screen parent, long expires) {}

    private WheelMenuReturn() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(WheelMenuReturn::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> observeScreen(client, screen));
    }

    public static void begin(KeyMapping control, InputConstants.Key wheelOpener) {
        BuildprintEditorReturn.begin(control, wheelOpener);
        clearOwnership();
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !isBuildprintControl(control.getName())) return;
        pending = new PendingMenu(control.getName(), wheelOpener, Minecraft.getInstance().gui.screen(), ticks + OPEN_TIMEOUT_TICKS);
    }

    public static void tick(Minecraft client) {
        BuildprintEditorReturn.tick(client);
        ticks++;
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !client.isWindowActive() || client.player == null) {
            clear();
            return;
        }
        if (pending != null && ticks > pending.expires()) pending = null;
        observeScreen(client, client.gui.screen());
    }

    public static boolean handleInput(InputConstants.Key key, int action) {
        if (BuildprintEditorReturn.handleInput(key, action)) return true;
        if (key.equals(consumedKey)) {
            if (action == InputConstants.RELEASE) consumedKey = null;
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !client.isWindowActive()) {
            clear();
            return false;
        }
        if (action != InputConstants.PRESS || opener == null || !opener.equals(key)) return false;
        if (!owns(client.gui.screen())) {
            clearOwnership();
            return false;
        }
        consumedKey = key;
        closeOwnedMenus(client);
        return true;
    }

    public static void clear() {
        BuildprintEditorReturn.clear();
        clearOwnership();
        consumedKey = null;
    }

    private static void observeScreen(Minecraft client, Screen screen) {
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !client.isWindowActive() || client.player == null) {
            clear();
            return;
        }
        if (root != null) {
            if (!owns(screen)) clearOwnership();
            return;
        }
        if (pending == null || screen == null || screen == pending.parent() || !matchesPending(screen)) return;
        root = screen;
        originParent = pending.parent();
        opener = pending.opener();
        pending = null;
    }

    private static boolean matchesPending(Screen screen) {
        String screenName = screen.getClass().getName();
        return switch (pending.control()) {
            case "key.buildprint.open" -> screenName.equals("com.buildprint.client.gui.BuildprintScreen");
            case "key.buildprint_toning.instamine_settings" -> screenName.equals("com.thenathe.buildprinttoning.client.InstamineSettingsScreen");
            default -> false;
        };
    }

    private static void closeOwnedMenus(Minecraft client) {
        Screen ownedRoot = root;
        Screen returnTo = originParent;
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            Screen screen = client.gui.screen();
            if (screen == null || screen == returnTo || !hasAncestor(screen, ownedRoot)) break;
            boolean closingRoot = screen == ownedRoot;
            // Native closure commits Buildprint edits and preserves each screen's save/cancel rules.
            closeNativeScreen(client, screen);
            if (client.gui.screen() == screen) return;
            if (closingRoot) break;
        }
        if (!hasAncestor(client.gui.screen(), ownedRoot)) clearOwnership();
    }

    private static boolean owns(Screen screen) {
        return root != null && screen != originParent && hasAncestor(screen, root);
    }

    private static boolean hasAncestor(Screen screen, Screen ancestor) {
        return ancestor != null && anyParent(screen, candidate -> candidate == ancestor);
    }

    static boolean isEditorMenu(Screen screen) {
        if (BuildprintApiIntegration.hasScreenApi()) {
            boolean owned = anyParent(screen, BuildprintApiIntegration::isEditorScreen);
            return owned && !BuildprintApiIntegration.screenApiFailed();
        }
        // Older versions have neither the public screen registry nor addon registration.
        return anyParent(screen, candidate -> {
            String name = candidate.getClass().getName();
            return name.startsWith("com.buildprint.client.gui.")
                    || name.equals("com.thenathe.buildprinttoning.ToneScreen")
                    || name.equals("com.thenathe.buildprinttoning.client.SplineSettingsScreen")
                    || name.equals("com.thenathe.buildprinttoning.client.ToolbarOverlayScreen");
        });
    }

    static void closeNativeScreen(Minecraft client, Screen screen) {
        screen.onClose();
        if (client.gui.screen() == screen && screen.getClass().getName().equals(MATERIAL_LIST_SCREEN)) screen.onClose();
    }

    private static boolean anyParent(Screen screen, Predicate<Screen> matches) {
        if (screen == null) return false;
        ArrayDeque<Screen> remaining = new ArrayDeque<>();
        Set<Screen> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        remaining.add(screen);
        while (!remaining.isEmpty() && visited.size() < MAX_PARENT_DEPTH) {
            Screen candidate = remaining.removeFirst();
            if (matches.test(candidate)) return true;
            if (!visited.add(candidate)) continue;
            for (Field field : PARENT_FIELDS.get(candidate.getClass())) {
                try {
                    Screen parent = (Screen) field.get(candidate);
                    if (parent != null && !visited.contains(parent)) remaining.addLast(parent);
                } catch (IllegalAccessException ignored) {
                    // An inaccessible optional-mod parent cannot establish ownership.
                }
            }
        }
        return false;
    }

    private static boolean isBuildprintControl(String name) {
        return name.equals("key.buildprint.open") || name.equals("key.buildprint_toning.instamine_settings");
    }

    private static void clearOwnership() {
        pending = null;
        root = null;
        originParent = null;
        opener = null;
    }
}
