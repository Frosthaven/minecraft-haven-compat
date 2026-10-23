package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.mixin.StructureTemplateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Links between blocks (a drawer's controller, a pipe filter's destination, a Mekanism bounding block's main block)
 * are saved as absolute positions, which are wrong anywhere but where the schematic was taken. Saving
 * ({@code mixin/SchematicExportMixin}, Create's {@code SchematicExport} after {@code fillFromWorld}) stores them
 * relative to the schematic's corner and marks the data; placing ({@code mixin/StructurePlaceMixin}, vanilla's
 * {@code processBlockInfos}, which every schematic print and structure placement goes through, after it copied the
 * data) moves them the way the block positions move (mirror, rotation, pivot, offset). Unmarked data (a schematic
 * saved before haven-compat 0.19.0) has no right answer: its links are dropped.
 * <p>{@link #placing()} is true while a structure is placed, for block turns that must differ from a wrench's.
 */
public final class SavedPositions {
    static final String MARK = "haven_compat:relative_positions";
    private static final ThreadLocal<Integer> PLACING = ThreadLocal.withInitial(() -> 0);

    private SavedPositions() {
    }

    /** After a schematic's blocks were read from {@code level} at {@code origin} (the selection's lowest corner). */
    public static void saved(StructureTemplate template, BlockPos origin, boolean clientLevel) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).haven$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                CompoundTag nbt = info.nbt();
                BlockData data = nbt == null ? null : SchematicCopy.of(nbt);
                if (data == null) {
                    continue;
                }
                if (clientLevel) {
                    ServerFill.merge(origin.offset(info.pos()), nbt);
                }
                data.positions(nbt, new BlockData.PosMap() {
                    @Override public BlockPos pos(BlockPos pos) { return pos.subtract(origin); }

                    @Override public Direction side(Direction side) { return side; }
                });
                nbt.putBoolean(MARK, true);
            }
        }
    }

    /** {@code infos}: the placed blocks (world positions, copied data) of a template placed at {@code offset}. */
    public static void placed(List<StructureTemplate.StructureBlockInfo> infos, BlockPos offset, StructurePlaceSettings settings) {
        for (StructureTemplate.StructureBlockInfo info : infos) {
            CompoundTag nbt = info.nbt();
            BlockData data = nbt == null ? null : SchematicCopy.of(nbt);
            if (data == null) {
                continue;
            }
            boolean relative = nbt.getBoolean(MARK);
            nbt.remove(MARK);
            data.positions(nbt, new BlockData.PosMap() {
                @Override
                public @Nullable BlockPos pos(BlockPos pos) {
                    return relative ? StructureTemplate.calculateRelativePosition(settings, pos).offset(offset) : null;
                }

                @Override
                public Direction side(Direction side) {
                    return settings.getRotation().rotate(settings.getMirror().mirror(side));
                }
            });
        }
    }

    public static void placing(boolean start) {
        PLACING.set(PLACING.get() + (start ? 1 : -1));
    }

    public static boolean placing() {
        return PLACING.get() > 0;
    }
}
