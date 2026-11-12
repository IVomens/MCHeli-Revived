package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.FriendlyFire;
import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.core.wingman.brain.AttackMode;
import com.ivomens.mcheli.core.wingman.brain.AttackOrders;
import com.ivomens.mcheli.core.wingman.brain.CombatBrain;
import com.ivomens.mcheli.core.wingman.brain.Point;
import com.ivomens.mcheli.core.wingman.brain.WeaponProfile;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The world side of wingman combat: picking targets, firing weapons, and the per-wingman state that
 * goes with it (fire cooldowns, the straight flight after a heavy release, the auto-target cache).
 * Where to fly and when a shot is on is {@link CombatBrain}'s.
 */
final class WingmanCombat {

    /** Covers targets at the centre of the default 300-block mission orbit. */
    private static final double AUTO_ATTACK_RANGE = 400.0;
    /** The nearest-hostile search is an area query, so its result is reused for this many ticks. */
    private static final int AUTO_TARGET_RESCAN_TICKS = 10;
    /** After releasing a heavy weapon the aircraft flies straight for this many ticks. */
    private static final int POST_FIRE_ESCAPE_TICKS = 40;

    private static final class AutoTarget {
        Entity target;
        long nextScan;
    }

    private final Roster roster;
    private final Map<UUID, Integer> fireCooldowns = new HashMap<>();
    private final Map<UUID, Integer> escapeTicks = new HashMap<>();
    private final Map<UUID, AutoTarget> autoTargets = new HashMap<>();

    /** Roster snapshot of the current tick phase. */
    private Map<UUID, Wingman> entries = Collections.emptyMap();

    WingmanCombat(Roster roster) {
        this.roster = roster;
    }

    void beginTick(Map<UUID, Wingman> snapshot) {
        this.entries = snapshot;
    }

    /** Drops state of aircraft that are no longer wingmen. */
    void pruneCaches() {
        this.autoTargets.keySet().removeIf(id -> !this.roster.contains(id));
        this.fireCooldowns.keySet().removeIf(id -> !this.roster.contains(id));
        this.escapeTicks.keySet().removeIf(id -> !this.roster.contains(id));
    }

    void clear() {
        this.entries = Collections.emptyMap();
        this.autoTargets.clear();
        this.fireCooldowns.clear();
        this.escapeTicks.clear();
    }

    /** Counts down the post-release escape; while it runs the aircraft must not be steered. */
    boolean consumeEscapeTick(UUID wingmanId) {
        int remaining = this.escapeTicks.getOrDefault(wingmanId, 0);
        if (remaining <= 0) {
            return false;
        }
        this.escapeTicks.put(wingmanId, remaining - 1);
        return true;
    }

    /** The current target of a wingman in an attack mode, or null. */
    @Nullable
    Entity resolveTarget(ServerLevel level, Entity wingman, Wingman entry) {
        AttackOrders attack = entry.attack();
        if (attack.mode() == AttackMode.MANUAL && attack.manualTarget() != null) {
            return level.getEntity(attack.manualTarget());
        }
        if (attack.mode() != AttackMode.AUTO) {
            return null;
        }

        UUID id = wingman.getUUID();
        long now = level.getGameTime();
        AutoTarget cached = this.autoTargets.get(id);
        if (cached != null && now < cached.nextScan
                && (cached.target == null || isValidTarget(cached.target, level))) {
            attack.setAutoTarget(cached.target != null ? cached.target.getUUID() : null);
            return cached.target;
        }
        Entity target = findNearestHostile(wingman, takenTargets(id, entry.leader()),
                siblings(id, entry.leader(), level), minimumSpacing());
        if (cached == null) {
            cached = new AutoTarget();
            this.autoTargets.put(id, cached);
        }
        cached.target = target;
        cached.nextScan = now + AUTO_TARGET_RESCAN_TICKS;
        attack.setAutoTarget(target != null ? target.getUUID() : null);
        return target;
    }

