package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.HandheldShooter;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * For weapons no bundled vehicle carries (launchers, gunner and pod weapons): a soldier standing at
 * the range origin holds a weapon group of one mount, with a full magazine, and fires it through
 * {@link WeaponGroup#fire} from eye height.
 */
final class HandheldMount implements Mount {

    private final ServerPlayer soldier;
    private final WeaponGroup weapons;

    private HandheldMount(ServerPlayer soldier, WeaponGroup weapons) {
        this.soldier = soldier;
        this.weapons = weapons;
    }

    @Nullable
    static HandheldMount create(ServerLevel level, String weaponName, ServerPlayer soldier) {
        WeaponMount weapon = WeaponMount.create(level, weaponName, Vec3.ZERO, 0.0F, 0.0F, false, null);
        if (weapon == null) {
            return null;
        }
        Vec3 standing = WeaponRange.at(0.0, 1.0, 0.0);
        soldier.moveTo(standing.x, standing.y, standing.z, 0.0F, 0.0F);
        WeaponGroup weapons = WeaponGroup.of(level, weapon);
        weapons.setAmmo(weapons.magazineSize());
        weapons.setReserveAmmo(weapons.maxAmmo());
        return new HandheldMount(soldier, weapons);
    }

    @Override
    public boolean prepare() {
        return true;
    }

    @Override
    public void fire(@Nullable Entity target) {
        Vec3 eyes = new Vec3(this.soldier.getX(), this.soldier.getEyeY(), this.soldier.getZ());
        FireRequest request = target != null ? FireRequest.locked(target.getId(), 0) : FireRequest.NONE;
        this.weapons.fire(new HandheldShooter(this.soldier), eyes, request, false);
    }

    @Override
    public void tick() {
        this.weapons.tick(false);
    }

    @Override
    public WeaponGroup weapons() {
        return this.weapons;
    }

    @Override
    public String describe() {
        return "soldier at the origin holding the weapon set (no bundled vehicle carries it)";
    }

    @Override
    public void remove() {
        this.soldier.server.getPlayerList().remove(this.soldier);
        this.soldier.discard();
    }
}
