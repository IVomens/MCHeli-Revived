package com.ivomens.mcheli.api.weapon;

import net.minecraft.resources.ResourceLocation;

/**
 * What a {@link WeaponBehaviourProvider} registers its behaviours with. A weapon file picks them by id:
 *
 * <pre>
 * Type: atmissile
 * Guidance: mymod:beam_rider          # or a map: { Id: mymod:beam_rider, Lead: 6 }
 * ProjectileEffects:
 * - mymod:emp                         # or a map: { Id: mymod:emp, Seconds: 4 }
 * </pre>
 *
 * <p>The keys of a map other than {@code Id} are the behaviour's settings. MCHeli turns them into a
 * {@link net.minecraft.nbt.CompoundTag} (whole numbers become int or long tags, other numbers double tags, booleans
 * byte tags, lists list tags and maps compound tags) and hands it to the factory once per weapon file each time the
 * content loads, on both sides. Decode it with a {@code Codec} of your own, for example
 * {@code SETTINGS.parse(NbtOps.INSTANCE, settings).getOrThrow(IllegalArgumentException::new)}. A factory that throws
 * is reported in the content report and the weapon keeps its built-in behaviour, as for an id nobody registered.
 *
 * <p>Thread: only valid during {@link WeaponBehaviourProvider#registerBehaviours}. Stability: since API 1.1;
 * implemented by MCHeli only, so a minor version may add methods.
 */
public interface WeaponBehaviourRegistrar {

    /**
     * Registers a guidance law. A weapon with {@code Guidance: <id>} steers its projectiles with it instead of
     * the guidance its {@code Type} picks.
     *
     * @throws IllegalArgumentException when a guidance law with this id is already registered
     */
    void guidance(ResourceLocation id, GuidanceLaw.Factory factory);

    /**
     * Registers a projectile effect. A weapon that lists {@code <id>} under {@code ProjectileEffects} runs it on
     * each of its projectiles, in the order the file lists the effects.
     *
     * @throws IllegalArgumentException when a projectile effect with this id is already registered
     */
    void projectileEffect(ResourceLocation id, ProjectileEffect.Factory factory);
}
