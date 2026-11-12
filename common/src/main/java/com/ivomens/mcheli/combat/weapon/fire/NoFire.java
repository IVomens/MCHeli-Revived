package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;

/**
 * Weapons that never fire a shot: the placeholder of an empty slot, and the smoke generator, whose
 * smoke the vehicle makes itself while the weapon is selected.
 */
public final class NoFire implements FireMode {

    @Override
    public boolean predict(Shot shot) {
        return false;
    }

    @Override
    public boolean fire(Shot shot) {
        return false;
    }
}
