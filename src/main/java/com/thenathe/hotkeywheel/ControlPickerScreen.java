package com.thenathe.hotkeywheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.hotkeywheel.look.Ink;
import com.thenathe.hotkeywheel.look.Look;
import com.thenathe.hotkeywheel.look.LookScreen;
import com.thenathe.hotkeywheel.look.LookTextField;
import com.thenathe.hotkeywheel.look.PlateButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Chooses a slot's control from folders: Minecraft's controls, mods' settings screens, then one folder per mod
 * that registers controls (or a single Mod controls folder), and Other controls for any control no mod can be
 * credited with, and Hotkey Wheel's own actions. A search looks through every folder at once.
 */
public final class ControlPickerScreen extends LookScreen {
    public static final String MINECRAFT_FOLDER = "Minecraft controls";
    public static final String MOD_SETTINGS_FOLDER = "Mod settings";
    public static final String MOD_CONTROLS_FOLDER = "Mod controls";
    public static final String OTHER_FOLDER = "Other controls";
    public static final String ACTIONS_FOLDER = "Hotkey Wheel actions";
    private final Screen parent;
    private final Consumer<String> accepted;
    private final boolean oneModFolder;
    private final List<AbstractWidget> resultWidgets = new ArrayList<>();
    private final List<ShownIcon> shownIcons = new ArrayList<>();
    private final List<Caption> headers = new ArrayList<>();
    private List<Folder> folders = List.of();
    private Folder openFolder;
    private List<Row> filtered = List.of();
    private String query = "";
    private int page;
    private int left;
    private int rowWidth;
    private int rowsTop;
    private int rows;
    private Button previous;
    private Button next;

    // Key mappings, Tool Pouch slot toggles, which Tool Pouch binds to raw keys instead of key mappings, and
    // mod settings screens, which need no key. Mod settings carry the mod whose icon they show. The section
    // groups a folder's controls: Minecraft's categories, or each mod's name in the single Mod controls folder.
    private record Option(String control, String name, String section, String key, String iconMod, String folder) {}

    private record Folder(String name, WheelIcons.Icon icon, List<Option> options) {}

    // A page row is a folder, a section heading or a control; exactly one is set.
    private record Row(Folder folder, String header, Option option) {}

    private record ShownIcon(WheelIcons.Icon icon, int x, int y) {}

    private record Caption(String text, int y) {}

    public ControlPickerScreen(Screen parent, Consumer<String> accepted) {
        this(parent, accepted, WheelConfig.get().oneModControlsFolder());
    }

    /** {@code oneModFolder} puts every mod's controls in one Mod controls folder, as the editor's draft says. */
    public ControlPickerScreen(Screen parent, Consumer<String> accepted, boolean oneModFolder) {
        super(Component.literal("Choose a control"));
        this.parent = parent;
        this.accepted = accepted;
        this.oneModFolder = oneModFolder;
    }

