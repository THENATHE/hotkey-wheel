package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/** Selection editing is a freecam session, with no Screen to serve as its parent. */
final class BuildprintEditorReturn {
    private static final String CONTROL = "key.buildprint.editor";
    private static PendingEditor pending;
    private static Object session;
    private static InputConstants.Key opener;
    private static InputConstants.Key consumedKey;
    private static long ticks;

    private record PendingEditor(InputConstants.Key opener, Object previousSession, long expires) {}

    private BuildprintEditorReturn() {}

    static void begin(KeyMapping control, InputConstants.Key wheelOpener) {
        if (!WheelConfig.get().closeBuildprintWithWheelKey) {
            clearOwnership();
            return;
        }
        if (!control.getName().equals(CONTROL)) {
            pending = null;
            // A second wheel action can leave the same editor session running.
            if (session != null && NativeEditor.session() != session) clearOwnership();
            return;
        }
        clearOwnership();
        pending = new PendingEditor(wheelOpener, NativeEditor.session(), ticks + 4);
    }

    static void tick(Minecraft client) {
        ticks++;
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !client.isWindowActive()
                || client.player == null || client.level == null) {
            clear();
            return;
        }
        if (pending == null && session == null) return;
        Object current = NativeEditor.session();
        if (pending != null) {
            if (ticks > pending.expires() || pending.previousSession() != null && current == null) {
                pending = null;
            } else if (current != null && current != pending.previousSession()) {
                session = current;
                opener = pending.opener();
                pending = null;
            }
        }
        Screen screen = client.gui.screen();
        if (session != null && (current != session || screen != null
                && !(screen instanceof WheelScreen) && !WheelMenuReturn.isEditorMenu(screen))) clearOwnership();
    }

    static boolean handleInput(InputConstants.Key key, int action) {
        if (key.equals(consumedKey)) {
            if (action == InputConstants.RELEASE) consumedKey = null;
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (!WheelConfig.get().closeBuildprintWithWheelKey || !client.isWindowActive()) {
            clear();
            return false;
        }
        if (action != InputConstants.PRESS || session == null || !key.equals(opener)
                || client.gui.screen() instanceof WheelScreen) return false;
        if (NativeEditor.session() != session || client.gui.screen() != null
                && !WheelMenuReturn.isEditorMenu(client.gui.screen())) {
            clearOwnership();
            return false;
        }
        consumedKey = key;
        closeEditor(client);
        return true;
    }

    private static void closeEditor(Minecraft client) {
        Object ownedSession = session;
        for (int depth = 0; depth < 32 && client.gui.screen() != null; depth++) {
            Screen screen = client.gui.screen();
            if (!WheelMenuReturn.isEditorMenu(screen)) return;
            WheelMenuReturn.closeNativeScreen(client, screen);
            if (client.gui.screen() == screen) return;
        }
        if (client.gui.screen() == null && NativeEditor.session() == ownedSession
                && NativeEditor.exit(client)) clearOwnership();
    }

    static void clear() {
        clearOwnership();
        consumedKey = null;
    }

    private static void clearOwnership() {
        pending = null;
        session = null;
        opener = null;
    }

    private static final class NativeEditor {
        private static Method active;
        private static Method freecam;
        private static Method exit;
        private static boolean unavailable;

        private static boolean resolve() {
            if (unavailable || !FabricLoader.getInstance().isModLoaded("buildprint")) return false;
            if (active != null) return true;
            try {
                Class<?> mode = Class.forName("com.buildprint.client.edit.EditorMode");
                Class<?> controller = Class.forName("com.buildprint.client.edit.EditorController");
                Method activeMethod = mode.getMethod("isActive");
                Method freecamMethod = mode.getMethod("freecam");
                Method exitMethod = controller.getDeclaredMethod("exit", Minecraft.class);
                if (!exitMethod.trySetAccessible()) throw new IllegalAccessException("Editor exit is inaccessible");
                active = activeMethod;
                freecam = freecamMethod;
                exit = exitMethod;
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                unavailable(failure);
                return false;
            }
        }

        private static Object session() {
            if (!resolve()) return null;
            try {
                return Boolean.TRUE.equals(active.invoke(null)) ? freecam.invoke(null) : null;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                unavailable(failure);
                return null;
            }
        }

        private static boolean exit(Minecraft client) {
            if (!resolve()) return false;
            try {
                // Use the same exit routine as the native toggle: restore camera/input and keep regions.
                exit.invoke(null, client);
                return true;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                unavailable(failure);
                return false;
            }
        }

        private static void unavailable(Throwable failure) {
            unavailable = true;
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint selection-editor return is unavailable", failure);
        }
    }
}
