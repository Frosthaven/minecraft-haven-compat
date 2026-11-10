package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tooltips always on top. Minecraft draws a tooltip at depth 400 above the pose it gets and trusts everything else on
 * screen to stay below; mod screens that draw items or panels deeper (Ponder's item index: buttons at 420, items about
 * 160 deeper) show through the tooltip. Right before a tooltip draws (after NeoForge's pre event let it through),
 * everything queued so far is drawn and the depth buffer cleared, so the tooltip covers whatever is under it no matter
 * what depth it used. Every tooltip goes through here: vanilla, JEI, Ponder, Legendary Tooltips' styled ones.
 */
@Mixin(value = GuiGraphics.class, remap = false)
public abstract class TooltipOnTopMixin {
    @Shadow
    public abstract void flush();

    @Inject(method = "renderTooltipInternal",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0))
    private void haven_compat$tooltipOnTop(CallbackInfo ci) {
        flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }
}
