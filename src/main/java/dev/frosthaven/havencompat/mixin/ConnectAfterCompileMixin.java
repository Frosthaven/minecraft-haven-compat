package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.CompileScreen;
import dev.frosthaven.havencompat.portals.StartupWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A connection that is asked for while the game start's shader work is not done (a quick-play start, a fast click) waits
 * behind {@link CompileScreen} and starts when that work is over: the player is never in a world, unable to see or
 * steer, while shaders compile.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectAfterCompileMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$afterCompile(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server, boolean quickPlay,
            TransferState transfer, CallbackInfo ci) {
        if (minecraft.screen instanceof CompileScreen || !StartupWarm.pending()) return;
        minecraft.setScreen(new CompileScreen(parent, () -> ConnectScreen.startConnecting(parent, minecraft, address, server, quickPlay, transfer)));
        ci.cancel();
    }
}
