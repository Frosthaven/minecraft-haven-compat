package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import dev.frosthaven.havencompat.contraptions.StorageMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A contraption storage menu stays open only on the carriage it was opened on; see {@link StorageMenus}. */
@Mixin(value = MountedItemStorage.class, remap = false)
public abstract class MountedStorageMenuMixin {
    @Inject(method = "handleInteraction", at = @At("HEAD"))
    private void havenCompat$opened(ServerPlayer player, Contraption contraption, StructureBlockInfo info, CallbackInfoReturnable<Boolean> cir) {
        StorageMenus.opened(contraption);
    }

    @Inject(method = "isMenuValid", at = @At("HEAD"), cancellable = true)
    private void havenCompat$valid(ServerPlayer player, Contraption contraption, Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        if (!StorageMenus.same(player, contraption)) cir.setReturnValue(false);
    }
}
