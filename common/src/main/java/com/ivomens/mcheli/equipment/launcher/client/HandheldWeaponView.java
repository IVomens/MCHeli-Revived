package com.ivomens.mcheli.equipment.launcher.client;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** What the launcher sight shows of the launcher the local player holds. */
public interface HandheldWeaponView {

    /** The launcher's weapon, or null while none is held. */
    @Nullable
    WeaponMount weapon();

    /** The launcher's seeker; a launcher without one has a sight that locks nothing. */
    @Nullable
    LockOnSeeker seeker();

    /** The fire mode picked on the launcher: 0 direct, 1 top attack. */
    int mode();

    /** Index into the weapon's zoom levels. */
    int zoom();

    /** Screen position (GUI-scaled, top-left origin) of the locked entity, or null. */
    @Nullable
    Vec3 markerPosition();

    /** Screen position (GUI-scaled, top-left origin) of the locked entity's marker corner, or null. */
    @Nullable
    Vec3 markerCorner();
}
