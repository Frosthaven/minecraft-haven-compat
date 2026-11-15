package dev.frosthaven.havencompat.repository;

import com.hollingsworth.arsnouveau.api.item.inv.IMapInventory;
import com.hollingsworth.arsnouveau.common.block.RepositoryBlock;
import com.hollingsworth.arsnouveau.common.block.tile.RepositoryCatalogTile;
import com.hollingsworth.arsnouveau.common.block.tile.RepositoryCatalogTile.ConnectedRepository;
import com.hollingsworth.arsnouveau.common.block.tile.RepositoryTile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;

/**
 * The Repository Catalog's network with upgraded storages in it. Ars Nouveau's two searches (the Catalog's
 * {@code buildRepositoryNetwork} and the Repository's {@code invalidateNetwork}) pass only through its own Repository
 * block; these two do the same work and also pass through a storage with the Repository Upgrade.
 */
public final class RepositoryNetwork {
    /** The Catalog's side of the mixin. */
    public interface Catalog {
        /** The Catalog searches its network again at its next tick. */
        void haven_compat$searchAgain();

        /** Capability watches on blocks that are in the network without an entry of their own; held here so they live. */
        List<Object> haven_compat$watches();
    }

    private RepositoryNetwork() {}

    /**
     * The Catalog's search, in place of its own: every Repository touching the Catalog or another member, and every
     * upgraded storage. A storage inventory enters once, at the block that holds it; its other blocks (the second half
     * of a double chest, another endpoint of the same linked group) pass the search on and are watched for changes.
     */
    public static void build(RepositoryCatalogTile catalog, Set<BlockPos> visited, BlockPos start) {
        if (!(catalog.getLevel() instanceof ServerLevel level)) return;
        Catalog self = (Catalog) catalog;
        self.haven_compat$watches().clear();
        Set<Object> inventories = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        visited.add(start);
        open.add(start);
        while (!open.isEmpty()) {
            BlockPos from = open.poll();
            for (Direction direction : Direction.values()) {
                BlockPos pos = from.relative(direction);
                if (!visited.add(pos)) continue;
                if (level.getBlockState(pos).getBlock() instanceof RepositoryBlock) {
                    catalog.connectedRepositories.add(entry(catalog, level, pos, direction));
                    open.add(pos);
                    continue;
                }
                StorageBlockEntity storage = RepositoryUpgrade.upgradedStorage(level, pos);
                if (storage == null) continue;
                StorageBlockEntity owner = RepositoryUpgrade.owner(storage);
                if (owner != null && inventories.add(RepositoryUpgrade.key(owner))) {
                    catalog.connectedRepositories.add(entry(catalog, level, owner.getBlockPos(), null));
                }
                if (owner != storage) self.haven_compat$watches().add(watch(catalog, level, Capabilities.ItemHandler.BLOCK, pos, null));
                open.add(pos);
            }
        }
    }

    private static ConnectedRepository entry(RepositoryCatalogTile catalog, ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockCapabilityCache<IMapInventory, Direction> map = watch(catalog, level, CapabilityRegistry.MAP_INV_CAP, pos, side);
        BlockCapabilityCache<IItemHandler, Direction> items = watch(catalog, level, Capabilities.ItemHandler.BLOCK, pos, side);
        return new ConnectedRepository(pos, map, items);
    }

    /** As the Catalog's own caches: a change of the capability at the block makes the Catalog search again. */
    private static <T> BlockCapabilityCache<T, Direction> watch(RepositoryCatalogTile catalog, ServerLevel level,
            BlockCapability<T, Direction> capability, BlockPos pos, @Nullable Direction side) {
        return BlockCapabilityCache.create(capability, level, pos, side, () -> !catalog.isRemoved(), ((Catalog) catalog)::haven_compat$searchAgain);
    }

    /**
     * A member was placed, removed or changed at this position: every Catalog reached from it through Repositories and
     * upgraded storages searches again. The start block passes the search on whatever it is now (an upgrade just taken
     * out still has to tell its Catalog).
     */
    public static void changed(@Nullable Level level, BlockPos start) {
        if (level == null || level.isClientSide) return;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        visited.add(start);
        open.add(start);
        while (!open.isEmpty()) {
            BlockPos from = open.poll();
            for (Direction direction : Direction.values()) {
                BlockPos pos = from.relative(direction);
                if (!visited.add(pos) || !level.isLoaded(pos)) continue;
                BlockEntity neighbor = level.getBlockEntity(pos);
                if (neighbor instanceof RepositoryCatalogTile catalog) {
                    ((Catalog) catalog).haven_compat$searchAgain();
                } else if (neighbor instanceof RepositoryTile || neighbor instanceof StorageBlockEntity storage && RepositoryUpgrade.upgraded(storage)) {
                    open.add(pos);
                }
            }
        }
    }
}
