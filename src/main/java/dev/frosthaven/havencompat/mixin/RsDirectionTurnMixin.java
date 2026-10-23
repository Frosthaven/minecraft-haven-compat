package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.schematics.SavedPositions;
import dev.frosthaven.havencompat.schematics.StateTurn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refined Storage 2's directional blocks answer {@code rotate} with one wrench step whatever the rotation, and have no
 * {@code mirror}. Structure placement turns every block (with {@code Rotation.NONE} too), so each importer, exporter,
 * grid or drive in a schematic printed one step off. While a structure is placed ({@link SavedPositions#placing()}),
 * {@code rotate} turns by the rotation; the wrench keeps its step.
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.support.AbstractDirectionalBlock", remap = false)
public abstract class RsDirectionTurnMixin extends Block {
    private RsDirectionTurnMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "rotate(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void haven$placedTurn(BlockState state, Rotation rotation, CallbackInfoReturnable<BlockState> cir) {
        if (SavedPositions.placing()) {
            cir.setReturnValue(StateTurn.namedDirection(state, "direction", rotation::rotate));
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return StateTurn.namedDirection(state, "direction", mirror::mirror);
    }
}
