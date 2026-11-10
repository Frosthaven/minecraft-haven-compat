package dev.frosthaven.havencompat.dyeing;

import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import java.util.List;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The nozzle's value box, drawn the way Create draws a scroll value ({@code ScrollValueRenderer.tick/addBox}, Create
 * 6.0.10): the face looked at gets a {@link ValueBox.TextValueBox} with the position letter on
 * {@link CenteredSideValueBoxTransform} (the slot at 8, 8, 15.5), the wide outline when the hit is on the slot
 * ({@code testHit}: within 4 px), and then Create's hover tip, title and "Right-click to change".
 */
public final class NozzleValueBox {
    private record Slot(BlockPos pos) {}

    private NozzleValueBox() {}

    public static void register(IEventBus bus) {
        bus.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ControlledCatalystBlock)) {
            return;
        }
        Direction face = hit.getDirection();
        CenteredSideValueBoxTransform transform = new CenteredSideValueBoxTransform();
        transform.fromSide(face);
        boolean highlight = transform.testHit(mc.level, pos, state, hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
        AABB bb = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.5).contract(0, 0, -0.5).move(0, 0, -0.125);
        MutableComponent label = Component.translatable("haven_compat.dyeing.position");
        DyePosition position = state.getValue(ControlledCatalystBlock.POSITION);
        ValueBox box = new ValueBox.TextValueBox(label, bb, pos, state, Component.literal(position.letter()));
        box.passive(!highlight).wideOutline();
        Outliner.getInstance().showOutline(new Slot(pos), box.transform(transform)).highlightFace(face);
        if (highlight) {
            CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(List.of(label.copy(), Component.translatable("haven_compat.dyeing.change")));
        }
    }
}
