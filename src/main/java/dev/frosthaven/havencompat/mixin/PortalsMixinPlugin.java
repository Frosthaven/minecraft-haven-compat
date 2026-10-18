package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalDepth;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The Immersive Portals mixins apply only when Immersive Portals is installed, and each applied one is recorded in
 * {@link PortalDepth#patched(String)} so {@code PortalShaderGuard} knows whether the fix is in place or it must fall
 * back to holding portals off.
 */
public final class PortalsMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}

    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return LoadingModList.get().getModFileById("immersive_portals_core") != null;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override public List<String> getMixins() { return null; }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        PortalDepth.patched(targetClassName);
    }
}
