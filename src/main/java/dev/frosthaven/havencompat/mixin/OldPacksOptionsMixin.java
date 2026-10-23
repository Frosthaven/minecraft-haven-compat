package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.packs.OldPacks;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Options keeps and saves an outdated resource pack like any other; see {@link OldPacks}. */
@Mixin(value = Options.class, remap = false)
public abstract class OldPacksOptionsMixin {
    @ModifyExpressionValue(method = {"updateResourcePacks", "loadSelectedResourcePacks"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"))
    private boolean haven_compat$compatible(boolean actual) {
        return OldPacks.compatible(actual);
    }

    /** Before vanilla reads the list: see {@link OldPacks#forgetIncompatible}. The lookup is vanilla's (bare id, then file/). */
    @Inject(method = "loadSelectedResourcePacks", at = @At("HEAD"))
    private void haven_compat$forgetIncompatible(PackRepository repository, CallbackInfo ci) {
        OldPacks.forgetIncompatible(((Options) (Object) this).incompatibleResourcePacks,
                id -> repository.getPack(id) != null || !id.startsWith("file/") && repository.getPack("file/" + id) != null);
    }
}
