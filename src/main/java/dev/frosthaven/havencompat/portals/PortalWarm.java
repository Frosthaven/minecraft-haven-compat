package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Portals to a dimension the join's prewarm did not cover (anything but overworld, the_nether, the_end; or a portal
 * between two of those that vanilla has none for). Within half a second of such a portal loading on the client, well before the
 * player reaches it, {@link PipelinePrewarm#link} queues that dimension's shader pipelines, its portal views and the
 * Create programs for both sides, one step a frame. While steps run in play the action bar says "Loading portals...".
 */
public final class PortalWarm {
    private static final Log LOG = new Log("[portal warm] ");
    private static final Component TEXT = Component.translatable("haven_compat.loading_portals");
    private static Class<?> portal;
    private static Method destination;
    private static boolean broken;
    private static int ticks;

    private PortalWarm() {}

    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded("colorwheel")) bus.addListener((ClientTickEvent.Post e) -> dev.frosthaven.havencompat.colorwheel.ProgramBinaries.saveUsed());
        if (!ModList.get().isLoaded("immersive_portals_core")) return;
        bus.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            // A portal's destination arrives after the entity joins, so the loaded portals are looked at twice a second.
            if (mc.level != null && ++ticks % 10 == 0 && !broken) for (Entity entity : mc.level.entitiesForRendering()) seen(entity);
            if (WarmQueue.busy() && mc.player != null && !(mc.screen instanceof ReceivingLevelScreen)) mc.gui.setOverlayMessage(TEXT, false);
        });
    }

    private static void seen(Entity entity) {
        if (broken) return;
        try {
            if (portal == null) {
                portal = Class.forName("qouteall.imm_ptl.core.portal.Portal");
                destination = portal.getMethod("getDestDim");
            }
            if (!portal.isInstance(entity)) return;
            String from = entity.level().dimension().location().toString();
            ResourceKey<?> dest = (ResourceKey<?>) destination.invoke(entity);
            if (dest == null) return;
            String to = dest.location().toString();
            if (from.equals(to) || PipelinePrewarm.linked(from, to)) return;
            PipelinePrewarm.LINKS.add(from + "|" + to);
            LOG.info("a portal links {} and {}: their shaders are built now, before the player reaches it", from, to);
            PipelinePrewarm.link(from, to);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("portal", "could not read a portal's destination; shaders for new dimensions are built at first look as before", e);
        }
    }
}
