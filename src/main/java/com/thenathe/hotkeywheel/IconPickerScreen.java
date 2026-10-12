package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class IconPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> accepted;
    private final String selectedIcon;
    private final List<AbstractWidget> tiles = new ArrayList<>();
    private final List<Icon> catalog;
    private List<Icon> filtered = List.of();
    private String query = "";
    private int page;
    private int left;
    private int panelWidth;
    private int columns;
    private int rows;
    private Button previous;
    private Button next;
    private boolean showItemIcons;

    private record Icon(Item item, String id, Component description, String searchable) {}

    public IconPickerScreen(Screen parent, String selectedIcon, Consumer<String> accepted) {
        super(Component.literal("Choose a block or item icon"));
        this.parent = parent;
        this.selectedIcon = selectedIcon;
        this.accepted = accepted;
        catalog = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).map(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            Component name = WheelUi.itemName(item);
            return new Icon(item, id, name.copy().append("\n" + id),
                    (name.getString() + " " + id).toLowerCase(Locale.ROOT));
        }).sorted(Comparator.comparing(icon -> icon.description().getString().toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    protected void init() {
        tiles.clear();
        panelWidth = Math.min(560, width - 40);
        left = (width - panelWidth) / 2;
        showItemIcons = minecraft.level != null;
        columns = Math.max(1, panelWidth / (showItemIcons ? 28 : 180));
        rows = Math.max(1, (height - 128) / 28);
        EditBox search = new EditBox(font, left, 32, panelWidth, 20, Component.literal("Search icons"));
        search.setHint(Component.literal("Search block, item, or registry ID…"));
        search.setMaxLength(128);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            refreshTiles();
        });
        addRenderableWidget(search);
        previous = addRenderableWidget(Button.builder(Component.literal("Previous"), button -> changePage(-1))
                .bounds(left, height - 49, 84, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> changePage(1))
                .bounds(left + panelWidth - 84, height - 49, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left, height - 25, panelWidth, 20).build());
        refreshTiles();
        setInitialFocus(search);
    }

    private List<Icon> matches() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return catalog.stream().filter(icon -> icon.searchable().contains(needle)).toList();
    }

    private void changePage(int amount) {
        page += amount;
        refreshTiles();
    }

    private void refreshTiles() {
        for (AbstractWidget tile : tiles) removeWidget(tile);
        tiles.clear();
        filtered = matches();
        int pageSize = rows * columns;
        int lastPage = Math.max(0, (filtered.size() - 1) / pageSize);
        page = Math.clamp(page, 0, lastPage);
        previous.active = page > 0;
        next.active = page < lastPage;
        int first = page * pageSize;
        for (int index = first; index < Math.min(filtered.size(), first + pageSize); index++) {
            Icon icon = filtered.get(index);
            int cell = index - first;
            Button.OnPress choose = button -> {
                accepted.accept(icon.id());
                onClose();
            };
            Button tile;
            if (showItemIcons) {
                tile = new WheelUi.IconButton(left + cell % columns * 28,
                        74 + cell / columns * 28, 24, icon.item(), icon.description(),
                        () -> icon.id().equals(selectedIcon), choose);
            } else {
                int cellWidth = panelWidth / columns;
                String label = font.plainSubstrByWidth(WheelUi.itemName(icon.item()).getString(), cellWidth - 14);
                tile = Button.builder(Component.literal(label), choose)
                        .bounds(left + cell % columns * cellWidth, 74 + cell / columns * 28, cellWidth - 4, 24)
                        .tooltip(Tooltip.create(icon.description())).build();
            }
            tiles.add(addRenderableWidget(tile));
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
        graphics.text(font, count + (showItemIcons ? " icons · cosmetic only" : " icons · previews load in a world"), left, 58, WheelUi.DIM);
        int pages = Math.max(1, (count + rows * columns - 1) / (rows * columns));
        graphics.centeredText(font, "Page " + (page + 1) + "/" + pages, width / 2, height - 43, WheelUi.DIM);
        if (count == 0) graphics.centeredText(font, "No matching icons", width / 2, 82, WheelUi.DIM);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
