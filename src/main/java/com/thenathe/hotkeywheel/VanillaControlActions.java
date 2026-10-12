package com.thenathe.hotkeywheel;

import com.thenathe.hotkeywheel.mixin.KeyboardDebugAccess;
import com.thenathe.hotkeywheel.mixin.MinecraftAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;

public final class VanillaControlActions {
    private static final ThreadLocal<KeyMapping> DEBUG_CONTROL = new ThreadLocal<>();

    private VanillaControlActions() {}

    public static boolean activate(Minecraft client, KeyMapping control) {
        if (control == client.options.keyScreenshot) {
            KeyEvent event = currentModifiers();
            Screenshot.grab(client, event.hasControlDownWithQuirk());
            return true;
        }
        if (control == client.options.keyFullscreen) {
            ((MinecraftAccess) client).hotkeyWheel$toggleFullscreen();
            return true;
        }
        if (control == client.options.keyFriends) {
            ((MinecraftAccess) client).hotkeyWheel$toggleFriendsScreen();
            return true;
        }
        if (control == client.options.keyDebugOverlay) {
            client.debugEntries.toggleDebugOverlay();
            return true;
        }
        // Vanilla's crash shortcut requires a sustained physical key chord.
        // Leave it and the debug modifier to the ordinary held-control path.
        if (control == client.options.keyDebugCrash || control == client.options.keyDebugModifier) {
            return false;
        }
        for (KeyMapping debugControl : client.options.debugKeys) {
            if (control == debugControl) {
                dispatchDebugControl(client, control);
                return true;
            }
        }
        return false;
    }

    public static boolean matchesDebugControl(KeyMapping control, KeyEvent event) {
        KeyMapping selected = DEBUG_CONTROL.get();
        return selected == null ? control.matches(event) : control == selected;
    }

    private static void dispatchDebugControl(Minecraft client, KeyMapping control) {
        KeyMapping previous = DEBUG_CONTROL.get();
        DEBUG_CONTROL.set(control);
        try {
            ((KeyboardDebugAccess) client.keyboardHandler).hotkeyWheel$handleDebugKeys(currentModifiers());
        } finally {
            if (previous == null) {
                DEBUG_CONTROL.remove();
            } else {
                DEBUG_CONTROL.set(previous);
            }
        }
    }

    private static KeyEvent currentModifiers() {
        return new KeyEvent(0, 0, SDLKeyboard.SDL_GetModState());
    }
}
