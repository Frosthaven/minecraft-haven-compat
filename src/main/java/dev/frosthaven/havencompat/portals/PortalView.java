package dev.frosthaven.havencompat.portals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havencompat.Log;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The player's Immersive Portal Rendering setting (Experimental Optimizations), saved on this computer. Normal: Immersive
 * Portals as configured. Disabled: no view through a portal, {@link PortalPlane} draws a purple plane; teleporting stays.
 */
public final class PortalView {
    public enum Level { NORMAL, DISABLED }

    private static final Log LOG = new Log("[portal view] ");
    private static volatile Level level = Level.NORMAL;

    private PortalView() {}

    public static Level level() { return level; }

    public static boolean disabled() { return level == Level.DISABLED; }

    /** Reads the saved choice at mod construction; a missing or unreadable file means Normal. */
    public static void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            level = Level.valueOf(json.get("portalView").getAsString());
            LOG.info("Immersive Portal Rendering: {}", level);
        } catch (Exception e) {
            LOG.error("read", "could not read the setting, Normal is used", e);
        }
    }

    /** The settings screen's choice: applies now and saves. */
    public static void choose(Level chosen) {
        level = chosen;
        LOG.info("Immersive Portal Rendering: {}", level);
        JsonObject json = new JsonObject();
        json.addProperty("portalView", level.name());
        try {
            Files.writeString(file(), json + "\n");
        } catch (Exception e) {
            LOG.error("save", "could not save the setting", e);
        }
    }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("haven-compat-portal-view.json"); }
}
