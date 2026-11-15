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
    private static boolean warmed, started, held;
    private static long openedAt;

    private PrewarmScreen() {}

    /** A loading screen opens. */
    public static void opened() {
        openedAt = System.currentTimeMillis();
        held = !warmed && PipelinePrewarm.expected();
    }

    /** Iris destroys its pipelines: the next ones need a round again. */
    public static void destroyed() {
        warmed = false;
        started = false;
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

    /** The loading screen's line of text. */
    public static Component text(Component loadingTerrain) {
        return held ? TEXT : loadingTerrain;
    }
}
