package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.oxidation.MoreOxidation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The entry Registrate registers while it builds it (Create add-ons: Create Casing, Steam 'n' Rails, ...); Registrate
 *  builds the value in {@code Registration.register} before handing it to NeoForge. Every registry's entries pass here;
 *  {@link MoreOxidation} only answers for block ids on its list. */
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate$Registration", remap = false)
public abstract class OxidationRegistrateIdMixin {
    @Shadow @Final private ResourceLocation name;

    @Inject(method = "register(Lnet/neoforged/neoforge/registries/RegisterEvent;)V", at = @At("HEAD"))
    private void haven_compat$enter(CallbackInfo ci) {
        MoreOxidation.enter(name);
    }

    @Inject(method = "register(Lnet/neoforged/neoforge/registries/RegisterEvent;)V", at = @At("RETURN"))
    private void haven_compat$leave(CallbackInfo ci) {
        MoreOxidation.leave();
    }
}
