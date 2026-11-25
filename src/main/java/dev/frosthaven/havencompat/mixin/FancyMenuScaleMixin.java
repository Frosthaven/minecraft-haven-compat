package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FancyMenu 3.9 tracks an extra render scale on every {@code PoseStack} push, pop and scale in the whole game (its
 * PoseStack mixin calls {@code RenderScaleUtil.setActiveRenderScale_FancyMenu}, a {@code Float.valueOf} and a
 * {@code ThreadLocal.set} each time): 111 render-thread samples on one traintest trip (about 2.2 s of render time), for a
 * value it reads only while drawing its own elements. On the render thread the value is kept in a plain field instead
 * (no boxing, no thread-map lookup); other threads keep FancyMenu's ThreadLocal. Same values, read and written the same.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.util.rendering.RenderScaleUtil", remap = false)
public abstract class FancyMenuScaleMixin {
    @Unique private static float haven$renderScale = 1f;

    @Inject(method = "setActiveRenderScale_FancyMenu", at = @At("HEAD"), cancellable = true)
    private static void haven$set(float scale, CallbackInfo ci) {
        if (RenderSystem.isOnRenderThread()) { haven$renderScale = scale; ci.cancel(); }
    }

    @Inject(method = "resetActiveRenderScale_FancyMenu", at = @At("HEAD"), cancellable = true)
    private static void haven$reset(CallbackInfo ci) {
        if (RenderSystem.isOnRenderThread()) { haven$renderScale = 1f; ci.cancel(); }
    }

    @Inject(method = "getCurrentAdditionalRenderScale", at = @At("HEAD"), cancellable = true)
    private static void haven$getAdditional(CallbackInfoReturnable<Float> cir) {
        if (RenderSystem.isOnRenderThread()) cir.setReturnValue(haven$renderScale);
    }

    @Inject(method = "getCurrentRenderScale", at = @At("HEAD"), cancellable = true)
    private static void haven$getCurrent(CallbackInfoReturnable<Float> cir) {
        if (RenderSystem.isOnRenderThread()) cir.setReturnValue((float) Minecraft.getInstance().getWindow().getGuiScale() * haven$renderScale);
    }
}
