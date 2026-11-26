package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.oxidation.MoreOxidation;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The block id NeoForge registers while its supplier builds the block (DeferredRegister goes through here); see
 *  {@link MoreOxidation}. */
@Mixin(value = RegisterEvent.class, remap = false)
public abstract class OxidationRegisterIdMixin {
    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V", at = @At("HEAD"))
    private void haven_compat$enter(ResourceKey<?> registry, ResourceLocation name, Supplier<?> value, CallbackInfo ci) {
        MoreOxidation.enter(registry.equals(Registries.BLOCK) ? name : null);
    }

    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V", at = @At("RETURN"))
    private void haven_compat$leave(ResourceKey<?> registry, ResourceLocation name, Supplier<?> value, CallbackInfo ci) {
        MoreOxidation.leave();
    }
}
