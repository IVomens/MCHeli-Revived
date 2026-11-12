package com.ivomens.mcheli.example.weapon;

import com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider;
import com.ivomens.mcheli.api.weapon.WeaponBehaviourRegistrar;
import com.ivomens.mcheli.example.ExampleAddon;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the addon's weapon behaviours: a beam-riding guidance law and an electromagnetic pulse. MCHeli finds
 * this class through {@code META-INF/services/com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider} before it
 * loads its content; the weapon files in the jar's pack ({@code example_beam_rider}, {@code example_emp_rocket})
 * pick the behaviours by id.
 */
public final class ExampleBehaviours implements WeaponBehaviourProvider {

    /** {@code Guidance: mcheli_example:beam_rider}. */
    public static final ResourceLocation BEAM_RIDER = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID,
            "beam_rider");
    /** {@code ProjectileEffects: [mcheli_example:emp]}. */
    public static final ResourceLocation EMP = ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "emp");

    @Override
    public void registerBehaviours(WeaponBehaviourRegistrar behaviours) {
        behaviours.guidance(BEAM_RIDER, BeamRider::fromSettings);
        behaviours.projectileEffect(EMP, EmpEffect::fromSettings);
    }
}
