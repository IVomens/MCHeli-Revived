package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** The camera through which a vehicle's crew watches the TV missile it launched. */
public interface MissileCamera {

    /** The TV missile whose camera the crew looks through, or null. */
    @Nullable
    ProjectileEntity tvMissile();

    /** The crew now looks through the camera of this TV missile; null stops. */
    void watchTvMissile(@Nullable ProjectileEntity missile);

    /** Whether the missile is drawn for the viewer: a TV missile the viewer flies is not drawn over its own camera. */
    boolean showsMissile(Entity missile, Entity viewer);
}
