package dev.frosthaven.havencompat.spells;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of {@link TouchTargets}: name the entity under the crosshair before a right-click with an Ars caster. */
public final class TouchTargetClient {
    private TouchTargetClient() {
    }

    public static void beforeUse(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || !(mc.hitResult instanceof EntityHitResult hit) || !connection.hasChannel(TouchTargetMessage.TYPE)) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !"ars_nouveau".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) return;
        PacketDistributor.sendToServer(new TouchTargetMessage(hit.getEntity().getId()));
    }
}
