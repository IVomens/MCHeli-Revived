package com.ivomens.mcheli.combat.weapon.fire;

import com.ivomens.mcheli.entityview.RappelRope;
import com.ivomens.mcheli.entityview.TowChain;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleHitBox;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.combat.HurtTime;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.ArmedVehicle;
import com.ivomens.mcheli.combat.weapon.FireMode;
import com.ivomens.mcheli.combat.weapon.Shot;
import com.ivomens.mcheli.combat.weapon.Shots;
import com.ivomens.mcheli.combat.weapon.WeaponPotionEffect;
import com.ivomens.mcheli.combat.weapon.WeaponRayUtil;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.net.HitMarkers;
import com.ivomens.mcheli.combat.weapon.net.LaserBeamMessage;
import com.ivomens.mcheli.combat.weapon.net.LaserBeamStyle;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hitscan directed-energy weapons. Hits are resolved on the server only: it ray traces, applies damage
 * and broadcasts one {@link LaserBeamMessage} for the beam the clients draw. The block trace stops at
 * unloaded chunks instead of loading them.
 */
public final class BeamFire implements FireMode {

    private static final double BROADCAST_RANGE = 1000.0;
    private static final double DEFAULT_RANGE = 256.0;
    private static final float DEFAULT_BEAM_WIDTH = 0.1F;
    private static final int DEFAULT_BEAM_COLOR = 0xFFFF0000;
    private static final float BURN_SECONDS = 5.0F;
    /** Extra margin around entity boxes so the thin beam does not slip past small targets. */
    private static final double HIT_BOX_MARGIN = 0.3;

    @Override
    public boolean predict(Shot shot) {
        Shots.applyRecoil(shot);
        return true;
    }

    @Override
    public boolean fire(Shot shot) {
        Vec2 rot = Shots.rotation(shot, true, false);
        this.fireBeam(shot, Vec3.directionFromRotation(rot.y, rot.x));
        Shots.playFireSound(shot, shot.platform());
        return true;
    }

    private void fireBeam(Shot shot, Vec3 look) {
        WeaponSpec info = shot.mount().spec();
        double range = info.length() > 0.0F ? info.length() : DEFAULT_RANGE;
        Vec3 start = shot.origin();
        Vec3 end = start.add(look.scale(range));
        HitResult result = trace(shot, start, end);
        Vec3 hitVec = result != null ? result.getLocation() : end;
        if (result instanceof EntityHitResult entityHit) {
            this.damage(shot, entityHit.getEntity(), start, hitVec);
        }
        int argb = info.bulletColor() != null ? info.bulletColor().toARGB() : DEFAULT_BEAM_COLOR;
        float width = info.radius() > 0.0F ? info.radius() : DEFAULT_BEAM_WIDTH;
        int life = Math.max(1, info.explosion().fuseTime());
        LaserBeamStyle style = new LaserBeamStyle(argb, width, life, true, info.missile().turningFactor());
        Network.sendToAllAround(shot.level(), start, BROADCAST_RANGE, new LaserBeamMessage(start, hitVec, style,
                result != null));
    }

    private void damage(Shot shot, Entity hit, Vec3 start, Vec3 hitVec) {
        HurtTime.scale(hit);
        WeaponSpec info = shot.mount().spec();
        DamageSource source = ModDamageSources.bullet(shot.level(), shot.platform(), shot.user());
        float damage = DamageScaling.vsEntity(hit, source, shot.mount().stats().power())
                * info.getDamageFactor(hit);
        float dist = (float) start.distanceTo(hitVec);
        if (info.enableBulletDecay() && info.bulletDecay() != null && !info.bulletDecay().isEmpty()) {
            // with several decay curves the last one wins
            damage *= info.bulletDecay().getLast().calculateDecayFactor(dist);
        }
        if (hit instanceof LivingEntity) {
            hit.igniteForSeconds(BURN_SECONDS);
        }
        if (info.potionEffects() != null) {
            applyEffects(info, hit, dist);
        }
        hit.hurt(source, damage);
        if (shot.user() instanceof ServerPlayer player && shot.shooter().vehicle() != null) {
            HitMarkers.send(shot.shooter().vehicle() != null ? shot.shooter().vehicle().entity() : null, player);
        }
    }

