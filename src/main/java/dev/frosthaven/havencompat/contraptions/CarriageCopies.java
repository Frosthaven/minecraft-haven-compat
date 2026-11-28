package dev.frosthaven.havencompat.contraptions;

import dev.frosthaven.havencompat.Log;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Server side. A train carriage that reaches another level (a portal) gets a new entity there, which Create builds from the
 * carriage's snapshot: the one saved when a copy of it was last removed or the world last saved. Block changes the live
 * carriage made since (copper that aged on it, oxidation/ContraptionAging) were missing on the far side, and that copy's
 * snapshot then carried the old blocks on (rig, 2026-11-28: aged copper came out new in the Nether). Its storage is not
 * affected: Create shares a carriage's storage between its copies (a chest a station emptied stayed empty through two
 * crossings, same run). Right before Create builds the copy, the live carriage is saved into the snapshot
 * ({@code mixin/CarriageCopyMixin}), as Create's own world save does. Logs "[carriage copies]" once.
 */
public final class CarriageCopies {
    private static final Log LOG = new Log("[carriage copies] ");

    private CarriageCopies() {}

    public static void saved(Entity live, Level to) {
        LOG.once("saved", "a carriage copy in another level is built from the live carriage (first: {} leaving {})", live.getName().getString(), live.level().dimension().location());
    }
}