    private static boolean isValidTarget(Entity target, ServerLevel level) {
        return !target.isRemoved() && target.isAlive() && target.level() == level;
    }

    private static double minimumSpacing() {
        return WingmanTicker.tuning().minimumSpacing();
    }

    /** Fires at the current target when a shot is on. */
    void engage(ServerLevel level, Entity wingman, Wingman entry) {
        Entity target = resolveTarget(level, wingman, entry);
        if (target == null || target.isRemoved()) {
            return;
        }
        String weaponType = entry.attack().weaponType();
        Point self = new Point(wingman.getX(), wingman.getY(), wingman.getZ());
        Point aim = new Point(target.getX(), target.getY(), target.getZ());
        float yaw = wingman instanceof AircraftEntity ac ? ac.getYRot() : wingman.getYRot();
        if (CombatBrain.isShotOn(self, yaw, aim, WeaponProfile.of(weaponType))) {
            tryFire(wingman, entry.leader(), weaponType);
        }
    }

    /**
     * Something worth shooting: never players, other wingmen or anything a player rides; among MCHeli's
     * own entities only real combatants (aircraft, tanks, vehicles, gunners), not bullets, seats or
     * flares; and only what MCHeli's team rules allow this aircraft to attack.
     */
    private boolean isEnemy(Entity wingman, Entity e) {
        if (e instanceof Player || this.entries.containsKey(e.getUUID())) {
            return false;
        }
        for (Entity passenger : e.getPassengers()) {
            if (passenger instanceof Player) {
                return false;
            }
        }
        if (e.getClass().getName().startsWith("com.ivomens.mcheli.")
                && !(e instanceof AircraftEntity || e instanceof TankEntity
                     || e instanceof GroundVehicleEntity || e instanceof GunnerEntity)) {
            return false;
        }
        return FriendlyFire.allows(wingman, e);
    }

