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
 * so the set lives and dies with it. Only lookups in play count (render thread, a level, no loading overlay): during a
 * reload, packs that make their files in a reload listener (Moonlight's dynamic packs, KubeJS's) are still filling up,
 * and a file asked for too soon would stay missing for the session.
 */
@Mixin(FallbackResourceManager.class)
public abstract class MissingResourceMixin {
    @Shadow @Final private PackType type;
    @Unique private final Set<ResourceLocation> haven_compat$missing = ConcurrentHashMap.newKeySet();

    @Unique
    private boolean haven_compat$inPlay() {
        if (type != PackType.CLIENT_RESOURCES || !com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc != null && mc.level != null && mc.getOverlay() == null;
    }

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void haven_compat$knownMissing(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!haven_compat$missing.isEmpty() && haven_compat$inPlay() && haven_compat$missing.contains(location)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getResource", at = @At("RETURN"))
    private void haven_compat$rememberMissing(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (cir.getReturnValue().isEmpty() && haven_compat$missing.size() < 200_000 && haven_compat$inPlay()) haven_compat$missing.add(location);
    }
}
