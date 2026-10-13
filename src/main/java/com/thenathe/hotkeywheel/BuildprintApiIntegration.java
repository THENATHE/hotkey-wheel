package com.thenathe.hotkeywheel;

import com.buildprint.api.BuildprintHud;
import com.buildprint.api.editor.BuildprintEditor;
import com.buildprint.api.editor.EditorListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/** Keeps the optional public API isolated from clients running older Buildprint or none at all. */
final class BuildprintApiIntegration {
    private static boolean initialized;
    private static boolean editorApi;
    private static boolean openApi;
    private static boolean closeApi;
    private static boolean screenApi;
    private static boolean lifecycleFailed;
    private static boolean screenApiFailed;

    private BuildprintApiIntegration() {}

    static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded("buildprint")) return;
        try {
            Class<?> editor = Class.forName("com.buildprint.api.editor.BuildprintEditor", false,
                    BuildprintApiIntegration.class.getClassLoader());
            openApi = hasBooleanMethod(editor, "open");
            closeApi = hasBooleanMethod(editor, "close");
            screenApi = hasBooleanMethod(editor, "isEditorScreen", Screen.class);
            PublicEditor.initialize();
            editorApi = true;
        } catch (ClassNotFoundException missingLegacyApi) {
            // Buildprint 0.1.16 uses the existing native session bridge.
        } catch (RuntimeException | LinkageError failure) {
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint editor API is unavailable; using the legacy bridge", failure);
        }
        try {
            Class.forName("com.buildprint.api.BuildprintHud", false,
                    BuildprintApiIntegration.class.getClassLoader());
            PublicHud.initialize();
        } catch (ClassNotFoundException missingLegacyApi) {
            // Older Buildprint retains its normal HUD rendering.
        } catch (RuntimeException | LinkageError failure) {
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint HUD API is unavailable", failure);
        }
    }

    static boolean hasEditorApi() {
        return editorApi;
    }

    static Object editorSession() {
        if (!editorApi) return null;
        try {
            return PublicEditor.session();
        } catch (RuntimeException | LinkageError failure) {
            editorApi = false;
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint editor API failed; using the legacy bridge", failure);
            // A changed bridge must never transfer an existing wheel's ownership to another session.
            return null;
        }
    }

    private static boolean hasBooleanMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters).getReturnType() == boolean.class;
        } catch (NoSuchMethodException missingLegacyMethod) {
            return false;
        }
    }

    static boolean hasCloseApi() {
        return closeApi;
    }

    static boolean closeEditor() {
        if (!closeApi || lifecycleFailed) return false;
        try {
            return PublicLifecycle.close();
        } catch (RuntimeException | LinkageError failure) {
            lifecycleFailed = true;
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint public editor closure failed", failure);
            return false;
        }
    }

    /** True means the API handled the action, even when it refused to change editor state. */
    static boolean activateEditor(KeyMapping control) {
        if (!control.getName().equals("key.buildprint.editor") || !openApi && !closeApi) return false;
        if (lifecycleFailed) {
            BuildprintEditorReturn.clear();
            return true;
        }
        try {
            if (PublicLifecycle.isActive()) {
                if (!closeApi) return false;
                // Wheel actions run only after the radial has closed and there is no child dialog.
                closeEditor();
                BuildprintEditorReturn.clear();
            } else {
                if (!openApi) return false;
                if (!PublicLifecycle.open()) BuildprintEditorReturn.clear();
            }
        } catch (RuntimeException | LinkageError failure) {
            lifecycleFailed = true;
            BuildprintEditorReturn.clear();
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint public editor activation failed", failure);
        }
        return true;
    }

    static boolean hasScreenApi() {
        return screenApi;
    }

    static boolean screenApiFailed() {
        return screenApiFailed;
    }

    static boolean isEditorScreen(Screen screen) {
        if (!screenApi || screenApiFailed) return false;
        try {
            return PublicScreens.isEditorScreen(screen);
        } catch (RuntimeException | LinkageError failure) {
            screenApiFailed = true;
            LoggerFactory.getLogger("hotkey-wheel").warn("Buildprint public screen ownership check failed", failure);
            return false;
        }
    }

    private static final class PublicLifecycle {
        private static boolean isActive() {
            return BuildprintEditor.isActive();
        }

        private static boolean open() {
            return BuildprintEditor.open();
        }

        private static boolean close() {
            return BuildprintEditor.close();
        }
    }

    private static final class PublicScreens {
        private static boolean isEditorScreen(Screen screen) {
            return BuildprintEditor.isEditorScreen(screen);
        }
    }

    private static final class PublicEditor {
        private static Object session;

        private static void initialize() {
            BuildprintEditor.addListener(new EditorListener() {
                @Override
                public void onEnter() {
                    session = new Object();
                }

                @Override
                public void onExit() {
                    session = null;
                }
            });
        }

        private static Object session() {
            if (!BuildprintEditor.isActive()) {
                session = null;
                return null;
            }
            // Handles initialization during an already-active editor without claiming wheel ownership.
            if (session == null) session = new Object();
            return session;
        }
    }

    private static final class PublicHud {
        private static void initialize() {
            BuildprintHud.hideWhile("hotkey_wheel:radial", () -> {
                Screen screen = Minecraft.getInstance().gui.screen();
                return screen instanceof WheelScreen || screen instanceof ToolPouchRadialScreen;
            });
        }
    }
}
