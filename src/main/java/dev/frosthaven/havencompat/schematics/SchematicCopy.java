package dev.frosthaven.havencompat.schematics;

import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers each {@link BlockData} with Create, by block entity type id (no link against the mods: their classes are
 * never loaded here): Create's safe data writer (what is copied), its block entity requirement (what the Schematicannon
 * charges, on top of the block's own item) and its block entity transformer (turns). {@link #of} is the lookup for
 * {@link SavedPositions} and {@link ServerFill}.
 */
final class SchematicCopy {
    private static final Map<BlockEntityType<?>, BlockData> BY_TYPE = new ConcurrentHashMap<>();

    private SchematicCopy() {
    }

    /** Registers {@code data} for every listed type id that exists; returns how many did. */
    static int register(BlockData data, String... typeIds) {
        int found = 0;
        for (String id : typeIds) {
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(id));
            if (type != null && !BY_TYPE.containsKey(type)) {
                register(type, data);
                found++;
            }
        }
        return found;
    }

    /** Registers {@code data} for every block entity type of {@code namespace} without a writer of its own; returns how many. */
    static int registerNamespace(BlockData data, String namespace) {
        int found = 0;
        for (Map.Entry<ResourceKey<BlockEntityType<?>>, BlockEntityType<?>> e : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet()) {
            if (e.getKey().location().getNamespace().equals(namespace) && !BY_TYPE.containsKey(e.getValue())
                    && SafeNbtWriterRegistry.REGISTRY.get(e.getValue()) == null) {
                register(e.getValue(), data);
                found++;
            }
        }
        return found;
    }

    private static void register(BlockEntityType<?> type, BlockData data) {
        BY_TYPE.put(type, data);
        SafeNbtWriterRegistry.REGISTRY.register(type, (be, out, registries) ->
                data.copy(be, be.saveWithoutMetadata(registries), out, Obtainable::test, registries));
        SchematicRequirementRegistries.BLOCK_ENTITIES.register(type, (be, state) -> requirement(data, be));
        MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(type, (be, transform) -> transform(data, be, transform));
    }

    static String id(BlockEntity be) {
        return String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
    }

    @Nullable
    static BlockData of(@Nullable BlockEntityType<?> type) {
        return type == null ? null : BY_TYPE.get(type);
    }

    @Nullable
    static BlockData of(CompoundTag saved) {
        if (!saved.contains("id")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(saved.getString("id"));
        return id == null ? null : of(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id));
    }

    private static ItemRequirement requirement(BlockData data, BlockEntity be) {
        if (be.getLevel() == null) {
            return ItemRequirement.NONE;
        }
        HolderLookup.Provider registries = be.getLevel().registryAccess();
        List<StackRequirement> charged = data.items(be, be.saveWithoutMetadata(registries), registries).stream()
                .filter(stack -> !stack.isEmpty() && Obtainable.test(stack))
                .map(stack -> new StackRequirement(stack.copy(), ItemUseType.CONSUME))
                .toList();
        return charged.isEmpty() ? ItemRequirement.NONE : new ItemRequirement(charged);
    }

    private static void transform(BlockData data, BlockEntity be, StructureTransform transform) {
        if (be.getLevel() == null) {
            return;
        }
        HolderLookup.Provider registries = be.getLevel().registryAccess();
        CompoundTag full = be.saveWithoutMetadata(registries);
        CompoundTag before = full.copy();
        data.turn(full, new BlockData.Turn(transform.rotation, transform.mirror));
        if (!full.equals(before)) {
            be.loadWithComponents(full, registries);
        }
    }
}
