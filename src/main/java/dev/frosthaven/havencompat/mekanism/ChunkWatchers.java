package dev.frosthaven.havencompat.mekanism;

import dev.frosthaven.havencompat.Log;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Server side. Mekanism sends a block's update packet (a fluid tank's level, a machine's state) only when
 * {@code PacketUtils.hasPlayersTracking} finds a player whose vanilla chunk tracking view holds the chunk. Immersive
 * Portals replaces vanilla chunk tracking with its own and leaves those views empty, so the answer was always "no one":
 * tanks showed no fluid although they held it (seen 2026-10-18; on every system since Immersive Portals joined the pack
 * on 2026-10-16). The same question goes to {@code ChunkMap.getPlayers}, which the packet is then sent through and which
 * Immersive Portals answers from its own tracking; without Immersive Portals it gives the vanilla answer.
 */
public final class ChunkWatchers {
    /** Mekanism: {@code PacketUtils.hasPlayersTracking(ServerLevel, BlockPos)}, answered at its start. */
    public static final String MEKANISM_TARGET = "mekanism.common.network.PacketUtils";
    private static final Log LOG = new Log("[mekanism] ");

    private ChunkWatchers() {
    }

    /** Whether any player gets packets for the chunk holding pos. */
    public static boolean any(ServerLevel level, BlockPos pos) {
        LOG.once("on", "block updates go to the players Immersive Portals tracks (Mekanism's own check reads empty vanilla views)");
        return !level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false).isEmpty();
    }
}
