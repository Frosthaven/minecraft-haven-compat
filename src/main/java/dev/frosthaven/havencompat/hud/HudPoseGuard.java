package dev.frosthaven.havencompat.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havencompat.Log;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws each HUD layer and the open screen from the drawing position the frame started with. A mod that moves the HUD's
 * pose and does not undo it (a draw that fails part-way, a "before" without its "after") shifts every later layer and
 * the screen: on 2026-11-21 a Windows player's chat log and chat input line were drawn on JourneyMap's minimap at GUI
 * scale 2 and off the screen at 3, and came back with the minimap off (her JourneyMap log was not available, so the
 * cause inside it is unknown). The start is recorded first ({@code RenderGuiEvent.Pre}, highest). When a layer or the
 * screen is about to draw (lowest, so only when it really draws) from a moved position, a pose of its own is pushed with
 * the start's position and popped after it: other mods' poses are never popped, so nothing can underflow. Compared are
 * x, y, rotation and scale; depth (z) grows per layer on purpose and is kept. Logged once per layer or screen.
 */
public final class HudPoseGuard {
    private static final Log LOG = new Log("[hud position] ");
    private static final Matrix4f START = new Matrix4f();
    private static final Matrix3f START_NORMAL = new Matrix3f();
    private static final Matrix4f WANT = new Matrix4f();
    private static boolean started;
    private static boolean layerPushed;
    private static boolean screenPushed;
    private static ResourceLocation lastLayer;

    private HudPoseGuard() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, true, RenderGuiEvent.Pre.class, e -> start(e.getGuiGraphics()));
        bus.addListener(EventPriority.LOWEST, false, RenderGuiLayerEvent.Pre.class, e -> layerPushed = reset(e.getGuiGraphics(), e.getName().toString()));
        bus.addListener(EventPriority.LOWEST, true, RenderGuiLayerEvent.Post.class, e -> {
            if (layerPushed) e.getGuiGraphics().pose().popPose();
            layerPushed = false;
            lastLayer = e.getName();
        });
        bus.addListener(EventPriority.LOWEST, false, ScreenEvent.Render.Pre.class, e -> screenPushed = reset(e.getGuiGraphics(), "the screen " + e.getScreen().getClass().getSimpleName()));
        bus.addListener(EventPriority.LOWEST, true, ScreenEvent.Render.Post.class, e -> {
            if (screenPushed) e.getGuiGraphics().pose().popPose();
            screenPushed = false;
            started = false;   // the frame's drawing is done
        });
    }

    private static void start(GuiGraphics graphics) {
        PoseStack.Pose pose = graphics.pose().last();
        START.set(pose.pose());
        START_NORMAL.set(pose.normal());
        started = true;
        lastLayer = null;
    }

    /** True when it pushed a pose with the frame's starting position (the caller pops it after the draw). */
    private static boolean reset(GuiGraphics graphics, String what) {
        if (!started) return false;
        PoseStack pose = graphics.pose();
        Matrix4f now = pose.last().pose();
        WANT.set(START).m32(now.m32());   // depth (z) grows per layer on purpose
        if (now.equals(WANT, 1e-4f)) return false;
        pose.pushPose();
        pose.last().pose().set(WANT);
        pose.last().normal().set(START_NORMAL);
        String after = lastLayer == null ? "the start of the HUD (a mod drawing before the layers, such as JourneyMap's minimap)" : lastLayer.toString();
        LOG.warnOnce("fixed:" + what, "the drawing position was left moved after {}; {} is drawn from the frame's start", after, what);
        return true;
    }
}
