package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Immersive Portals puts "The other side of the Portal is not yet loaded" on the action bar while the client holds the
 * player's movement at a portal whose far side has no chunks yet, and clears the action bar when it lets go. It has no
 * option for it. Both action bar calls are skipped; the movement hold itself is unchanged.
 */
@Mixin(targets = "qouteall.imm_ptl.core.collision.CollisionHelper", remap = false)
public abstract class PortalStagnateMessageMixin {
    @WrapWithCondition(method = "updateClientStagnateStatus",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private static boolean haven_compat$noMessage(Gui gui, Component message, boolean animate) {
        return false;
    }
}