    private static void applyEffects(WeaponSpec info, Entity hit, float dist) {
        for (LivingEntity victim : affectedCreatures(hit)) {
            for (WeaponPotionEffect effect : info.potionEffects()) {
                boolean anyDistance = effect.startDist < 0 && effect.endDist < 0;
                if (anyDistance || (effect.startDist <= dist && dist < effect.endDist)) {
                    victim.addEffect(new MobEffectInstance(effect.potionEffect));
                }
            }
        }
    }

    /** The hit creature itself, or the crew of a hit vehicle. */
    private static List<LivingEntity> affectedCreatures(Entity hit) {
        List<LivingEntity> creatures = new ArrayList<>();
        if (hit instanceof LivingEntity living) {
            creatures.add(living);
        }
        if (hit instanceof Vehicle vehicle) {
            if (vehicle.seats().riderOf(0) instanceof LivingEntity pilot) {
                creatures.add(pilot);
            }
            for (Entity passenger : vehicle.seats().passengers()) {
                if (passenger instanceof LivingEntity living) {
                    creatures.add(living);
                }
            }
        }
        return creatures;
    }

    private static boolean canHit(Entity entity, Shot shot) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof TowChain || entity instanceof VehicleSeat || entity instanceof RappelRope) {
            return false;
        }
        Entity platform = shot.platform();
        Entity user = shot.user();
        if (entity instanceof ProjectileEntity projectile) {
            if (projectile.spec() != null && !projectile.spec().missile().canBeIntercepted()) return false;
            if (sameId(projectile.platform(), platform)) return false;
            if (sameId(projectile.shooter(), user)) return false;
        }
        if (entity instanceof VehicleHitBox hitBox && sameId(hitBox.owner(), platform)) {
            return false;
        }
        if (user != null) {
            if (sameId(entity, user)) return false;
            if (user.getVehicle() != null && sameId(entity, user.getVehicle())) return false;
        }
        ArmedVehicle vehicle = shot.shooter().vehicle();
        return vehicle == null || !sameId(entity, vehicle.entity()) && !vehicle.seats().isAboard(entity);
    }

    private static boolean sameId(@Nullable Entity a, @Nullable Entity b) {
        return (a != null ? a.getId() : -1) == (b != null ? b.getId() : -1);
    }

    /** Blocks first (loaded chunks only), then the entities along the beam: the nearest hit, or null. */
    @Nullable
    private static HitResult trace(Shot shot, Vec3 start, Vec3 end) {
        Entity user = shot.user() != null ? shot.user() : shot.platform();
        Level level = user.level();
        double maxDist = start.distanceTo(end);
        BlockHitResult blockHit = WeaponRayUtil.clipLoadedResult(level, start, end, user, null);
        double blockDist = blockHit != null ? blockHit.getLocation().distanceTo(start) : maxDist;
        Vec3 rayEnd = blockHit != null ? blockHit.getLocation() : end;
        Entity pointed = null;
        Vec3 pointedVec = null;
        double closest = blockDist;
        AABB searchBox = new AABB(start, rayEnd).inflate(1.0);
        for (Entity entity : level.getEntities(user, searchBox, e -> e.isPickable() && canHit(e, shot))) {
            AABB box = entity.getBoundingBox().inflate(HIT_BOX_MARGIN);
            Optional<Vec3> intercept = box.clip(start, end);
            if (box.contains(start)) {
                pointed = entity;
                pointedVec = intercept.orElse(start);
                closest = 0.0;
            } else if (intercept.isPresent()) {
                double d = start.distanceTo(intercept.get());
                if (d < closest || closest == 0.0) {
                    pointed = entity;
                    pointedVec = intercept.get();
                    closest = d;
                }
            }
        }
        if (pointed != null && (closest < blockDist || blockHit == null)) {
            return new EntityHitResult(pointed, pointedVec);
        }
        return blockHit;
    }
}
