package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/**
 * Simple Magnets' demagnetization coils (data under {@code data}): the range per axis, the range preview and the
 * filter (ghost stacks); a quarter turn swaps the X and Z ranges. Coils hold no items.
 */
final class MagnetCoilData implements BlockData {
    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        out.put("data", full.getCompound("data").copy());
    }

    @Override
    public void turn(CompoundTag full, Turn turn) {
        CompoundTag data = full.getCompound("data");
        if (turn.quarter() && data.contains("rangeX") && data.contains("rangeZ")) {
            int x = data.getInt("rangeX");
            data.putInt("rangeX", data.getInt("rangeZ"));
            data.putInt("rangeZ", x);
        }
    }
}
