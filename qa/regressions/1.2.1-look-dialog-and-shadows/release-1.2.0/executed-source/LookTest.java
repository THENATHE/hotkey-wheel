package com.thenathe.hotkeywheel.qa;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.ControlPickerScreen;
import com.thenathe.hotkeywheel.IconPickerScreen;
import com.thenathe.hotkeywheel.MoveSlotScreen;
import com.thenathe.hotkeywheel.WheelBindings;
import com.thenathe.hotkeywheel.WheelConfig;
import com.thenathe.hotkeywheel.WheelEditorScreen;
import com.thenathe.hotkeywheel.look.LookDialog;
import com.thenathe.hotkeywheel.look.LookScreen;
import com.thenathe.hotkeywheel.look.LookTextField;
import com.thenathe.hotkeywheel.look.PlateButton;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * The editor, the control chooser, the icon picker, Move to… and the remove-folder question in House dark, House
 * light and the Minecraft look, at GUI scale 2 and 3 in a 1280×800 window. Each screen is screenshotted and its
 * widgets are measured: every visible one inside the screen, none overlapping another, the editor's scrolling rows
 * inside the panel. The footer switches change the look at once, save it, and Save or Cancel in the editor keep it.
 */
public final class LookTest implements FabricClientGameTest {
    private static final String LONG_FOLDER = "Gemstones and other shiny things";
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, List<String>> shortenedLabels = new LinkedHashMap<>();

    private enum Style {
        HOUSE_DARK("house-dark"), HOUSE_LIGHT("house-light"), MINECRAFT("minecraft");

        final String label;

        Style(String label) { this.label = label; }
    }

