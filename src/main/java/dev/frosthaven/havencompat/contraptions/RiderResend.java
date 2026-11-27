package dev.frosthaven.havencompat.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.frosthaven.havencompat.Log;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Server side. A mob Create seats on a contraption right after loading it (a carriage coming out of a portal or into
 * loaded ground puts its riders back from their saved data) is sent to every player again 2 seconds later, seated.
 * <p>
 * Players watching a train come out of a portal saw the train and every rider but the drekki driver, whose seat stayed
 * empty until one of them sat on that carriage (Shane, 2026-11-27; traintest rig run F: the watcher's game listed the
 * drekki among the carriage's riders and did not have it in its world, the chicken on the same carriage was drawn).
 * The game dropped the rider while it sat on the part of the carriage still in the portal; sent again once the carriage
 * is out, it is added back and drawn on its seat. A game that kept it only replaces its copy. Logged as
 * "[contraptions] ... sent again".
 */
public final class RiderResend {
    private static final Log LOG = new Log("[contraptions] ");
    private static final int DELAY = 40;
    private record Due(Entity rider, int at) {}
    private static final List<Due> DUE = new ArrayList<>();

    private RiderResend() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, e -> {
            if (!e.isMounting() || e.getLevel().isClientSide || !(e.getEntityBeingMounted() instanceof AbstractContraptionEntity)) return;
            Entity r = e.getEntityMounting(), v = e.getEntityBeingMounted();
            /* only on a contraption just built (its first 5 seconds: out of a portal, into loaded ground); a mob seated
               on a standing train by a player or a whistle is sent the usual way */
            if (r instanceof Player || v.tickCount > 100 || !(r.level() instanceof ServerLevel level)) return;
            DUE.add(new Due(r, level.getServer().getTickCount() + DELAY));
            LOG.info("{} seated on a new {} (its tick {}, the rider's {}): sent again in 2 s", r.getName().getString(), v.getType().toShortString(), v.tickCount, r.tickCount);
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, e -> tick(e.getServer()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, e -> DUE.clear());
    }

    static void tick(MinecraftServer s) {
        if (DUE.isEmpty()) return;
        int now = s.getTickCount();
        for (Due d : List.copyOf(DUE)) {
            if (d.at > now) continue;
            DUE.remove(d);
            Entity r = d.rider;
            if (r.isRemoved() || !(r.getVehicle() instanceof AbstractContraptionEntity) || !(r.level() instanceof ServerLevel level)) continue;
            var cache = level.getChunkSource();
            cache.removeEntity(r);
            cache.addEntity(r);
            LOG.info("{} seated on a {} after it loaded: sent again to every player", r.getName().getString(), r.getVehicle().getType().toShortString());
        }
    }
}
