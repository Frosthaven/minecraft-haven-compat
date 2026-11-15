package dev.frosthaven.havencompat.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.RepositoryTile;
import dev.frosthaven.havencompat.repository.RepositoryNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A placed or removed Repository reaches its Catalog also through storages with the Repository Upgrade. */
@Mixin(value = RepositoryTile.class, remap = false)
public abstract class RepositoryTileMixin {
    @Inject(method = "invalidateNetwork()V", at = @At("HEAD"), cancellable = true)
    private void haven_compat$changed(CallbackInfo ci) {
        RepositoryTile self = (RepositoryTile) (Object) this;
        RepositoryNetwork.changed(self.getLevel(), self.getBlockPos());
        ci.cancel();
    }
}