    @Override
    protected void init() {
        resultWidgets.clear();
        layoutFrame(false);
        left = listX();
        rowWidth = listWidth();
        rowsTop = listTop() + 36;
        rows = Math.max(2, (listBottom() - 24 - rowsTop) / 24);
        folders = buildFolders();
        if (openFolder != null) {
            String name = openFolder.name();
            openFolder = folders.stream().filter(folder -> folder.name().equals(name)).findFirst().orElse(null);
        }
        LookTextField search = new LookTextField(left, listTop(), rowWidth, 20, Component.literal("Search controls"),
                "Search all folders: control, category, mod, or key…", true);
        search.setMaxLength(128);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            refreshResults();
        });
        addRenderableWidget(search);
        int pageY = listBottom() - 20;
        previous = addRenderableWidget(PlateButton.of("Previous", left, pageY, 84, button -> changePage(-1)));
        next = addRenderableWidget(PlateButton.of("Next", left + rowWidth - 84, pageY, 84, button -> changePage(1)));
        addLookControls();
        addRenderableWidget(PlateButton.of("Cancel", contentX + contentWidth - 70, footerY, 70, button -> onClose()));
        refreshResults();
        setInitialFocus(search);
    }

    private List<Folder> buildFolders() {
        List<Option> vanilla = new ArrayList<>();
        Map<String, List<Option>> byMod = new LinkedHashMap<>();
        List<Option> other = new ArrayList<>();
        List<KeyMapping> mappings = new ArrayList<>(List.of(minecraft.options.keyMappings));
        // Key Binds' own order: by category, then each category's order.
        mappings.sort(KeyMapping::compareTo);
        for (KeyMapping mapping : mappings) {
            if (mapping.getName().contains("hotkey_wheel")) continue;
            String owner = ControlOwners.modId(mapping).orElse(null);
            String category = mapping.getCategory().label().getString();
            String key = mapping.getTranslatedKeyMessage().getString();
            if ("minecraft".equals(owner)) {
                vanilla.add(new Option(mapping.getName(), controlName(mapping), category, key, null, MINECRAFT_FOLDER));
            } else if (owner == null) {
                other.add(new Option(mapping.getName(), controlName(mapping), category, key, null, OTHER_FOLDER));
            } else {
                byMod.computeIfAbsent(owner, id -> new ArrayList<>())
                        .add(new Option(mapping.getName(), controlName(mapping), category, key, null, modName(owner)));
            }
        }
        for (ToolPouchToggles.Choice choice : ToolPouchToggles.choices()) {
            byMod.computeIfAbsent("toolpouch", id -> new ArrayList<>())
                    .add(new Option(choice.control(), choice.name(), "Slot toggles", choice.key(), null, modName("toolpouch")));
        }
        List<Option> actions = WheelActions.choices().stream()
                .map(choice -> new Option(choice.control(), choice.name(), ACTIONS_FOLDER, choice.note(), null, ACTIONS_FOLDER)).toList();
        List<Option> settings = ModSettingsActions.choices().stream()
                .map(choice -> new Option(choice.control(), choice.name() + " settings", MOD_SETTINGS_FOLDER,
                        "no key needed", choice.modId(), MOD_SETTINGS_FOLDER)).toList();

        List<Folder> result = new ArrayList<>();
        WheelIcons.Icon folderIcon = WheelIcons.resolve(WheelIcons.GLYPH_PREFIX + "folder");
        if (!vanilla.isEmpty()) result.add(new Folder(MINECRAFT_FOLDER, folderIcon, vanilla));
        if (!settings.isEmpty()) result.add(new Folder(MOD_SETTINGS_FOLDER, WheelIcons.resolve(WheelIcons.GLYPH_PREFIX + "cog"), settings));
        if (!actions.isEmpty()) result.add(new Folder(ACTIONS_FOLDER, WheelIcons.resolve(WheelIcons.GLYPH_PREFIX + "lightning"), actions));
        List<String> mods = new ArrayList<>(byMod.keySet());
        mods.sort(Comparator.comparing((String modId) -> modName(modId).toLowerCase(Locale.ROOT)).thenComparing(modId -> modId));
        if (oneModFolder && !mods.isEmpty()) {
            List<Option> merged = new ArrayList<>();
            for (String modId : mods) {
                for (Option option : byMod.get(modId)) {
                    merged.add(new Option(option.control(), option.name(), modName(modId), option.key(), null, MOD_CONTROLS_FOLDER));
                }
            }
            result.add(new Folder(MOD_CONTROLS_FOLDER, folderIcon, merged));
        } else {
            for (String modId : mods) {
                result.add(new Folder(modName(modId), WheelIcons.resolve(WheelIcons.modIcon(modId)), byMod.get(modId)));
            }
        }
        if (!other.isEmpty()) result.add(new Folder(OTHER_FOLDER, folderIcon, other));
        return result;
    }

    private static String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(mod -> mod.getMetadata().getName()).orElse(modId);
    }

    private static String controlName(KeyMapping mapping) {
        return Component.translatable(mapping.getName()).getString();
    }

    /** Folders with no search; a folder's controls under their section headings; or every match, by name. */
    private List<Row> rowsToShow() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (!needle.isEmpty()) {
            return folders.stream().flatMap(folder -> folder.options().stream()).filter(option -> {
                String searchable = option.name() + " " + option.control() + " " + option.section() + " " + option.key() + " " + option.folder();
                return searchable.toLowerCase(Locale.ROOT).contains(needle);
            }).sorted(Comparator.comparing(option -> option.name().toLowerCase(Locale.ROOT)))
                    .map(option -> new Row(null, null, option)).toList();
        }
        if (openFolder == null) return folders.stream().map(folder -> new Row(folder, null, null)).toList();
        List<Row> result = new ArrayList<>();
        boolean sections = openFolder.options().stream().map(Option::section).distinct().count() > 1;
        String section = null;
        for (Option option : openFolder.options()) {
            if (sections && !option.section().equals(section)) {
                section = option.section();
                result.add(new Row(null, section, null));
            }
            result.add(new Row(null, null, option));
        }
        return result;
    }

    private void changePage(int amount) {
        page += amount;
        refreshResults();
    }

    private void showFolder(Folder folder) {
        openFolder = folder;
        page = 0;
        refreshResults();
    }

    /** Whether the folder list or a search is shown, rather than one folder. */
    private boolean inFolder() { return openFolder != null && query.isBlank(); }

    private void refreshResults() {
        for (AbstractWidget widget : resultWidgets) removeWidget(widget);
        resultWidgets.clear();
        shownIcons.clear();
        headers.clear();
        filtered = rowsToShow();
        // Inside a folder the first row leads back to the folder list.
        int top = rowsTop;
        int pageRows = rows;
        if (inFolder()) {
            PlateButton back = PlateButton.of("‹ All folders · " + openFolder.name(), left, top, rowWidth, button -> showFolder(null))
                    .help("Back to the list of folders. Escape also goes back.");
            resultWidgets.add(addRenderableWidget(back));
            top += 24;
            pageRows--;
        }
        int lastPage = Math.max(0, (filtered.size() - 1) / pageRows);
        page = Math.clamp(page, 0, lastPage);
        previous.active = page > 0;
        next.active = page < lastPage;
        int first = page * pageRows;
        for (int index = first; index < Math.min(filtered.size(), first + pageRows); index++) {
            Row row = filtered.get(index);
            int y = top + (index - first) * 24;
            if (row.header() != null) {
                headers.add(new Caption(row.header(), y + 6));
                continue;
            }
            if (row.folder() != null) {
                Folder folder = row.folder();
                int count = folder.options().size();
                String text = folder.name() + " · " + count + (count == 1 ? " control" : " controls") + "  ›";
                shownIcons.add(new ShownIcon(folder.icon(), left + 2, y + 2));
                resultWidgets.add(addRenderableWidget(new IconRow(left, y, rowWidth, text, button -> showFolder(folder))
                        .help("Open " + folder.name())));
                continue;
            }
            Option option = row.option();
            boolean searching = !query.isBlank();
            String text = option.name() + " · " + option.key() + (searching ? " · " + option.folder() : "");
            String help = option.name() + "\n" + option.folder() + (option.section().equals(option.folder()) ? "" : " › " + option.section())
                    + "\n" + option.control();
            if (option.iconMod() != null) shownIcons.add(new ShownIcon(WheelIcons.resolve(WheelIcons.modIcon(option.iconMod())), left + 2, y + 2));
            PlateButton button = (option.iconMod() == null ? PlateButton.of(text, left, y, rowWidth, selected -> choose(option))
                    : new IconRow(left, y, rowWidth, text, selected -> choose(option))).help(help);
            resultWidgets.add(addRenderableWidget(button));
        }
    }

    private void choose(Option option) {
        accepted.accept(option.control());
        onClose();
    }

    /** A row with an icon at its left; the label is centred in the room beside the icon so a long one stays clear of it. */
    private final class IconRow extends PlateButton {
        IconRow(int x, int y, int width, String label, OnPress onPress) {
            super(x, y, width, 20, Component.literal(label), onPress);
        }

        @Override
        protected void drawLabel(GuiGraphicsExtractor graphics, Look look, int colour) {
            String text = Ink.trim(font, getMessage().getString(), width - 48);
            graphics.text(font, text, getX() + 22 + (width - 44 - font.width(text)) / 2, getY() + 6, colour, look.plateShadow(colour));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            changePage(event.key() == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && inFolder()) {
            showFolder(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && y >= rowsTop && y < listBottom()) {
            changePage(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Look look = Look.current();
        int count = (int) filtered.stream().filter(row -> row.option() != null).count();
        String summary = !query.isBlank() ? count + " matching controls in all folders"
                : inFolder() ? openFolder.name() + " · " + count + (count == 1 ? " control" : " controls")
                : folders.size() + " folders · includes installed mods";
        caption(graphics, summary, left, listTop() + 26, rowWidth);
        int pageRows = inFolder() ? rows - 1 : rows;
        graphics.centeredText(font, "Page " + (page + 1) + "/" + Math.max(1, (filtered.size() + pageRows - 1) / pageRows),
                left + rowWidth / 2, listBottom() - 14, look.dim());
        if (filtered.isEmpty()) graphics.centeredText(font, "No matching controls", left + rowWidth / 2, inFolder() ? rowsTop + 30 : rowsTop + 6, look.dim());
        for (Caption header : headers) caption(graphics, header.text(), left + 4, header.y(), rowWidth - 4);
        for (ShownIcon shown : shownIcons) shown.icon().draw(graphics, shown.x(), shown.y());
        extractAccentPopup(graphics, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void removed() {
        ModIcons.releaseExcept(WheelIcons.modsUsedBy(WheelConfig.get().allEntries()));
    }

}
