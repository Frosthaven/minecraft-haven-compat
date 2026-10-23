package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.HavenCompat;
import dev.frosthaven.havencompat.Log;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

/**
 * A schematic saved with the Schematic and Quill's "save to file" is read from the client's world, whose block entities
 * hold only what the server sends for display: a bus's filters, a machine's upgrades and side setup are not there. The
 * client therefore asks the server for the copied data ({@link BlockData#copy}: settings and upgrades only, never
 * contents, owners or frequencies) of the selection's blocks before it saves ({@code mixin/SchematicQuillSaveMixin},
 * {@link ServerFillClient}), and {@link #merge} puts it into the saved file. "Save and upload" saves on the server and
 * needs none of this. A server without haven-compat (no channel) gets the plain client save.
 */
public final class ServerFill {
    private static final Log LOG = new Log("[schematics] ");
    /** Selections larger than this on any axis get no server data (Create's own size limit is far below). */
    private static final int MAX_SPAN = 512;
    /** The data of one save, client side, between the server's reply and the save it fills. */
    private static Map<BlockPos, CompoundTag> filling = Map.of();

    private ServerFill() {
    }

    public record Request(BlockPos first, BlockPos second) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "schematic_data_request"));
        static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::first, BlockPos.STREAM_CODEC, Request::second, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code blocks}: a list of {@code {pos, data}}. */
    public record Reply(CompoundTag blocks) implements CustomPacketPayload {
        static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "schematic_data"));
        static final StreamCodec<ByteBuf, Reply> CODEC = ByteBufCodecs.COMPOUND_TAG.map(Reply::new, Reply::blocks);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar("1").optional();
            registrar.playToServer(Request.TYPE, Request.CODEC, ServerFill::answer);
            registrar.playToClient(Reply.TYPE, Reply.CODEC, (reply, context) -> ServerFillClient.reply(reply.blocks()));
        });
    }

    private static void answer(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ListTag blocks = new ListTag();
        BoundingBox box = BoundingBox.fromCorners(request.first(), request.second());
        boolean quill = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString().equals("create:schematic_and_quill");
        if (quill && box.getXSpan() <= MAX_SPAN && box.getYSpan() <= MAX_SPAN && box.getZSpan() <= MAX_SPAN) {
            ServerLevel level = player.serverLevel();
            HolderLookup.Provider registries = level.registryAccess();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockData data = SchematicCopy.of(be.getType());
                        if (data == null || !box.isInside(be.getBlockPos())) {
                            continue;
                        }
                        CompoundTag copied = new CompoundTag();
                        try {
                            data.copy(be, be.saveWithoutMetadata(registries), copied, Obtainable::test, registries);
                        } catch (RuntimeException e) {
                            LOG.error("fill:" + SchematicCopy.id(be), "copying " + SchematicCopy.id(be) + " for a client schematic failed", e);
                            continue;
                        }
                        CompoundTag entry = new CompoundTag();
                        entry.put("pos", NbtUtils.writeBlockPos(be.getBlockPos()));
                        entry.put("data", copied);
                        blocks.add(entry);
                    }
                }
            }
        }
        CompoundTag reply = new CompoundTag();
        reply.put("blocks", blocks);
        context.reply(new Reply(reply));
    }

    /** Client: the reply's data by position, for the save that follows ({@link #merge}); {@code null} ends it. */
    static void filling(CompoundTag reply) {
        if (reply == null) {
            filling = Map.of();
            return;
        }
        Map<BlockPos, CompoundTag> map = new HashMap<>();
        for (Tag tag : reply.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> map.put(pos, entry.getCompound("data")));
        }
        filling = map;
    }

    /** Client save: the server's copied data over the client's own data of the block at world position {@code pos}. */
    static void merge(BlockPos pos, CompoundTag nbt) {
        CompoundTag server = filling.get(pos);
        if (server != null) {
            for (String key : server.getAllKeys()) {
                nbt.put(key, server.get(key).copy());
            }
        }
    }
}
