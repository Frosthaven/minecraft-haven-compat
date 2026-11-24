package dev.frosthaven.havencompat.mekanism;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Mekanism 10.7.19's item mode key changes the main hand's mode item, or the off hand's when the main hand holds none.
 * Haven binds it and NTGL's fire select to the same key (";"): with a gun in the main hand and a fluid tank in the off
 * hand, one press switched both. Here the off hand is used only while sneaking (as NTGL's fire select does); without
 * sneaking only the main hand, and nothing when it holds no mode item. Sneaking on the main hand keeps Mekanism's
 * reverse step, except when the off hand holds an NTGL weapon: the same press then switches that gun's fire mode (NTGL
 * reads the off hand while sneaking), and the main hand stays. The off hand, chosen by sneaking, steps forward. The radial selector (Configurator and others, key
 * held) is Mekanism's own tick code and unchanged. Mekanism is reached by reflection: IModeItem.isModeItem(Player,
 * EquipmentSlot, false) (false: radial items are not stepped by a press, as in Mekanism), PacketModeChange(slot,
 * shift), PacketUtils.sendToServer.
 */
public final class ModeHand {
    private static final Log LOG = new Log("[mekanism mode hand] ");
    private static Method isModeItem, send;
    private static Constructor<?> packet;
    private static Class<?> weapon;            // NTGL's IWeapon, null without NTGL
    private static boolean weaponLooked;
    private static boolean broken;

    private ModeHand() {}

    /** True when the press was handled here (Mekanism's own action is then skipped). */
    public static boolean press() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || broken) return false;
        try {
            if (packet == null) {
                isModeItem = Class.forName("mekanism.common.item.interfaces.IModeItem")
                        .getMethod("isModeItem", net.minecraft.world.entity.player.Player.class, EquipmentSlot.class, boolean.class);
                packet = Class.forName("mekanism.common.network.to_server.PacketModeChange").getConstructor(EquipmentSlot.class, boolean.class);
                send = Class.forName("mekanism.common.network.PacketUtils")
                        .getMethod("sendToServer", net.minecraft.network.protocol.common.custom.CustomPacketPayload.class);
            }
            boolean sneaking = player.isShiftKeyDown();
            LOG.once("on", "Mekanism's item mode key: off hand only while sneaking, else the main hand");
            EquipmentSlot slot = sneaking ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
            if (!(boolean) isModeItem.invoke(null, player, slot, false)) {
                if (!sneaking) return true;   // main hand only: nothing to change
                if (ntglWeapon(player.getOffhandItem().getItem())) return true;   // NTGL's fire select takes this press
                slot = EquipmentSlot.MAINHAND;   // sneaking with no off-hand mode item: Mekanism's reverse step on the main hand
                if (!(boolean) isModeItem.invoke(null, player, slot, false)) return true;
            }
            send.invoke(null, packet.newInstance(slot, slot == EquipmentSlot.MAINHAND && sneaking));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LOG.error("reflect", "cannot reach Mekanism's mode change; its own hand choice stays", e);
            return false;
        }
    }

    /** True for an NTGL weapon (its items implement IWeapon); false without NTGL. */
    private static boolean ntglWeapon(Object item) {
        if (!weaponLooked) {
            weaponLooked = true;
            try {
                weapon = Class.forName("com.nukateam.ntgl.common.foundation.item.interfaces.IWeapon");
            } catch (ClassNotFoundException | LinkageError e) {
                weapon = null;
            }
        }
        boolean gun = weapon != null && weapon.isInstance(item);
        if (gun) LOG.once("gun", "sneak with an NTGL gun in the off hand: the press is the gun's, the main hand stays");
        return gun;
    }
}
