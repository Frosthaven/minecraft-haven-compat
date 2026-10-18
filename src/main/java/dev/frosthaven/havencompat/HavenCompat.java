package dev.frosthaven.havencompat;

import dev.frosthaven.havencompat.defaults.ShaderOptions;
import dev.frosthaven.havencompat.defaults.TitleDefaults;
import dev.frosthaven.havencompat.jade.JadeNotch;
import dev.frosthaven.havencompat.portals.PortalShaderGuard;
import dev.frosthaven.havencompat.servers.ServerListSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client only. Fixes between mods of the Haven pack that belong to none of them, one package per fix, each one off
 * when the mods it is about are absent:
 *  - {@code portals}: Immersive Portals with Iris shaders on a driver without OpenGL 4.3 (macOS): the main framebuffer
 *    keeps its stencil so the depth blit is legal, and the 4.3-only copy is replaced by a blit (the mixins); if those
 *    patches are not in place, {@link PortalShaderGuard} holds Immersive Portals off while shaders are on instead of
 *    letting the JVM abort.
 *  - {@code servers}: the pack's server entries stay current in the player's server list (address changes reach
 *    instances that already had the list, which Default Options' first-launch copy never does).
 *  - {@code updates}: mods' own "update available" screens are off (Create Deep Seas): the pack manages mod versions.
 *  - {@code jade}: on macOS, Jade's box moves below the camera notch (the pack's {@code jade} defaults), unless the
 *    player moved it themselves.
 *  - {@code defaults}: the pack's one-time defaults (moved here from haven-create-ui-ux, an optional mod): the listed
 *    resource packs enabled once, the first-run shader pack per OS, and per-OS shader pack options (macOS: Complementary
 *    at Block Reflection Quality Low, so Apple's 16 texture units hold Deep Seas' hull sampler too).
 */
@Mod(value = HavenCompat.ID, dist = Dist.CLIENT)
public final class HavenCompat {
    public static final String ID = "haven_compat";

    public HavenCompat(IEventBus modBus, ModContainer container) {
        PortalShaderGuard.register(NeoForge.EVENT_BUS);
        ServerListSync.register(NeoForge.EVENT_BUS);
        JadeNotch.register(NeoForge.EVENT_BUS);
        ShaderOptions.apply();   // before Iris loads its pack
        TitleDefaults.register(NeoForge.EVENT_BUS);
    }
}
