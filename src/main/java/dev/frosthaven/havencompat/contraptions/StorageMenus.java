package dev.frosthaven.havencompat.contraptions;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server side. Any menu on storage that an entity carries (a chest, barrel, vault or backpack on a Create contraption, a
 * Sophisticated storage on a minecart or boat) closes when that entity is removed or replaced, is in another level than
 * the player, or the player is in another level than where the menu opened. A train keeps its contraption through a
 * portal or an unload in a new carriage entity with its own copy of the storage, so a menu left open showed and handed
 * out items the train no longer held: Shane took items from his train's chest that a station had already unloaded
 * (2026-11-27; a Sophisticated Storage chest, whose own check is only "alive and within reach").
 * <p>
 * Which menus: one that opened during a click on a contraption ({@code AbstractContraptionEntity.handlePlayerInteraction},
 * every mod's mounted storage) and every Sophisticated menu that names its entity ({@code getEntity()}: mounted chests,
 * barrels and backpacks, their settings screens, Storage In Motion). Block storage and the player's own items are left
 * alone. Each close logs one "[contraptions] ... closed" line.
 */
public final class StorageMenus {
    private static final Log LOG = new Log("[contraptions] ");

    /** a menu's entity and the level the player was in when it opened */
    private record Bound(Entity entity, Level level) {}
    private static final Map<AbstractContainerMenu, Bound> BOUND = new WeakHashMap<>();
    /** per menu class: its no-argument {@code getEntity()} (Sophisticated), or empty */
    private static final Map<Class<?>, Optional<Method>> ENTITY_GETTER = new ConcurrentHashMap<>();

    private StorageMenus() {}

    public static void register(IEventBus bus) {
        bus.addListener((PlayerTickEvent.Post e) -> { if (e.getEntity() instanceof ServerPlayer p) check(p); });
    }

    /** from the contraption click: a menu that is open now and was not before belongs to this entity */
    public static void clicked(ServerPlayer p, AbstractContainerMenu before, Entity on) {
        AbstractContainerMenu now = p.containerMenu;
        if (now != before && now != p.inventoryMenu) bind(now, on, p.level());
    }

    private static void bind(AbstractContainerMenu m, Entity e, Level at) {
        synchronized (BOUND) { BOUND.putIfAbsent(m, new Bound(e, at)); }
    }

    static void check(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        if (m == null || m == p.inventoryMenu) return;
        Bound b;
        synchronized (BOUND) { b = BOUND.get(m); }
        if (b == null) {
            Entity e = named(m);
            if (e == null) return;
            bind(m, e, p.level());
            synchronized (BOUND) { b = BOUND.get(m); }
        }
        String why = b.entity().isRemoved() ? "its entity was removed (portal, unload)"
            : b.entity().level() != p.level() ? "its entity is in another level"
            : p.level() != b.level() ? "the player changed level" : null;
        if (why == null) return;
        LOG.info("{}'s {} closed: {}", p.getName().getString(), m.getClass().getSimpleName(), why);
        p.closeContainer();
    }

    /** the entity a Sophisticated menu names, or null */
    private static Entity named(AbstractContainerMenu m) {
        Class<?> c = m.getClass();
        if (!c.getName().startsWith("net.p3pp3rf1y.")) return null;
        Optional<Method> g = ENTITY_GETTER.computeIfAbsent(c, k -> {
            try {
                Method x = k.getMethod("getEntity");
                return x.getParameterCount() == 0 && Optional.class.isAssignableFrom(x.getReturnType()) ? Optional.of(x) : Optional.empty();
            } catch (NoSuchMethodException ex) { return Optional.empty(); }
        });
        if (g.isEmpty()) return null;
        try {
            return ((Optional<?>) g.get().invoke(m)).orElse(null) instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.error("getter:" + c.getName(), "could not read the entity of " + c.getName(), ex);
            ENTITY_GETTER.put(c, Optional.empty());
            return null;
        }
    }
}
