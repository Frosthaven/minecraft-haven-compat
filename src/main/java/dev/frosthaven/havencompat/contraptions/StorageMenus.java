package dev.frosthaven.havencompat.contraptions;

import com.simibubi.create.content.contraptions.Contraption;
import dev.frosthaven.havencompat.Log;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server side. A chest, barrel or vault opened on a Create contraption (a train's storage) closes when its carriage is not
 * the one it was opened on any more, or the player is in another level. Create checks only that the contraption's entity
 * is alive and within 8 blocks; a train keeps its contraption through a portal or an unload and gives it a new carriage
 * entity, so the menu stayed open on storage the server no longer keeps in step: Shane opened his train's chest, let the
 * train go through portals and unload, and took coal the chest no longer held (2026-11-27). Only these menus: the
 * player's inventory, backpacks and other screens are left alone. Logged once as "[contraptions] ... closes".
 */
public final class StorageMenus {
    private static final Log LOG = new Log("[contraptions] ");
    private static final Map<Contraption, Entity> OPENED_ON = new WeakHashMap<>();

    private StorageMenus() {}

    public static void opened(Contraption c) {
        if (c.entity != null) OPENED_ON.put(c, c.entity);
    }

    /** the same carriage entity it was opened on, alive, in the player's level */
    public static boolean same(ServerPlayer p, Contraption c) {
        Entity at = OPENED_ON.get(c);
        boolean ok = at != null && at == c.entity && at.isAlive() && at.level() == p.level();
        if (!ok) LOG.once("closed", "a contraption storage menu closes when its carriage is replaced (portal, unload) or the player changes level");
        return ok;
    }
}
