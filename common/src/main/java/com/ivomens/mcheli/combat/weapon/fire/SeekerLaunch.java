package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.guidance.TargetFilter;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Lock-on missiles. Unless the missile is radar guided, a shot needs a completed lock: the client locks
 * and asks for its target, the server launches the missile at it. An air-to-air missile prefers the
 * target its radar designated beyond visual range; an anti-tank missile fired in mode 1 flies a top
 * attack.
 */
public final class SeekerLaunch implements FireMode {

    private final LockOnSeeker seeker;
    private final boolean antiTank;

    private SeekerLaunch(LockOnSeeker seeker, boolean antiTank) {
        this.seeker = seeker;
        this.antiTank = antiTank;
    }

    public static SeekerLaunch airToAir(Level level, WeaponSpec info, @Nullable Predicate<Entity> excluded) {
        return new SeekerLaunch(seeker(level, info, TargetFilter.of(info, true, false), excluded), false);
    }

    /** Ships sit in water, so anti-surface missiles always engage watercraft. */
    public static SeekerLaunch antiTank(Level level, WeaponSpec info, @Nullable Predicate<Entity> excluded) {
        TargetFilter filter = TargetFilter.of(info, false, true).withWater(true);
        return new SeekerLaunch(seeker(level, info, filter, excluded), true);
    }

    private static LockOnSeeker seeker(Level level, WeaponSpec info, TargetFilter filter,
                                       @Nullable Predicate<Entity> excluded) {
        return new LockOnSeeker(level, filter, info.missile().lockOn().maxRange(), info.missile().lockOn().maxAngle(),
                info.missile().lockOn().time(),
                info.missile().pdhdnMaxDegree(), excluded);
    }

    @Override
    public LockOnSeeker seeker() {
        return this.seeker;
    }

    private static boolean isRadarGuided(WeaponSpec info) {
        return info.missile().radar().passiveRadar() || info.missile().radar().activeRadar();
    }

    @Override
    public boolean predict(Shot shot) {
        WeaponMount mount = shot.mount();
        if (isRadarGuided(mount.spec())) {
            return true;
        }
        Entity locked = this.seeker.lock(shot.user()) ? this.seeker.lastLocked() : null;
        if (locked == null) {
            return false;
        }
        mount.setRequest(mount.request().withTarget(locked.getId()));
        if (this.antiTank) {
            mount.setRequest(mount.request().withMode(mount.currentMode()));
        }
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        boolean radarGuided = isRadarGuided(shot.mount().spec());
        Entity target = this.target(shot, radarGuided);
        if (!radarGuided && (target == null || target.isRemoved())) {
            return false;
        }
        Shots.playFireSound(shot, shot.platform());
        Vec2 rot = Shots.rotation(shot, radarGuided, !radarGuided);
        Vec3 motion = Vec3.directionFromRotation(rot.y, rot.x);
        ProjectileKind kind = this.antiTank ? ProjectileKind.AT_MISSILE : ProjectileKind.AA_MISSILE;
        ProjectileEntity missile = Shots.projectile(shot, kind, motion, rot.x, rot.y, shot.mount().stats().speed());
        if (this.antiTank) {
            missile.setTopAttack(shot.request().mode() == 1);
        }
        if (target != null) {
            missile.setTarget(target);
        }
        shot.level().addFreshEntity(missile);
        return true;
    }

    /** The target of a server-side shot; radar guided anti-tank missiles fly without one. */
    @Nullable
    private Entity target(Shot shot, boolean radarGuided) {
        if (this.antiTank && radarGuided) {
            return null;
        }
        Entity designated = this.antiTank ? null : this.seeker.target();
        return designated != null ? designated : shot.level().getEntity(shot.request().targetId());
    }

    @Override
    public void tick(WeaponMount mount, int cooldown) {
        this.seeker.update();
    }
}