    /**
     * Nearest enemy in range that is not already taken by a sibling. Targets whose attack path would
     * pass close to a sibling (judged by the midpoint) are skipped as well.
     */
    private @Nullable Entity findNearestHostile(Entity wingman, Set<UUID> taken, List<Entity> siblings,
                                                double minSpacing) {
        double bestSq = AUTO_ATTACK_RANGE * AUTO_ATTACK_RANGE;
        Entity best = null;
        AABB box = wingman.getBoundingBox().inflate(AUTO_ATTACK_RANGE);
        for (Entity e : wingman.level().getEntities(wingman, box, en -> en.isAlive() && !en.isRemoved())) {
            if (!isEnemy(wingman, e) || taken.contains(e.getUUID())) {
                continue;
            }
            if (minSpacing > 0 && pathCrossesSibling(wingman, e, siblings, minSpacing)) {
                continue;
            }
            double distSq = wingman.distanceToSqr(e);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = e;
            }
        }
        return best;
    }

    private static boolean pathCrossesSibling(Entity wingman, Entity target, List<Entity> siblings, double minSpacing) {
        double midX = (wingman.getX() + target.getX()) * 0.5;
        double midZ = (wingman.getZ() + target.getZ()) * 0.5;
        for (Entity sibling : siblings) {
            double dx = midX - sibling.getX();
            double dz = midZ - sibling.getZ();
            if (dx * dx + dz * dz < minSpacing * minSpacing) {
                return true;
            }
        }
        return false;
    }

    /** Other wingmen of the same leader that are loaded in this level. */
    List<Entity> siblings(UUID selfId, @Nullable Entity leader, ServerLevel level) {
        List<Entity> result = new ArrayList<>();
        for (Map.Entry<UUID, Wingman> e : this.entries.entrySet()) {
            if (e.getKey().equals(selfId) || e.getValue().leader() != leader) {
                continue;
            }
            Entity sibling = level.getEntity(e.getKey());
            if (sibling != null) {
                result.add(sibling);
            }
        }
        return result;
    }

    /** Targets already engaged by other wingmen of the same leader, so they spread out. */
    private Set<UUID> takenTargets(UUID selfId, @Nullable Entity leader) {
        Set<UUID> taken = new HashSet<>();
        for (Map.Entry<UUID, Wingman> e : this.entries.entrySet()) {
            if (e.getKey().equals(selfId) || e.getValue().leader() != leader) {
                continue;
            }
            UUID engaged = e.getValue().attack().engagedTarget();
            if (engaged != null) {
                taken.add(engaged);
            }
        }
        return taken;
    }

    private void tryFire(Entity aircraft, @Nullable Entity leader, @Nullable String weaponType) {
        if (!(aircraft instanceof AircraftEntity ac)) {
            return;
        }
        WeaponGroup[] weapons = ac.weapons().all();
        if (weapons == null || weapons.length == 0) {
            return;
        }

        UUID id = aircraft.getUUID();
        int cooldown = this.fireCooldowns.getOrDefault(id, 0);
        if (cooldown > 0) {
            this.fireCooldowns.put(id, cooldown - 1);
            return;
        }

        WeaponGroup chosen = pickWeapon(weapons, weaponType, true);
        if (chosen == null) {
            chosen = pickWeapon(weapons, weaponType, false);
        }
        if (chosen == null) {
            return;
        }

        // the aircraft fires, the leader is credited (kill attribution and IFF); infinite ammo lets an
        // autonomous aircraft keep fighting without a resupply
        Vec3 from = new Vec3(ac.getX(), ac.getY(), ac.getZ());
        if (!chosen.fire(ac.weapons().shooter(leader), from, FireRequest.NONE, true).fired()) {
            return;
        }

        WeaponProfile fired = WeaponProfile.of(weaponTypeOf(chosen, weaponType));
        this.fireCooldowns.put(id, fired.cooldownTicks);
        if (fired.heavy) {
            this.escapeTicks.put(id, POST_FIRE_ESCAPE_TICKS);
        }
    }

    /** First usable weapon set matching the filter (any type when the filter is null). */
    private static @Nullable WeaponGroup pickWeapon(WeaponGroup[] weapons, @Nullable String weaponType,
                                                    boolean requireCanFire) {
        for (WeaponGroup set : weapons) {
            if (set == null) {
                continue;
            }
            String type = weaponTypeOf(set, "");
            if ("dummy".equalsIgnoreCase(type)) {
                continue;
            }
            if ("targetingpod".equalsIgnoreCase(type) && !"targetingpod".equalsIgnoreCase(weaponType)) {
                continue;
            }
            if (weaponType != null && !matchesWeaponType(set, weaponType)) {
                continue;
            }
            if (requireCanFire && !set.canFire()) {
                continue;
            }
            return set;
        }
        return null;
    }

    private static String weaponTypeOf(WeaponGroup set, String fallback) {
        WeaponSpec info = set.spec();
        return info == null || info.definition().type() == null ? fallback : info.definition().type();
    }

    /** Matches a weapon filter like "gun" or "missile" against a weapon's own, more specific type. */
    private static boolean matchesWeaponType(WeaponGroup set, String expected) {
        String actual = weaponTypeOf(set, null);
        if (actual == null) {
            return false;
        }
        if (expected.equalsIgnoreCase(actual)) {
            return true;
        }
        String act = actual.toLowerCase(Locale.ROOT);
        return switch (expected.toLowerCase(Locale.ROOT)) {
            case "gun" -> act.contains("gun") || act.equals("cas");
            case "cannon" -> act.contains("cannon");
            case "missile" -> act.contains("missile");
            case "rocket" -> act.contains("rocket");
            case "bomb" -> act.contains("bomb");
            case "torpedo" -> act.contains("torpedo");
            default -> false;
        };
    }
}
