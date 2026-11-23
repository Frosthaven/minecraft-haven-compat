package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import dev.frosthaven.havencompat.journeymap.WaypointIcons;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A waypoint's missing icon texture in lists, on the map and in the world; see {@link WaypointIcons}. */
@Pseudo
@Mixin(targets = "journeymap.client.texture.TextureCache", remap = false)
public abstract class JourneyMapIconMixin {
    @WrapOperation(method = "getWaypointIcon", at = @At(value = "INVOKE",
            target = "Ljourneymap/client/texture/TextureCache;resolveImage(Lnet/minecraft/resources/ResourceLocation;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private static NativeImage haven_compat$orDefault(ResourceLocation location, Operation<NativeImage> original) {
        NativeImage image = original.call(location);
        if (image != null) return image;
        WaypointIcons.missing(location);
        return original.call(WaypointIcons.DEFAULT);
    }
}
