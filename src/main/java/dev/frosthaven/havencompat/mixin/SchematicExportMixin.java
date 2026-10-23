package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.schematics.SchematicExport;
import dev.frosthaven.havencompat.schematics.SavedPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A schematic saved by the Schematic and Quill (in the client or, for "save and upload", on the server); see {@link SavedPositions}. */
@Mixin(value = SchematicExport.class, remap = false)
public abstract class SchematicExportMixin {
    @WrapOperation(method = "saveSchematic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;fillFromWorld(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Vec3i;ZLnet/minecraft/world/level/block/Block;)V"))
    private static void haven$relativePositions(StructureTemplate template, Level level, BlockPos origin, Vec3i size, boolean entities, Block ignore, Operation<Void> original) {
        original.call(template, level, origin, size, entities, ignore);
        SavedPositions.saved(template, origin, level.isClientSide());
    }
}
