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
 */
public final class CarriedRiders {
    private static final Log LOG = new Log("[carried riders] ");
    static final long KEEP_MS = 5_000;
    static final double NEAR = 4;
    private record Left(ResourceKey<Level> dim, Vec3 at, long when) {}
    private static final Map<UUID, Left> LEFT = new HashMap<>();
    private static Field serialised;
    private static Method ipTeleport;
    private static boolean ipMissing;

    private CarriedRiders() {}

    /** From the mixin, at the end of {@code dismountPlayer}: a filed player who was not teleported anywhere. */
    public static void dismounted(ServerLevel from, ServerPlayer p, boolean capture) {
        if (!capture || p.getVehicle() != null || p.level() != from) return;
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
