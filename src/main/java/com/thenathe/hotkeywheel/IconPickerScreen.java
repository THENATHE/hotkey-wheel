package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import net.minecraft.world.item.Items;

public final class IconPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> accepted;
    private final String selectedIcon;
    private final List<AbstractWidget> tiles = new ArrayList<>();
    private final List<Icon> catalog = new ArrayList<>();
    private List<Icon> filtered = List.of();
    private String query = "";
    private String chosenIcon;
    private int page;
    private int left;
    private int panelWidth;
    private int columns;
    private int rows;
    private Button previous;
    private Button next;
    private boolean showItemIcons;

    // Groups in the order an empty search lists them, which also breaks ties between equally good matches.
    private static final int GLYPHS = 0;
    private static final int MODS = 1;
    private static final int ITEMS = 2;

    /** {@code names} are the lower-case names searched: the display name first, then aliases, mod IDs and item paths. */
    private record Icon(String id, WheelIcons.Icon shown, Component description, int group, List<String> names) {}

    public IconPickerScreen(Screen parent, String selectedIcon, Consumer<String> accepted) {
        super(Component.literal("Choose an icon"));
        this.parent = parent;
        this.selectedIcon = selectedIcon;
        this.accepted = accepted;
        for (WheelIcons.Glyph glyph : WheelIcons.GLYPHS) {
            List<String> names = new ArrayList<>(List.of(glyph.label(), glyph.name(), glyph.name().replace('_', ' ')));
            names.addAll(glyph.aliases());
            catalog.add(new Icon(glyph.id(), WheelIcons.resolve(glyph.id()), Component.literal(glyph.label() + "\nHotkey Wheel icon · "
                    + String.join(", ", glyph.aliases())), GLYPHS, lowerCase(names)));
        }
        for (ModIcons.Choice mod : ModIcons.choices()) {
            String id = WheelIcons.modIcon(mod.modId());
            catalog.add(new Icon(id, WheelIcons.resolve(id), Component.literal(mod.name() + "\nMod icon · " + mod.modId()),
                    MODS, lowerCase(List.of(mod.name(), mod.modId(), "mod"))));
        }
        BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).map(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            Component name = WheelUi.itemName(item);
            return new Icon(id, WheelIcons.resolve(id), name.copy().append("\n" + id), ITEMS,
                    lowerCase(List.of(name.getString(), path, path.replace('_', ' '))));
        }).sorted(Comparator.comparing(icon -> icon.names().getFirst())).forEach(catalog::add);
    }

    private static List<String> lowerCase(List<String> names) {
        return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
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
        search.setHint(Component.literal("Search icons, mods, blocks or items…"));
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

    /**
     * With no search, the catalog's own order: Hotkey Wheel's icons, then mods' icons, then blocks and items.
     * A search keeps every icon with a name or alias containing it, best matches first: an exact name, then
     * names starting with it, then a word starting with it, then the rest. Equal matches keep the group order.
     * A search with a colon is matched against saved IDs too, such as {@code minecraft:stone}.
     */
    private List<Icon> matches() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return catalog;
        record Match(Icon icon, int rank) {}
        List<Match> found = new ArrayList<>();
        for (Icon icon : catalog) {
            int rank = rank(icon, needle);
            if (rank >= 0) found.add(new Match(icon, rank));
        }
        // List.sort is stable, so equal matches keep the catalog's order inside each group.
        found.sort(Comparator.comparingInt(Match::rank).thenComparingInt(match -> match.icon().group()));
        return found.stream().map(Match::icon).toList();
    }

    private static int rank(Icon icon, String needle) {
        int best = !needle.contains(":") || !icon.id().contains(needle) ? -1
                : icon.id().equals(needle) ? 0 : icon.id().startsWith(needle) ? 1 : 3;
        for (String name : icon.names()) {
            int rank = name.equals(needle) ? 0
                    : name.startsWith(needle) ? 1
                    : (" " + name).contains(" " + needle) ? 2
                    : name.contains(needle) ? 3 : -1;
            if (rank >= 0 && (best < 0 || rank < best)) best = rank;
        }
        return best;
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
                chosenIcon = icon.id();
                accepted.accept(icon.id());
                onClose();
            };
            Button tile;
            if (showItemIcons) {
                tile = new WheelUi.IconButton(left + cell % columns * 28,
                        74 + cell / columns * 28, 24, icon.shown(), icon.description(),
                        () -> icon.id().equals(selectedIcon), choose);
            } else {
                int cellWidth = panelWidth / columns;
                String label = font.plainSubstrByWidth(icon.shown().name().getString(), cellWidth - 14);
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
    public void removed() {
        // Keep the icons the saved wheels, this slot and the new choice use; the rest load again if shown later.
        Set<String> kept = WheelIcons.modsUsedBy(WheelConfig.get().allEntries());
        for (String icon : new String[] {selectedIcon, chosenIcon}) {
            if (icon != null && WheelIcons.modId(icon) != null) kept.add(WheelIcons.modId(icon));
        }
        ModIcons.releaseExcept(kept);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
