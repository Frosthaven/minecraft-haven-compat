package dev.frosthaven.havencompat.binder;

import dev.frosthaven.havencompat.binder.CardView.Sort;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * The sort choice. Closed it is a normal widget. The open list draws in the screen's post render and takes its own
 * clicks through {@link #listClicked}, because slots and items draw above a screen's widgets.
 */
final class SortDropdown extends AbstractWidget {
    private static final int ROW = 13;
    private static final int LIST_Z = 500;
    private final Consumer<Sort> onChoice;
    private Sort sort;
    private boolean open;

    SortDropdown(int x, int y, int width, int height, Sort sort, Consumer<Sort> onChoice) {
        super(x, y, width, height, Component.empty());
        this.sort = sort;
        this.onChoice = onChoice;
    }

    boolean isOpen() { return open; }

    @Override
    public void onClick(double mouseX, double mouseY) { open = !open; }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF000000);
        g.drawString(font, Component.translatable(sort.label), getX() + 4, getY() + (height - 8) / 2, 0xFFFFFFFF);
        g.drawString(font, open ? "▲" : "▼", getX() + width - 10, getY() + (height - 8) / 2, 0xFFA0A0A0);
    }

    /** Draws the open list over everything else. */
    void renderList(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) return;
        Font font = Minecraft.getInstance().font;
        Sort[] all = Sort.values();
        int top = getY() + height;
        g.pose().pushPose();
        g.pose().translate(0, 0, LIST_Z);
        g.fill(getX(), top, getX() + width, top + all.length * ROW + 2, 0xFFA0A0A0);
        g.fill(getX() + 1, top + 1, getX() + width - 1, top + all.length * ROW + 1, 0xFF000000);
        for (int i = 0; i < all.length; i++) {
            int y = top + 1 + i * ROW;
            if (rowAt(mouseX, mouseY) == i) g.fill(getX() + 1, y, getX() + width - 1, y + ROW, 0xFF404040);
            g.drawString(font, Component.translatable(all[i].label), getX() + 4, y + 3, all[i] == sort ? 0xFFFFFF55 : 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    /** An open list takes every click: a row chooses, and any click closes it. Returns true when the click is taken. */
    boolean listClicked(double mouseX, double mouseY) {
        if (!open || isMouseOver(mouseX, mouseY)) return false;
        int row = rowAt(mouseX, mouseY);
        open = false;
        if (row >= 0) {
            sort = Sort.values()[row];
            onChoice.accept(sort);
        }
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        int top = getY() + height + 1;
        if (mouseX < getX() || mouseX >= getX() + width || mouseY < top) return -1;
        int row = (int) (mouseY - top) / ROW;
        return row < Sort.values().length ? row : -1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable(sort.label));
    }
}
