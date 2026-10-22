package dev.frosthaven.havencompat.schematics;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
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
import java.util.function.UnaryOperator;

/**
 * AE2 keeps every cable, bus, terminal and facade at one position in a single block, {@code ae2:cable_bus}, whose
 * block entity data holds the parts. Create copies block entity data into a placed schematic block only for blocks
 * marked safe (dupe protection); this one was not, so an empty cable bus was placed and AE2 removed it on its first
 * tick (the glass sound), and a Schematicannon asked for the uncraftable {@code ae2:cable_bus} item.
 * <p>Copied: each part's item id and spin (the screen's turn of a terminal or monitor on a floor or ceiling) and each
 * facade's block state, nothing else (no filters, patterns, P2P frequencies, stored items: players set those again, as
 * for any Create-placed block). Charged: one item per part, and each facade as its exact item.
 * <p>Turned: a rotated or mirrored schematic turns its blocks, but a cable bus keeps its parts and facades under side
 * names ({@code north}, {@code facadeNorth}). Create's block entity transformer moves them to the turned sides (and
 * turns each spin with them); without it every attachment pointed the way it did when the schematic was saved.
 */
final class AeCableBus {
    /** A terminal's or monitor's turn on its side (AE2's {@code AbstractReportingPart}; {@link BlockOrientation} of side and spin). */
    private static final String SPIN = "spin";

    private AeCableBus() {
    }

    static void register() {
        SafeNbtWriterRegistry.REGISTRY.register(AEBlockEntities.CABLE_BUS.get(), AeCableBus::writeSafe);
        SchematicRequirementRegistries.BLOCKS.register(AEBlocks.CABLE_BUS.block(), AeCableBus::requirement);
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(AEBlockEntities.CABLE_BUS.get(), AeCableBus::transform);
    }

    /** Create's turn of a schematic (mirror, then rotation, as for block states) applied to the parts' sides. */
    static void transform(BlockEntity be, StructureTransform transform) {
        if (be.getLevel() == null) {
            return;
        }
        HolderLookup.Provider registries = be.getLevel().registryAccess();
        UnaryOperator<Direction> turn = side -> transform.rotateFacing(transform.mirrorFacing(side));
        be.loadWithComponents(turned(be.saveWithoutMetadata(registries), turn), registries);
    }

    /** {@code full} with each side's part and facade under the turned side; the centre ({@code cable}) stays. */
    static CompoundTag turned(CompoundTag full, UnaryOperator<Direction> turn) {
        CompoundTag out = full.copy();
        for (Direction side : Direction.values()) {
            out.remove(side.getSerializedName());
            out.remove(facadeKey(side));
        }
        for (Direction side : Direction.values()) {
            Direction to = turn.apply(side);
            if (full.get(side.getSerializedName()) instanceof CompoundTag part) {
                CompoundTag moved = part.copy();
                if (moved.contains(SPIN, Tag.TAG_BYTE)) {
                    Direction up = BlockOrientation.get(side, moved.getByte(SPIN)).getSide(RelativeSide.TOP);
                    moved.putByte(SPIN, (byte) AeBlockTurn.spin(to, turn.apply(up)));
                }
                out.put(to.getSerializedName(), moved);
            }
            Tag facade = full.get(facadeKey(side));
            if (facade != null) {
                out.put(facadeKey(to), facade.copy());
            }
        }
        return out;
    }

    /** AE2's facade key: {@code facade} and the side's name capitalised ({@code facadeNorth}). */
    private static String facadeKey(Direction side) {
        String name = side.getSerializedName();
        return "facade" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static void writeSafe(BlockEntity be, CompoundTag data, HolderLookup.Provider registries) {
        copySafe(be.saveWithoutMetadata(registries), data);
    }

    /** Facade keys ({@code facade<Side>}, a block state) as they are; part keys (a compound with an item {@code id}) as the id and spin only. */
    static void copySafe(CompoundTag full, CompoundTag data) {
        for (String key : full.getAllKeys()) {
            Tag value = full.get(key);
            if (key.startsWith("facade")) {
                data.put(key, value.copy());
            } else if (value instanceof CompoundTag part && part.contains("id", Tag.TAG_STRING)) {
                CompoundTag id = new CompoundTag();
                id.putString("id", part.getString("id"));
                if (part.contains(SPIN, Tag.TAG_BYTE)) {
                    id.putByte(SPIN, part.getByte(SPIN));
                }
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
