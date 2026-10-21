package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Log;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NTGL (NukaTeam's Gun Lib) 3.2.0 creates its {@code NetworkProjectileManager} only in the server's reload event, so on
 * a client that joins a dedicated server it is null, and the projectile list the server sends on join ends in a
 * NullPointerException ("Error executing task on Client" in every client log). Nothing in NTGL reads that list on the
 * client ({@code getConfig} has no callers), so the handler is skipped while the manager is missing; with an
 * integrated server (single player) the manager exists and the handler runs as before.
 */
@Mixin(targets = "com.nukateam.ntgl.client.handlers.ClientPlayHandler", remap = false)
public abstract class NtglProjectileSyncMixin {
    @Unique
    private static final Log haven_compat$LOG = new Log("[ntgl] ");

    @Inject(method = "handleUpdateProjectile", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$skipWithoutManager(CallbackInfo ci) {
        try {
            Class<?> manager = Class.forName("com.nukateam.ntgl.modules.datapack.managers.NetworkProjectileManager");
            if (manager.getMethod("get").invoke(null) == null) {
                haven_compat$LOG.once("skip", "projectile list from the server skipped: no client-side manager (NTGL never reads it there)");
                ci.cancel();
            }
        } catch (ReflectiveOperationException e) {
            haven_compat$LOG.error("lookup", "NTGL projectile manager not found; its handler runs unchanged", e);
        }
    }
}
