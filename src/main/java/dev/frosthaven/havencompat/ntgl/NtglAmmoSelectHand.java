package dev.frosthaven.havencompat.ntgl;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * NTGL 3.2.0's ammo select key ({@code key.ntgl.ammo_select}, {@code InputHandler.selectAmmoKeyPressed} through
 * {@code KeyPressHandler}) opens the ammo wheel with whatever is in the hand: the off hand while sneaking, else the main
 * hand. While a wheel is shown NTGL's {@code MouseHandlerMixin} cancels {@code turnPlayer}, so the key held with no gun
 * froze mouse look on an empty wheel. Here the key does nothing unless that hand holds an NTGL weapon; the release
 * ({@code closeWheel}) then finds no wheel and does nothing either.
 */
public final class NtglAmmoSelectHand {
    private static final Log LOG = new Log("[ammo select hand] ");

    private NtglAmmoSelectHand() {}

    /** True when the press must be skipped: the hand NTGL picks holds no NTGL weapon. */
    public static boolean skip() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return false;
        InteractionHand hand = mc.options.keyShift.isDown() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (NtglAltHand.isWeapon(player.getItemInHand(hand).getItem())) return false;
        LOG.once("on", "NTGL's ammo select key with no gun in the {} hand: no wheel", hand == InteractionHand.OFF_HAND ? "off" : "main");
        return true;
    }
}
