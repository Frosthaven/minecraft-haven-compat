package dev.frosthaven.havencompat.oxidation;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Server side. Copper carried by a Create contraption (a train, a bearing, an elevator) ages as it would in the world:
 * the blocks of a contraption get no random ticks, so a train's copper stayed new (Shane, 2026-11-28). Every
 * {@link #INTERVAL} ticks each contraption gives each of its copper blocks the random ticks it missed (the world's
 * randomTickSpeed rate, at most 20 intervals' worth) and ages them by the world's own rule: Create: Oxidation's for blocks with its stage and wax
 * properties (its blocks and the ones haven-compat adds to it, MoreOxidation), vanilla's for WeatheringCopper. Neighbours
 * are the contraption's own blocks. Waxed blocks stay. All of one contraption's changes go out at once, so its model is
 * rebuilt at most once per interval (Create rebuilds the whole contraption on a block change). A carriage in two levels at
 * once ages in one copy and the change goes to both; the copy Create builds at a portal starts from the live carriage
 * (contraptions/CarriageCopies). Logs "[contraption aging]".
 */
public final class ContraptionAging {
    private static final Log LOG = new Log("[contraption aging] ");
    /** ticks between a contraption's aging passes (a minute) */
    static final int INTERVAL = 1200;
    /** vanilla's chance that a random tick tries to age a weathering copper block (ChangeOverTimeBlock) */
    private static final float VANILLA_TRY = 0.05688889f;
    private static final int REACH = 4;

    private ContraptionAging() {}

    /** Create: Oxidation's two properties on one block, or none */
    private record Props(Property<?> stage, Property<Boolean> waxed) {}
    private static final Map<Object, Optional<Props>> PROPS = new HashMap<>();
    private static Method enabled, speed;
    /** each carriage's (or other contraption's) last pass, in game time */
    private static final Map<Object, Long> LAST = new java.util.WeakHashMap<>();
    private static boolean looked;

    public static void tick(AbstractContraptionEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.getContraption() == null) return;
        /* a train carriage in two levels at once (a portal) has a copy in each: one of them ages, the others follow. Its
           time is the carriage's, not the entity's: a portal makes a new entity, and a train through portals every few
           seconds never reached a pass on its entities' own ticks (rig, 2026-11-28) */
        Object key = e;
        if (e instanceof CarriageContraptionEntity ce && ce.getCarriage() != null) {
            if (ce.getCarriage().anyAvailableEntity() != ce) return;
            key = ce.getCarriage();
        }
        long now = level.getGameTime();
        Long last = LAST.get(key);
        if (last == null) { LAST.put(key, now); return; }
        if (now - last < INTERVAL) return;
        LAST.put(key, now);
        int rate = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (rate <= 0) return;
        try {
            age(e, level.random, Math.min(now - last, 20L * INTERVAL) * rate / 4096.0);
        } catch (RuntimeException ex) {
            LOG.error("pass", "an aging pass failed on " + e, ex);
        }
    }

    /** the random ticks each block missed (mean {@code lambda}), then the changes, sent together */
    static void age(AbstractContraptionEntity e, RandomSource r, double lambda) {
        Map<BlockPos, StructureBlockInfo> blocks = e.getContraption().getBlocks();
        Map<BlockPos, BlockState> now = new HashMap<>();
        List<BlockPos> copper = new ArrayList<>();
        for (var en : blocks.entrySet()) {
            BlockState s = en.getValue().state();
            if (stage(s) >= 0) { now.put(en.getKey(), s); if (canAge(s)) copper.add(en.getKey()); }
        }
        if (copper.isEmpty()) return;
        double ox = oxidationTry();
        Map<BlockPos, BlockState> changed = new HashMap<>();
        for (BlockPos p : copper) {
            for (int n = poisson(r, lambda); n > 0; n--) {
                BlockState s = now.get(p);
                BlockState next = randomTick(s, p, now, r, ox);
                if (next != null) { now.put(p, next); changed.put(p, next); }
            }
        }
        List<AbstractContraptionEntity> copies = new ArrayList<>(List.of(e));
        if (e instanceof CarriageContraptionEntity ce && ce.getCarriage() != null) ce.getCarriage().forEachPresentEntity(o -> { if (o != e) copies.add(o); });
        for (var c : changed.entrySet()) {
            StructureBlockInfo old = blocks.get(c.getKey());
            for (AbstractContraptionEntity to : copies)
                if (to.getContraption() != null && to.getContraption().getBlocks().containsKey(c.getKey()))
                    to.setBlock(c.getKey(), new StructureBlockInfo(old.pos(), c.getValue(), old.nbt()));
        }
        if (!changed.isEmpty()) LOG.once("aged", "{} copper block(s) on {} aged a stage (the first contraption pass that aged any)", changed.size(), e.getName().getString());
    }

    /** one random tick, as the block would get it in the world: the next state, or null */
    static BlockState randomTick(BlockState s, BlockPos p, Map<BlockPos, BlockState> around, RandomSource r, double oxTry) {
        Props pr = props(s);
        boolean ox = pr != null;
        if (ox ? r.nextDouble() >= oxTry : r.nextFloat() >= VANILLA_TRY) return null;
        int mine = stage(s), same = 0, older = 0;
        for (BlockPos q : BlockPos.withinManhattan(p, REACH, REACH, REACH)) {
            if (q.distManhattan(p) > REACH) break;
            if (q.equals(p)) continue;
            BlockState n = around.get(q);
            if (n == null) continue;
            Props np = props(n);
            if (np != null && n.getValue(np.waxed())) continue;  /* waxed neighbours do not count (Create: Oxidation) */
            int st = stage(n);
            if (st < 0 || !ox && np != null) continue;           /* vanilla copper counts vanilla copper only */
            if (st < mine) return null;
            if (st == mine) same++; else older++;
        }
        float f = (older + 1f) / (older + same + 1f);
        float mod = mine == 0 ? 0.75f : 1f;
        if (r.nextFloat() >= mod * f * f) return null;
        return ox ? next(s, pr) : WeatheringCopper.getNext(s.getBlock()).map(b -> b.withPropertiesOf(s)).orElse(null);
    }

    /** 0 unaffected .. 3 oxidized; -1 for anything else */
    static int stage(BlockState s) {
        Props pr = props(s);
        if (pr != null) return ((Enum<?>) s.getValue(pr.stage())).ordinal();
        return s.getBlock() instanceof WeatheringCopper w ? w.getAge().ordinal() : -1;
    }

    static boolean canAge(BlockState s) {
        Props pr = props(s);
        if (pr != null) return !s.getValue(pr.waxed()) && stage(s) < 3 && oxidationOn();
        return s.getBlock() instanceof WeatheringCopper && WeatheringCopper.getNext(s.getBlock()).isPresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState next(BlockState s, Props pr) {
        Comparable cur = s.getValue(pr.stage());
        for (Object v : pr.stage().getPossibleValues())
            if (((Enum<?>) v).ordinal() == ((Enum<?>) cur).ordinal() + 1) return s.setValue((Property) pr.stage(), (Comparable) v);
        return null;
    }

    /** Create: Oxidation's two properties on a block it ages (its own OxidationProperties.isOxidizable: it adds the
     *  properties to every block of the classes it lists, andesite casings too, and ages only the copper ones) */
    @SuppressWarnings("unchecked")
    private static Props props(BlockState s) {
        return PROPS.computeIfAbsent(s.getBlock(), b -> {
            if (!oxidizable(s)) return Optional.empty();
            Property<?> st = null; Property<Boolean> wx = null;
            for (Property<?> p : s.getProperties()) {
                if (p.getName().equals("createoxidized_stage")) st = p;
                else if (p.getName().equals("createoxidized_waxed") && p.getValueClass() == Boolean.class) wx = (Property<Boolean>) p;
            }
            return st != null && wx != null ? Optional.of(new Props(st, wx)) : Optional.empty();
        }).orElse(null);
    }

    private static Method isOxidizable;

    private static boolean oxidizable(BlockState s) {
        look();
        if (isOxidizable == null) return false;
        try { return (boolean) isOxidizable.invoke(null, s); } catch (ReflectiveOperationException ex) { return false; }
    }

    /** Create: Oxidation's own switch and speed (CreateOxidizedCommon.enabled, speedFactor); on and 1 when unread */
    private static void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> c = Class.forName("dev.mrmulle.createoxidized.CreateOxidizedCommon");
            enabled = c.getMethod("enabled");
            speed = c.getMethod("speedFactor");
            isOxidizable = Class.forName("dev.mrmulle.createoxidized.OxidationProperties").getMethod("isOxidizable", BlockState.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOG.once("noconfig", "Create: Oxidation not found ({}): only vanilla copper ages on contraptions", ex.toString());
        }
    }

    private static boolean oxidationOn() {
        look();
        try { return enabled == null || (boolean) enabled.invoke(null); } catch (ReflectiveOperationException ex) { return true; }
    }

    /** the chance that a random tick tries to age a Create: Oxidation block (its randomTick: 1 - 0.94311111^speed) */
    private static double oxidationTry() {
        look();
        double f = 1;
        try { if (speed != null) f = (double) speed.invoke(null); } catch (ReflectiveOperationException ex) { }
        return f <= 0 ? 0 : 1 - Math.pow(0.94311111, f);
    }

    /** Knuth's sampler: the random ticks a block got in the interval */
    static int poisson(RandomSource r, double lambda) {
        double l = Math.exp(-lambda), p = 1;
        int k = 0;
        do { k++; p *= r.nextDouble(); } while (p > l && k < 1000);
        return k - 1;
    }
}
