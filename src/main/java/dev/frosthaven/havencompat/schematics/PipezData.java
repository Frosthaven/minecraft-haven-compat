package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pipez 1.2.x pipes: per side (index {@link Direction#ordinal()}) whether it extracts ({@code ExtractingSides}) and
 * whether it is cut off ({@code DisconnectedSides}), and the side's upgrade ({@code Upgrades}, a stack with {@code Slot}
 * = side). The upgrade item itself holds that side's filters, filter mode, redstone mode and distribution (data
 * components {@code pipez:item}, {@code fluid}, {@code energy}, {@code gas}); a filter's {@code Destination} is an
 * absolute position and side. Breaking a pipe drops its upgrades, so the Schematicannon charges one upgrade item per
 * upgraded side (any upgrade of that tier counts: its settings are replaced by the schematic's). Infinity upgrades have
 * no recipe and are left out.
 */
final class PipezData implements BlockData {
    static final String[] TYPES = {"pipez:item_pipe", "pipez:fluid_pipe", "pipez:energy_pipe", "pipez:gas_pipe", "pipez:universal_pipe"};
    private static final String UPGRADES = "Upgrades";

    @Override
    public void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries) {
        Nbt.copy(full, out, "ExtractingSides", "DisconnectedSides");
        ListTag kept = new ListTag();
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            if (obtainable.test(ItemStack.parseOptional(registries, (CompoundTag) tag))) {
                kept.add(tag.copy());
            }
        }
        out.put(UPGRADES, kept);
    }

    @Override
    public List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            items.add(ItemStack.parseOptional(registries, (CompoundTag) tag));
        }
        return items;
    }

    @Override
    public void turn(CompoundTag full, Turn turn) {
        Nbt.turnByteList(full, "ExtractingSides", turn);
        Nbt.turnByteList(full, "DisconnectedSides", turn);
        for (Tag tag : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            CompoundTag slot = (CompoundTag) tag;
            int side = slot.getInt("Slot");
            if (side >= 0 && side < 6) {
                slot.putInt("Slot", turn.apply(Direction.values()[side]).ordinal());
            }
        }
    }

    @Override
    public void positions(CompoundTag full, PosMap map) {
        for (Tag upgrade : full.getList(UPGRADES, Tag.TAG_COMPOUND)) {
            CompoundTag components = ((CompoundTag) upgrade).getCompound("components");
            for (String type : new String[]{"pipez:item", "pipez:fluid", "pipez:energy", "pipez:gas"}) {
                for (Tag filter : components.getCompound(type).getList("Filters", Tag.TAG_COMPOUND)) {
                    CompoundTag f = (CompoundTag) filter;
                    if (!f.contains("Destination", Tag.TAG_COMPOUND)) {
                        continue;
                    }
                    CompoundTag destination = f.getCompound("Destination");
                    Nbt.mapIntArrayPos(destination, "position", map);
                    Direction side = Direction.byName(destination.getString("direction"));
                    if (!destination.contains("position") || side == null) {
                        f.remove("Destination");   // the filter then applies to every destination, as one without a target
                    } else {
                        destination.putString("direction", map.side(side).getSerializedName());
                    }
                }
            }
        }
    }
}
