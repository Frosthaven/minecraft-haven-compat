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
 * between two of those that vanilla has none for). When such a portal loads on the client (its entity joins the level,
 * well before the player reaches it; its destination arrives a few ticks later, so a new portal is looked at each tick
 * until it has one, and nothing runs while no portal waits), {@link PipelinePrewarm#link} queues that dimension's shader pipelines, its portal views and the
 * Create programs for both sides, one step a frame. While steps run in play the action bar says "Loading portals...".
 */
public final class PortalWarm {
    private static final Log LOG = new Log("[portal warm] ");
    private static final Component TEXT = Component.translatable("haven_compat.loading_portals");
    private static Class<?> portal;
    private static Method destination;
    private static boolean broken;
    /** portals that joined before their destination arrived: looked at each tick until it does (or they leave) */
    private static final java.util.List<Entity> PENDING = new java.util.ArrayList<>();

    private PortalWarm() {}

    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded("colorwheel")) bus.addListener((ClientTickEvent.Post e) -> dev.frosthaven.havencompat.colorwheel.ProgramBinaries.saveUsed());
        if (!ModList.get().isLoaded("immersive_portals_core")) return;
        bus.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide && !broken && isPortal(e.getEntity()) && !seen(e.getEntity())) PENDING.add(e.getEntity());
        });
        bus.addListener((ClientTickEvent.Post e) -> {
            if (!PENDING.isEmpty()) PENDING.removeIf(p -> broken || p.isRemoved() || seen(p));
            Minecraft mc = Minecraft.getInstance();
            if (WarmQueue.busy() && mc.player != null && !(mc.screen instanceof ReceivingLevelScreen)) mc.gui.setOverlayMessage(TEXT, false);
        });
    }

    private static boolean isPortal(Entity entity) {
        try {
            if (portal == null) {
                portal = Class.forName("qouteall.imm_ptl.core.portal.Portal");
                destination = portal.getMethod("getDestDim");
            }
            return portal.isInstance(entity);
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            LOG.error("portal", "could not find Immersive Portals' portal class; shaders for new dimensions are built at first look as before", e);
            return false;
        }
    }

    /** false while the portal's destination has not arrived yet */
    private static boolean seen(Entity entity) {
        if (broken) return true;
        try {
            String from = entity.level().dimension().location().toString();
            ResourceKey<?> dest = (ResourceKey<?>) destination.invoke(entity);
            if (dest == null) return false;
            String to = dest.location().toString();
            if (from.equals(to) || PipelinePrewarm.linked(from, to)) return true;
            PipelinePrewarm.LINKS.add(from + "|" + to);
            LOG.info("a portal links {} and {}: their shaders are built now, before the player reaches it", from, to);
            PipelinePrewarm.link(from, to);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("portal", "could not read a portal's destination; shaders for new dimensions are built at first look as before", e);
            return true;
        }
    }
}
