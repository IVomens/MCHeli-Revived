package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weapon type from {@code assets/mcheli/weapons/}, as the file describes it. The section records mirror
 * the YAML sections; {@link WeaponSchema} documents every setting with its TXT key, range and default.
 * <p>
 * Values the game derives at run time (a destruction power that follows the explosion power when unset, a
 * magazine that defaults to the maximum ammo) are not resolved here, so a converted file keeps what its
 * author wrote; {@code WeaponSpec} resolves them.
 *
 * @param damageFactors damage multipliers by target: {@code Player}, {@code Helicopter}, {@code Plane},
 *                      {@code Ship}, {@code Tank}, {@code Vehicle}, or an entity class name, in file order
 * @param ntm           Hammer's Nuclear Tech payload, or null
 */
public record WeaponDefinition(
        String name,
        String displayName,
        String type,
        String group,
        int baseDamage,
        Ballistics ballistics,
        Sound sound,
        Recoil recoil,
        Explosion explosion,
        Camera camera,
        Missile missile,
        Ammo ammo,
        int fireCooldown,
        int target,
        int modes,
        int fixMode,
        boolean ridableOnly,
        float accuracy,
        boolean selfDestruct,
        boolean canLoadChunks,
        Render render,
        Submunition submunition,
        Effects effects,
        DispenseItem dispenseItem,
        int length,
        int radius,
        int markTime,
        Map<String, Float> damageFactors,
        Buckshot buckshot,
        Mine mine,
        NtmPayload ntm) implements ContentDefinition {

    public WeaponDefinition {
        damageFactors = Collections.unmodifiableMap(new LinkedHashMap<>(damageFactors));
    }

    public record Ballistics(float acceleration, float accelerationInWater, float gravity, float gravityInWater,
                             float velocityInWater, float speedFactor, int speedFactorStartTick,
                             int speedFactorEndTick, boolean speedDependsAircraft, int piercing, float flakSpread) {
    }

    public record Sound(int delay, float volume, float pitch, float pitchRandom, int hitSoundRange,
                        Locations locations) {
    }

    /** @param hit null for no hit sound */
    public record Locations(ResourceLocation fire, ResourceLocation hit, ResourceLocation railgun) {
    }

    public record Recoil(float power, float pitch, float yaw, float pitchRange, float yawRange, float recoverFactor,
                         int bufferCount, int bufferSpeed) {
    }

    /**
     * @param radius           0 means the explosion power
     * @param destructionPower -1 means the explosion power
     * @param airburstPower    0 means the explosion power
     */
    public record Explosion(int power, float radius, float damageRadius, int destructionPower, int powerUnderwater,
                            int altitude, int fuseTime, int fuseDelay, float fuseRebound, boolean flaming,
                            boolean canAirburst, int airburstPower, boolean throughWall, float throughWallFactor,
                            boolean newBreak, float proximityFuseDist, boolean fuelAir) {
    }

    public record Camera(double mortarRadarMaxDist, boolean displayMortarDistance, boolean fixPitch,
                         float rotationSpeedPitch, boolean ccip, float ccipFactor, boolean lockEntity, SightType sight,
                         List<Float> zoom) {

        public Camera {
            zoom = List.copyOf(zoom);
        }
    }

    public record Missile(boolean laserGuidance, boolean hasLaserGuidancePod, boolean offAxis, boolean guidedTorpedo,
                          boolean predictTargetPos, boolean enableBvr, int minRangeBvr, int scanInterval,
                          String nameOnRwr, boolean gpsMissile, int tickEndHoming, float pdhdnMaxDegree,
                          int pdhdnMaxDegreeLockOutCount, double turningFactor, int maxDegreeOfMissile,
                          boolean canBeIntercepted, MissileBallistic ballistic, Arm arm, LockOn lockOn, Radar radar,
                          Heat heat) {
    }

    public record MissileBallistic(int canister, double dragInAir) {
    }

    public record Arm(boolean antiRadiationMissile) {
    }

    public record LockOn(boolean canLockMissile, int time, int delay, int maxRange, int maxAngle, int minHeight,
                         int passiveRadarLockOutCount, int lockedChaffMax, int proximityFuseTick,
                         float proximityFuseDamage, int proximityFuseHeight, boolean lockInWater) {
    }

    public record Radar(boolean radarMissile, boolean activeRadar, boolean passiveRadar) {
    }

    public record Heat(boolean heatMissile, int heatCount, int maxHeatCount, int antiFlareCount) {
    }

    /** @param magSize 0 means the maximum ammo */
    public record Ammo(int reloadTime, int magSize, int maxAmmo, int resupplyCount, List<AmmoItem> items) {

        public Ammo {
            items = List.copyOf(items);
        }
    }

    /** @param spentCasings null for none */
    public record Render(List<MuzzleFlashSpec> muzzleFlashes, List<MuzzleFlashSpec> muzzleSmoke,
                         CasingSpec spentCasings, String bulletModel, String bombletModel, String trajectoryParticle,
                         int trajectoryParticleStartTick, int flakParticlesCrack, int particlesFlak, Smoke smoke) {

        public Render {
            muzzleFlashes = List.copyOf(muzzleFlashes);
            muzzleSmoke = List.copyOf(muzzleSmoke);
        }
    }

    public record Smoke(boolean disabled, float size, int count, int maxAge, ArgbColor bulletColorInWater,
                        ArgbColor bulletColor) {
    }

    public record Submunition(int count, int deployTime, float spread, boolean spawnInAir, int spawnMaxNum,
                              int spawnIntervalTick, int spawnPerNum, boolean spawnInheritSpeed,
                              boolean destructAfterSpawn, boolean ahead) {
    }

    public record Effects(List<PotionEffectSpec> potionEffects, List<DecayCurve> bulletDecay) {

        public Effects {
            potionEffects = List.copyOf(potionEffects);
            bulletDecay = List.copyOf(bulletDecay);
        }
    }

    /** @param location the item id, or null when the weapon dispenses nothing */
    public record DispenseItem(String location, int meta, int range) {
    }

    public record Buckshot(int count, BuckshotPellet pellet) {
    }

    public record Mine(MineFuze fuze, float range, int armDelay) {
    }
}
