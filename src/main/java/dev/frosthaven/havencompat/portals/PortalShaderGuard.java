package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The safety net behind {@link PortalDepth}. On a driver without {@code glCopyImageSubData} the mixins make Immersive
 * Portals' shader path legal; should they not be in place (Immersive Portals' internals moved: the mixins apply to
 * named classes and methods of its jar), this holds Immersive Portals' render mode at {@code none} while Iris shaders
 * are on (portals still teleport, they are just not drawn through), tells the player once, and gives the mode back when
 * shaders go off. Where the call exists, or with the patches in place, nothing happens beyond one log line.
 * Reflection only: Immersive Portals or Iris absent, or their internals moved too, means no guard (logged once), never
 * a crash of its own.
 */
public final class PortalShaderGuard {
    private static final Log LOG = new Log("[portal guard] ");
    private static final String IP_GLOBAL = "qouteall.imm_ptl.core.IPGlobal", IRIS = "net.irisshaders.iris.Iris";
    private static Object forcedFrom;   // the render mode replaced by "none" while shaders are on
    private static boolean told, broken;
    private static int tick;

    private PortalShaderGuard() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("immersive_portals_core") || !ModList.get().isLoaded("iris")) return;
        // Define the two patched classes now (no initialization): the mixins apply as a class is defined, so by the
        // first tick PortalDepth.patched() is an answer and not a "not yet".
        for (String name : new String[] {PortalDepth.STENCIL_TARGET, PortalDepth.COPY_TARGET}) {
            try {
                Class.forName(name, false, PortalShaderGuard.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                LOG.warnOnce("define:" + name, "{} is not in this Immersive Portals: its patch is not in place ({})", name, e.toString());
            }
        }
        bus.addListener(ClientTickEvent.Post.class, PortalShaderGuard::onTick);
    }

    private static void onTick(ClientTickEvent.Post event) {
        if (broken || ++tick % 10 != 0) return;   // twice a second is plenty: the renderer is re-chosen every frame
        try {
            if (!PortalDepth.noCopyImage()) return;
            if (PortalDepth.patched()) {
                LOG.once("patched", "both patches are in place: Immersive Portals' shader path runs with the stencil hold"
                        + " and the blit copies; portals render through with shaders on");
                return;
            }
            LOG.warnOnce("unpatched", "the patches are not in place: Immersive Portals' render mode is held at none while shaders are on");
            Field mode = Class.forName(IP_GLOBAL).getField("renderMode");
            Object current = mode.get(null), none = constant(mode.getType(), "none");
            if (shadersOn()) {
                if (current != none) {
                    if (forcedFrom == null) forcedFrom = current;
                    mode.set(null, none);
                    LOG.once("forced", "shaders on: Immersive Portals render mode {} -> none", current);
                }
                Minecraft mc = Minecraft.getInstance();
                if (!told && mc.player != null) {
                    mc.gui.getChat().addMessage(Component.translatable("haven_compat.portals.shaders_off"));
                    told = true;
                }
            } else if (forcedFrom != null) {
                if (current == none) mode.set(null, forcedFrom);
                LOG.once("restored:" + forcedFrom, "shaders off: Immersive Portals render mode back to {}", forcedFrom);
                forcedFrom = null;
                told = false;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("guard", "could not reach Immersive Portals' render mode or Iris; the portal shader guard is off", e);
        }
    }

    /** Iris's own answer: a shader pack is in use (what Immersive Portals checks before taking its shader path). */
    private static boolean shadersOn() throws ReflectiveOperationException {
        Object pack = Class.forName(IRIS).getMethod("getCurrentPack").invoke(null);
        return pack instanceof Optional<?> o && o.isPresent();
    }

    private static Object constant(Class<?> type, String name) {
        for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equals(name)) return c;
        throw new IllegalStateException(type.getName() + " has no constant " + name);
    }
}
