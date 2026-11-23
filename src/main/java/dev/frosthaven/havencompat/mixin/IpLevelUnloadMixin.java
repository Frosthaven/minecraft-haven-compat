package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.leaks.LevelUnloads;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immersive Portals drops its client levels with no unload event; see {@link LevelUnloads}. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.ClientWorldLoader", remap = false)
public abstract class IpLevelUnloadMixin {
    @Shadow private static Map<ResourceKey<Level>, ClientLevel> CLIENT_WORLD_MAP;

    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void haven_compat$unloadAll(CallbackInfo ci) {
        LevelUnloads.dropping(CLIENT_WORLD_MAP);
    }

    @Inject(method = "disposeDimensionDynamically", at = @At("HEAD"))
    private static void haven_compat$unloadOne(ResourceKey<Level> dimension, CallbackInfo ci) {
        LevelUnloads.dropping(java.util.List.of(CLIENT_WORLD_MAP.get(dimension)));
    }
}
