package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PrewarmScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The "Loading terrain..." screen says "Compiling shaders..." and waits for the shader prewarm; see {@link PrewarmScreen}. */
@Mixin(ReceivingLevelScreen.class)
public abstract class PrewarmScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void haven_compat$opened(CallbackInfo ci) {
        PrewarmScreen.opened();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void haven_compat$hold(CallbackInfo ci) {
        if (PrewarmScreen.holds()) ci.cancel();
    }

    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private Component haven_compat$text(Component loadingTerrain) {
        return PrewarmScreen.text(loadingTerrain);
    }
}
