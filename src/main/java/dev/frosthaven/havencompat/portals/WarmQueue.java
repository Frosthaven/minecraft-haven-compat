package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The shader prewarm as a list of steps, one per frame. A step is one build the driver cannot split (an Iris pipeline,
 * the Colorwheel programs of one engine for one pipeline). Between steps the game draws a frame, so the loading screen
 * stays live and shows how far the work is ({@link #percent}); in play (a portal to a new dimension loads) the same
 * steps cost one frame each, not one long stall.
 * <p>
 * A step returns false to run again on the next frame (it waits for something). A step that throws ends the round:
 * {@link PipelinePrewarm} logs it and the game goes on as before.
 */
public final class WarmQueue {
    private static final Log LOG = new Log("[shader warm] ");
    private static final ArrayDeque<BooleanSupplier> STEPS = new ArrayDeque<>();
    private static int total, done, shown, reserved;

    private WarmQueue() {}

    public static void register(IEventBus bus) {
        bus.addListener((RenderFrameEvent.Post e) -> step());
    }

    public static void add(BooleanSupplier step) {
        STEPS.add(step);
        if (reserved > 0) reserved--; else total++;
    }

    /** Steps that a later step will add: counted now, so the percent does not stall near the end and jump back. */
    public static void reserve(int steps) {
        total += steps;
        reserved += steps;
    }

    public static void add(Runnable step) {
        add(() -> { step.run(); return true; });
    }

    public static boolean busy() {
        return !STEPS.isEmpty();
    }

    /** Iris destroyed its pipelines, or a step failed: what is left is void. */
    public static void clear() {
        STEPS.clear();
        total = done = shown = reserved = 0;
    }

    /** How far the round is, 0 to 100; it never goes back while a round lasts. */
    public static int percent() {
        if (total > 0) shown = Math.max(shown, done * 100 / total);
        return shown;
    }

    private static void step() {
        BooleanSupplier next = STEPS.peek();
        if (next == null) {
            if (total != 0) total = done = shown = reserved = 0;
            return;
        }
        try {
            if (!next.getAsBoolean()) return;
        } catch (RuntimeException | LinkageError e) {
            clear();
            LOG.error("step", "a shader warm-up step failed; the rest is built at first use as before", e);
            PrewarmScreen.done();
            return;
        }
        if (STEPS.peek() == next) STEPS.poll();   // a failed step may have cleared the queue
        done++;
    }
}
