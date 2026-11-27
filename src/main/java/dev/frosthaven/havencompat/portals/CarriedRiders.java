package dev.frosthaven.havencompat.portals;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side. A train passenger whose seat reaches an Immersive Portals portal before the carriage has a copy on the
 * far side is brought through as soon as that copy exists.
 * <p>
 * Create hands seated players from one carriage copy to the other in two steps
 * ({@code Carriage$DimensionalCarriageEntity}): the copy whose side the seat left calls {@code dismountPlayer(..., true)},
 * which takes the player off, files the seat under "PlayerPassenger" in the carriage's serialised passengers and
 * teleports the player to every other copy that exists (Immersive Portals: its seamless rider teleport); the far copy's
 * {@code updatePassengerLoadout} seats a filed player only once that player is in its own dimension. A seat ahead of
 * the driver's crosses the plane before the far copy exists: no copy, no teleport, the player is left standing at the
 * portal on the old side and is never seated (gooplife on 2026-11-24; reproduced in the traintest rig with a second
 * player on the front seat: "P2 PASSENGER LOST"). The driver's seat crosses later, when the copy exists.
 * <p>
 * Here: a player left that way (still on the old side, not riding) is remembered {@value #KEEP_MS} ms. When a copy in
 * another dimension runs its passenger loadout and has that player filed, and the player has not walked away (within
 * {@value #NEAR} blocks of the spot), the player is moved to the seat in that copy with Immersive Portals' own rider
 * teleport (vanilla's when that fails); Create's loadout then seats them in the same call.
 * <p>
 * Also (0.45.0): a player Immersive Portals did move across but nobody seated again. Its rider teleport re-seats only
 * when the far copy takes the seat in that same call; Shane, on a rear seat of a scheduled two-carriage train with a
 * drekki conductor, was put down at the Overworld side of the -835 portal and stayed there (live, 2026-11-26 20:19:41:
 * "[IPTrainRider] moved ... (left carriage 104098)", then no seat). Every filed player is remembered with its train,
 * carriage and seat; from {@value #GRACE_MS} ms after the hand-over (Create and Immersive Portals go first) a player
 * still not riding is seated on that seat of the carriage's copy in the player's own dimension, when that copy exists,
 * the seat is free and the player stands within {@value #REACH} blocks of it. Log: "seated again".
 * <p>
 * Mob riders (a conductor, a drekki, a chicken): Create saves one into the carriage's serialised passengers when its seat
 * leaves the near copy's side and spawns it on the far copy at that copy's next loadout, so the carriage came out of
 * the portal empty and its conductor showed up 0.3 to 1.5 s later (traintest courier-conductor-4: "4334{}" then
 * "4334{Conductor,}" for a player watching on the Overworld side). Here the far copies run their loadout right after a
 * copy filed a mob, as Immersive Portals' rider teleport does for players.
 */
public final class CarriedRiders {
    private static final Log LOG = new Log("[carried riders] ");
    static final long KEEP_MS = 5_000;
    static final double NEAR = 4;
    private record Left(ResourceKey<Level> dim, Vec3 at, long when) {}
    private static final Map<UUID, Left> LEFT = new HashMap<>();
    static final long GRACE_MS = 100;
    static final double REACH = 16;   // the train runs on meanwhile: up to 1.4 blocks a tick
    /** a filed player's seat: the train, the carriage's index in it, the seat's index in the carriage */
    private record Filed(UUID train, int carriage, int seat, long when) {}
    private static final Map<UUID, Filed> FILED = new HashMap<>();
    private static Field serialised;
    private static Method ipTeleport;
    private static boolean ipMissing;

    private CarriedRiders() {}

    /** From the mixin, at the end of {@code dismountPlayer} of a carriage copy. */
    public static void dismounted(Carriage.DimensionalCarriageEntity copy, ServerLevel from, ServerPlayer p, Integer seat, boolean capture) {
        if (!capture) return;
        Carriage c = carriageOf(copy);
        if (c != null && c.train != null && seat != null) {
            int index = c.train.carriages.indexOf(c);
            if (index >= 0) FILED.put(p.getUUID(), new Filed(c.train.id, index, seat, System.currentTimeMillis()));
        }
        if (p.getVehicle() != null || p.level() != from) return;
        LEFT.put(p.getUUID(), new Left(from.dimension(), p.position(), System.currentTimeMillis()));
        LOG.info("{} left a seat at the portal in {} with no carriage copy on the far side yet: kept for the copy", p.getName().getString(), from.dimension().location());
    }

    /** From the mixin, at the start of {@code updatePassengerLoadout} of a carriage copy. */
    public static void bring(Carriage.DimensionalCarriageEntity copy) {
        if (LEFT.isEmpty()) return;
        long now = System.currentTimeMillis();
        LEFT.values().removeIf(l -> now - l.when > KEEP_MS);
        CarriageContraptionEntity cce = copy.entity.get();
        if (cce == null || !(cce.level() instanceof ServerLevel level)) return;
        Map<Integer, CompoundTag> filed = filed(cce.getCarriage());
        if (filed == null || filed.isEmpty()) return;
        List<BlockPos> seats = cce.getContraption().getSeats();
        for (var entry : filed.entrySet()) {
            CompoundTag tag = entry.getValue();
            if (!tag.contains("PlayerPassenger")) continue;
            UUID id = tag.getUUID("PlayerPassenger");
            Left left = LEFT.get(id);
            if (left == null || left.dim.equals(level.dimension())) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.isPassenger() || !p.level().dimension().equals(left.dim) || p.position().distanceTo(left.at) > NEAR) { LEFT.remove(id); continue; }
            int seat = entry.getKey();
            if (seat < 0 || seat >= seats.size()) { LEFT.remove(id); continue; }
            Vec3 to = cce.toGlobalVector(Vec3.atBottomCenterOf(seats.get(seat)), 1f);
            LEFT.remove(id);
            boolean seamless = teleport(p, cce, level, to);
            LOG.info("brought {} through to seat {} of the carriage copy in {} ({})", p.getName().getString(), seat, level.dimension().location(), seamless ? "Immersive Portals' rider teleport" : "vanilla teleport");
        }
    }

    private static boolean handing;

    /** From the mixin, at the end of {@code updatePassengerLoadout}: mobs it filed go to the other copies now. */
    public static void handOver(Carriage.DimensionalCarriageEntity copy) {
        if (handing) return;
        Carriage c = carriageOf(copy);
        if (c == null) return;
        Map<Integer, CompoundTag> filed = filed(c);
        if (filed == null || filed.values().stream().allMatch(t -> t.contains("PlayerPassenger"))) return;
        handing = true;
        try {
            c.forEachPresentEntity(other -> {
                if (other == copy.entity.get() || !(other.level() instanceof ServerLevel)) return;
                Carriage.DimensionalCarriageEntity there = c.getDimensionalIfPresent(other.level().dimension());
                if (there != null && there != copy) there.updatePassengerLoadout();
            });
        } finally { handing = false; }
    }

    /** Server tick: a filed player still not riding after the grace time is seated on its carriage's copy here. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (FILED.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = FILED.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Filed f = e.getValue();
            if (now - f.when > KEEP_MS) { it.remove(); continue; }
            if (now - f.when < GRACE_MS) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || p.isSpectator()) { it.remove(); continue; }
            if (p.getVehicle() != null) { it.remove(); continue; }   // Create or Immersive Portals seated it
            var train = com.simibubi.create.Create.RAILWAYS.trains.get(f.train);
            if (train == null || f.carriage >= train.carriages.size()) { it.remove(); continue; }
            Carriage c = train.carriages.get(f.carriage);
            Carriage.DimensionalCarriageEntity here = c.getDimensionalIfPresent(p.level().dimension());
            CarriageContraptionEntity cce = here == null ? null : here.entity.get();
            if (cce == null || cce.getContraption() == null) continue;   // its copy here is not there yet
            List<BlockPos> seats = cce.getContraption().getSeats();
            if (f.seat < 0 || f.seat >= seats.size()) { it.remove(); continue; }
            if (cce.getContraption().getSeatMapping().containsValue(f.seat)) { it.remove(); continue; }   // someone else sits there
            Vec3 at = cce.toGlobalVector(Vec3.atBottomCenterOf(seats.get(f.seat)), 1f);
            if (p.position().distanceTo(at) > REACH) continue;
            Map<Integer, CompoundTag> filed = filed(c);
            if (filed != null) {   // Create's own record of it would seat it a second time
                CompoundTag t = filed.get(f.seat);
                if (t != null && t.contains("PlayerPassenger") && t.getUUID("PlayerPassenger").equals(p.getUUID())) filed.remove(f.seat);
            }
            cce.addSittingPassenger(p, f.seat);
            it.remove();
            LOG.info("{} seated again on seat {} of carriage {} in {} ({} ms after the hand-over left it standing)", p.getName().getString(), f.seat,
                    f.carriage, p.level().dimension().location(), now - f.when);
        }
    }

    private static Field outer;

    /** the carriage of a copy: its entity's, else the inner class's outer instance */
    private static Carriage carriageOf(Carriage.DimensionalCarriageEntity copy) {
        CarriageContraptionEntity cce = copy.entity.get();
        if (cce != null && cce.getCarriage() != null) return cce.getCarriage();
        try {
            if (outer == null) { outer = Carriage.DimensionalCarriageEntity.class.getDeclaredField("this$0"); outer.setAccessible(true); }
            return (Carriage) outer.get(copy);
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, CompoundTag> filed(Carriage c) {
        try {
            if (serialised == null) { serialised = Carriage.class.getDeclaredField("serialisedPassengers"); serialised.setAccessible(true); }
            return (Map<Integer, CompoundTag>) serialised.get(c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.info("Create's serialised passengers are not readable ({}): left-behind riders stay", e.toString());
            LEFT.clear();
            return null;
        }
    }

    /** Immersive Portals' {@code IPCreateRiderTeleport.teleport} (the one Create's own hand-over uses here), else vanilla. */
    private static boolean teleport(ServerPlayer p, Entity carriage, ServerLevel to, Vec3 at) {
        if (!ipMissing) {
            try {
                if (ipTeleport == null) ipTeleport = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateRiderTeleport")
                        .getMethod("teleport", ServerPlayer.class, Entity.class, ServerLevel.class, double.class, double.class, double.class);
                if (Boolean.TRUE.equals(ipTeleport.invoke(null, p, carriage, to, at.x, at.y, at.z))) return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                ipMissing = true;
                LOG.info("Immersive Portals' rider teleport is not callable ({}): vanilla teleport", e.toString());
            }
        }
        p.teleportTo(to, at.x, at.y, at.z, p.getYRot(), p.getXRot());
        return false;
    }
}
