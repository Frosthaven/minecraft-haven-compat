package dev.frosthaven.havencompat.megatorch;

import dev.frosthaven.havencompat.HavenCompat;
import dev.frosthaven.havencompat.Log;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mega Torch areas on the JourneyMap fullscreen map, server side. A player "knows" a Mega Torch once they have stood
 * inside its area (horizontally); the known torches are saved on the player per dimension (attachment, kept on death).
 * Torchmaster's own registry ({@link TorchmasterLights}) is the list of torches, so no chunk is read or loaded. Every
 * {@link #CHECK_TICKS} ticks each dimension with players is read once; a player who watches (the map is open with the
 * toggle on, {@link Watch}) gets their known torches there ({@link Areas}) when that list changed, else nothing.
 */
public final class MegatorchAreas {
    static final Log LOG = new Log("[megatorch map] ");
    private static final int CHECK_TICKS = 40;
    private static final int MAX_TORCHES = 1 << 16;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HavenCompat.ID);
    /** dimension id -> packed positions of the Mega Torches this player knows there. */
    private static final java.util.function.Supplier<AttachmentType<CompoundTag>> KNOWN = ATTACHMENTS.register("known_megatorches",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    /** Per online player: watching or not, and what was last sent (a hash, so an unchanged list is not sent again). */
    private static final Map<UUID, Watcher> WATCHERS = new HashMap<>();

    private record Watcher(long[] sent) {}

    private MegatorchAreas() {}

    /** C2S: the fullscreen map is open with the toggle on (true), or no longer (false). */
    public record Watch(boolean on) implements CustomPacketPayload {
        static final Type<Watch> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "megatorch_watch"));
        static final StreamCodec<ByteBuf, Watch> CODEC = ByteBufCodecs.BOOL.map(Watch::new, Watch::on);

        @Override public Type<Watch> type() { return TYPE; }
    }

    /** S2C: the Mega Torches the player knows in {@code dimension} (packed {@link BlockPos}), and their radius. */
    public record Areas(ResourceLocation dimension, int radius, long[] torches) implements CustomPacketPayload {
        static final Type<Areas> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "megatorch_areas"));
        private static final StreamCodec<ByteBuf, long[]> LONGS = StreamCodec.of((buf, longs) -> {
            VarInt.write(buf, longs.length);
            for (long l : longs) buf.writeLong(l);
        }, buf -> {
            int n = VarInt.read(buf);
            if (n < 0 || n > MAX_TORCHES) throw new IllegalArgumentException("megatorch list of " + n);
            long[] longs = new long[n];
            for (int i = 0; i < n; i++) longs[i] = buf.readLong();
            return longs;
        });
        static final StreamCodec<ByteBuf, Areas> CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC, Areas::dimension,
                ByteBufCodecs.VAR_INT, Areas::radius, LONGS, Areas::torches, Areas::new);

        @Override public Type<Areas> type() { return TYPE; }
    }

    /** Both sides, with Torchmaster loaded. */
    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var registrar = event.registrar("1").optional();
            registrar.playToServer(Watch.TYPE, Watch.CODEC, MegatorchAreas::watch);
            registrar.playToClient(Areas.TYPE, Areas.CODEC, (areas, context) -> context.enqueueWork(() -> MegatorchAreasClient.received(areas)));
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, MegatorchAreas::tick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> WATCHERS.remove(e.getEntity().getUUID()));
    }

    private static void watch(Watch watch, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!watch.on()) {
                WATCHERS.remove(player.getUUID());
                return;
            }
            WATCHERS.put(player.getUUID(), new Watcher(null));
            LongOpenHashSet torches = TorchmasterLights.megatorches(player.serverLevel());
            if (torches != null) check(player, torches, TorchmasterLights.radius());
        });
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS != 0) return;
        int radius = -1;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) continue;
            LongOpenHashSet torches = TorchmasterLights.megatorches(level);
            if (torches == null) return;   // Torchmaster could not be read; logged once
            if (radius < 0) radius = TorchmasterLights.radius();
            for (ServerPlayer player : level.players()) check(player, torches, radius);
        }
    }

    /** Learns the torches whose area the player stands in, forgets removed ones, and answers a watcher. */
    private static void check(ServerPlayer player, LongOpenHashSet torches, int radius) {
        String dimension = player.level().dimension().location().toString();
        CompoundTag all = player.getData(KNOWN);
        long[] before = all.getLongArray(dimension);
        LongArrayList known = new LongArrayList(before.length + 1);
        for (long t : before) if (torches.contains(t)) known.add(t);   // a broken torch is forgotten
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        var iterator = torches.iterator();
        while (iterator.hasNext()) {
            long t = iterator.nextLong();
            if (Math.abs(BlockPos.getX(t) - px) <= radius && Math.abs(BlockPos.getZ(t) - pz) <= radius && !known.contains(t)) known.add(t);
        }
        long[] now = known.toLongArray();
        if (!Arrays.equals(before, now)) {
            all.putLongArray(dimension, now);
            player.setData(KNOWN, all);
        }
        Watcher watcher = WATCHERS.get(player.getUUID());
        if (watcher == null) return;
        long[] sent = with(now, dimension.hashCode(), radius);
        if (Arrays.equals(watcher.sent(), sent)) return;
        WATCHERS.put(player.getUUID(), new Watcher(sent));
        PacketDistributor.sendToPlayer(player, new Areas(player.level().dimension().location(), radius, now));
    }

    /** The list plus what else decides the reply, to compare with the last one sent. */
    private static long[] with(long[] torches, int dimension, int radius) {
        long[] key = Arrays.copyOf(torches, torches.length + 1);
        key[torches.length] = ((long) dimension << 32) | radius;
        return key;
    }
}
