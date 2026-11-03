package dev.frosthaven.havencompat.binder;

import dev.frosthaven.havencompat.HavenCompat;
import dev.frosthaven.havencompat.Log;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the new real order of the open binder's cards, for the menu state the client saw. No client classes
 * here, a dedicated server loads it.
 */
public record BinderSortMessage(int containerId, int stateId, List<Integer> order) implements CustomPacketPayload {
    private static final Log LOG = new Log("[card binder] ");
    static final Type<BinderSortMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "card_binder_sort"));
    private static final StreamCodec<ByteBuf, BinderSortMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BinderSortMessage::containerId,
            ByteBufCodecs.VAR_INT, BinderSortMessage::stateId,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1024)), BinderSortMessage::order,
            BinderSortMessage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("1").optional().playToServer(TYPE, CODEC, BinderSortMessage::apply));
    }

    private static void apply(BinderSortMessage message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != message.containerId() || !isBinderMenu(menu)) return;
        // A click the client had not seen the answer to: its order is for old slots. Its reply changes the client's state, then it asks again.
        if (menu.getStateId() != message.stateId()) return;
        List<Slot> cards = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (BinderTargets.SLOT_CLASS.equals(slot.getClass().getName())) cards.add(slot);
        }
        if (!CardView.rearrangesAll(message.order(), cards.size())) {
            LOG.warnOnce("order", "refused a binder order that is not a rearrangement of its {} slots", cards.size());
            return;
        }
        List<ItemStack> stacks = new ArrayList<>(cards.size());
        for (Slot slot : cards) stacks.add(slot.getItem());
        for (int i = 0; i < cards.size(); i++) cards.get(i).set(stacks.get(message.order().get(i)));
        menu.broadcastFullState();   // one packet with every slot and a new state: the client never sees a half-sorted binder
    }

    private static boolean isBinderMenu(AbstractContainerMenu menu) {
        for (Class<?> c = menu.getClass(); c != null; c = c.getSuperclass()) {
            if (BinderTargets.MENU_CLASS.equals(c.getName())) return true;
        }
        return false;
    }
}
