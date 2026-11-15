package dev.frosthaven.havencompat.mixin;

import com.railwayteam.railways.ModSetup;
import dev.frosthaven.havencompat.tracks.WoodTracks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Steam 'n' Rails registers the tracks of other mods' woods at the end of its own registration; the tracks of Haven
 * Backports' woods join there (the same thread and moment: its Registrate still takes entries).
 */
@Mixin(value = ModSetup.class, remap = false)
public abstract class RailwaysSetupMixin {
    @Inject(method = "register()V", at = @At("TAIL"))
    private static void haven_compat$woodTracks(CallbackInfo ci) {
        WoodTracks.register();
    }
}
