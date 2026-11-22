package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.journeymap.JourneyMapReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * JourneyMap's minimap at a dimension change keeps its frame. Its reset rebuilds the display with "force", which closes
 * the frame's textures and scales them all again (stb resize on the render thread, 0.1 to 0.3 s at a portal crossing,
 * 2026-11-22). With the method's own "preview" flag the rebuild takes the frame it has; the frame cannot differ between
 * two dimensions of one server session. See {@link JourneyMapReset#keepTheme}.
 */
@Pseudo
@Mixin(targets = "journeymap.client.ui.minimap.MiniMap", remap = false)
public abstract class JourneyMapMiniMapMixin {
    @ModifyVariable(method = "updateDisplayVars(Ljourneymap/client/ui/minimap/Shape;FFLjourneymap/client/ui/minimap/Position;ZZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private boolean haven_compat$keepFrame(boolean preview) {
        return preview || JourneyMapReset.keepTheme();
    }
}
