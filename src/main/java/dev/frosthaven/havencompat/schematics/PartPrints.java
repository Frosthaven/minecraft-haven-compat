package dev.frosthaven.havencompat.schematics;

import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Blocks a Schematicannon skipped because they have no item of their own (Create asks for the block's item and skips
 * the block when there is none). On 2026-11-22 a printed Drilling Machine came out as its core block alone and the
 * steam engines without their shafts:
 *  - Create Ore Excavation's machine parts ({@code multiblock}, {@code io_block}, {@code kinetic_input}): the item of the
 *    Drilling Machine or Fluid Extractor places them, so they cost nothing here; the core block is charged as before.
 *    They stand only next to the rest of the machine, so a part printed alone falls away and drops nothing.
 *  - Create's powered shaft (a shaft a steam engine has taken over): charged as a shaft; it turns back into a shaft
 *    without an engine and the engine takes it over again.
 */
final class PartPrints {
    private static final String[] FREE_PARTS = {"createoreexcavation:multiblock", "createoreexcavation:io_block", "createoreexcavation:kinetic_input"};

    private PartPrints() {}

    /** The number of blocks registered (each one found in the block registry). */
    static int register() {
        int n = 0;
        for (String id : FREE_PARTS) {
            Block block = block(id);
            if (block != null) {
                SchematicRequirementRegistries.BLOCKS.register(block, (state, be) -> ItemRequirement.NONE);
                n++;
            }
        }
        Block powered = block("create:powered_shaft");
        Block shaft = block("create:shaft");
        if (powered != null && shaft != null && shaft.asItem() != Items.AIR) {
            SchematicRequirementRegistries.BLOCKS.register(powered,
                    (state, be) -> new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, shaft.asItem()));
            n++;
        }
        return n;
    }

    private static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return block == Blocks.AIR ? null : block;
    }
}
