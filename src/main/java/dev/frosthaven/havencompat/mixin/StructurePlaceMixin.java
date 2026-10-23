package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.frosthaven.havencompat.schematics.SavedPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Links in placed block data follow the placement ({@link SavedPositions#placed}); {@link SavedPositions#placing()} while it runs. */
@Mixin(StructureTemplate.class)
public abstract class StructurePlaceMixin {
    @Inject(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;", at = @At("RETURN"))
    private static void haven$placedPositions(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, List<StructureTemplate.StructureBlockInfo> infos, StructureTemplate template, CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir) {
        SavedPositions.placed(cir.getReturnValue(), offset, settings);
    }

    @WrapMethod(method = "placeInWorld")
    private boolean haven$placing(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, RandomSource random, int flags, Operation<Boolean> original) {
        SavedPositions.placing(true);
        try {
            return original.call(level, offset, pos, settings, random, flags);
        } finally {
            SavedPositions.placing(false);
        }
    }
}
