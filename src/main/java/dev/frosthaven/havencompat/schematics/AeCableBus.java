package dev.frosthaven.havencompat.schematics;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StrictNbtStackRequirement;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * AE2 keeps every cable, bus, terminal and facade at one position in a single block, {@code ae2:cable_bus}, whose
 * block entity data holds the parts. Create copies block entity data into a placed schematic block only for blocks
 * marked safe (dupe protection); this one was not, so an empty cable bus was placed and AE2 removed it on its first
 * tick (the glass sound), and a Schematicannon asked for the uncraftable {@code ae2:cable_bus} item.
 * <p>Copied: each part's item id and each facade's block state, nothing else (no filters, patterns, P2P frequencies,
 * stored items: players set those again, as for any Create-placed block). Charged: one item per part, and each facade
 * as its exact item.
 */
final class AeCableBus {
    private AeCableBus() {
    }

    static void register() {
        SafeNbtWriterRegistry.REGISTRY.register(AEBlockEntities.CABLE_BUS.get(), AeCableBus::writeSafe);
        SchematicRequirementRegistries.BLOCKS.register(AEBlocks.CABLE_BUS.block(), AeCableBus::requirement);
    }

    static void writeSafe(BlockEntity be, CompoundTag data, HolderLookup.Provider registries) {
        copySafe(be.saveWithoutMetadata(registries), data);
    }

    /** Facade keys ({@code facade<Side>}, a block state) as they are; part keys (a compound with an item {@code id}) as the id only. */
    static void copySafe(CompoundTag full, CompoundTag data) {
        for (String key : full.getAllKeys()) {
            Tag value = full.get(key);
            if (key.startsWith("facade")) {
                data.put(key, value.copy());
            } else if (value instanceof CompoundTag part && part.contains("id", Tag.TAG_STRING)) {
                CompoundTag id = new CompoundTag();
                id.putString("id", part.getString("id"));
                data.put(key, id);
            }
        }
    }

    static ItemRequirement requirement(BlockState state, BlockEntity be) {
        if (!(be instanceof IPartHost host)) {
            return ItemRequirement.INVALID;
        }
        List<StackRequirement> items = new ArrayList<>();
        for (Direction side : sides()) {
            IPart part = host.getPart(side);
            if (part != null) {
                items.add(new StackRequirement(new ItemStack(part.getPartItem()), ItemUseType.CONSUME));
            }
            if (side != null && host.getFacadeContainer().getFacade(side) != null) {
                items.add(new StrictNbtStackRequirement(host.getFacadeContainer().getFacade(side).getItemStack(), ItemUseType.CONSUME));
            }
        }
        return items.isEmpty() ? ItemRequirement.INVALID : new ItemRequirement(items);
    }

    /** The six sides and {@code null}, the cable in the centre. */
    private static Direction[] sides() {
        Direction[] all = new Direction[7];
        System.arraycopy(Direction.values(), 0, all, 0, 6);
        return all;
    }
}
