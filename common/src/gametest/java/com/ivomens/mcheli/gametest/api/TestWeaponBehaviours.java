package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.weapon.DetonationContext;
import com.ivomens.mcheli.api.weapon.GuidanceContext;
import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.ProjectileEffect;
import com.ivomens.mcheli.api.weapon.ProjectileEffectContext;
import com.ivomens.mcheli.api.weapon.SteeringCommand;
import com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider;
import com.ivomens.mcheli.api.weapon.WeaponBehaviourRegistrar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game tests' own weapon behaviours, registered through the public API as an addon registers them
 * ({@code META-INF/services/com.ivomens.mcheli.api.weapon.WeaponBehaviourProvider}); the weapons of
 * {@code api_test_pack} name them. {@link WeaponBehaviourGameTests} flies them.
 */
public final class TestWeaponBehaviours implements WeaponBehaviourProvider {

    static final ResourceLocation CLIMB = id("climb");
    static final ResourceLocation TRACK = id("track");
    static final ResourceLocation RECORDER = id("recorder");
    static final ResourceLocation EMP = id("emp");

    /** What the recorders saw, by projectile entity id: {@code <tag>:<hook>} in the order the hooks ran. */
    static final Map<Integer, List<String>> RECORDED = new ConcurrentHashMap<>();

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mcheli_gametest", path);
    }

    @Override
    public void registerBehaviours(WeaponBehaviourRegistrar behaviours) {
        behaviours.guidance(CLIMB, settings -> new Climb(settings.getDouble("Height")));
        behaviours.guidance(TRACK, settings -> context -> context.target() != null
                ? SteeringCommand.track(context.target().position()) : SteeringCommand.keepCourse());
        behaviours.projectileEffect(RECORDER, Recorder::new);
        behaviours.projectileEffect(EMP, settings -> new Emp(settings.getInt("Ticks"), settings.getDouble("Radius")));
    }

    static List<String> recorded(Entity projectile) {
        return RECORDED.getOrDefault(projectile.getId(), List.of());
    }

    /** Steers towards a point {@code Height} blocks above the projectile: it turns to fly straight up. */
    private record Climb(double height) implements GuidanceLaw {

        Climb {
            if (height <= 0) {
                throw new IllegalArgumentException("Height must be positive");
            }
        }

        @Override
        public SteeringCommand steer(GuidanceContext projectile) {
            return SteeringCommand.steerTowards(projectile.position().add(0, this.height, 0));
        }
    }

    /** Writes down each hook it hears, tagged with its {@code Tag} setting; {@code Cancel} cancels explosions. */
    private static final class Recorder implements ProjectileEffect {

        private final String tag;
        private final boolean cancel;

        Recorder(CompoundTag settings) {
            this.tag = settings.getString("Tag");
            this.cancel = settings.getBoolean("Cancel");
        }

        private void record(ProjectileEffectContext projectile, String hook) {
            RECORDED.computeIfAbsent(projectile.entity().getId(), id -> new ArrayList<>()).add(this.tag + ":" + hook);
        }

        @Override
        public void onSpawn(ProjectileEffectContext projectile) {
            this.record(projectile, "spawn");
        }

        @Override
        public void onTick(ProjectileEffectContext projectile) {
            // once, so the record stays short
            if (!projectile.data().getBoolean(this.tag + "ticked")) {
                projectile.data().putBoolean(this.tag + "ticked", true);
                this.record(projectile, "tick");
            }
        }

        @Override
        public void onEntityHit(ProjectileEffectContext projectile, EntityHitResult hit) {
            this.record(projectile, "entity");
        }

        @Override
        public void onBlockHit(ProjectileEffectContext projectile, BlockHitResult hit) {
            this.record(projectile, "block");
        }

        @Override
        public void onDetonate(DetonationContext projectile) {
            this.record(projectile, projectile.isExplosionCancelled() ? "detonate:cancelled" : "detonate");
            if (this.cancel) {
                projectile.cancelExplosion();
            }
        }

        @Override
        public void onExpire(ProjectileEffectContext projectile) {
            this.record(projectile, "expire");
        }
    }

    /** Stalls the engines of the vehicles within {@code Radius} of the burst for {@code Ticks}, and no explosion. */
    private record Emp(int ticks, double radius) implements ProjectileEffect {

        @Override
        public void onDetonate(DetonationContext projectile) {
            projectile.cancelExplosion();
            for (Vehicle vehicle : McheliApi.get().vehiclesIn(projectile.level(),
                    new AABB(projectile.at(), projectile.at()).inflate(this.radius))) {
                projectile.stallEngine(vehicle, this.ticks);
            }
        }
    }
}
