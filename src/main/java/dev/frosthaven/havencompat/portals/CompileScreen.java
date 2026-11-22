package dev.frosthaven.havencompat.portals;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The shader work of a game start, as its own screen: black, "Compiling shaders... N%" and a bar, nothing to click. It
 * stands in front of the title screen while {@link PipelinePrewarm#startup} runs, and in front of a connection that was
 * asked for before that work was done ({@code mixin/ConnectAfterCompileMixin}); when the queue is empty it opens what it
 * covered, or runs what it held back. So no player is in a world while shaders still compile. It gives up after
 * {@value #LIMIT_MS} ms, so a stuck step cannot keep anyone out.
 */
public final class CompileScreen extends Screen {
    private static final long LIMIT_MS = 180_000;
    private final Screen behind;
    private final Runnable then;
    private final long opened = System.currentTimeMillis();
    private int quiet;

    public CompileScreen(Screen behind, Runnable then) {
        super(Component.translatable("haven_compat.compiling_shaders"));
        this.behind = behind;
        this.then = then;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        quiet = WarmQueue.busy() ? 0 : quiet + 1;
        if (quiet < 3 && System.currentTimeMillis() - opened < LIMIT_MS) return;   // a few quiet ticks: a step may queue more steps
        if (then != null) then.run();
        else minecraft.setScreen(behind);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
        int percent = WarmQueue.busy() ? WarmQueue.percent() : 100;
        g.drawCenteredString(font, Component.translatable("haven_compat.compiling_shaders_percent", percent), width / 2, height / 2 - 14, 0xFFFFFF);
        int w = Math.min(240, width - 40), x = (width - w) / 2, y = height / 2 + 4, h = 8;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + 1 + (w - 2) * percent / 100, y + h - 1, 0xFFFFFFFF);
    }
}
