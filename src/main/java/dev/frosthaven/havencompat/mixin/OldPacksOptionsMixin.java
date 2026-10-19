package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.packs.OldPacks;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Options keeps and saves an outdated resource pack like any other; see {@link OldPacks}. */
@Mixin(value = Options.class, remap = false)
public abstract class OldPacksOptionsMixin {
    @ModifyExpressionValue(method = {"updateResourcePacks", "loadSelectedResourcePacks"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"))
    private boolean haven_compat$compatible(boolean actual) {
        return OldPacks.compatible(actual);
    }
}