    private record Box(String name, int x, int y, int width, int height) {
        boolean overlaps(Box other) {
            return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
        }
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        report.put("checks", checks);
        report.put("shortened_labels", shortenedLabels);
        WheelConfig[] original = {null};
        try {
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> {
                original[0] = WheelConfig.get().copy();
                client.options.guiScale().set(2);
                client.options.pauseOnLostFocus = false;
                fixture(client);
            });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksDownload();
                context.waitTicks(20);
                if (FabricLoader.getInstance().isModLoaded("buildprint")) {
                    // A new game shows Buildprint's welcome screen once; mark it seen, as the other fixtures do.
                    context.runOnClient(client -> {
                        Object onboarding = Class.forName("com.buildprint.client.onboarding.BuildprintOnboarding")
                                .getConstructor().newInstance();
                        onboarding.getClass().getMethod("markDone").invoke(onboarding);
                        client.gui.setScreen(null);
                    });
                    context.waitTicks(2);
                }
                footerSwitches(context);
                Map<String, Double> brightness = new LinkedHashMap<>();
                for (Style style : Style.values()) {
                    context.runOnClient(client -> apply(style));
                    for (int scale : new int[] {2, 3}) {
                        context.runOnClient(client -> client.options.guiScale().set(scale));
                        context.waitTicks(2);
                        screens(context, style, scale, brightness);
                    }
                }
                report.put("panel_brightness", brightness);
                check(brightness.get("house-light") > brightness.get("house-dark") + 60,
                        "House light draws a lighter panel than House dark");
                check(Math.abs(brightness.get("minecraft") - brightness.get("house-dark")) > 20,
                        "The Minecraft look draws a different panel from House dark");
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    client.options.guiScale().set(2);
                });
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failure", failure.toString());
            throw new AssertionError("Hotkey Wheel look QA", failure);
        } finally {
            try {
                context.runOnClient(client -> {
                    client.gui.setScreen(null);
                    client.options.guiScale().set(2);
                    if (original[0] != null && !WheelConfig.save(original[0])) throw new AssertionError("Cannot restore the configuration");
                });
            } finally {
                try {
                    Files.writeString(Path.of(System.getProperty("wheel.qa.root"), "look-results.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(report));
                } catch (IOException failure) { throw new RuntimeException(failure); }
            }
        }
    }

    /** One wheel with a slot, a slot with a long label, and a folder with a long name that holds a slot. */
    private void fixture(Minecraft client) {
        WheelConfig configuration = new WheelConfig();
        configuration.wheelCount = 2;
        List<WheelConfig.Entry> entries = configuration.wheels.get(0).entries;
        entries.clear();
        entries.add(slot("key.inventory", "minecraft:chest", ""));
        entries.add(slot("key.drop", "minecraft:dropper", "Drop the selected item, even a whole stack of it"));
        WheelConfig.Entry folder = WheelConfig.Entry.folder(LONG_FOLDER);
        folder.icon = "minecraft:diamond";
        folder.entries.add(slot("key.togglePerspective", "minecraft:spyglass", ""));
        entries.add(folder);
        InputConstants.Key[] keys = {key(InputConstants.KEY_G), key(InputConstants.KEY_H), InputConstants.UNKNOWN,
                key(InputConstants.KEY_TAB), InputConstants.UNKNOWN, key(InputConstants.KEY_U)};
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.getName().startsWith("key.hotkey_wheel.")) continue;
            if (mapping.matches(keys[0]) || mapping.matches(keys[1])) mapping.setKey(InputConstants.UNKNOWN);
        }
        KeyMapping.resetMapping();
        String error = WheelBindings.save(configuration, keys);
        check(error.isEmpty(), "The look fixture saves" + (error.isEmpty() ? "" : ": " + error));
    }

    private static WheelConfig.Entry slot(String control, String icon, String label) {
        WheelConfig.Entry entry = new WheelConfig.Entry();
        entry.control = control;
        entry.icon = icon;
        entry.label = label;
        return entry;
    }

    private static InputConstants.Key key(int code) { return InputConstants.Type.KEYBOARD.getOrCreate(code); }

    private static void apply(Style style) {
        if (!WheelConfig.update(config -> {
            config.setHouseLook(style != Style.MINECRAFT);
            config.setLightLook(style == Style.HOUSE_LIGHT);
        })) throw new AssertionError("Cannot switch the look");
    }

    /** The footer's Look, light/dark and colour switches act at once, are saved, and outlast Save and Cancel. */
    private void footerSwitches(ClientGameTestContext context) {
        context.runOnClient(client -> {
            apply(Style.HOUSE_DARK);
            check(!saved().contains("\"look\"") && !saved().contains("lightMode") && !saved().contains("\"accent\""),
                    "House dark, the default, keeps the look out of hotkey-wheel.json");
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            check(button(editor, "Look: House") != null, "The footer starts with Look: House");
            press(button(editor, "Light or dark"));
            check(WheelConfig.get().lightLook() && saved().contains("\"lightMode\": true"), "The sun and moon switch saves light panels at once");
            press(button(editor, "Look: House"));
            check(!WheelConfig.get().houseLook() && saved().contains("\"look\": \"minecraft\""), "Look: House switches to the Minecraft look and saves it");
            check(button(editor, "Look: Minecraft") != null && !hasButton(editor, "Colour") && !hasButton(editor, "Light or dark"),
                    "The Minecraft look has no colour or light switch");
            press(button(editor, "Cancel"));
            check(!WheelConfig.get().houseLook(), "Cancel in the editor keeps the look chosen in it");
            editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Look: Minecraft"));
            press(button(editor, "General"));
            press(button(editor, "Safe mode:"));
            press(button(editor, "Save"));
            check(WheelConfig.get().houseLook() && WheelConfig.get().lightLook(), "Save keeps the look chosen while editing");
            editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Colour"));
        });
        context.waitTicks(2);
        context.takeScreenshot("look-accent-list");
        context.runOnClient(client -> {
            LookScreen editor = (LookScreen) client.gui.screen();
            Button colour = button(editor, "Colour");
            // The list opens above the Colour button, one 14-pixel row per colour; choose the second row.
            int listBottom = colour.getY() - 2 - 4;
            int rows = com.thenathe.hotkeywheel.look.Accent.values().length;
            int secondRow = listBottom - rows * 14 + 2 + 14 + 7;
            editor.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(colour.getX() + 20, secondRow,
                    new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            com.thenathe.hotkeywheel.look.Accent chosen = WheelConfig.get().accentChoice();
            report.put("accent_chosen", chosen.name());
            check(chosen == com.thenathe.hotkeywheel.look.Accent.values()[1] && saved().contains("\"accent\""),
                    "Choosing a colour from the list saves it at once");
            WheelConfig.update(config -> config.setAccentChoice(com.thenathe.hotkeywheel.look.Accent.values()[0]));
            check(!saved().contains("\"accent\""), "The default colour is left out of the file again");
            client.gui.setScreen(null);
        });
    }

    private void screens(ClientGameTestContext context, Style style, int scale, Map<String, Double> brightness) {
        String prefix = "look-" + style.label + "-scale-" + scale + "-";
        show(context, prefix + "editor-wheel", client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Slot 2"));
            return editor;
        }, true);
        if (scale == 2) {
            Path shot = context.takeScreenshot(prefix + "editor-wheel-brightness");
            brightness.put(style.label, context.computeOnClient(client -> panelBrightness(client, shot)));
        }
        show(context, prefix + "editor-general", client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "General"));
            return editor;
        }, true);
        show(context, prefix + "editor-folder", client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Slot 3"));
            press(button(editor, "Open folder"));
            return editor;
        }, true);
        show(context, prefix + "chooser", client -> {
            ControlPickerScreen picker = new ControlPickerScreen(null, control -> { });
            client.gui.setScreen(picker);
            return picker;
        }, false);
        show(context, prefix + "chooser-folder", client -> {
            ControlPickerScreen picker = new ControlPickerScreen(null, control -> { });
            client.gui.setScreen(picker);
            press(button(picker, ControlPickerScreen.MINECRAFT_FOLDER));
            return picker;
        }, false);
        show(context, prefix + "icons", client -> {
            IconPickerScreen picker = new IconPickerScreen(null, "minecraft:stone", icon -> { });
            client.gui.setScreen(picker);
            return picker;
        }, false);
        show(context, prefix + "move-to", client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Slot 3"));
            press(button(editor, "Move to…"));
            check(client.gui.screen() instanceof MoveSlotScreen, "Move to… opens for the folder");
            return client.gui.screen();
        }, false);
        show(context, prefix + "remove-folder", client -> {
            WheelEditorScreen editor = new WheelEditorScreen(null);
            client.gui.setScreen(editor);
            press(button(editor, "Slot 3"));
            press(button(editor, "Remove"));
            check(client.gui.screen() instanceof LookDialog, "Removing the folder asks first");
            return client.gui.screen();
        }, false);
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private void show(ClientGameTestContext context, String name, Function<Minecraft, Screen> open, boolean editor) {
        context.runOnClient(client -> open.apply(client));
        context.waitTicks(3);
        Path shot = context.takeScreenshot(name);
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            measure(screen, name, editor);
            if (screen instanceof LookDialog dialog) dialogFits(dialog, name);
            if (screen instanceof ControlPickerScreen || screen instanceof IconPickerScreen) pageLabel(client, (LookScreen) screen, shot, name);
            for (var child : screen.children()) {
                if (child instanceof LookTextField field) textFieldShadow(field, name);
            }
        });
    }

    /** The question's title and message wrap inside its panel, which stays on screen. */
    private void dialogFits(LookDialog dialog, String name) {
        Minecraft client = Minecraft.getInstance();
        int boxX = intField(dialog, "boxX");
        int boxY = intField(dialog, "boxY");
        int boxWidth = intField(dialog, "boxWidth");
        int boxHeight = intField(dialog, "boxHeight");
        check(boxX >= 0 && boxY >= 0 && boxX + boxWidth <= dialog.width && boxY + boxHeight <= dialog.height,
                name + ": the question's panel is on screen");
        // 1.2.0 drew the title as a single line; later releases wrap it into titleLines.
        List<?> titleLines = hasField(dialog, "titleLines") ? listField(dialog, "titleLines") : List.of(dialog.getTitle().getVisualOrderText());
        List<?> lines = listField(dialog, "lines");
        int widest = 0;
        for (Object line : titleLines) widest = Math.max(widest, client.font.width((net.minecraft.util.FormattedCharSequence) line));
        for (Object line : lines) widest = Math.max(widest, client.font.width((net.minecraft.util.FormattedCharSequence) line));
        report.put(name + " widest line", widest + " of " + (boxWidth - 20));
        check(widest <= boxWidth - 20, name + ": the title and message fit inside the panel (" + widest + " of " + (boxWidth - 20) + ")");
    }

    /** House light and the Minecraft look draw dark text on a light panel, which must not have a black shadow. */
    private void pageLabel(Minecraft client, LookScreen screen, Path shot, String name) {
        if (com.thenathe.hotkeywheel.look.Look.current().panelShadow()) return;
        try {
            BufferedImage image = ImageIO.read(shot.toFile());
            double scale = (double) image.getWidth() / screen.width;
            int left = intCall(screen, "listX");
            int rowWidth = intCall(screen, "listWidth");
            int y = intCall(screen, "listBottom") - 14;
            int centre = left + rowWidth / 2;
            int dark = 0;
            for (int x = (int) ((centre - 30) * scale); x < (int) ((centre + 30) * scale); x++) {
                for (int row = (int) (y * scale); row < (int) ((y + 9) * scale); row++) {
                    int rgb = image.getRGB(x, row);
                    if (((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3 < 60) dark++;
                }
            }
            check(dark == 0, name + ": the page label has no black shadow on the light panel (" + dark + " dark pixels)");
        } catch (IOException failure) {
            throw new AssertionError("Cannot read " + shot, failure);
        }
    }

    /** Text in a field is shadowed only where it is light: House dark and vanilla's black field. */
    private void textFieldShadow(LookTextField field, String name) {
        try {
            var shadow = net.minecraft.client.gui.components.EditBox.class.getDeclaredField("textShadow");
            shadow.setAccessible(true);
            var look = com.thenathe.hotkeywheel.look.Look.current();
            boolean expected = !look.house() || !look.colors().light();
            check(shadow.getBoolean(field) == expected, name + ": text field text is " + (expected ? "" : "not ") + "shadowed");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read the text field's shadow", failure);
        }
    }

    /** Every visible widget inside the screen and clear of every other; the editor's rows inside its panel. */
    private void measure(Screen screen, String name, boolean editor) {
        List<Box> boxes = new ArrayList<>();
        List<String> shortened = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            String label = widget.getMessage().getString();
            boxes.add(widget instanceof LookTextField field ? frame(field, label) : new Box(label, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
            // The light/dark switch draws a sun or moon; its label is only read out.
            boolean drawsLabel = !label.equals("Light or dark");
            if (widget instanceof PlateButton && drawsLabel && client.font.width(label) > widget.getWidth() - 8) shortened.add(label);
        }
        if (!shortened.isEmpty()) shortenedLabels.put(name, shortened);
        for (Box box : boxes) {
            check(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= screen.width && box.y() + box.height() <= screen.height,
                    name + ": \"" + box.name() + "\" is inside the " + screen.width + "×" + screen.height + " screen");
        }
        List<String> overlaps = new ArrayList<>();
        for (int first = 0; first < boxes.size(); first++) {
            for (int second = first + 1; second < boxes.size(); second++) {
                if (boxes.get(first).overlaps(boxes.get(second))) overlaps.add(boxes.get(first).name() + " / " + boxes.get(second).name());
            }
        }
        check(overlaps.isEmpty(), name + ": no two of its " + boxes.size() + " widgets overlap " + overlaps);
        if (editor) {
            int panelX = intField(screen, "panelX");
            int panelWidth = intField(screen, "panelWidth");
            int top = intField(screen, "viewportTop");
            int bottom = intField(screen, "viewportBottom");
            int navWidth = intField(screen, "navWidth");
            int contentX = intField(screen, "contentX");
            for (Box box : boxes) {
                boolean inNav = box.x() + box.width() <= contentX + navWidth;
                boolean inFooter = box.y() >= intField(screen, "footerY");
                if (inNav || inFooter) continue;
                check(box.x() >= panelX && box.x() + box.width() <= panelX + panelWidth && box.y() >= top && box.y() + box.height() <= bottom,
                        name + ": \"" + box.name() + "\" sits inside the panel");
            }
        }
    }

    private static Box frame(LookTextField field, String label) {
        return new Box(label.isEmpty() ? "text field" : label, intField(field, "frameX"), intField(field, "frameY"),
                intField(field, "frameWidth"), intField(field, "frameHeight"));
    }

    /** The average brightness of a strip of the panel under the slots' heading, 0 to 255. */
    private static double panelBrightness(Minecraft client, Path screenshot) {
        try {
            BufferedImage image = ImageIO.read(screenshot.toFile());
            Screen screen = client.gui.screen();
            double scale = (double) image.getWidth() / screen.width;
            int x0 = (int) ((intField(screen, "panelX") + intField(screen, "panelWidth") - 30) * scale);
            int y0 = (int) ((intField(screen, "panelBottom") - 12) * scale);
            double total = 0;
            int count = 0;
            for (int x = x0; x < x0 + (int) (20 * scale); x++) {
                for (int y = y0; y < y0 + (int) (6 * scale); y++) {
                    int rgb = image.getRGB(x, y);
                    total += ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3.0;
                    count++;
                }
            }
            return total / count;
        } catch (IOException failure) {
            throw new AssertionError("Cannot read " + screenshot, failure);
        }
    }

    private static int intField(Object owner, String name) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(owner);
            } catch (NoSuchFieldException next) {
                // Declared further up, such as LookScreen's frame.
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot read " + name, failure);
            }
        }
        throw new AssertionError("No field " + name + " on " + owner.getClass().getSimpleName());
    }

    private static boolean hasField(Object owner, String name) {
        try {
            owner.getClass().getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException absent) {
            return false;
        }
    }

    private static List<?> listField(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (List<?>) field.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read " + name, failure);
        }
    }

    private static int intCall(LookScreen screen, String method) {
        try {
            var call = LookScreen.class.getDeclaredMethod(method);
            call.setAccessible(true);
            return (int) call.invoke(screen);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot call " + method, failure);
        }
    }

    private static String saved() {
        try {
            return Files.readString(WheelConfig.path());
        } catch (IOException failure) {
            throw new AssertionError("Cannot read hotkey-wheel.json", failure);
        }
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static Button button(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix))
                .sorted(java.util.Comparator.comparing(candidate -> !candidate.getMessage().getString().equals(prefix)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button " + prefix));
    }

    private static boolean hasButton(Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(candidate -> candidate.getMessage().getString().startsWith(prefix));
    }
}
