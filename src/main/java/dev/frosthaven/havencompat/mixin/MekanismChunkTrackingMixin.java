package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.mekanism.ChunkWatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mekanism's "does a player watch this chunk?" asks the chunk map, which Immersive Portals keeps; see {@link ChunkWatchers}. */
@Mixin(targets = ChunkWatchers.MEKANISM_TARGET, remap = false)
public abstract class MekanismChunkTrackingMixin {
    @Inject(method = "hasPlayersTracking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void haven_compat$tracking(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ChunkWatchers.any(level, pos));
    }
}
