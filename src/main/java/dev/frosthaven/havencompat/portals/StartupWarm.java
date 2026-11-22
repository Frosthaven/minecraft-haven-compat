package dev.frosthaven.havencompat.portals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The shader prewarm at game start ({@link PipelinePrewarm#startup}): started when the title screen opens, and shown as
 * {@link CompileScreen} in its place until it is done. Leaving a world tells {@link PrewarmScreen} that the next join
 * has engines to look at again.
 */
public final class StartupWarm {
    private StartupWarm() {}

    public static void register(IEventBus bus) {
        bus.addListener((ScreenEvent.Init.Post e) -> {
            if (!(e.getScreen() instanceof TitleScreen title)) return;
            PipelinePrewarm.startup();
            // after this event: the title screen is being set right now
            if (WarmQueue.busy()) Minecraft.getInstance().tell(() -> {
                if (Minecraft.getInstance().screen == title && WarmQueue.busy()) Minecraft.getInstance().setScreen(new CompileScreen(title, null));
            });
        });
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> PrewarmScreen.left());
    }

    /** Shader work is running, or is due and has not been started: a connection waits for it. */
    public static boolean pending() {
        if (WarmQueue.busy()) return true;
        if (!PipelinePrewarm.startupDue()) return false;
        PipelinePrewarm.startup();
        return WarmQueue.busy();
    }
}
