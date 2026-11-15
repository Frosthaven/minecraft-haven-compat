package dev.frosthaven.havencompat.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.RepositoryCatalogTile;
import dev.frosthaven.havencompat.repository.RepositoryNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Repository Catalog's network search also takes storages with the Repository Upgrade; see {@link RepositoryNetwork}. */
@Mixin(value = RepositoryCatalogTile.class, remap = false)
public abstract class RepositoryCatalogMixin implements RepositoryNetwork.Catalog {
    @Shadow boolean invalidateNextTick;
    @Unique private final List<Object> haven_compat$watches = new ArrayList<>();

    @Inject(method = "buildRepositoryNetwork", at = @At("HEAD"), cancellable = true)
    private void haven_compat$build(Set<BlockPos> visited, BlockPos nextPos, CallbackInfo ci) {
        RepositoryNetwork.build((RepositoryCatalogTile) (Object) this, visited, nextPos);
        ci.cancel();
    }

    @Override
    public void haven_compat$searchAgain() {
        invalidateNextTick = true;
    }

    @Override
    public List<Object> haven_compat$watches() {
        return haven_compat$watches;
    }
}
