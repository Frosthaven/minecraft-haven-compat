package dev.frosthaven.havencompat.tracks;

import com.railwayteam.railways.ModSetup;
import com.railwayteam.railways.Railways;
import com.railwayteam.railways.compat.tracks.TrackCompatUtils;
import com.railwayteam.railways.registry.CRBlocks;
import com.railwayteam.railways.registry.CRItems;
import com.railwayteam.railways.registry.CRTrackMaterials;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackMaterialFactory;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Train tracks of Haven Backports' woods (pale oak, poplar) in the three gauges, made the way Steam 'n' Rails makes the
 * tracks of other mods' woods ({@code GenericTrackCompat}, which only takes the mods of its own list): the same block
 * classes and ids ({@code railways:track_haven_backports_<wood>[_wide|_narrow]}), its gauge maps, its incomplete items
 * and its tracks tab. Blockstates, models, textures, names, tags, loot tables and recipes are files of this mod
 * ({@code tools/wood_tracks.py}); Steam 'n' Rails writes those in its data generation only.
 */
public final class WoodTracks {
    private static final String WOODS_MOD = "haven_backports";
    private static final Map<String, String> WOODS = Map.of("pale_oak", "Pale Oak", "poplar", "Poplar");

    private WoodTracks() {}

    /** Called at the end of Steam 'n' Rails' own registration ({@code mixin/RailwaysSetupMixin}). */
    public static void register() {
        ModSetup.useTracksTab();
        WOODS.forEach(WoodTracks::add);
    }

    private static void add(String wood, String name) {
        ResourceLocation particle = ResourceLocation.fromNamespaceAndPath("haven_compat",
                "block/track/" + wood + "/standard_track_crossing_" + wood);
        NonNullSupplier<?>[] block = new NonNullSupplier<?>[1];
        TrackMaterial standard = material(wood, name, particle).block(() -> track(block[0])).build();
        block[0] = TrackCompatUtils.makeTrack(standard, (context, provider) -> {}, track -> {}, properties -> properties);
        incomplete(standard);

        TrackMaterial wide = material(wood + "_wide", "Wide " + name, particle)
                .trackType(CRTrackMaterials.CRTrackType.WIDE_GAUGE).noRecipeGen()
                .block(() -> CRBlocks.WIDE_GAUGE_TRACKS.get(CRTrackMaterials.WIDE_GAUGE.get(standard))).build();
        CRTrackMaterials.WIDE_GAUGE.put(standard, wide);
        CRTrackMaterials.WIDE_GAUGE_REVERSE.put(wide, standard);
        CRBlocks.WIDE_GAUGE_TRACKS.put(wide,
                TrackCompatUtils.makeTrack(wide, (context, provider) -> {}, track -> {}, properties -> properties));
        incomplete(wide);

        TrackMaterial narrow = material(wood + "_narrow", "Narrow " + name, particle)
                .trackType(CRTrackMaterials.CRTrackType.NARROW_GAUGE).noRecipeGen()
                .block(() -> CRBlocks.NARROW_GAUGE_TRACKS.get(CRTrackMaterials.NARROW_GAUGE.get(standard))).build();
        CRTrackMaterials.NARROW_GAUGE.put(standard, narrow);
        CRTrackMaterials.NARROW_GAUGE_REVERSE.put(narrow, standard);
        CRBlocks.NARROW_GAUGE_TRACKS.put(narrow,
                TrackCompatUtils.makeTrack(narrow, (context, provider) -> {}, track -> {}, properties -> properties));
        incomplete(narrow);
    }

    /** A material with its tie and rail segment models: {@code haven_compat:block/track/<wood>[_gauge]/}. */
    private static TrackMaterialFactory material(String path, String name, ResourceLocation particle) {
        return TrackMaterialFactory.make(ResourceLocation.fromNamespaceAndPath(WOODS_MOD, path)).lang(name).particle(particle)
                .customModels(() -> () -> model(path, "tie"), () -> () -> model(path, "segment_left"),
                        () -> () -> model(path, "segment_right"));
    }

    private static PartialModel model(String path, String part) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath("haven_compat", "block/track/" + path + "/" + part));
    }

    @SuppressWarnings("unchecked")
    private static NonNullSupplier<? extends TrackBlock> track(NonNullSupplier<?> block) {
        return (NonNullSupplier<? extends TrackBlock>) block;
    }

    private static void incomplete(TrackMaterial material) {
        CRItems.ITEM_INCOMPLETE_TRACK.put(material, Railways.registrate()
                .item("track_incomplete_" + WOODS_MOD + "_" + material.resourceName(), SequencedAssemblyItem::new)
                .register());
    }
}
