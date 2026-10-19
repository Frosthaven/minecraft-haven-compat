package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.Os;
import dev.frosthaven.havencompat.PackDefaults;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The pack's defaults that need a running client, applied on the first title screen of a session (moved here from
 * haven-create-ui-ux 1.3.0, an optional mod; this one is required, so every instance gets them):
 * <pre>
 * {"resourcePacks": [{"id": "file/FA+All_Extensions-v1.8.1.zip", "above": "file/FreshAnimations_v1.10.4.zip"},
 *                    {"id": "file/Some Pack.zip"}],
 *  "resourcePackOrder": [{"id": "file/FreshAnimations_v1.10.4.zip", "above": "file/Vanilla Experience.zip"}],
 *  "shader": {"default": "ComplementaryUnbound_r5.9.zip", "macos": "BSL_v10.1.1.zip", "enabled": true}}
 * </pre>
 * Resource packs: a listed pack is enabled when it is first seen installed, or when it is installed again after it was
 * removed; "above" / "below" place it right above (higher priority) or below another selected pack. A pack the player
 * turned off while it stayed installed is never turned back on. Order fixes ({@code resourcePackOrder}): each rule
 * moves {@code id} right above {@code above} once, when both are selected ({@link PackOrder}); the player's later
 * order stays (state {@code ordered}). Shader: on the first run only, Iris gets the
 * OS-specific shader pack (macOS: "macos", else "default") and shaders on or off, by reflection; Iris absent or the
 * file missing: skipped. The player's later choices always win ({@link DefaultsState}).
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
            if (spec.has("resourcePacks")) {
                resourcePacks(spec.getAsJsonArray("resourcePacks"), state.object("seen"));
            }
            if (spec.has("resourcePackOrder")) {
                resourcePackOrder(spec.getAsJsonArray("resourcePackOrder"), state.object("ordered"));
            }
            if (spec.has("shader") && !state.root().has("shaderApplied")) {
                shader(spec.getAsJsonObject("shader"));
                state.root().addProperty("shaderApplied", true);
            }
            state.save();
        } catch (Throwable t) {
            LOG.error("apply", "pack defaults left as they were", t);
        }
    }

    /** Enables newly seen packs in order, then saves options (Minecraft reloads resources if the list changed). */
    static void resourcePacks(JsonArray list, JsonObject seen) {
        Minecraft mc = Minecraft.getInstance();
        var repository = mc.getResourcePackRepository();
        repository.reload();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());   // bottom (lowest priority) first
        boolean changed = false;
        for (JsonElement el : list) {
            JsonObject entry = el.getAsJsonObject();
            String id = entry.get("id").getAsString();
            boolean present = repository.getAvailableIds().contains(id);
            boolean wasSeen = seen.has(id) && seen.get(id).getAsBoolean();
            seen.addProperty(id, present);
            if (!present) {
                LOG.once("missing:" + id, "{} is not installed; nothing to do", id);
                continue;
            }
            if (wasSeen) {
                continue;   // installed before: the player's choice stands
            }
            if (selected.contains(id)) {
                LOG.once("already:" + id, "{} is newly installed and already enabled", id);
                continue;
            }
            int at = selected.size();
            String anchor = entry.has("above") ? entry.get("above").getAsString() : entry.has("below") ? entry.get("below").getAsString() : null;
            if (anchor != null && selected.contains(anchor)) {
                at = selected.indexOf(anchor) + (entry.has("above") ? 1 : 0);
            }
            selected.add(at, id);
            changed = true;
            LOG.once("enabled:" + id, "enabled {}{}", id, anchor == null ? "" : (entry.has("above") ? " above " : " below ") + anchor);
        }
        if (changed) {
            repository.setSelected(selected);
            mc.options.updateResourcePacks(repository);   // saves options.txt, reloads resources if the list changed
        }
    }

    /** Applies each order rule once (both packs selected), then saves options if the order changed. */
    static void resourcePackOrder(JsonArray rules, JsonObject ordered) {
        Minecraft mc = Minecraft.getInstance();
        var repository = mc.getResourcePackRepository();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());   // bottom (lowest priority) first
        boolean changed = false;
        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            String id = rule.get("id").getAsString();
            String anchor = rule.get("above").getAsString();
            String key = id + " above " + anchor;
            if (ordered.has(key) || !PackOrder.settled(selected, id, anchor)) {
                continue;   // done before (the player's order stands), or a pack is not selected
            }
            if (PackOrder.moveAbove(selected, id, anchor)) {
                changed = true;
                LOG.once("ordered:" + key, "moved {}", key);
            }
            ordered.addProperty(key, true);
        }
        if (changed) {
            repository.setSelected(selected);
            mc.options.updateResourcePacks(repository);   // saves options.txt, reloads resources
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
