package dev.frosthaven.havencompat.jade;

import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Jade's "what am I looking at" box sits at the top centre of the screen. A MacBook with a camera notch draws a full
 * screen game under the notch, so the box hides behind it; Jade has no notch awareness, and its position is a per-player
 * value ({@code overlay.overlayPosY}, 1.0 = the top edge) in {@code config/jade/jade.json}, a file the pack ships.
 * On macOS, on the first client tick, this reads the pack's {@code jade} defaults:
 * <pre>{"jade": {"packValue": 1.0, "macos": 0.94, "force": false}}</pre>
 * and, while the player's value still equals {@code packValue} (or always, with {@code force}), sets {@code macos}
 * through Jade's API and saves Jade's config. A player who moved the box keeps their position. Other systems: nothing.
 * {@code -Dhaven_compat.assumeMac=true} runs the macOS path elsewhere, for testing.
 */
public final class JadeNotch {
    static final String FLAG = "haven_compat.assumeMac";
    private static final Log LOG = new Log("[jade notch] ");
    private static boolean done;

    private JadeNotch() {
    }

    public static void register(IEventBus gameBus) {
        if (ModList.get().isLoaded("jade")) {
            gameBus.addListener(ClientTickEvent.Post.class, JadeNotch::onTick);
        }
    }

    static void onTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        done = true;
        try {
            apply(PackDefaults.file());
        } catch (Throwable t) {
            LOG.error("apply", "Jade overlay left as it was", t);
        }
    }

    static boolean macOS() {
        return Boolean.getBoolean(FLAG) || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    static void apply(Path file) throws Exception {
        if (!macOS()) {
            return;
        }
        JsonObject spec = PackDefaults.section(file, "jade");
        if (spec == null || !spec.has("macos")) {
            LOG.once("none", "macOS, but no jade.macos in {}: nothing to move", file.getFileName());
            return;
        }
        float macos = spec.get("macos").getAsFloat();
        float packValue = spec.has("packValue") ? spec.get("packValue").getAsFloat() : 1.0f;
        boolean force = spec.has("force") && spec.get("force").getAsBoolean();
        // Jade's API by reflection, through the interfaces (the implementing classes are not public):
        // IWailaConfig.get().getOverlay().getOverlayPosY() / setOverlayPosY(float); Jade.CONFIG.save().
        Class<?> api = Class.forName("snownee.jade.api.config.IWailaConfig");
        Class<?> overlayApi = Class.forName("snownee.jade.api.config.IWailaConfig$IConfigOverlay");
        Object overlay = api.getMethod("getOverlay").invoke(api.getMethod("get").invoke(null));
        float current = (Float) overlayApi.getMethod("getOverlayPosY").invoke(overlay);
        if (!NotchMove.shouldMove(current, packValue, macos, force)) {
            LOG.once("kept", "macOS: Jade overlay Y {} left alone (pack value {}, macOS value {})", current, packValue, macos);
            return;
        }
        overlayApi.getMethod("setOverlayPosY", float.class).invoke(overlay, macos);
        Object config = Class.forName("snownee.jade.Jade").getField("CONFIG").get(null);
        Class.forName("snownee.jade.util.JsonConfig").getMethod("save").invoke(config);
        LOG.once("moved", "macOS: Jade overlay Y {} -> {} (camera notch)", current, macos);
    }
}
