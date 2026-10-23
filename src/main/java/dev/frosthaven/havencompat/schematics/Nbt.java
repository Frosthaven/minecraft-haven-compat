package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Small edits on saved block entity data shared by the {@link BlockData} of several mods (checked by the in-game schematic test, see CLAUDE.md). */
final class Nbt {
    private Nbt() {
    }

    static void copy(CompoundTag full, CompoundTag out, String... keys) {
        for (String key : keys) {
            if (full.contains(key)) {
                out.put(key, full.get(key).copy());
            }
        }
    }

    /** An array with one value per side, indexed by {@link Direction#ordinal()} (down, up, north, south, west, east). */
    static void turnIntArray(CompoundTag tag, String key, BlockData.Turn turn) {
        if (tag.get(key) instanceof IntArrayTag array && array.size() == 6) {
            int[] was = array.getAsIntArray(), now = new int[6];
            for (Direction side : Direction.values()) {
                now[turn.apply(side).ordinal()] = was[side.ordinal()];
            }
            tag.putIntArray(key, now);
        }
    }

    /** A list of six bytes, one per side by {@link Direction#ordinal()}. */
    static void turnByteList(CompoundTag tag, String key, BlockData.Turn turn) {
        if (tag.get(key) instanceof ListTag list && list.size() == 6 && list.getElementType() == Tag.TAG_BYTE) {
            ListTag now = new ListTag();
            for (int i = 0; i < 6; i++) {
                now.add(ByteTag.ZERO);
            }
            for (Direction side : Direction.values()) {
                now.set(turn.apply(side).ordinal(), list.get(side.ordinal()).copy());
            }
            tag.put(key, now);
        }
    }

    /** A position saved as {@code [I; x, y, z]} ({@code NbtUtils.writeBlockPos}, {@code BlockPos.CODEC}); removed when the map drops it. */
    static void mapIntArrayPos(CompoundTag tag, String key, BlockData.PosMap map) {
        if (tag.get(key) instanceof IntArrayTag array && array.size() == 3) {
            int[] p = array.getAsIntArray();
            BlockPos to = map.pos(new BlockPos(p[0], p[1], p[2]));
            if (to == null) {
                tag.remove(key);
            } else {
                tag.putIntArray(key, new int[]{to.getX(), to.getY(), to.getZ()});
            }
        }
    }

    /** A position saved as a compound with {@code x}, {@code y}, {@code z}; false when the map drops it. */
    static boolean mapXyz(CompoundTag pos, BlockData.PosMap map) {
        BlockPos to = map.pos(new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z")));
        if (to == null) {
            return false;
        }
        pos.putInt("x", to.getX());
        pos.putInt("y", to.getY());
        pos.putInt("z", to.getZ());
        return true;
    }
}
