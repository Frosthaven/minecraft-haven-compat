package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.foundation.utility.BlockHelper;
import dev.frosthaven.havencompat.schematics.PrintOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Create's {@code setPlacedBy} call for a block it lands from a schematic gets the print's starter as placer while a
 * Schematicannon lands its blocks; see {@link PrintOwner#placer}.
 */
@Mixin(value = BlockHelper.class, remap = false)
public abstract class SchematicPlacerMixin {
    @ModifyArg(method = "placeSchematicBlock", index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private static LivingEntity haven$placer(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        return PrintOwner.placer(level, state, placer);
    }
}
