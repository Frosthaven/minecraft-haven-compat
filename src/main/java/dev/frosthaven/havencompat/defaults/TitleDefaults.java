package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.Os;
import dev.frosthaven.havencompat.PackDefaults;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * The pack default that needs a running client, applied on the first title screen of a session (moved here from
 * haven-create-ui-ux 1.3.0, an optional mod; this one is required, so every instance gets it):
 * <pre>{"shader": {"default": "ComplementaryUnbound_r5.9.zip", "macos": "BSL_v10.1.1.zip", "enabled": true}}</pre>
 * On the first run only, Iris gets the OS-specific shader pack (macOS: "macos", else "default") and shaders on or off,
 * by reflection; Iris absent or the file missing: skipped. The player's later choices always win ({@link DefaultsState}).
 * The resource pack defaults run earlier, before the first resource load ({@link LoadDefaults}).
 */
public final class TitleDefaults {
    private static final Log LOG = new Log("[pack defaults] ");
    private static boolean done;

    private TitleDefaults() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ScreenEvent.Init.Post.class, TitleDefaults::onScreen);
    }

    static void onScreen(ScreenEvent.Init.Post event) {
        if (done || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        done = true;
        try {
            JsonObject spec = PackDefaults.root(PackDefaults.file());
            if (spec == null) {
                LOG.once("absent", "no {} in config: nothing to apply", PackDefaults.FILE);
                return;
            }
            DefaultsState state = DefaultsState.load();
            if (spec.has("shader") && !state.root().has("shaderApplied")) {
                shader(spec.getAsJsonObject("shader"));
                state.root().addProperty("shaderApplied", true);
            }
            state.save();
        } catch (Throwable t) {
            LOG.error("apply", "pack defaults left as they were", t);
        }
    }

    /** First run only: sets Iris's shader pack for this OS (reflection; Iris absent or file missing: skipped). */
    static void shader(JsonObject spec) {
        boolean mac = Os.macOS();
        String key = mac ? "macos" : "default";
        String name = spec.has(key) ? spec.get(key).getAsString() : spec.has("default") ? spec.get("default").getAsString() : null;
        boolean enabled = !spec.has("enabled") || spec.get("enabled").getAsBoolean();
        if (name == null) {
            return;
        }
        if (!Files.exists(FMLPaths.GAMEDIR.get().resolve("shaderpacks").resolve(name))) {
            LOG.once("shader-missing", "shader pack {} is not in shaderpacks/; skipped", name);
            return;
        }
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, name);
            cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, enabled);
            cfg.getClass().getMethod("save").invoke(cfg);
            Method reload = iris.getMethod("reload");
            reload.invoke(null);
            LOG.once("shader", "first run: shader pack {} ({}), shaders {}", name, mac ? "macOS" : "default", enabled ? "on" : "off");
        } catch (ClassNotFoundException e) {
            LOG.once("no-iris", "Iris is not installed; shader default skipped");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.error("shader", "setting the shader pack through Iris failed; skipped", e);
        }
    }
}
