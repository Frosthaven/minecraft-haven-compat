package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.journeymap.JourneyMapReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** JourneyMap's reset at a dimension change keeps its scaled theme textures; see {@link JourneyMapReset#keepTheme}. */
@Pseudo
@Mixin(targets = "journeymap.client.io.ThemeLoader", remap = false)
public abstract class JourneyMapThemeMixin {
    @ModifyVariable(method = "getCurrentTheme(Z)Ljourneymap/client/ui/theme/Theme;", at = @At("HEAD"), argsOnly = true)
    private static boolean haven_compat$noForcedReload(boolean forceReload) {
        return forceReload && !JourneyMapReset.keepTheme();
    }
}
