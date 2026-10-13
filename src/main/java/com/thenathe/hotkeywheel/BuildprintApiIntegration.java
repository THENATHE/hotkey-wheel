package com.thenathe.hotkeywheel;

import com.buildprint.api.BuildprintHud;
import com.buildprint.api.editor.BuildprintEditor;
import com.buildprint.api.editor.EditorListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/** Keeps the optional public API isolated from clients running older Buildprint or none at all. */
final class BuildprintApiIntegration {
    private static boolean initialized;
    private static boolean editorApi;

    private BuildprintApiIntegration() {}

    static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded("buildprint")) return;
        try {
            Class.forName("com.buildprint.api.editor.BuildprintEditor", false,
                    BuildprintApiIntegration.class.getClassLoader());
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
