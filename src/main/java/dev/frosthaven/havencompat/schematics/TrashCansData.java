package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/**
 * Trash Cans (SuperMartijn642, data under {@code data}): the item and liquid filters (ghost stacks), whitelist modes
 * and the energy limit. Never copied: the deleted items a player can still take back, and the drain slots' items.
 */
final class TrashCansData implements BlockData {
    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        CompoundTag data = full.getCompound("data").copy();
        data.remove("deletedItems");
        data.remove("liquidItem");
        data.remove("energyItem");
        out.put("data", data);
    }
}
