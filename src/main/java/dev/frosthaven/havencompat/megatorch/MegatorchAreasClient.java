package dev.frosthaven.havencompat.megatorch;

import dev.frosthaven.havencompat.settings.SavedChoice;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the Mega Torch areas: the toggle (off by default, saved on this computer), the last list the server
 * sent (one dimension; a new one replaces it) and the watch message. Knows nothing of JourneyMap: the map layer
 * ({@link MegatorchMap}) sets {@link #changed} and calls {@link #watch}.
 */
public final class MegatorchAreasClient {
    public enum Shown { OFF, ON }

    public static final SavedChoice<Shown> SHOWN = new SavedChoice<>(Shown.class, Shown.OFF, "haven_compat_megatorch_map.json",
            "shown", "Mega Torch areas on the map", MegatorchAreas.LOG);

    static Runnable changed = () -> {};
    private static MegatorchAreas.Areas latest;
    private static boolean watching;

    private MegatorchAreasClient() {}

    /** Client constructor, with Torchmaster and JourneyMap loaded. */
    public static void register() {
        SHOWN.load();
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            latest = null;
            watching = false;
            changed.run();
        });
    }

    static MegatorchAreas.Areas latest() { return latest; }

    static void received(MegatorchAreas.Areas areas) {
        latest = areas;
        changed.run();
    }

    /** Tells the server whether to send updates; a server without haven-compat has no channel and gets nothing. */
    static void watch(boolean on) {
        var connection = Minecraft.getInstance().getConnection();
        if (on == watching || connection == null || !connection.hasChannel(MegatorchAreas.Watch.TYPE)) return;
        watching = on;
        PacketDistributor.sendToServer(new MegatorchAreas.Watch(on));
    }
}
