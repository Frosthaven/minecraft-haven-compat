package dev.frosthaven.havencompat.mixin;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A client resource that no pack has is remembered as missing. To answer "is there such a file", the game asks every pack
 * of the namespace, and with the pack's 400 mods that is slow; mods ask for optional files in play (Entity Texture
 * Features looks for blink and variant textures the first time each mob texture is drawn: a frame of 0.25 s when a
 * train passed the first mobs after a portal, 2026-11-22). A resource manager is made anew at every resource reload,
 * and its packs do not change in between, so the set lives and dies with it.
 */
@Mixin(FallbackResourceManager.class)
public abstract class MissingResourceMixin {
    @Shadow @Final private PackType type;
    @Unique private final Set<ResourceLocation> haven_compat$missing = ConcurrentHashMap.newKeySet();

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void haven_compat$knownMissing(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (type == PackType.CLIENT_RESOURCES && haven_compat$missing.contains(location)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getResource", at = @At("RETURN"))
    private void haven_compat$rememberMissing(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (type == PackType.CLIENT_RESOURCES && cir.getReturnValue().isEmpty() && haven_compat$missing.size() < 200_000) haven_compat$missing.add(location);
    }
}
