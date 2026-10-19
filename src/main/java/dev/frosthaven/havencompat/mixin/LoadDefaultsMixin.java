package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.defaults.LoadDefaults;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pack defaults that change options.txt, before the first resource load; see {@link LoadDefaults}. */
@Mixin(value = Options.class, remap = false)
public abstract class LoadDefaultsMixin {
    @Inject(method = "loadSelectedResourcePacks", at = @At("TAIL"))
    private void haven_compat$defaults(PackRepository repository, CallbackInfo ci) {
        LoadDefaults.apply((Options) (Object) this, repository);
    }
}
