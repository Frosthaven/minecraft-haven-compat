package dev.frosthaven.havencompat.repository;

import com.hollingsworth.arsnouveau.api.item.inv.FilterSet;
import com.hollingsworth.arsnouveau.api.item.inv.IMapInventory;
import com.hollingsworth.arsnouveau.common.items.ItemScroll.SortPref;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;

/**
 * A Sophisticated storage as Ars Nouveau's repository inventory, with the rules of {@code RepositoryTile}: item frames
 * and scrolls on the block filter and rank what goes in, an item the storage already holds ranks it higher. The
 * storage's own handler does the insert and extract, so stack upgrades, filters and locked slots hold.
 */
final class StorageMapInventory implements IMapInventory {
    /** Ars Nouveau's repository reads its item frames at load and on block changes; frames are entities, so here: by age. */
    private static final int FILTER_TICKS = 20;
    private final StorageBlockEntity storage;
    private FilterSet filters;
    private long filtersAt;

    StorageMapInventory(StorageBlockEntity storage) {
        this.storage = storage;
    }

    private ITrackedContentsItemHandler handler() {
        return storage.getStorageWrapper().getInventoryForInputOutput();
    }

    /** The filters on the block; on both halves of a double chest. */
    private FilterSet filters() {
        Level level = storage.getLevel();
        if (level == null) return new FilterSet.ListSet();
        long now = level.getGameTime();
        if (filters == null || now - filtersAt >= FILTER_TICKS || now < filtersAt) {
            FilterSet own = FilterSet.forPosition(level, storage.getBlockPos());
            BlockPos half = RepositoryUpgrade.otherHalf(storage);
            filters = half == null ? own : new FilterSet.Composite().withFilter(own).withFilter(FilterSet.forPosition(level, half));
            filtersAt = now;
        }
        return filters;
    }

    @Override
    public ItemStack insertStack(ItemStack stack, boolean simulate) {
        return filters().getHighestPreference(stack) == SortPref.INVALID ? stack : handler().insertItem(stack, simulate);
    }

    /** Ars Nouveau asks with an empty stack for "has a free slot". */
    @Override
    public boolean hasExistingSlotsForInsertion(ItemStack stack) {
        return stack.isEmpty() ? handler().hasEmptySlots()
                : storage.getStorageWrapper().getInventoryHandler().getSlotTracker().getItems().contains(stack.getItem());
    }

    @Override
    public ItemStack extractByItem(Item item, int count, boolean simulate, Predicate<ItemStack> filter) {
        if (!storage.getStorageWrapper().getInventoryHandler().getSlotTracker().getItems().contains(item)) return ItemStack.EMPTY;
        ITrackedContentsItemHandler handler = handler();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() == item && filter.test(stack)) {
                return simulate ? stack.copy() : handler.extractItem(slot, count, false);   // as RepositoryTile: the simulation shows the stack found
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public SortPref getInsertionPreference(ItemStack stack) {
        SortPref pref = filters().getHighestPreference(stack);
        return pref == SortPref.LOW && hasExistingSlotsForInsertion(stack) ? SortPref.HIGH : pref;
    }
}
