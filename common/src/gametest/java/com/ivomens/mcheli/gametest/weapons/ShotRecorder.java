package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Writes down what a shot does, tick by tick: every entity it spawns (type, position, motion), the
 * positions of those entities after each level tick, when and why they disappear (fuze, impact,
 * age), and the parameters of every explosion. Block drops are left out (see {@link #entities()}). Entities are numbered in spawn order, never by
 * their world id, so two runs compare equal.
 */
final class ShotRecorder {

    /** Positions are written for this many spawned entities; later ones only get a spawn line. */
    private static final int MAX_TRACKED = 24;

    private final ServerLevel level;
    private final AABB area;
    private final Set<Integer> known = new HashSet<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Set<Entity> gone = new HashSet<>();
    private final List<String> lines;
    /**
     * Explosions of the level tick in progress. Bomblets in different chunks tick in an order that differs
     * between runs, so explosions of one tick are written sorted.
     */
    private final List<String> explosions = new ArrayList<>();
    private final Consumer<Explosions.Detonation> listener = this::onExplosion;
    /** Level ticks completed since the shot. */
    private int tick;

    ShotRecorder(ServerLevel level, AABB area, List<String> lines) {
        this.level = level;
        this.area = area;
        this.lines = lines;
    }

    /** Remembers what already exists, so only what the shot creates is recorded. */
    void beforeShot() {
        for (Entity entity : entities()) {
            this.known.add(entity.getId());
        }
        Explosions.addListener(this.listener);
    }

    void afterShot() {
        flushExplosions();
        recordNewEntities();
    }

    /** Called once after every level tick of the flight. */
    void afterTick() {
        flushExplosions();
        this.tick++;
        recordNewEntities();
        for (int i = 0; i < Math.min(this.spawned.size(), MAX_TRACKED); i++) {
            Entity entity = this.spawned.get(i);
            if (this.gone.contains(entity)) {
                continue;
            }
            if (entity.isRemoved()) {
                this.gone.add(entity);
                this.lines.add("t" + this.tick + " #" + i + " removed: " + entity.getRemovalReason());
            } else {
                this.lines.add("t" + this.tick + " #" + i + " " + vec(entity.position()));
            }
        }
    }

    void stop() {
        Explosions.removeListener(this.listener);
    }

    int tick() {
        return this.tick;
    }

    private void recordNewEntities() {
        List<Entity> fresh = new ArrayList<>();
        for (Entity entity : entities()) {
            if (this.known.add(entity.getId())) {
                fresh.add(entity);
            }
        }
        fresh.sort(Comparator.comparingInt(Entity::getId));
        for (Entity entity : fresh) {
            int index = this.spawned.size();
            this.spawned.add(entity);
            this.lines.add("t" + this.tick + " spawned #" + index + " " + type(entity) + " at " + vec(entity.position())
                    + " motion " + vec(entity.getDeltaMovement()) + " yaw " + entity.getYRot() + " pitch "
                    + entity.getXRot());
        }
    }

    private void onExplosion(Explosions.Detonation d) {
        ExplosionSpec spec = d.spec();
        // the listener runs inside the level tick that is about to complete
        this.explosions.add("t" + (this.tick + 1) + " explosion by " + name(d.exploder()) + " at " + spec.at().x + " "
                + spec.at().y + " " + spec.at().z + " size " + spec.size() + " block " + spec.blockSize() + " damage "
                + spec.damagePower() + " blockPower " + spec.blockPower() + " radius " + spec.damageRadius() + " sound "
                + spec.sound() + " flaming " + spec.fire() + " destroyBlocks " + spec.destroyBlocks() + " setFire "
                + spec.igniteEntities() + " damageFactor " + (spec.damageFactor() != null) + " inWater "
                + spec.inWater());
    }

    private void flushExplosions() {
        this.explosions.sort(null);
        this.lines.addAll(this.explosions);
        this.explosions.clear();
    }

    private String name(Entity exploder) {
        if (exploder == null) {
            return "nobody";
        }
        int index = this.spawned.indexOf(exploder);
        return index >= 0 ? "#" + index : type(exploder);
    }

    /**
     * Everything but players, and but the items and orbs blown out of the ground. Which blocks an
     * explosion breaks depends on the level's random generator, which chunk ticking shuffles by a
     * different amount whenever another test has chunks loaded, so the drops cannot be replayed.
     */
    private List<Entity> entities() {
        return this.level.getEntities((Entity) null, this.area, entity -> !(entity instanceof Player
                || entity instanceof ItemEntity || entity instanceof ExperienceOrb));
    }

    private static String type(Entity entity) {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    static String vec(Vec3 v) {
        return v.x + " " + v.y + " " + v.z;
    }
}
