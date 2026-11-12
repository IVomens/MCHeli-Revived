package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.fire.BeamFire;
import com.ivomens.mcheli.combat.weapon.fire.BombDrop;
import com.ivomens.mcheli.combat.weapon.fire.BuckshotFire;
import com.ivomens.mcheli.combat.weapon.fire.CloseAirSupport;
import com.ivomens.mcheli.combat.weapon.fire.ContainerDrop;
import com.ivomens.mcheli.combat.weapon.fire.GunFire;
import com.ivomens.mcheli.combat.weapon.fire.NoFire;
import com.ivomens.mcheli.combat.weapon.fire.PointAttackLaunch;
import com.ivomens.mcheli.combat.weapon.fire.RailgunFire;
import com.ivomens.mcheli.combat.weapon.fire.RocketLaunch;
import com.ivomens.mcheli.combat.weapon.fire.SeekerLaunch;
import com.ivomens.mcheli.combat.weapon.fire.SpotterDesignation;
import com.ivomens.mcheli.combat.weapon.fire.TorpedoLaunch;
import com.ivomens.mcheli.combat.weapon.fire.TvLaunch;
import com.ivomens.mcheli.content.def.weapon.WeaponSchema;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * What a weapon is, by the {@code Type} of its definition: how it fires, what it launches, the labels
 * of its modes and how its shots are predicted. The table replaces a class per weapon kind; everything
 * else about a weapon comes from its definition.
 */
public enum WeaponKind {

    MACHINE_GUN("machinegun1", Trajectory.GUN, false, false),
    /** A machine gun with tracers; mode 1 fires high-explosive rounds (see {@link GunFire}). */
    MACHINE_GUN_TRACER("machinegun2", Trajectory.GUN, false, false, "", " [HE]"),
    BUCKSHOT("buckshot", Trajectory.DIRECT, false, false),
    TV_MISSILE("tvmissile", Trajectory.DIRECT, true, false, " [TV]", "", " [TA]"),
    TORPEDO("torpedo", Trajectory.TORPEDO, true, false),
    CLOSE_AIR_SUPPORT("cas", Trajectory.DIRECT, true, false),
    /** Unguided rockets; mode 1 of a two-mode pod fires piercing rounds. */
    ROCKET("rocket", Trajectory.ROCKET, true, false, "", " [HEIAP]"),
    AIR_TO_SURFACE_MISSILE("asmissile", Trajectory.DIRECT, true, true),
    AIR_TO_AIR_MISSILE("aamissile", Trajectory.DIRECT, true, true),
    /** Anti-tank missiles; mode 1 flies a top attack. */
    ANTI_TANK_MISSILE("atmissile", Trajectory.DIRECT, true, true, "", " [TA]"),
    BOMB("bomb", Trajectory.BOMB, true, false),
    MARKER_ROCKET("mkrocket", Trajectory.DIRECT, false, false),
    /** The placeholder of an empty slot. */
    DUMMY("dummy", Trajectory.DIRECT, false, false),
    /** A smoke generator: the vehicle makes the smoke while the weapon is selected and used. */
    SMOKE("smoke", Trajectory.DIRECT, false, false),
    DISPENSER("dispenser", Trajectory.DIRECT, false, false),
    TARGETING_POD("targetingpod", Trajectory.DIRECT, false, false),
    RAILGUN("railgun", Trajectory.DIRECT, true, false),
    LASER("laser", Trajectory.DIRECT, false, false);

    /** How the impact point of a shot is predicted (the CCIP sight). */
    public enum Trajectory {
        /** Along the aim at the capped speed, one step per tick. */
        DIRECT,
        /** Rounds: several steps per tick when fast, starting half a step ahead of the muzzle. */
        GUN,
        /** Rockets: several steps per tick when fast. */
        ROCKET,
        /** Released with the carrier's own velocity. */
        BOMB,
        /** The definition's speed along the aim plus the carrier's velocity. */
        TORPEDO
    }

    private final String legacyName;
    private final Trajectory trajectory;
    private final boolean alwaysHeavy;
    private final boolean cooldownSpansReload;
    private final List<String> modeLabels;

