package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Log;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Veil 4.3.2 (inside Sable) asks every mod for shader pre-processors each time the game loads one vanilla-style shader:
 * {@code ModLoader.postEvent}, which for each of the pack's 400 mods also builds two trace log lines with the whole
 * event printed into them (the debug log takes trace lines, so they are really formatted). Iris makes several hundred
 * such shaders per pipeline; with the shader prewarm this was a quarter of the "Compiling shaders" wait (24% of 302
 * samples, 2026-11-22). The event still goes to every mod's bus, in the same order; only the two log lines per mod are
 * gone. A listener that throws is logged once and the other mods still get the event.
 */
@Pseudo
@Mixin(targets = "foundry.veil.forge.platform.NeoForgeVeilClientPlatform", remap = false)
public abstract class VeilShaderEventMixin {
    @Unique private static final Log HAVEN_COMPAT$LOG = new Log("[veil shaders] ");

    @Redirect(method = "onRegisterShaderPreProcessors", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private void haven_compat$quietPost(Event event) {
        HAVEN_COMPAT$LOG.once("on", "Veil's per-shader event goes to the mods without two trace log lines per mod");
        ModList.get().forEachModInOrder(mod -> {
            IEventBus bus = mod.getEventBus();
            if (bus == null) return;
            try {
                bus.post(event);
            } catch (RuntimeException e) {
                HAVEN_COMPAT$LOG.error("listener:" + mod.getModId(), "a shader pre-processor listener of " + mod.getModId() + " failed", e);
            }
        });
    }
}
