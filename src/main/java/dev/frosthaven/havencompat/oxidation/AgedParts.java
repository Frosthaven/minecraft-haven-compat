package dev.frosthaven.havencompat.oxidation;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.frosthaven.havencompat.Log;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Client. The aged look of moving parts on the copper blocks of {@link MoreOxidation}.
 * <p>
 * A spinning shaft or cogwheel is drawn by Flywheel from a fixed partial model that the block's visual picks by name
 * (Create Casing's copper shaft and cogwheels: {@code EncasedPartialModels.SHAFT_MODELS} and the cog maps), not from the
 * block state, so Create: Oxidation's texture swap on block models never reached them: the block aged, waxed and scraped,
 * but always looked new (Shane, 2026-11-27). Flywheel builds a block entity's visual in
 * {@code BlockEntityStorage.createRaw} and builds it again when the block state changes; while it builds one, the block's
 * state is known here ({@link #building}), and {@code Models.partial} takes the aged copy of a listed partial for the
 * state's stage. The copies ({@code haven_compat:block/oxidation/<stage>/<ns>/<path>}) come from
 * {@code tools/oxidation/aged_partials.py} for the models in {@code oxidation/partials.txt}.
 */
public final class AgedParts {
    private static final Log LOG = new Log("[aged parts] ");
    private static final String[] STAGES = {"exposed", "weathered", "oxidized"};
    /** original partial model id -> stage name -> its aged copy */
    private static final Map<ResourceLocation, Map<String, PartialModel>> AGED = new HashMap<>();
    private static final ThreadLocal<BlockState> BUILDING = new ThreadLocal<>();
    private static int logged;

    private AgedParts() {}

    /** Before model baking (the mod's construction): Flywheel bakes every partial model made by then. */
    public static void init() {
        for (String ref : MoreOxidation.read("/haven_compat/oxidation/partials.txt")) {
            ResourceLocation id = ResourceLocation.parse(ref);
            Map<String, PartialModel> byStage = new HashMap<>();
            for (String stage : STAGES)
                byStage.put(stage, PartialModel.of(ResourceLocation.fromNamespaceAndPath("haven_compat",
                        "block/oxidation/" + stage + "/" + id.getNamespace() + "/" + id.getPath().substring("block/".length()))));
            AGED.put(id, byStage);
        }
        LOG.info("aged copies of {} moving-part models", AGED.size());
    }

    /** The visual of a block entity with this state is being built (null: done). */
    public static void building(BlockState state) {
        if (state == null) BUILDING.remove(); else BUILDING.set(state);
    }

    /** The partial model to draw: the aged copy for the stage of the block being built, else the one given. */
    public static PartialModel pick(PartialModel partial) {
        BlockState state = BUILDING.get();
        if (state == null || partial == null) return partial;
        Map<String, PartialModel> byStage = AGED.get(partial.modelLocation());
        if (byStage == null) return partial;
        String stage = stage(state);
        PartialModel aged = stage == null ? null : byStage.get(stage);
        if (aged == null) return partial;
        if (logged++ < 5) LOG.info("{} drawn {} for {}", partial.modelLocation(), stage, state.getBlock());
        return aged;
    }

    private static String stage(BlockState state) {
        for (Property<?> p : state.getProperties())
            if (p.getName().equals("createoxidized_stage")) return name(state, p);
        return null;
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }
}
