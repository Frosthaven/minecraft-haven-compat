package dev.frosthaven.havencompat.oxidation;

import dev.frosthaven.havencompat.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * More blocks for Create: Oxidation (createoxidized 1.0.0): copper machines and copper-encased parts of other mods that it
 * does not age (Shane picked them, 2026-11-25). Create: Oxidation gives a block its two block state properties (stage,
 * waxed) when the block's exact class is on its fixed list, and ages a block that has them and is in the block tag
 * {@code createoxidized:oxidizable_create_blocks}. A class is too wide here (plain {@code Block}, and classes shared with
 * the brass and andesite siblings), so the properties go by block id: the id NeoForge's RegisterEvent or Registrate is
 * registering while the block is built ({@link #registering}), only for the ids in {@code oxidation/blocks.txt}. The tag
 * entries (data/createoxidized/tags) make them age; the client parts are the model swap for these ids and the extra
 * copper textures ({@code oxidation/textures.txt}) whose aged versions this mod ships under
 * {@code createoxidized:block/<stage>/compat/<mod>/}, made by {@code tools/oxidation/age_textures.py} (only copper pixels
 * changed).
 */
public final class MoreOxidation {
    private static final Log LOG = new Log("[more oxidation] ");
    public static final Set<String> BLOCKS = read("/haven_compat/oxidation/blocks.txt");
    public static final Set<String> TEXTURES = read("/haven_compat/oxidation/textures.txt");
    /** the block ids being registered right now, innermost last (a block built inside another's supplier) */
    private static final ThreadLocal<Deque<String>> REGISTERING = ThreadLocal.withInitial(ArrayDeque::new);

    private MoreOxidation() {}

    static Set<String> read(String path) {
        Set<String> out = new LinkedHashSet<>();
        try (InputStream in = MoreOxidation.class.getResourceAsStream(path)) {
            if (in == null) { LOG.warnOnce("read", "missing {}", path); return out; }
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(out::add);
        } catch (Exception e) {
            LOG.error("read", "could not read " + path, e);
        }
        return Collections.unmodifiableSet(out);
    }

    public static void enter(Object id) { REGISTERING.get().push(String.valueOf(id)); }

    public static void leave() { Deque<String> d = REGISTERING.get(); if (!d.isEmpty()) d.pop(); }

    /** a block of ours is being built now: it carries Create: Oxidation's properties */
    public static boolean registering() {
        String id = REGISTERING.get().peek();
        return id != null && BLOCKS.contains(id);
    }

    /** after the blocks are registered: says which listed ids lack the properties, read from the blocks themselves (some
     *  get them from Create: Oxidation's own class list before our hook is asked, so the hook's count is not the answer) */
    public static void report() {
        Set<String> missing = new LinkedHashSet<>();
        for (String id : BLOCKS) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.tryParse(id));
            if (block.isEmpty() || block.get().defaultBlockState().getProperties().stream().noneMatch(p -> p.getName().equals("createoxidized_stage"))) missing.add(id);
        }
        if (missing.isEmpty()) LOG.info("{} extra blocks age with Create: Oxidation", BLOCKS.size());
        else LOG.warnOnce("missing", "{} of {} extra blocks have no aging properties (not installed, or built outside the hooks): {}", missing.size(), BLOCKS.size(), missing);
    }
}
