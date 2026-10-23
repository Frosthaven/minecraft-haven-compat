package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.Log;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Refined Storage 2: what its own configuration card copies ({@code ConfigurationCardTarget.writeConfiguration}: filters,
 * modes, priorities, a detector's amount, a relay's pass settings), the redstone mode ({@code rm}), the name, the disk
 * interface's {@code fim} (its card misses it) and the upgrades ({@code upgr}, slots {@code i0}...), charged as items.
 * Never copied: stored resources, disks, patterns, tasks, the placer ({@code pbpid}), network ids.
 */
final class RsData implements BlockData {
    private static final Log LOG = new Log("[schematics] ");
    private static final String UPGRADES = "upgr";

    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        Nbt.copy(full, out, "CustomName", "rm", "fim");
        out.merge(configuration(be, registries));
        CompoundTag kept = new CompoundTag();
        CompoundTag upgrades = full.getCompound(UPGRADES);
        for (String slot : upgrades.getAllKeys()) {
            if (upgrades.get(slot) instanceof CompoundTag stack && obtainable.test(ItemStack.parseOptional(registries, stack))) {
                kept.put(slot, stack.copy());
            }
        }
        if (!kept.isEmpty()) {
            out.put(UPGRADES, kept);
        }
    }

    @Override
    public List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        CompoundTag upgrades = full.getCompound(UPGRADES);
        for (String slot : upgrades.getAllKeys()) {
            if (upgrades.get(slot) instanceof CompoundTag stack) {
                items.add(ItemStack.parseOptional(registries, stack));
            }
        }
        return items;
    }

    private static CompoundTag configuration(BlockEntity be, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        try {
            Method write = be.getClass().getMethod("writeConfiguration", CompoundTag.class, HolderLookup.Provider.class);
            write.invoke(be, data, registries);
        } catch (NoSuchMethodException ignored) {
            // no configuration card target: nothing beyond the name and redstone mode
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("rs:" + SchematicCopy.id(be), "Refined Storage settings of " + SchematicCopy.id(be) + " not copied", e);
        }
        data.remove(UPGRADES);
        return data;
    }
}
