package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Os;
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
    private static final Map<String, List<String>> MODS_OF = Map.ofEntries(
            Map.entry("PortalStencilHoldMixin", List.of("immersive_portals_core")),
            Map.entry("PortalDepthCopyMixin", List.of("immersive_portals_core")),
            Map.entry("DeepSeasUpdateCheckMixin", List.of("create_submarine")),
            Map.entry("IrisPipelinePrewarmMixin", List.of("iris")),
            Map.entry("MekanismChunkTrackingMixin", List.of("mekanism", "immersive_portals_core")),
            Map.entry("OldPacksOptionsMixin", List.of()),   // vanilla only
            Map.entry("OldPacksEntryMixin", List.of()),
            Map.entry("LoadDefaultsMixin", List.of()),
            Map.entry("MacDropStackMixin", List.of()),
            Map.entry("MacDropStackScreenMixin", List.of()),
            Map.entry("RefusedCrossingMixin", List.of("immersive_portals_core")),
            Map.entry("PortalPipesTfmgMixin", List.of("immersive_portals_core", "tfmg", "create")),
            Map.entry("NtglProjectileSyncMixin", List.of("ntgl")),
            Map.entry("AeBlockTurnMixin", List.of("ae2")),
            Map.entry("StructureTemplateAccessor", List.of("create")),
            Map.entry("SchematicExportMixin", List.of("create")),
            Map.entry("StructurePlaceMixin", List.of("create")),
            Map.entry("SchematicQuillSaveMixin", List.of("create")),
            Map.entry("PipezPipeTurnMixin", List.of("pipez", "create")),
            Map.entry("MagnetCoilTurnMixin", List.of("simplemagnets", "create")),
            Map.entry("RsDirectionTurnMixin", List.of("refinedstorage", "create")));
    /** Mixins that apply on macOS only ({@link Os#macOS()}): on Windows and Linux the target classes stay unchanged. */
    private static final Set<String> MAC_ONLY = Set.of("MacDropStackMixin", "MacDropStackScreenMixin");

    @Override public void onLoad(String mixinPackage) {}

    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (MAC_ONLY.contains(name) && !Os.macOS()) {
            return false;
        }
        List<String> mods = MODS_OF.get(name);
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
