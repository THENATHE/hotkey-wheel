package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ControlPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> accepted;
    private final List<AbstractWidget> resultWidgets = new ArrayList<>();
    private List<Option> catalog;
    private List<Option> filtered = List.of();
    private String query = "";
    private int page;
    private int left;
    private int panelWidth;
    private int rows;
    private Button previous;
    private Button next;

    // Key mappings and Tool Pouch slot toggles, which Tool Pouch binds to raw keys instead of key mappings.
    private record Option(String control, String name, String category, String key) {}

    public ControlPickerScreen(Screen parent, Consumer<String> accepted) {
        super(Component.literal("Choose a control"));
        this.parent = parent;
        this.accepted = accepted;
    }

    @Override
    protected void init() {
        resultWidgets.clear();
        panelWidth = Math.min(560, width - 40);
        left = (width - panelWidth) / 2;
        rows = Math.max(1, (height - 126) / 24);
        List<Option> options = new ArrayList<>();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().contains("hotkey_wheel")) continue;
            options.add(new Option(mapping.getName(), controlName(mapping), mapping.getCategory().label().getString(),
                    mapping.getTranslatedKeyMessage().getString()));
        }
        for (ToolPouchToggles.Choice choice : ToolPouchToggles.choices()) {
            options.add(new Option(choice.control(), choice.name(), "Tool Pouch slot toggles", choice.key()));
        }
        catalog = options.stream().sorted(Comparator.comparing(option -> option.name().toLowerCase(Locale.ROOT))).toList();
        EditBox search = new EditBox(font, left, 32, panelWidth, 20, Component.literal("Search controls"));
        search.setHint(Component.literal("Search control, category, or key…"));
        search.setMaxLength(128);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            refreshResults();
        });
        addRenderableWidget(search);
        previous = addRenderableWidget(Button.builder(Component.literal("Previous"), button -> changePage(-1))
                .bounds(left, height - 49, 84, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> changePage(1))
                .bounds(left + panelWidth - 84, height - 49, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left, height - 25, panelWidth, 20).build());
        refreshResults();
        setInitialFocus(search);
    }

    private static String controlName(KeyMapping mapping) {
        return Component.translatable(mapping.getName()).getString();
    }

    private List<Option> matches() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return catalog.stream().filter(option -> {
            String searchable = option.name() + " " + option.control() + " " + option.category() + " " + option.key();
            return searchable.toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
    }

    private void changePage(int amount) {
        page += amount;
        refreshResults();
    }

    private void refreshResults() {
        for (AbstractWidget widget : resultWidgets) removeWidget(widget);
        resultWidgets.clear();
        filtered = matches();
        int lastPage = Math.max(0, (filtered.size() - 1) / rows);
        page = Math.clamp(page, 0, lastPage);
        previous.active = page > 0;
        next.active = page < lastPage;
        int first = page * rows;
        for (int index = first; index < Math.min(filtered.size(), first + rows); index++) {
            Option option = filtered.get(index);
            String text = option.name() + " · " + option.key();
            String help = option.name() + "\n" + option.category() + "\n" + option.control();
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(text, panelWidth - 12)), selected -> {
                accepted.accept(option.control());
                onClose();
            }).bounds(left, 72 + (index - first) * 24, panelWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(help))).build();
            resultWidgets.add(addRenderableWidget(button));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            changePage(event.key() == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && y >= 64 && y < height - 53) {
            changePage(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 12, WheelUi.GOLD);
        int count = filtered.size();
        graphics.text(font, count + " controls · includes installed mods", left, 58, WheelUi.DIM);
        graphics.centeredText(font, "Page " + (page + 1) + "/" + Math.max(1, (count + rows - 1) / rows),
                width / 2, height - 43, WheelUi.DIM);
        if (count == 0) graphics.centeredText(font, "No matching controls", width / 2, 80, WheelUi.DIM);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
