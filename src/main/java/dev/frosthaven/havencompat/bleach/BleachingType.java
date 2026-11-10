package dev.frosthaven.havencompat.bleach;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Bulk bleaching: an encased fan blowing through bleach, loose or in a Fan Bleaching Catalyst ({@link BleachCatalyst});
 * {@link BleachProcess} for items. Living things in the
 * stream get the same as in the fluid ({@link BleachExposure}), the way a fan through lava sets them on fire; sheep in
 * it turn white.
 */
public final class BleachingType implements FanProcessingType {
    static final int COLOR = 0xECF1C4;
    private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0xEC / 255f, 0xF1 / 255f, 0xC4 / 255f), 1.0F);

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        return level.getFluidState(pos).getFluidType() == Bleach.TYPE.get()
                || BleachCatalyst.registered() && level.getBlockState(pos).is(BleachCatalyst.BLOCK.get());
    }

    @Override
    public int getPriority() {
        return 450;   // its own catalyst (the bleach fluid), so the value only orders Create's list
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return BleachProcess.canBleach(stack, level);
    }

    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        return BleachProcess.process(stack, level);
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        if (level.random.nextInt(8) != 0) return;
        level.addParticle(MIST, pos.x + (level.random.nextFloat() - .5f) * .5f, pos.y + .5f, pos.z + (level.random.nextFloat() - .5f) * .5f, 0, 1 / 16f, 0);
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        particleAccess.setColor(COLOR);
        particleAccess.setAlpha(.6f);
        if (random.nextFloat() < 1 / 32f) particleAccess.spawnExtraParticle(ParticleTypes.BUBBLE_POP, .125f);
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        BleachExposure.expose(entity);
        if (!level.isClientSide && entity instanceof Sheep sheep && sheep.getColor() != DyeColor.WHITE) {
            sheep.setColor(DyeColor.WHITE);
        }
        if (entity.isOnFire()) {
            entity.clearFire();   // it is a liquid, as washing puts fire out
        }
    }
}
