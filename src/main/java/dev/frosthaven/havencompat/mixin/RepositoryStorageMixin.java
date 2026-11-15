package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.repository.RepositoryNetwork;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A storage tells the Repository Catalogs around it when its upgrades change (the Repository Upgrade went in or out)
 * and when it loads (placed, chunk loaded): the same two moments at which Ars Nouveau's Repository tells them.
 */
@Mixin(value = StorageBlockEntity.class, remap = false)
public abstract class RepositoryStorageMixin {
    @Inject(method = "onUpgradeCachesInvalidated", at = @At("TAIL"))
    private void haven_compat$upgradesChanged(CallbackInfo ci) {
        StorageBlockEntity self = (StorageBlockEntity) (Object) this;
        RepositoryNetwork.changed(self.getLevel(), self.getBlockPos());
    }

    @Inject(method = "onLoad", at = @At("TAIL"))
    private void haven_compat$loaded(CallbackInfo ci) {
        StorageBlockEntity self = (StorageBlockEntity) (Object) this;
        RepositoryNetwork.changed(self.getLevel(), self.getBlockPos());
    }
}
