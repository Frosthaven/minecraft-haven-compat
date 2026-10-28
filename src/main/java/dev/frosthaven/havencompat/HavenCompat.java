package dev.frosthaven.havencompat;

import dev.frosthaven.havencompat.defaults.ShaderOptions;
import dev.frosthaven.havencompat.defaults.TitleDefaults;
import dev.frosthaven.havencompat.jade.JadeNotch;
import dev.frosthaven.havencompat.keys.MacInputLogs;
import dev.frosthaven.havencompat.keys.MacInputOptions;
import dev.frosthaven.havencompat.portals.PortalShaderGuard;
import dev.frosthaven.havencompat.servers.ServerListSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fixes between mods of the Haven pack that belong to none of them, one package per fix, each one off when the mods
 * it is about are absent. Client side, except {@code mekanism} (a server mixin; on a dedicated server this class is not
 * built, only the mixins in the "mixins" list apply):
 *  - {@code portals}: Immersive Portals with Iris shaders on a driver without OpenGL 4.3 (macOS): the main framebuffer
 *    keeps its stencil so the depth blit is legal, and the 4.3-only copy is replaced by a blit (the mixins); if those
 *    patches are not in place, {@link PortalShaderGuard} holds Immersive Portals off while shaders are on instead of
 *    letting the JVM abort. {@link dev.frosthaven.havencompat.portals.PipelinePrewarm} (through a mixin on Iris's
 *    PipelineManager) builds the other dimensions' shader pipelines behind the loading screen, so the first look
 *    through a portal does not link shaders and rebuild every chunk.
 *  - {@code servers}: the pack's server entries stay current in the player's server list (address changes reach
 *    instances that already had the list, which Default Options' first-launch copy never does).
 *  - {@code updates}: mods' own "update available" screens are off (Create Deep Seas): the pack manages mod versions.
 *  - {@code jade}: on macOS, Jade's box moves below the camera notch (the pack's {@code jade} defaults), unless the
 *    player moved it themselves.
 *  - {@code keys}: on macOS, Control + the drop key drops the whole stack (two macOS-only mixins); MacOS Input Fixes'
 *    per-scroll INFO logging is set to WARN, and its options get the pack's values once (Cmd for shortcuts).
 *  - {@code mekanism} (server): Mekanism's block update packets (a fluid tank's level, a machine's state) reach the
 *    players again under Immersive Portals' chunk tracking ({@link dev.frosthaven.havencompat.mekanism.ChunkWatchers}).
 *  - {@code mekanism} (client): while a shader pack is in use, the chemical contents of Mekanism multiblocks take full
 *    block light so they show in dark rooms ({@link dev.frosthaven.havencompat.mekanism.ChemicalGlow}; a mixin, nothing
 *    registered here).
 *  - {@code packs}: resource packs made for another pack format count as compatible (no warning, no confirmation,
 *    kept when enabled by the pack defaults); see {@link dev.frosthaven.havencompat.packs.OldPacks}.
 *  - {@code trash}: TrashSlot's trash slot stays visible on the screens the pack lists ({@code trashSlot} in the pack
 *    defaults); its toggle key no longer hides it there (two mixins, no class of ours registered here).
 *  - {@code defaults}: the pack's one-time defaults (moved here from haven-create-ui-ux, an optional mod): the listed
 *    resource packs enabled once and order fixes, before the first resource load (no extra reload), the loading
 *    screens black (Monochrome Logo, once), key binding moves off clashing defaults (once), the first-run shader pack per OS, and per-OS shader pack options (macOS: Complementary
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
        MacInputLogs.quiet();
        MacInputOptions.apply();   // before MacOS Input Fixes reads its options at client setup
    }
}
