package dev.frosthaven.havencompat.defaults;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pack defaults that change options.txt, applied while the game starts: right after Options reads the selected
 * resource packs (a mixin on {@code Options.loadSelectedResourcePacks}), before the first resource load. A change here
 * costs no second resource reload (up to 0.10.0 they ran on the title screen, and Minecraft reloaded every resource
 * with its own progress bar on the launch after a pack change).
 * <pre>
 * {"resourcePacks": [{"id": "file/FA+All_Extensions-v1.8.1.zip", "above": "file/FreshAnimations_v1.10.4.zip"}],
 *  "resourcePackOrder": [{"id": "file/FreshAnimations_v1.10.4.zip", "above": "file/Vanilla Experience.zip"}],
 *  "darkLoadingScreen": true}
 * </pre>
 * {@code resourcePacks}: {@link PackList} (state {@code seen}). {@code resourcePackOrder}: each rule moves {@code id}
 * right above {@code above} once, when both are selected ({@link PackOrder}; state {@code ordered}).
 * {@code darkLoadingScreen}: the Monochrome Logo option (options.txt {@code darkMojangStudiosBackground}) is switched on
 * once (state {@code darkLoadingScreen}); NeoForge's early loading window reads it at launch and is black, not red, from
 * the next launch. The player's later choices always win ({@link DefaultsState}).
 * <p>The changes reach options.txt as text ({@link OptionsFile}), not through {@code Options.save()}: at this point only
 * vanilla's key mappings exist, and a save dropped every mod's key binding.
 */
public final class LoadDefaults {
    private static final Log LOG = new Log("[pack defaults] ");
    private static final Gson GSON = new Gson();   // options.txt writes its pack lists as compact JSON
    private static boolean done;

    private LoadDefaults() {
    }

    public static void apply(Options options, PackRepository repository) {
        if (done) {
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
            List<String> selected = new ArrayList<>(repository.getSelectedIds());   // bottom (lowest priority) first
            boolean packs = false;
            if (spec.has("resourcePacks")) {
                for (String what : PackList.enable(selected, repository.getAvailableIds(), spec.getAsJsonArray("resourcePacks"), state.object("seen"))) {
                    LOG.once("enabled:" + what, "enabled {}", what);
                    packs = true;
                }
            }
            if (spec.has("resourcePackOrder")) {
                packs |= order(selected, spec.getAsJsonArray("resourcePackOrder"), state.object("ordered"));
            }
            Map<String, String> lines = new LinkedHashMap<>();
            if (packs) {
                repository.setSelected(selected);
                keep(options, repository);
                lines.put("resourcePacks", GSON.toJson(options.resourcePacks));
                lines.put("incompatibleResourcePacks", GSON.toJson(options.incompatibleResourcePacks));
            }
            boolean dark = spec.has("darkLoadingScreen") && spec.get("darkLoadingScreen").getAsBoolean()
                    && !state.root().has("darkLoadingScreen");
            if (dark) {
                options.darkMojangStudiosBackground().set(true);
                state.root().addProperty("darkLoadingScreen", true);
                lines.put("darkMojangStudiosBackground", "true");
                LOG.once("dark", "loading screens set to black (Monochrome Logo on)");
            }
            if (!lines.isEmpty()) {
                OptionsFile.write(FMLPaths.GAMEDIR.get().resolve("options.txt"), lines);
            }
            state.save();
        } catch (Throwable t) {
            LOG.error("apply", "pack defaults left as they were", t);
        }
    }

    /** Applies each order rule once (both packs selected); returns whether the order changed. */
    static boolean order(List<String> selected, JsonArray rules, JsonObject ordered) {
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
        return changed;
    }

    /** Options' pack lists from the repository, as {@code Options.updateResourcePacks} does, without its reload. */
    private static void keep(Options options, PackRepository repository) {
        options.resourcePacks.clear();
        options.incompatibleResourcePacks.clear();
        for (Pack pack : repository.getSelectedPacks()) {
            if (!pack.isFixedPosition() && !pack.isHidden()) {
                options.resourcePacks.add(pack.getId());
                if (!pack.getCompatibility().isCompatible()) {
                    options.incompatibleResourcePacks.add(pack.getId());
                }
            }
        }
    }
}
