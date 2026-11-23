package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A rider list (who rides a vehicle) that the client cannot use yet is kept and used a moment later, not lost. Vanilla
 * handles the list once: a vehicle it does not have gives "Received passengers for unknown entity", and a rider it does
 * not find in that level is skipped without a word. Neither is sent again.
 * <p>
 * With a Create train through an Immersive Portals portal both happen. The carriage on the far side is a new entity, and
 * the server puts the player on it the tick the player crosses; the client steps through the portal on its own frame, a
 * few milliseconds apart. When the list came first, the client looked for its own player in the far level, did not find
 * it, and the player was left sitting at the portal while the train went on; it rode again only when the train came back
 * (tests and Shane's log, 2026-11-22).
 * <p>
 * Kept: a list whose vehicle this level lacks, and a list that names the local player while the player is in another
 * level. Each client tick, a kept list whose vehicle and player are now together in the player's level is handled again.
 * A list is dropped after {@value #KEEP_MS} ms, or when a newer list for the same vehicle is handled in full.
 * <p>
 * And see {@link #reseat}: a seat that the client loses again within a moment of the server giving it.
 */
public final class LatePassengers {
    private static final Log LOG = new Log("[late passengers] ");
    private static final long KEEP_MS = 10_000;
    private record Kept(ClientboundSetPassengersPacket packet, long at) {}
    private static final Map<Integer, Kept> KEPT = new HashMap<>();
    private static boolean applying;
    /** the server's latest list that seats the local player, until a list for that vehicle without the player */
    private static Kept seat;
    private static final long RESEAT_MS = 1_500;
    private static int reseats;

    private LatePassengers() {}

    public static void register(IEventBus bus) {
        bus.addListener((ClientTickEvent.Post e) -> { if (!KEPT.isEmpty()) retry(); if (seat != null) reseat(); });
    }

    /** From the mixin, on the client thread, before vanilla handles the list in {@code level}. */
    public static void seen(ClientLevel level, ClientboundSetPassengersPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null) return;
        boolean namesPlayer = false;
        for (int id : packet.getPassengers()) if (id == mc.player.getId()) namesPlayer = true;
        boolean usable = level.getEntity(packet.getVehicle()) != null && (!namesPlayer || mc.player.level() == level);
        if (!applying) {
            if (namesPlayer) { seat = new Kept(packet, System.currentTimeMillis()); reseats = 0; }
            else if (seat != null && seat.packet.getVehicle() == packet.getVehicle()) seat = null;   // the server took the player off it
        }
        if (usable) KEPT.remove(packet.getVehicle());
        else if (!applying) KEPT.put(packet.getVehicle(), new Kept(packet, System.currentTimeMillis()));
    }

    /**
     * The second way a seat is lost at a crossing: the list seats the player, then a position packet of the same crossing
     * arrives a millisecond later and accepting it takes the player off the vehicle again. The server still has the
     * player on it. Within {@value #RESEAT_MS} ms of the list, a player who is off that vehicle is put back on.
     */
    private static void reseat() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.player == null || mc.level == null || connection == null) { seat = null; return; }
        Kept s = seat;
        if (System.currentTimeMillis() - s.at > RESEAT_MS) { seat = null; return; }
        var vehicle = mc.level.getEntity(s.packet.getVehicle());
        if (vehicle == null || mc.player.level() != mc.level || mc.player.getVehicle() == vehicle || reseats >= 3) return;
        if (mc.player.distanceToSqr(vehicle) > 16 * 16) { seat = null; return; }   // moved elsewhere (a teleport, a death), not this crossing
        reseats++;
        applying = true;
        try {
            s.packet.handle(connection);
            LOG.info("the player was taken off entity {} in {} right after the server seated them ({} ms ago); seated again", s.packet.getVehicle(),
                    mc.level.dimension().location(), System.currentTimeMillis() - s.at);
        } catch (RuntimeException e) {
            LOG.error("reseat", "could not seat the player again", e);
        } finally {
            applying = false;
        }
    }

    private static void retry() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.player == null || mc.level == null || connection == null) { KEPT.clear(); return; }
        long now = System.currentTimeMillis();
        List<Kept> ready = new ArrayList<>();
        KEPT.values().removeIf(k -> {
            if (now - k.at > KEEP_MS) return true;
            if (mc.level.getEntity(k.packet.getVehicle()) == null) return false;   // not here (yet)
            ready.add(k);
            return true;
        });
        for (Kept k : ready) {
            applying = true;
            try {
                k.packet.handle(connection);   // the player's level is the current one: no world switch needed
                LOG.info("the riders of entity {} in {} came {} ms before the client could seat them; seated now", k.packet.getVehicle(),
                        mc.level.dimension().location(), now - k.at);
            } catch (RuntimeException e) {
                LOG.error("apply", "could not apply a rider list that came early", e);
            } finally {
                applying = false;
            }
        }
    }
}
