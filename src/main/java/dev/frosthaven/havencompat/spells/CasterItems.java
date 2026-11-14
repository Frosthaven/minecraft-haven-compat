package dev.frosthaven.havencompat.spells;

/**
 * Which items cast an Ars Nouveau spell on a right-click: the item class implements {@code ICasterTool} and it, or a
 * mod superclass, has its own {@code use} (spell book, wand, caster tome, gauntlet, mirror, scry caster, spell bow,
 * fishing rod, Ars Elemental's horn). An {@code ICasterTool} that keeps vanilla's {@code use} casts another way
 * (Enchanter's Sword on a hit, Spell Parchment never, Spell Crossbow on release) and is not one. By name only: this
 * mod does not compile against Ars. No Minecraft classes here.
 */
public final class CasterItems {
    private static final String CASTER_TOOL = "com.hollingsworth.arsnouveau.api.item.ICasterTool";
    private static final String VANILLA = "net.minecraft.";
    private static final ClassValue<Boolean> CASTS_ON_USE = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> item) { return castsOnUse(item, CASTER_TOOL, VANILLA); }
    };

    private CasterItems() {
    }

    public static boolean castsOnUse(Class<?> item) { return CASTS_ON_USE.get(item); }

    static boolean castsOnUse(Class<?> item, String casterInterface, String vanillaPrefix) {
        return implementsNamed(item, casterInterface) && ownUse(item, vanillaPrefix);
    }

    private static boolean implementsNamed(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(name)) return true;
            for (Class<?> parent : c.getInterfaces()) if (implementsNamed(parent, name)) return true;
        }
        return false;
    }

    /** True when the nearest {@code use(level, player, hand)} up the class chain is not vanilla's. */
    private static boolean ownUse(Class<?> type, String vanillaPrefix) {
        for (Class<?> c = type; c != null && !c.getName().startsWith(vanillaPrefix); c = c.getSuperclass()) {
            for (var method : c.getDeclaredMethods()) {
                if (method.getName().equals("use") && method.getParameterCount() == 3 && !method.isSynthetic()) return true;
            }
        }
        return false;
    }
}