    WeaponKind(String legacyName, Trajectory trajectory, boolean alwaysHeavy, boolean cooldownSpansReload,
               String... modeLabels) {
        this.legacyName = legacyName;
        this.trajectory = trajectory;
        this.alwaysHeavy = alwaysHeavy;
        this.cooldownSpansReload = cooldownSpansReload;
        this.modeLabels = List.of(modeLabels);
    }

    /** The kind a definition's {@code Type} names, or null for an unknown type. */
    @Nullable
    public static WeaponKind ofLegacyName(String type) {
        for (WeaponKind kind : values()) {
            if (kind.legacyName.equals(type)) {
                return kind;
            }
        }
        return null;
    }

    public String legacyName() {
        return this.legacyName;
    }

    public Trajectory trajectory() {
        return this.trajectory;
    }

    /** Heard over the vehicle sound range whatever its explosion; other kinds only when they explode. */
    public boolean isAlwaysHeavy() {
        return this.alwaysHeavy;
    }

    /** Lock-on and cruise missiles: the cooldown after a shot lasts at least the reload time. */
    public boolean cooldownSpansReload() {
        return this.cooldownSpansReload;
    }

    /** The label shown after the weapon's name in the given mode. */
    public String modeLabel(int mode) {
        return mode >= 0 && mode < this.modeLabels.size() ? this.modeLabels.get(mode) : "";
    }

    /** The projectile the kind launches, for kinds that launch one of a single kind. */
    @Nullable
    public ProjectileKind projectile() {
        return switch (this) {
            case MACHINE_GUN, MACHINE_GUN_TRACER, RAILGUN -> ProjectileKind.BULLET;
            case ROCKET -> ProjectileKind.ROCKET;
            case MARKER_ROCKET -> ProjectileKind.MARKER_ROCKET;
            case TV_MISSILE -> ProjectileKind.TV_MISSILE;
            case TORPEDO -> ProjectileKind.TORPEDO;
            case AIR_TO_SURFACE_MISSILE -> ProjectileKind.AS_MISSILE;
            case AIR_TO_AIR_MISSILE -> ProjectileKind.AA_MISSILE;
            case ANTI_TANK_MISSILE -> ProjectileKind.AT_MISSILE;
            case BOMB -> ProjectileKind.BOMB;
            case DISPENSER -> ProjectileKind.DISPENSED_ITEM;
            case BUCKSHOT, CLOSE_AIR_SUPPORT, DUMMY, SMOKE, TARGETING_POD, LASER -> null;
        };
    }

    FireMode newFireMode(Level level, WeaponMount mount, @Nullable Predicate<Entity> excludedTargets) {
        return switch (this) {
            case MACHINE_GUN -> new GunFire(false);
            case MACHINE_GUN_TRACER -> new GunFire(true);
            case BUCKSHOT -> new BuckshotFire();
            case TV_MISSILE -> new TvLaunch();
            case TORPEDO -> new TorpedoLaunch();
            case CLOSE_AIR_SUPPORT -> new CloseAirSupport(mount);
            case ROCKET -> RocketLaunch.rockets();
            case MARKER_ROCKET -> RocketLaunch.markers();
            case AIR_TO_SURFACE_MISSILE -> new PointAttackLaunch();
            case AIR_TO_AIR_MISSILE -> SeekerLaunch.airToAir(level, mount.spec(), excludedTargets);
            case ANTI_TANK_MISSILE -> SeekerLaunch.antiTank(level, mount.spec(), excludedTargets);
            case BOMB -> new BombDrop();
            case DISPENSER -> new ContainerDrop();
            case TARGETING_POD -> new SpotterDesignation();
            case RAILGUN -> new RailgunFire();
            case LASER -> new BeamFire();
            case DUMMY, SMOKE -> new NoFire();
        };
    }

    /** The definition of the empty slot's placeholder. */
    static WeaponSpec placeholderInfo() {
        return PlaceholderHolder.INFO;
    }

    private static final class PlaceholderHolder {
        static final WeaponSpec INFO = WeaponSpec.of(WeaponSchema.SCHEMA.defaults("none"), Map.of());
    }
}
