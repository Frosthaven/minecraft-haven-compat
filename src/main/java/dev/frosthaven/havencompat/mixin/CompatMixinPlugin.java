package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalDepth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Each mixin applies only when the mods it is about are installed (their mod ids in the loading mod list), so the jar
 * is safe in any pack. The Immersive Portals ones are also recorded in {@link PortalDepth#patched(String)} so
 * {@code PortalShaderGuard} knows whether that fix is in place or it must fall back to holding portals off.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    /** Mixin class (simple name) -> the mod ids it needs, all of them. */
    private static final Map<String, List<String>> MODS_OF = Map.of(
            "PortalStencilHoldMixin", List.of("immersive_portals_core"),
            "PortalDepthCopyMixin", List.of("immersive_portals_core"),
            "DeepSeasUpdateCheckMixin", List.of("create_submarine"),
            "IrisPipelinePrewarmMixin", List.of("iris"),
            "MekanismChunkTrackingMixin", List.of("mekanism", "immersive_portals_core"),
            "OldPacksOptionsMixin", List.of(),   // vanilla only
            "OldPacksEntryMixin", List.of());

    @Override public void onLoad(String mixinPackage) {}

    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        List<String> mods = MODS_OF.get(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        return mods != null && mods.stream().allMatch(mod -> LoadingModList.get().getModFileById(mod) != null);
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override public List<String> getMixins() { return null; }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        PortalDepth.patched(targetClassName);   // records any target; patched() asks for the two portal ones
    }
}
