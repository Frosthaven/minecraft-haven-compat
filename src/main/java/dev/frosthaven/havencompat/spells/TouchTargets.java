package dev.frosthaven.havencompat.spells;

import dev.frosthaven.havencompat.Log;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ars Nouveau aims a spell book's Touch (and the start of other forms) with its own ray on the server
 * ({@code AbstractCaster.castSpell} -> {@code SpellUtil.rayTrace}), against the server's positions. The client clicked
 * what it drew, a little behind the server: a small mob that keeps moving (a familiar hopping around its owner) is
 * missed most clicks on a real server, and the spell lands on the floor ("Name only works if I spam it"). Now the
 * client names the entity under its crosshair ({@link TouchTargetMessage}, sent before the use packet), and the
 * server's ray returns that entity when it is still in reach (reach + 1.5 blocks slack, same level, alive), as vanilla
 * trusts the client for right-clicks on mobs. A hint is used once, within 2 ticks; otherwise Ars' own ray decides.
 */
public final class TouchTargets {
    private static final Log LOG = new Log("[spell aim] ");
    private static final int MAX_AGE_TICKS = 2;
    private static final double SLACK = 1.5;
    private static final Map<UUID, long[]> HINTS = new ConcurrentHashMap<>();   // player -> {entity id, server tick}

    private TouchTargets() {
    }

    static void hint(ServerPlayer player, int entityId) {
        HINTS.put(player.getUUID(), new long[] {entityId, player.server.getTickCount()});
    }

    /** The hinted entity as the ray's hit, or null for Ars' own ray. Server only. */
    @Nullable
    public static HitResult hit(Entity caster, double reach) {
        if (!(caster instanceof ServerPlayer player)) return null;
        long[] hint = HINTS.remove(player.getUUID());
        if (hint == null || player.server.getTickCount() - hint[1] > MAX_AGE_TICKS) return null;
        Entity target = player.serverLevel().getEntity((int) hint[0]);
        if (target == null || !target.isAlive() || target == player || target.isSpectator()) return null;
        Vec3 eye = player.getEyePosition();
        double limit = reach + SLACK;
        if (target.getBoundingBox().distanceToSqr(eye) > limit * limit) return null;
        LOG.once("used", "spell book right-clicks hit the mob the player clicked");
        return new EntityHitResult(target, target.getBoundingBox().getCenter());
    }
}
