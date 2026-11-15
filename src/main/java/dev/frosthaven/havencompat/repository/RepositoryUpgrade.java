package dev.frosthaven.havencompat.repository;

import com.hollingsworth.arsnouveau.api.item.inv.IMapInventory;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import dev.frosthaven.havencompat.HavenCompat;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;

/**
 * The Repository Upgrade: a Sophisticated Storage upgrade that makes a chest, barrel, limited barrel or shulker box an
 * Ars Nouveau Repository. Ars Nouveau's Repository has no logic of its own that others use; its Repository Catalog
 * joins the repositories it touches into one inventory, and knows them by block class and by the
 * {@code MAP_INV_CAP} capability. So a storage with this upgrade answers that capability ({@link StorageMapInventory})
 * and the Catalog's network search takes it ({@link RepositoryNetwork}, two mixins). Wixies, Starbuncles and the Storage
 * Lectern talk to the Catalog, so they need nothing.
 * <p>
 * One inventory is one repository: the two halves of a double chest and the endpoints of one linked storage group join
 * the network once ({@link #key}).
 */
public final class RepositoryUpgrade {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenCompat.ID);
    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);
    /** One per storage; it has no levels. */
    private static final IUpgradeCountLimitConfig ONE = new IUpgradeCountLimitConfig() {
        @Override
        public int getMaxUpgradesPerStorage(String storageType, @Nullable ResourceLocation upgrade) {
            return 1;
        }

        @Override
        public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup group) {
            return Integer.MAX_VALUE;
        }
    };
    public static final DeferredItem<Item> ITEM = ITEMS.register("repository_upgrade", Item::new);

    private RepositoryUpgrade() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(RepositoryUpgrade::capabilities);
        modBus.addListener(RepositoryUpgrade::creativeTab);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<? extends StorageBlockEntity> type : List.of(ModBlocks.CHEST_BLOCK_ENTITY_TYPE.get(),
                ModBlocks.BARREL_BLOCK_ENTITY_TYPE.get(), ModBlocks.LIMITED_BARREL_BLOCK_ENTITY_TYPE.get(),
                ModBlocks.SHULKER_BOX_BLOCK_ENTITY_TYPE.get())) {
            event.registerBlockEntity(CapabilityRegistry.MAP_INV_CAP, type, (storage, side) -> mapInventory(storage));
        }
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModItems.CREATIVE_TAB.get()) event.accept(ITEM.get());
    }

    /** The repository view of a storage: only the block that holds the inventory answers, and only with the upgrade in. */
    @Nullable
    private static IMapInventory mapInventory(StorageBlockEntity storage) {
        return owner(storage) == storage && upgraded(storage) ? new StorageMapInventory(storage) : null;
    }

    /** The storage block entity at a position when its inventory has the upgrade, else null. */
    @Nullable
    public static StorageBlockEntity upgradedStorage(Level level, BlockPos pos) {
        BlockEntity found = level.getBlockEntity(pos);
        return found instanceof StorageBlockEntity storage && upgraded(storage) ? storage : null;
    }

    /** The upgrade is in the inventory this block shows (the main half's, for a double chest). */
    public static boolean upgraded(StorageBlockEntity storage) {
        StorageBlockEntity owner = owner(storage);
        return owner != null && owner.getStorageWrapper().getUpgradeHandler().hasUpgrade(TYPE);
    }

    /** The block entity that holds the inventory: the main half of a double chest, else the block itself. */
    @Nullable
    public static StorageBlockEntity owner(StorageBlockEntity storage) {
        return storage instanceof ChestBlockEntity chest ? chest.getMainChestBlockEntity() : storage;
    }

    /** What makes two blocks the same inventory: the linked storage group, else the holder's storage wrapper. */
    public static Object key(StorageBlockEntity owner) {
        var linked = owner.getLinkedStorageEndpointData();
        return linked != null ? linked.groupId() : owner.getStorageWrapper();
    }

    /** The other half of a double chest, else null. Sophisticated Storage's own rule ({@code runOnTheOtherPart}). */
    @Nullable
    public static BlockPos otherHalf(StorageBlockEntity storage) {
        if (!(storage instanceof ChestBlockEntity chest) || chest.getBlockState().getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        Direction facing = chest.getBlockState().getValue(ChestBlock.FACING);
        return chest.getBlockPos().relative(chest.isMainChest() ? facing.getCounterClockWise() : facing.getClockWise());
    }

    public static final class Item extends UpgradeItemBase<Wrapper> {
        public Item() {
            super(ONE);
        }

        @Override
        public UpgradeType<Wrapper> getType() {
            return TYPE;
        }

        @Override
        public List<IUpgradeItem.UpgradeConflictDefinition> getUpgradeConflicts() {
            return List.of();
        }
    }

    /** No settings and no state: the upgrade's presence is the whole effect. */
    public static final class Wrapper extends UpgradeWrapperBase<Wrapper, Item> {
        Wrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(storageWrapper, upgrade, upgradeSaveHandler);
        }

        @Override
        public boolean canBeDisabled() {
            return false;
        }

        @Override
        public boolean hideSettingsTab() {
            return true;
        }
    }
}
