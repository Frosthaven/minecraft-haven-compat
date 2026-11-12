package dev.frosthaven.havencompat.spells;

import dev.frosthaven.havencompat.HavenCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, just before a right-click with an Ars Nouveau spell caster: the entity under the client's
 * crosshair; see {@link TouchTargets}. No client classes here, a dedicated server loads it.
 */
public record TouchTargetMessage(int entityId) implements CustomPacketPayload {
    public static final Type<TouchTargetMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "touch_target"));
    private static final StreamCodec<ByteBuf, TouchTargetMessage> CODEC =
            ByteBufCodecs.VAR_INT.map(TouchTargetMessage::new, TouchTargetMessage::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("1").optional().playToServer(TYPE, CODEC, TouchTargetMessage::apply));
    }

    private static void apply(TouchTargetMessage message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) TouchTargets.hint(player, message.entityId());
    }
}
