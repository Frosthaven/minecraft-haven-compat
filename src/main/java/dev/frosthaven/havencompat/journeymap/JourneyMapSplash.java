package dev.frosthaven.havencompat.journeymap;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;

/**
 * JourneyMap opens its "About JourneyMap" splash over the fullscreen map the first time the map opens on a new
 * JourneyMap version ({@code Fullscreen.init}: {@code CoreProperties.splashViewed} differs from {@code
 * Journeymap.JM_VERSION}). Players took it for the map not opening. At the head of {@code Fullscreen.init} this sets
 * {@code splashViewed} to the running version and saves it, as the About screen itself does when it opens; JourneyMap's
 * own About button still works. Only that one field changes (JourneyMap's StringField set + save, by reflection).
 */
public final class JourneyMapSplash {
    private static final Log LOG = new Log("[journeymap splash] ");
    private static boolean broken;

    private JourneyMapSplash() {}

    public static void markSeen() {
        if (broken) return;
        try {
            String version = Class.forName("journeymap.common.Journeymap").getField("JM_VERSION").get(null).toString();
            Object client = Class.forName("journeymap.client.JourneymapClient").getMethod("getInstance").invoke(null);
            Object core = client.getClass().getMethod("getCoreProperties").invoke(client);
            Field f = core.getClass().getField("splashViewed");
            Object field = f.get(core);
            Object seen = field.getClass().getMethod("get").invoke(field);
            if (version.equals(seen)) return;
            field.getClass().getMethod("set", String.class).invoke(field, version);
            field.getClass().getMethod("save").invoke(field);
            LOG.info("JourneyMap's About splash for {} skipped (splashViewed {} -> {})", version, seen, version);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LOG.error("mark", "cannot mark JourneyMap's splash as seen; it may show once", e);
        }
    }
}
