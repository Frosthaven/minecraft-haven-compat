package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * AE2 19.2 and ExtendedAE settings, by key (a part's data under its side in the cable bus, or an AE2 block's data):
 * the config manager's keys (one string per setting), priority, the ghost filter slots ({@code config},
 * ExtendedAE's {@code config2}), a level emitter's amount, a monitor's item and lock, terminals' view, the pattern
 * encoding terminal's mode and ghost slots, ExtendedAE's mod / tag expressions, thresholds and output sides, and the
 * upgrade cards ({@code upgrades}, real items, charged). Never copied: contents (cells, patterns, stored items, fuel,
 * energy), the grid node and its owner ({@code gn}, {@code proxy}), crafting links, P2P and wireless frequencies (a copy
 * would join the original's tunnel), annihilation planes' enchantments (the cannon charges a plain plane).
 */
final class AeSettings implements BlockData {
    static final Set<String> KEYS = Set.of(
            // config manager
            "fuzzy_mode", "redstone_controlled", "craft_only", "scheduling_mode", "access", "filter_on_extract",
            "storage_filter", "place_block", "redstone_emitter", "craft_via_redstone", "sort_by", "view_mode",
            "sort_direction", "show_pattern_providers", "blocking_mode", "lock_crafting_mode", "pattern_access_terminal",
            "fullness_mode", "operation_mode", "auto_export", "inscriber_input_capacity", "inscriber_separate_sides",
            "copy_mode", "condenser_output", "overlay_mode", "crafting_scheduling_mode",
            // other settings
            "priority", "config", "enabledKeyTypes", "reportingValue", "isLocked", "configuredItem", "customName",
            "paintedColor", "mode", "substitute", "substituteFluids", "stonecuttingRecipeId", "encodedInputs",
            "encodedOutputs",
            // ExtendedAE
            "config2", "cmod", "modid", "oreExp", "oreExp2", "upperValue", "lowerValue", "stacksize", "output_side");
    private static final String UPGRADES = "upgrades";

    /** The settings and the obtainable upgrade cards of {@code from} into {@code to}. */
    static void copySettings(CompoundTag from, CompoundTag to, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        for (String key : KEYS) {
            if (from.contains(key)) {
                to.put(key, from.get(key).copy());
            }
        }
        ListTag kept = new ListTag();
        for (Tag tag : from.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            if (obtainable.test(ItemStack.parseOptional(registries, (CompoundTag) tag))) {
                kept.add(tag.copy());
            }
        }
        if (!kept.isEmpty()) {
            to.put(UPGRADES, kept);
        }
    }

    static List<ItemStack> upgrades(CompoundTag from, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag tag : from.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            items.add(ItemStack.parseOptional(registries, (CompoundTag) tag));
        }
        return items;
    }

    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        copySettings(full, out, obtainable, registries);
    }

    @Override
    public List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        return upgrades(full, registries);
    }

    /** ExtendedAE's circuit cutter and crystal assembler: {@code output_side}, a list of absolute side names. */
    @Override
    public void turn(CompoundTag full, Turn turn) {
        ListTag sides = full.getList("output_side", Tag.TAG_STRING);
        for (int i = 0; i < sides.size(); i++) {
            Direction side = Direction.byName(sides.getString(i));
            if (side != null) {
                sides.set(i, StringTag.valueOf(turn.apply(side).getSerializedName()));
            }
        }
    }
}
