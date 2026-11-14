package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.contraptions.ContraptionGround;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A contraption rider's on-ground flag right before it goes to other players; see {@link ContraptionGround}. */
@Mixin(ServerEntity.class)
public abstract class ServerEntityGroundMixin {
    @Shadow @Final private Entity entity;

    @Unique private boolean havencompat$stood;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void havencompat$stand(CallbackInfo ci) {
        havencompat$stood = entity instanceof ServerPlayer player && ContraptionGround.standsForOthers(player);
        if (havencompat$stood) entity.setOnGround(true);
    }

    @Inject(method = "sendChanges", at = @At("RETURN"))
    private void havencompat$restore(CallbackInfo ci) {
        if (havencompat$stood) entity.setOnGround(false);
    }
}
