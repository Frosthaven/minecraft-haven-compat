package dev.frosthaven.havencompat.trash;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * TrashSlot's trash slot stays visible on the screens the pack lists ({@code trashSlot.alwaysVisible} in
 * haven-pack-defaults.json: TrashSlot's own screen ids, the keys of {@code TrashSlotSaveState.json}). TrashSlot keeps one
 * {@code ContainerSettings} per screen id and its toggle key flips {@code isEnabled} on it; so each settings object of a
 * listed screen is switched on when {@code TrashSlotSaveState.getSettings} hands it out, and a later
 * {@code setEnabled(false)} on it is refused. Screens not listed keep TrashSlot's own toggle. TrashSlot is reached by
 * reflection only.
 */
public final class TrashSlotLock {
    private static final Log LOG = new Log("[trash slot] ");
    private static final String SAVE_STATE = "net.blay09.mods.trashslot.TrashSlotSaveState";
    /** Settings objects of listed screens (identity: ContainerSettings has no equals). */
    private static final Set<Object> LOCKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Set<String> listed;

    private TrashSlotLock() {
    }

    /** At the return of {@code TrashSlotSaveState.getSettings}: a listed screen's settings are switched on and locked. */
    public static void settingsReturned(Object settings) {
        if (settings == null || LOCKED.contains(settings)) {
            return;
        }
        String id = idOf(settings);
        if (id == null || !listed().contains(id)) {
            return;
        }
        enable(settings);
        LOCKED.add(settings);
        LOG.once("lock:" + id, "always visible on {} (the toggle key does not hide it there)", id);
    }

    /** True when TrashSlot tries to hide the slot on a listed screen. */
    public static boolean refusesHide(Object settings, boolean enabled) {
        return !enabled && LOCKED.contains(settings);
    }

    private static void enable(Object settings) {
        try {
            Method set = settings.getClass().getMethod("setEnabled", boolean.class);
            set.invoke(settings, true);
        } catch (ReflectiveOperationException e) {
            LOG.error("enable", "TrashSlot's ContainerSettings.setEnabled not found; the slot keeps TrashSlot's own state", e);
        }
    }

    /** The screen id TrashSlot files these settings under, or null. */
    private static String idOf(Object settings) {
        try {
            Class<?> state = Class.forName(SAVE_STATE);
            Object instance = state.getMethod("getInstance").invoke(null);
            Field field = state.getDeclaredField("cachedSettings");
            field.setAccessible(true);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) field.get(instance)).entrySet()) {
                if (entry.getValue() == settings) {
                    return String.valueOf(entry.getKey());
                }
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            LOG.error("lookup", "TrashSlot's saved screen list not found; the slot keeps TrashSlot's own state", e);
        }
        return null;
    }

    private static Set<String> listed() {
        if (listed == null) {
            Set<String> ids = new HashSet<>();
            try {
                JsonObject section = PackDefaults.section(PackDefaults.file(), "trashSlot");
                if (section != null && section.has("alwaysVisible")) {
                    for (JsonElement id : section.getAsJsonArray("alwaysVisible")) {
                        ids.add(id.getAsString());
                    }
                }
            } catch (Exception e) {
                LOG.error("defaults", "trashSlot in " + PackDefaults.FILE + " unreadable; no screen is locked", e);
            }
            LOG.once("list", "{} screen(s) with the trash slot always visible", ids.size());
            listed = ids;
        }
        return listed;
    }
}
