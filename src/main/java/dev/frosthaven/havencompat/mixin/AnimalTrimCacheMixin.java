package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animal Armor Trims 2.3.1 rebuilds its two texture caches on every {@code EntityJoinLevelEvent} of the client: every
 * trim material times every pattern, two resource lookups each. With Immersive Portals each portal crossing is such a
 * join, and the rebuild was a frame of 100 ms and more right at the crossing (11 of 88 sampled stalls, 2026-11-22).
 * The result depends only on the trim registries and the resource packs, so it is built once for those and kept.
 */
@Pseudo
@Mixin(targets = "io.github.jason13official.animal_armor_trims.AnimalArmorTrimsClient", remap = false)
public abstract class AnimalTrimCacheMixin {
    @Unique private static final Log HAVEN_COMPAT$LOG = new Log("[animal trims] ");
    @Unique private static Object haven_compat$registries;
    @Unique private static String haven_compat$packs;

    @Inject(method = "onClientJoinLevel", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$once(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Object registries = mc.level.registryAccess();
        String packs = String.join(",", mc.getResourcePackRepository().getSelectedIds());
        if (registries == haven_compat$registries && packs.equals(haven_compat$packs)) {
            HAVEN_COMPAT$LOG.once("kept", "Animal Armor Trims' texture caches are built once per session and pack set, not at every entity or dimension change");
            ci.cancel();
            return;
        }
        haven_compat$registries = registries;
        haven_compat$packs = packs;
    }
}
