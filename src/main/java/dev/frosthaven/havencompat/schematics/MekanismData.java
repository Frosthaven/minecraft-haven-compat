package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.Log;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Mekanism 10.7 and Mekanism Generators.
 * <ul>
 *   <li>Transmitters (transporters, pipes, tubes, cables, conductors): each side's connection mode ({@code connection},
 *       int[6] by absolute side), redstone sensitivity ({@code redstone}), a transporter's colour ({@code color}) and a
 *       diversion transporter's per-side mode ({@code mode}, absolute). In-transit contents are never copied.</li>
 *   <li>Machines: the name, the sustained data Mekanism keeps on a configuration card ({@code writeSustainedData}:
 *       redstone control and each machine's own settings, such as sorter and miner filters), the side setup
 *       ({@code component_config}, sides relative to the facing: a mirror swaps left and right) and ejector
 *       ({@code component_ejector}), and the installed upgrades ({@code component_upgrade} {@code upgrades}:
 *       {@code {type, amount}}), charged as upgrade items. Never copied: contents (items, tanks, energy, heat), the
 *       owner and security, frequencies (a teleporter or QIO network of whoever saved the schematic), chunk loading.</li>
 * </ul>
 */
final class MekanismData implements BlockData {
    private static final Log LOG = new Log("[schematics] ");
    private static final String UPGRADE = "component_upgrade", CONFIG = "component_config", EJECTOR = "component_ejector";
    /** {@code RelativeSide} order: front, left, right, back, top, bottom. */
    private static final int LEFT = 1, RIGHT = 2;

    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        if (full.get("connection") instanceof IntArrayTag) {
            Nbt.copy(full, out, "connection", "redstone", "color", "mode");
            return;
        }
        Nbt.copy(full, out, "CustomName", CONFIG, EJECTOR);
        out.merge(sustained(be, registries));
        ListTag kept = new ListTag();
        for (Tag tag : full.getCompound(UPGRADE).getList("upgrades", Tag.TAG_COMPOUND)) {
            if (obtainable.test(upgradeItem((CompoundTag) tag))) {
                kept.add(tag.copy());
            }
        }
        if (!kept.isEmpty()) {
            CompoundTag upgrades = new CompoundTag();
            upgrades.put("upgrades", kept);
            out.put(UPGRADE, upgrades);
        }
    }

    @Override
    public List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag tag : full.getCompound(UPGRADE).getList("upgrades", Tag.TAG_COMPOUND)) {
            items.add(upgradeItem((CompoundTag) tag));
        }
        return items;
    }

    @Override
    public void turn(CompoundTag full, Turn turn) {
        Nbt.turnIntArray(full, "connection", turn);
        Nbt.turnIntArray(full, "mode", turn);
        if (turn.mirror() != Mirror.NONE) {
            CompoundTag config = full.getCompound(CONFIG);
            for (String key : config.getAllKeys()) {
                if (key.startsWith("config")) {
                    swapLeftRight(config, key);
                }
            }
            swapLeftRight(full.getCompound(EJECTOR), "input_color");
        }
    }

    private static void swapLeftRight(CompoundTag tag, String key) {
        if (tag.get(key) instanceof IntArrayTag array && array.size() == 6) {
            int[] sides = array.getAsIntArray();
            int left = sides[LEFT];
            sides[LEFT] = sides[RIGHT];
            sides[RIGHT] = left;
            tag.putIntArray(key, sides);
        }
    }

    /** {@code {type: Upgrade ordinal, amount}} as that many upgrade items ({@code mekanism:upgrade_speed} ...). */
    private static ItemStack upgradeItem(CompoundTag upgrade) {
        try {
            Object[] types = Class.forName("mekanism.api.Upgrade").getEnumConstants();
            int type = upgrade.getInt("type");
            if (type < 0 || type >= types.length) {
                return ItemStack.EMPTY;
            }
            String id = "mekanism:upgrade_" + ((Enum<?>) types[type]).name().toLowerCase(Locale.ROOT);
            return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), Math.max(1, upgrade.getInt("amount")));
        } catch (ClassNotFoundException e) {
            return ItemStack.EMPTY;
        }
    }

    /** What Mekanism's configuration card copies of this machine, without frequencies ({@code TileEntityMekanism.writeSustainedData}). */
    private static CompoundTag sustained(BlockEntity be, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        try {
            Method write = be.getClass().getMethod("writeSustainedData", HolderLookup.Provider.class, CompoundTag.class);
            write.invoke(be, registries, data);
        } catch (NoSuchMethodException ignored) {
            // not a TileEntityMekanism (a bounding block): nothing to copy
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("mekanism:" + SchematicCopy.id(be), "Mekanism settings of " + SchematicCopy.id(be) + " not copied", e);
        }
        return data;
    }
}
