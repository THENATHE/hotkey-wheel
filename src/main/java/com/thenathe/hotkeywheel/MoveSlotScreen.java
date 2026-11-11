package com.thenathe.hotkeywheel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Picks where a slot or folder goes: the wheel's top level or any folder in it, shown as a tree. */
public final class MoveSlotScreen extends Screen {
    private final Screen parent;
    private final WheelConfig.Wheel wheel;
    private final List<WheelConfig.Entry> currentLevel;
    private final WheelConfig.Entry moving;
    private final Mover mover;
    private final List<Destination> destinations = new ArrayList<>();
    private int page;
    private int rows;

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
        int panelWidth = Math.min(560, width - 40);
        int left = (width - panelWidth) / 2;
        rows = Math.max(1, (height - 126) / 24);
        int lastPage = Math.max(0, (destinations.size() - 1) / rows);
        page = Math.clamp(page, 0, lastPage);
        int first = page * rows;
        for (int index = first; index < Math.min(destinations.size(), first + rows); index++) {
            Destination destination = destinations.get(index);
            String indent = "  ".repeat(destination.level());
            String text = indent + (destination.level() == 0 ? destination.name() + " · top level" : "› " + destination.name())
                    + " · " + destination.entries().size() + "/" + WheelConfig.MAX_SLOTS;
            String help = destination.refusal() != null ? destination.refusal() : "Move " + slotName() + " here, after its last slot.";
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth(text, panelWidth - 12)), pressed -> choose(destination))
                    .bounds(left, 52 + (index - first) * 24, panelWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(help))).build();
            button.active = destination.refusal() == null;
            addRenderableWidget(button);
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("Previous"), button -> changePage(-1))
                .bounds(left, height - 49, 84, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> changePage(1))
                .bounds(left + panelWidth - 84, height - 49, 84, 20).build());
        next.active = page < lastPage;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left, height - 25, panelWidth, 20).build());
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
        graphics.centeredText(font, title, width / 2, 12, WheelUi.GOLD);
        graphics.centeredText(font, "Choose where " + slotName() + " goes · greyed-out levels say why in their tooltip",
                width / 2, 30, WheelUi.DIM);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
