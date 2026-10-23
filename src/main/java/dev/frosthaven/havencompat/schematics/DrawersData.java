package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storage Drawers 13: lock, shroud, quantify, priority, suspend and name; a locked drawer's item, with no count (the
 * lock's filter, not its contents); the upgrades ({@code Upgrades}, real items, charged); a framed drawer's materials
 * ({@code MatB/S/F/T}, charged). Links: a drawer's {@code Controller} and a controller's {@code RemoteNodes} (positions
 * {@code x/y/z}), a bound remote upgrade's {@code storagedrawers:controller_binding} ({@code valid, x, y, z}). A bound remote upgrade has no
 * recipe of its own: an unbound one is charged. Never copied: contents, the owner and personal key ({@code Own},
 * {@code Sec}).
 */
final class DrawersData implements BlockData {
    private static final String UPGRADES = "Upgrades", BINDING = "storagedrawers:controller_binding";
    private static final String[] MATERIALS = {"MatB", "MatS", "MatF", "MatT"};

    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        Nbt.copy(full, out, "Lock", "Shr", "Qua", "Pri", "Sus", "CustomName", "Controller", "Validate", "RemoteNodes");
        Nbt.copy(full, out, MATERIALS);
        if (full.getByte("Lock") != 0 && full.contains("Drawers")) {
            out.put("Drawers", withoutCounts(full.get("Drawers").copy()));
        }
        ListTag kept = new ListTag();
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            if (obtainable.test(charged(ItemStack.parseOptional(registries, (CompoundTag) tag)))) {
                kept.add(tag.copy());
            }
        }
        out.put(UPGRADES, kept);
    }

    @Override
    public List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            items.add(charged(ItemStack.parseOptional(registries, (CompoundTag) tag)));
        }
        for (String key : MATERIALS) {
            if (full.contains(key, Tag.TAG_COMPOUND)) {
                items.add(ItemStack.parseOptional(registries, full.getCompound(key)));
            }
        }
        return items;
    }

    @Override
    public void positions(CompoundTag full, PosMap map) {
        if (full.contains("Controller", Tag.TAG_COMPOUND) && !Nbt.mapXyz(full.getCompound("Controller"), map)) {
            full.remove("Controller");
        }
        ListTag nodes = full.getList("RemoteNodes", Tag.TAG_COMPOUND);
        nodes.removeIf(node -> !Nbt.mapXyz((CompoundTag) node, map));
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            CompoundTag components = ((CompoundTag) tag).getCompound("components");
            if (components.contains(BINDING, Tag.TAG_COMPOUND) && !Nbt.mapXyz(components.getCompound(BINDING), map)) {
                components.remove(BINDING);   // the drawer then unbinds the upgrade, as for a controller that is gone
            }
        }
    }

    /** What the Schematicannon takes for an upgrade: a bound remote upgrade is paid with an unbound one. */
    private static ItemStack charged(ItemStack upgrade) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(upgrade.getItem());
        if (id.getNamespace().equals("storagedrawers") && id.getPath().endsWith("_bound")) {
            ResourceLocation unbound = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - "_bound".length()));
            if (BuiltInRegistries.ITEM.containsKey(unbound)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(unbound), upgrade.getCount());
            }
        }
        return upgrade;
    }

    /** Standard drawers: a list of {@code {Item, Count}}; compacting drawers: {@code {Count, Items}}. */
    private static Tag withoutCounts(Tag drawers) {
        if (drawers instanceof ListTag list) {
            for (Tag slot : list) {
                if (slot instanceof CompoundTag c && c.contains("Count")) {
                    c.putInt("Count", 0);
                }
            }
        } else if (drawers instanceof CompoundTag c && c.contains("Count")) {
            c.putInt("Count", 0);
        }
        return drawers;
    }
}
