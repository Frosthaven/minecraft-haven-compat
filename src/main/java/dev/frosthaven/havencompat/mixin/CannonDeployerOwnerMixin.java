package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import dev.frosthaven.havencompat.schematics.PrintOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * The Schematicannon keeps the player who started its print (saved with the block, so a restart in the middle of a
 * print keeps it) and gives that owner to the deployers it launches; see {@link PrintOwner}.
 */
@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class CannonDeployerOwnerMixin implements PrintOwner.Cannon {
    @Unique private UUID haven$starter;

    @Override
    public void haven$starter(UUID player) {
        if (!player.equals(haven$starter)) {
            haven$starter = player;
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void haven$saveStarter(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket && haven$starter != null) {
            tag.putUUID(PrintOwner.STARTER, haven$starter);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void haven$loadStarter(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket && tag.hasUUID(PrintOwner.STARTER)) {
            haven$starter = tag.getUUID(PrintOwner.STARTER);
        }
    }

    @ModifyVariable(method = "launchBlock", at = @At("HEAD"), argsOnly = true)
    private CompoundTag haven$deployerOwner(CompoundTag data, BlockPos target, ItemStack stack, BlockState state) {
        return PrintOwner.launched(state, data, haven$starter);
    }
}
