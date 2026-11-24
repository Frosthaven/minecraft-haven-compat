package dev.frosthaven.havencompat.ntgl;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * NTGL 3.2.0's alt key ({@code key.ntgl.alt}) acts on the main-hand gun, or, when the main hand holds no weapon, on an
 * off-hand gun ({@code InputHandler.handleWeaponKeys}: {@code handleInput(data, OFF_HAND, KEY_ALT_ATTACK)}). Haven
 * binds it to G with Create's item menus, so G with a Create item in the main hand and a gun in the off hand did both.
 * Here the alt key acts on the main hand only: the off-hand call for it is skipped. Other off-hand keys (use, the
 * additional attack) are unchanged.
 */
public final class NtglAltHand {
    private static final Log LOG = new Log("[ntgl alt hand] ");
    private static final String ALT = "key.ntgl.alt";
    private static Class<?> weapon;
    private static boolean looked;

    private NtglAltHand() {}

    /** True when this call is the alt key on the off hand while the main hand holds no NTGL weapon: skip it. */
    public static boolean skip(InteractionHand hand, KeyMapping key) {
        if (hand != InteractionHand.OFF_HAND || key == null || !ALT.equals(key.getName())) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || isWeapon(player.getMainHandItem().getItem())) return false;
        LOG.once("on", "NTGL's alt key with no gun in the main hand: the off-hand gun is left alone");
        return true;
    }

    /** True when item is an NTGL weapon ({@code IWeapon}, by class name). */
    static boolean isWeapon(Object item) {
        if (!looked) {
            looked = true;
            try {
                weapon = Class.forName("com.nukateam.ntgl.common.foundation.item.interfaces.IWeapon");
            } catch (ClassNotFoundException | LinkageError e) {
                LOG.error("reflect", "cannot find NTGL's IWeapon; the alt key's off-hand gate treats no item as a gun", e);
            }
        }
        return weapon != null && weapon.isInstance(item);
    }
}
