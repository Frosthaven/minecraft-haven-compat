package dev.frosthaven.havencompat.ntgl;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * NTGL 3.2.0's reload and unload keys ({@code InputHandler.reloadUnload}) open a wheel with one entry per weapon mode
 * whose max ammo is above 0, plus {@code WeaponMode.PRIMARY} always added again. Max ammo comes from attachments that
 * do not look at the mode (Create Gunsmithing's shotgun pump: 10 for every mode), so the shotgun's scope mode
 * ({@code ntgl:secondary}, action {@code ntgl:scope}) got an entry: two "Main Weapon" entries for one magazine (the
 * ammo count is one item component for all modes). Here the wheel keeps one entry per mode and drops modes whose
 * action is scope; when one entry is left, its action runs at once, as NTGL itself does for a one-entry list (a hold
 * is then the same as a tap: a tap opened the wheel and its release ran the default, PRIMARY's reload or unload).
 */
public final class NtglReloadWheel {
    private static final Log LOG = new Log("[reload wheel] ");
    private static Method actionColor, actionRun, modeColor, setMode, weaponAction;
    private static Constructor<?> newData;
    private static Object scope;
    private static boolean looked, ok;

    private NtglReloadWheel() {}

    /**
     * Called just before NTGL shows the wheel. Removes the dropped entries from {@code actions} (the list NTGL passes
     * to the wheel); returns true when one entry was left and its action ran (the wheel is then not shown).
     */
    public static boolean beforeShow(List<?> actions, List<?> modes, ItemStack held, boolean reload) {
        if (!look()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || actions == null || modes == null || held == null) return false;
        try {
            List<Object> entryModes = new ArrayList<>();
            List<Boolean> uses = new ArrayList<>();
            for (Object action : actions) {
                Object mode = modeOf(action, modes);
                entryModes.add(mode);
                uses.add(mode == null || usesAmmo(held, player, mode));
            }
            List<Integer> keep = WheelEntries.kept(entryModes, uses);
            if (keep.size() == actions.size()) return false;
            Object single = keep.size() == 1 ? actions.get(keep.get(0)) : null;
            for (int i = actions.size() - 1; i >= 0; i--) if (!keep.contains(i)) actions.remove(i);
            String key = reload ? "reload" : "unload";
            if (single == null) {
                LOG.once(key + ":wheel", "NTGL's {} wheel: entries for modes without their own ammo use left out", key);
                return false;
            }
            LOG.once(key + ":single", "NTGL's {} wheel had one real entry: it runs at once, as a tap does", key);
            ((Runnable) actionRun.invoke(single)).run();
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("run", "cannot read NTGL's wheel entries; the wheel is shown as NTGL built it", e);
            return false;
        }
    }

    private static Object modeOf(Object action, List<?> modes) throws ReflectiveOperationException {
        Object color = actionColor.invoke(action);
        for (Object mode : modes) if (Objects.equals(modeColor.invoke(mode), color)) return mode;
        return null;
    }

    private static boolean usesAmmo(ItemStack held, LivingEntity player, Object mode) throws ReflectiveOperationException {
        Object data = setMode.invoke(newData.newInstance(held, player), mode);
        return weaponAction.invoke(null, data) != scope;
    }

    private static boolean look() {
        if (looked) return ok;
        looked = true;
        try {
            ClassLoader cl = NtglReloadWheel.class.getClassLoader();
            Class<?> action = Class.forName("com.nukateam.ntgl.modules.wheel.ActionWheel$WheelAction", false, cl);
            Class<?> mode = Class.forName("com.nukateam.ntgl.common.data.holders.WeaponMode", false, cl);
            Class<?> data = Class.forName("com.nukateam.ntgl.common.data.WeaponData", false, cl);
            Class<?> helper = Class.forName("com.nukateam.ntgl.common.util.util.WeaponModifierHelper", false, cl);
            Class<?> weaponActionType = Class.forName("com.nukateam.ntgl.common.data.holders.WeaponAction", false, cl);
            actionColor = action.getMethod("getColor");
            actionRun = action.getMethod("getAction");
            modeColor = mode.getMethod("getColor");
            newData = data.getConstructor(ItemStack.class, LivingEntity.class);
            setMode = data.getMethod("setWeaponMode", mode);
            weaponAction = helper.getMethod("getWeaponAction", data);
            scope = weaponActionType.getField("SCOPE").get(null);
            ok = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.error("reflect", "cannot find NTGL's wheel or weapon classes; the reload wheel stays as NTGL builds it", e);
        }
        return ok;
    }
}
