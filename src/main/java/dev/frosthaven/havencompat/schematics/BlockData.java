package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * What a Create schematic keeps of one mod's block entity ({@link SchematicCopy} registers it with Create). Create
 * copies no data of other mods' blocks (dupe protection), so they printed with factory settings.
 * <ul>
 *   <li>{@link #copy}: settings, and the real items those settings hold (upgrades), never stored contents;</li>
 *   <li>{@link #items}: those real items, charged by the Schematicannon (one each). An item no recipe makes is neither
 *       copied nor charged ({@code obtainable}): a player could never pay for it;</li>
 *   <li>{@link #turn}: data kept per absolute side, turned with a rotated or mirrored schematic;</li>
 *   <li>{@link #positions}: absolute positions (links to other blocks), saved relative to the schematic and placed
 *       relative to the print ({@link SavedPositions}).</li>
 * </ul>
 * All methods work on the block entity's saved data ({@code saveWithoutMetadata}); {@code be} is there for mods whose
 * settings are only readable through their own methods.
 */
interface BlockData {
    void copy(BlockEntity be, CompoundTag full, CompoundTag out, Predicate<ItemStack> obtainable, HolderLookup.Provider registries);

    default List<ItemStack> items(BlockEntity be, CompoundTag full, HolderLookup.Provider registries) {
        return List.of();
    }

    default void turn(CompoundTag full, Turn turn) {
    }

    default void positions(CompoundTag full, PosMap map) {
    }

    /** A schematic's turn: mirror, then rotation (as vanilla turns block states). */
    record Turn(Rotation rotation, Mirror mirror) {
        Direction apply(Direction side) {
            return rotation.rotate(mirror.mirror(side));
        }

        boolean quarter() {
            return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        }
    }

    /** Moves a saved position: to schematic-relative on save, to world on print. {@code null}: the link is dropped. */
    interface PosMap {
        @Nullable BlockPos pos(BlockPos pos);

        Direction side(Direction side);
    }
}
