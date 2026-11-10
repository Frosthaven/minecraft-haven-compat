package dev.frosthaven.havencompat.bleach;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What bleach does to a living thing in it (the fluid) or in its air stream (a fan through it): Nausea the way the
 * sulfur lakes give it (4 s, refreshed every 10 ticks, ambient), and 1 damage every 2 s (fire does 1 per second).
 */
public final class BleachExposure {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("haven_compat", "bleach"));

    private BleachExposure() {}

    public static void expose(Entity entity) {
        if (entity.level().isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
            return;
        }
        long time = entity.level().getGameTime();
        if (time % 10 == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, true));
        }
        if (time % 40 == 0) {
            living.hurt(entity.level().damageSources().source(DAMAGE), 1.0F);
        }
    }
}
