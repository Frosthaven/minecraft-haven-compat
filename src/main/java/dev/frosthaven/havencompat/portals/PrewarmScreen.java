package dev.frosthaven.havencompat.portals;

import net.minecraft.network.chat.Component;

/**
 * The "Loading terrain..." screen of a join covers the shader prewarm ({@link PipelinePrewarm}, and with Colorwheel
 * {@code colorwheel/ColorwheelWarm}): it says "Compiling shaders..." and stays open until that work is done, so the
 * seconds the driver needs are spent behind the screen, not as a frozen world. The text is set from the screen's first
 * frame, because nothing is drawn while a build runs.
 * <p>
 * The screen is held only when a prewarm is due: a shader pack is active and no round has finished since Iris last
 * destroyed its pipelines (a join does that; a later dimension change that shows the same screen does not). Limits, so
 * a missing step can never keep the player out: {@value #START_MS} ms for the round to start, and {@value #LIMIT_MS} ms
 * in all. A failure or a round without Colorwheel ends the hold at once.
 */
public final class PrewarmScreen {
    private static final long START_MS = 20_000, LIMIT_MS = 180_000;
    private static final Component TEXT = Component.translatable("haven_compat.compiling_shaders");
    private static final Component JOINING = Component.translatable("multiplayer.downloadingTerrain");
    private static boolean warmed, started, held;
    private static long openedAt;

    private PrewarmScreen() {}

    /** A loading screen opens. */
    public static void opened() {
        openedAt = System.currentTimeMillis();
        held = !warmed && PipelinePrewarm.expected();
        // The pipelines exist already (built at the title screen), or are being built there: this join starts no round
        // by itself, so the rest (Colorwheel's programs for this connection's engines) is queued here.
        if (held && !started && (PipelinePrewarm.builtAtStartup() || WarmQueue.busy())) PipelinePrewarm.joinAfterStartup();
    }

    /** The player left the world: the next join has new engines to prepare. */
    public static void left() {
        warmed = false;
        started = false;
        held = false;
    }

    /** Iris destroys its pipelines: the next ones need a round again. */
    public static void destroyed() {
        warmed = false;
        started = false;
        PipelinePrewarm.destroyed();
    }

    /** A prewarm round begins its work. */
    public static void roundStarted() {
        started = true;
    }

    /** The round, with its Colorwheel part, is over (done, not needed, or failed). */
    public static void done() {
        warmed = true;
        held = false;
    }

    /** The loading screen must stay open: the prewarm is due and not over, within the limits. */
    public static boolean holds() {
        if (!held) return false;
        long open = System.currentTimeMillis() - openedAt;
        if (open > LIMIT_MS || (!started && open > START_MS)) held = false;
        return held;
    }

    /**
     * The held screen, drawn whole: black, the line of text, and a bar of the round's steps. Each step blocks one frame
     * (the driver links shaders), so anything that moves behind the text (the panorama) would stutter; a still screen
     * with a bar does not. False when the screen is not held: vanilla draws its own.
     */
    public static boolean draw(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, int width, int height) {
        if (!held) return false;
        g.fill(0, 0, width, height, 0xFF000000);
        // Until the first shader step has run the game is doing its ordinary join work: say so, not "0%".
        // A join says "Loading terrain", as vanilla does: what is left to do here is not the player's concern.
        g.drawCenteredString(font, WarmQueue.busy() ? Component.translatable("haven_compat.loading_terrain_percent", WarmQueue.percent()) : JOINING,
                width / 2, height / 2 - 14, 0xFFFFFF);
        int w = Math.min(240, width - 40), x = (width - w) / 2, y = height / 2 + 4, h = 8;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        int done = WarmQueue.busy() ? WarmQueue.percent() : started ? 100 : 0;
        g.fill(x + 1, y + 1, x + 1 + (w - 2) * done / 100, y + h - 1, 0xFFFFFFFF);
        return true;
    }

    /** The loading screen's line of text. */
    public static Component text(Component loadingTerrain) {
        if (!held) return loadingTerrain;
        return WarmQueue.busy() ? Component.translatable("haven_compat.compiling_shaders_percent", WarmQueue.percent()) : TEXT;
    }
}
