package dev.frosthaven.havencompat.trash;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import java.lang.reflect.Method;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * TrashSlot's trash slot is shown on every screen with the player's hotbar and hidden on screens without one (nothing
 * to drag into it). TrashSlot keeps one {@code ContainerSettings} per screen id and its toggle key flips
 * {@code isEnabled} on it, so each settings object is set when {@code TrashSlotSaveState.getSettings} hands it out and a
 * later {@code setEnabled} the other way is refused: the toggle key does nothing. That covers every screen TrashSlot
 * handles (any container screen but the creative inventory).
 *
 * <p>TrashSlot's default spot is under the bottom-right corner of the screen's {@code imageWidth} x {@code imageHeight}
 * box, which floats away from the inventory on screens that draw a smaller panel in a bigger box. The slot goes under
 * the bottom-right corner of the player's hotbar panel instead ({@link TrashSpot}); a pack spot for a screen id
 * ({@code trashSlot.positions} in haven-pack-defaults.json: id to {@code [x, y]}, TrashSlot's units) wins over that. Both
 * apply only while the slot still sits on TrashSlot's default, so a slot the player dragged stays where they put it.
 * TrashSlot is reached by reflection only.
 */
public final class TrashSlotLock {
    private static final Log LOG = new Log("[trash slot] ");
    /** Settings objects already handled, and whether their slot is held shown (identity: ContainerSettings has no equals). */
    private static final Map<Object, Boolean> HELD = Collections.synchronizedMap(new WeakHashMap<>());
    private static Map<String, int[]> positions;

    private TrashSlotLock() {
    }

    /**
     * At the return of {@code TrashSlotSaveState.getSettings(screen, layout)}: on a screen with the player's hotbar the
     * slot is switched on, held there and placed; on a screen without one (nothing to drag into it) it is switched off
     * and held off.
     */
    public static void settingsReturned(Object screen, Object layout, Object settings) {
        if (settings == null || HELD.containsKey(settings)) {
            return;
        }
        boolean shown = screen instanceof AbstractContainerScreen<?> container && TrashSpot.underHotbar(container) != null;
        try {
            call(settings, "setEnabled", new Class<?>[] {boolean.class}, shown);
            HELD.put(settings, shown);
            if (shown) {
                place(screen, layout, settings);
            } else {
                Object id = call(layout, "getContainerId", new Class<?>[] {AbstractContainerScreen.class}, screen);
                LOG.once("hidden:" + id, "no hotbar on {}; trash slot hidden", id);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("lock", "TrashSlot's settings API not found; the slot keeps TrashSlot's own state", e);
        }
    }

    /** True when TrashSlot (its toggle key) tries to change a held slot. */
    public static boolean refusesChange(Object settings, boolean enabled) {
        Boolean held = HELD.get(settings);
        return held != null && held != enabled;
    }

    private static void place(Object screen, Object layout, Object settings) throws ReflectiveOperationException {
        Class<?> screenType = Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
        int defaultX = (int) call(layout, "getDefaultSlotX", new Class<?>[] {screenType}, screen);
        int defaultY = (int) call(layout, "getDefaultSlotY", new Class<?>[] {screenType}, screen);
        boolean untouched = (int) call(settings, "getSlotX", new Class<?>[0]) == defaultX
                && (int) call(settings, "getSlotY", new Class<?>[0]) == defaultY
                && (float) call(settings, "getAnchorX", new Class<?>[0]) == 0.5F
                && (float) call(settings, "getAnchorY", new Class<?>[0]) == 0.5F;
        if (!untouched || !(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        String id = String.valueOf(call(layout, "getContainerId", new Class<?>[] {screenType}, screen));
        int[] spot = positions().get(id);
        if (spot == null) {
            spot = TrashSpot.underHotbar(container);
            if (spot == null || TrashSpot.close(spot, defaultX, defaultY)) {
                return;
            }
        }
        if (spot[0] == defaultX && spot[1] == defaultY) {
            return;
        }
        call(settings, "setSlotX", new Class<?>[] {int.class}, spot[0]);
        call(settings, "setSlotY", new Class<?>[] {int.class}, spot[1]);
        LOG.once("place:" + id, "slot moved from {},{} to {},{} on {}", defaultX, defaultY, spot[0], spot[1], id);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] types) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException ignored) {
                // try the superclass, then the public interface methods below
            }
        }
        return type.getMethod(name, types);
    }

    private static Map<String, int[]> positions() {
        if (positions == null) {
            Map<String, int[]> map = new HashMap<>();
            try {
                JsonObject section = PackDefaults.section(PackDefaults.file(), "trashSlot");
                if (section != null && section.has("positions")) {
                    for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject("positions").entrySet()) {
                        JsonArray xy = entry.getValue().getAsJsonArray();
                        map.put(entry.getKey(), new int[] {xy.get(0).getAsInt(), xy.get(1).getAsInt()});
                    }
                }
            } catch (Exception e) {
                LOG.error("defaults", "trashSlot.positions in " + PackDefaults.FILE + " unreadable; TrashSlot's own spots are kept", e);
            }
            LOG.once("list", "trash slot always visible; {} screen(s) with a pack spot", map.size());
            positions = map;
        }
        return positions;
    }
}
