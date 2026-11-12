package dev.frosthaven.havencompat.backpacks;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * A backpack on a moving Create contraption drew white. Sophisticated Backpacks Create Integration empties the
 * backpack's block data in the contraption ({@code clearNbt}), so the client's render block entity (in Create's
 * virtual render level) has no backpack; its refresh ({@code refreshRenderBlockEntity}) calls
 * {@code BackpackBlockEntity.setBackpack}, which in that level (not a client level) resolves a server-side wrapper and
 * gets the no-op one: colour -1, white. The render block entity's colours come from {@code pendingLoadedBackpack} when
 * it is set ({@code getMainColor}/{@code getAccentColor}), so after each refresh it is set to the mounted backpack's
 * stack, which carries the colours; until the mounted backpack is linked to its contraption entity (not on its first
 * client tick) each client tick tries again. Reached by reflection only.
 */
public final class MountedBackpackColour {
    private static final Log LOG = new Log("[mounted backpack] ");

    private MountedBackpackColour() {
    }

    /** True once the render block entity holds the backpack stack (and the contraption is redrawn). */
    public static boolean fill(Object mounted) {
        try {
            Object entity = call(mounted, "getEntity");
            if (entity == null) {
                return false;   // not linked to its contraption entity yet: the next client tick tries again
            }
            Object contraption = call(entity, "getContraption");
            BlockPos localPos = (BlockPos) field(mounted.getClass(), "localPos").get(mounted);
            Method clientSide = contraption.getClass().getMethod("getBlockEntityClientSide", BlockPos.class);
            Object blockEntity = clientSide.invoke(contraption, localPos);
            if (blockEntity == null || !blockEntity.getClass().getName().endsWith("BackpackBlockEntity")) {
                return false;
            }
            ItemStack stack = (ItemStack) call(mounted, "getStorageStack");
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            field(blockEntity.getClass(), "pendingLoadedBackpack").set(blockEntity, stack.copy());
            contraption.getClass().getMethod("invalidateClientContraptionStructure").invoke(contraption);
            LOG.once("fill", "moving backpacks draw with their own colours");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("fill", "Sophisticated Backpacks or Create changed; a moving backpack may draw white", e);
            return true;   // do not retry every tick
        }
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // look in the superclass
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
