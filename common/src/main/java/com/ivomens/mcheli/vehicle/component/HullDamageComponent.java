package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.vehicle.settings.DurabilityRules;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * Damage the vehicle does to itself: flying or driving into walls, scraping the ground at a steep
 * angle, sinking, and the explosion when a wreck hits the ground hard.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL, after = DamageComponent.class)
public final class HullDamageComponent implements VehicleComponent {

    /** Horizontal speed (blocks/tick) that can be lost against a wall without taking damage. */
    private static final double WALL_COLLISION_SAFE_SPEED = 0.25;
    /** Speed loss (blocks/tick above the safe speed) that costs the whole HP at factor 1.0. */
    private static final double WALL_COLLISION_FATAL_SPEED = 2.0;
    private static final int FLOOD_CHECK_INTERVAL = 30;
    /** A wreck falling faster than this explodes when it lands. */
    private static final double CRASH_SPEED = -0.2;

    private final AircraftEntity vehicle;
    private final DamageComponent damage;
    private final LifecycleComponent lifecycle;
    private final Supplier<DurabilityRules> durability;
    /** Pitch or roll beyond which touching the ground hurts the hull. */
    private float maxSafeTilt = 40.0F;
    private boolean onGroundBeforePhysics;
    private double motionYBeforePhysics;

    public HullDamageComponent(AircraftEntity vehicle, DamageComponent damage, LifecycleComponent lifecycle,
            Supplier<DurabilityRules> durability) {
        this.vehicle = vehicle;
        this.damage = damage;
        this.lifecycle = lifecycle;
        this.durability = durability;
    }

    /** Tanks shrug off steep slopes that would wreck an aircraft. */
    public void setMaxSafeTilt(float degrees) {
        this.maxSafeTilt = degrees;
    }

    /** Scraping the ground at a steep bank or pitch hurts, and so does sinking for non-floating hulls. */
    @Override
    public void serverTick(TickContext ctx) {
        AircraftEntity ac = this.vehicle;
        if (ac.isDestroyed()) {
            return;
        }
        float maxSafeAngle = this.maxSafeTilt;
        float roll = Mth.abs(Mth.wrapDegrees(ac.roll()));
        float pitch = Mth.abs(Mth.wrapDegrees(ac.getXRot()));
        if ((roll > maxSafeAngle || pitch > maxSafeAngle) && Terrain.hasBlockY(ac, 3, -3)) {
            float damage = roll + pitch;
            if (damage < 90.0F) {
                damage *= 0.4F * (float) Math.sqrt(ac.distanceToSqr(ac.xo, ac.yo, ac.zo));
            } else {
                damage *= 0.4F;
            }
            if (damage > 1.0F && ac.getRandom().nextInt(4) == 0) {
                ac.hurt(ac.damageSources().inWall(), damage);
            }
        }

        VehicleSpec info = ac.spec();
        if (this.lifecycle.age() % FLOOD_CHECK_INTERVAL == 0 && info != null && !info.file().physics().canFloat()
                && Terrain.isNearWater(ac.level(), Mth.floor(ac.getX() + 0.5),
                        Mth.floor(ac.getY() + 1.5 + info.file().armor().submergedDamageHeight()),
                                Mth.floor(ac.getZ() + 0.5))) {
            ac.hurt(ac.damageSources().inWall(), Math.max(this.damage.maxHp() / 10, 1));
        }
    }

    /**
     * Server: damage from flying or driving into a wall, scaled by the horizontal speed lost in the
     * collision, so planes need a runway and walls hurt at speed while taxiing does not. A floating
     * hull on the water runs aground instead: boats are driven up beaches and against banks to land
     * their crew, and McHeli never damaged them for it.
     */
    public void onWallCollision(double requestedSpeed, double actualSpeed) {
        AircraftEntity ac = this.vehicle;
        DurabilityRules rules = this.durability.get();
        if (!rules.collisionDamage() || ac.isDestroyed() || ac.getVehicle() != null || isAfloat(ac)) {
            return;
        }
        double lost = requestedSpeed - actualSpeed;
        if (lost <= WALL_COLLISION_SAFE_SPEED) {
            return;
        }
        double factor = Math.min(1.0, (lost - WALL_COLLISION_SAFE_SPEED) / WALL_COLLISION_FATAL_SPEED)
                * rules.collisionDamageFactor();
        float damage = (float) (this.damage.maxHp() * factor);
        if (damage >= 1.0F) {
            ac.hurt(ac.damageSources().inWall(), damage);
        }
    }

    private static boolean isAfloat(AircraftEntity ac) {
        return ac.canFloatWater() && ac.isInWater();
    }

    @Override
    public void describe(StateSink state) {
        state.put("max safe tilt", this.maxSafeTilt);
        state.put("on ground before physics", this.onGroundBeforePhysics);
        state.put("motion y before physics", this.motionYBeforePhysics);
    }

    /** Remembers how the vehicle was falling right before the physics step. */
    public void beforePhysics() {
        this.onGroundBeforePhysics = this.vehicle.onGround();
        this.motionYBeforePhysics = this.vehicle.getDeltaMovement().y;
    }

    /** Server: a wreck that just hit the ground hard blows up, once. */
    public void checkCrashLanding() {
        AircraftEntity ac = this.vehicle;
        if (ac.isDestroyed() && !this.damage.isExploded() && !this.onGroundBeforePhysics && ac.onGround()
                && this.motionYBeforePhysics < CRASH_SPEED) {
            DamageComponent damage = this.damage;
            damage.explodeOnCrash();
            damage.markExploded();
        }
    }
}
