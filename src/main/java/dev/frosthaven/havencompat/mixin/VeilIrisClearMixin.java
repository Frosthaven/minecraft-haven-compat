package dev.frosthaven.havencompat.mixin;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of {@link VeilIrisBindMixin}: Veil 4.3.2 never unbinds the framebuffer it bound for a Veil shader (its
 * {@code @Share} flag does not carry from {@code apply} to {@code clear}), so what is drawn next goes to it. The draw
 * and read framebuffers bound before {@code apply} are bound again at the end of {@code clear}. Render thread only.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.program.ShaderProgramImpl$Wrapper", remap = false)
public abstract class VeilIrisClearMixin {
    @Unique
    private static int haven_compat$draw = -1, haven_compat$read = -1;

    @Inject(method = "apply", at = @At("HEAD"), require = 0)
    private void haven_compat$save(CallbackInfo ci) {
        haven_compat$draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        haven_compat$read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    }

    @Inject(method = "clear", at = @At("TAIL"), require = 0)
    private void haven_compat$restore(CallbackInfo ci) {
        if (haven_compat$draw < 0) return;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, haven_compat$draw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, haven_compat$read);
        haven_compat$draw = haven_compat$read = -1;
    }
}
