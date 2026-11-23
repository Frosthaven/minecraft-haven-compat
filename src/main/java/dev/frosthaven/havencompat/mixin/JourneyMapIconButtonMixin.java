package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havencompat.journeymap.WaypointIcons;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A waypoint's missing icon texture on the waypoint editor's icon button; see {@link WaypointIcons}. */
@Pseudo
@Mixin(targets = "journeymap.client.ui.component.popupscreenbutton.imageselect.ImageSelectButton", remap = false)
public abstract class JourneyMapIconButtonMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Ljourneymap/client/texture/TextureCache;getTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/texture/DynamicTexture;"))
    private DynamicTexture haven_compat$orDefault(ResourceLocation location, Operation<DynamicTexture> original) {
        DynamicTexture texture = original.call(location);
        if (texture != null) return texture;
        WaypointIcons.missing(location);
        return original.call(WaypointIcons.DEFAULT);
    }
}
