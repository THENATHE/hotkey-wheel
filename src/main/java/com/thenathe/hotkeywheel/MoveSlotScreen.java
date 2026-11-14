package com.thenathe.hotkeywheel;

import com.thenathe.hotkeywheel.look.Look;
import com.thenathe.hotkeywheel.look.LookScreen;
import com.thenathe.hotkeywheel.look.PlateButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Picks where a slot or folder goes: the wheel's top level or any folder in it, shown as a tree. */
public final class MoveSlotScreen extends LookScreen {
    private final Screen parent;
    private final WheelConfig.Wheel wheel;
    private final List<WheelConfig.Entry> currentLevel;
    private final WheelConfig.Entry moving;
    private final Mover mover;
    private final List<Destination> destinations = new ArrayList<>();
    private int page;
    private int rows;
    private int subtitleTop;
    private List<FormattedCharSequence> subtitle = List.of();

    @FunctionalInterface
    public interface Mover {
        void move(WheelConfig.Entry entry, List<WheelConfig.Entry> destination, String destinationName);
    }

    /** A level slots can go to; {@code refusal} says why this one cannot take the slot, or is null. */
    public record Destination(String name, List<WheelConfig.Entry> entries, int level, String refusal) {}

    public MoveSlotScreen(Screen parent, WheelConfig.Wheel wheel, List<WheelConfig.Entry> currentLevel,
                          WheelConfig.Entry moving, Mover mover) {
        super(Component.literal("Move to…"));
        this.parent = parent;
        this.wheel = wheel;
        this.currentLevel = currentLevel;
        this.moving = moving;
        this.mover = mover;
        collect(wheel.entries, wheel.name, 0, false);
    }

    public List<Destination> destinations() { return List.copyOf(destinations); }

    private void collect(List<WheelConfig.Entry> entries, String name, int level, boolean insideMoving) {
        destinations.add(new Destination(name, entries, level, refusal(entries, level, insideMoving)));
        for (WheelConfig.Entry entry : entries) {
            if (!entry.isFolder()) continue;
            collect(entry.entries, entry.folderName(), level + 1, insideMoving || entry == moving);
        }
    }

    private String refusal(List<WheelConfig.Entry> entries, int level, boolean insideMoving) {
        if (entries == currentLevel) return "It is already here.";
        if (insideMoving) return "A folder cannot go inside itself.";
        if (entries.size() >= WheelConfig.MAX_SLOTS) return "Full: " + WheelConfig.MAX_SLOTS + " slots is the most a level holds.";
        // The moved folder's own slots, and any folders in it, sit below the level it goes to.
        if (level + height(moving) > WheelConfig.MAX_FOLDER_DEPTH) {
            return "Too deep: folders go at most " + WheelConfig.MAX_FOLDER_DEPTH + " levels below the wheel.";
        }
        return null;
    }

    /** How many levels a folder adds below the level it is in: 0 for a slot, 1 for a folder of slots. */
    static int height(WheelConfig.Entry entry) {
        if (!entry.isFolder()) return 0;
        int deepest = 0;
        for (WheelConfig.Entry child : entry.entries) deepest = Math.max(deepest, height(child));
        return 1 + deepest;
    }

    public void choose(Destination destination) {
        if (destination.refusal() != null) return;
        mover.move(moving, destination.entries(), destination.level() == 0 ? wheel.name + " (top level)" : destination.name());
        // The mover rebuilt the editor, which is the parent; returning shows the result.
        minecraft.gui.setScreen(parent);
    }

    @Override
    protected void init() {
        layoutFrame(false);
        int left = listX();
        int rowWidth = listWidth();
        // A long folder name or a large GUI scale wraps the subtitle, and the list moves down to make room.
        subtitle = font.split(Component.literal(subtitleText()), rowWidth);
        subtitleTop = listTop() + 2;
        int top = subtitleTop + subtitle.size() * 10 + 6;
        rows = Math.max(1, (listBottom() - 24 - top) / 24);
        int lastPage = Math.max(0, (destinations.size() - 1) / rows);
        page = Math.clamp(page, 0, lastPage);
        int first = page * rows;
        for (int index = first; index < Math.min(destinations.size(), first + rows); index++) {
            Destination destination = destinations.get(index);
            String indent = "  ".repeat(destination.level());
            String text = indent + (destination.level() == 0 ? destination.name() + " · top level" : "› " + destination.name())
                    + " · " + destination.entries().size() + "/" + WheelConfig.MAX_SLOTS;
            String help = destination.refusal() != null ? destination.refusal() : "Move " + slotName() + " here, after its last slot.";
            PlateButton button = PlateButton.of(text, left, top + (index - first) * 24, rowWidth, pressed -> choose(destination)).help(help);
            button.active = destination.refusal() == null;
            addRenderableWidget(button);
        }
        int pageY = listBottom() - 20;
        PlateButton previous = addRenderableWidget(PlateButton.of("Previous", left, pageY, 84, button -> changePage(-1)));
        previous.active = page > 0;
        PlateButton next = addRenderableWidget(PlateButton.of("Next", left + rowWidth - 84, pageY, 84, button -> changePage(1)));
        next.active = page < lastPage;
        addLookControls();
        addRenderableWidget(PlateButton.of("Cancel", contentX + contentWidth - 70, footerY, 70, button -> onClose()));
    }

    private String subtitleText() {
        String verb = moving.isFolder() ? " go" : " goes";
        return "Choose where " + slotName() + verb + " · greyed-out levels say why in their tooltip";
    }

    /** The subtitle's lines, as drawn; one line where it fits, as before. */
    public List<String> subtitleLines() {
        return subtitle.stream().map(line -> {
            StringBuilder text = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                text.appendCodePoint(codePoint);
                return true;
            });
            return text.toString();
        }).toList();
    }

    private String slotName() {
        return moving.isFolder() ? "the folder " + moving.folderName() + " and its slots" : "this slot";
    }

    private void changePage(int amount) {
        page += amount;
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Look look = Look.current();
        int y = subtitleTop;
        for (FormattedCharSequence line : subtitle) {
            graphics.text(font, line, listX(), y, look.dim(), look.panelShadow());
            y += 10;
        }
        extractAccentPopup(graphics, mouseX, mouseY);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

}
