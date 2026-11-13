package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import dev.frosthaven.havencompat.schematics.PrintOwner;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player who presses Play on a Schematicannon starts its print; see {@link PrintOwner}. */
@Mixin(value = ConfigureSchematicannonPacket.class, remap = false)
public abstract class CannonPrintStarterMixin {
    @Shadow @Final private ConfigureSchematicannonPacket.Option option;

    @Inject(method = "handle", at = @At("TAIL"))
    private void haven$starter(ServerPlayer player, CallbackInfo ci) {
        if (option == ConfigureSchematicannonPacket.Option.PLAY && player != null
                && player.containerMenu instanceof SchematicannonMenu menu
                && menu.contentHolder instanceof PrintOwner.Cannon cannon) {
            cannon.haven$starter(player.getUUID());
        }
    }
}
