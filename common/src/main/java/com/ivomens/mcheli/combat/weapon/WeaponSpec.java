package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.projectile.behaviour.WeaponBehaviourSet;
import com.ivomens.mcheli.combat.projectile.behaviour.WeaponBehaviours;
import com.ivomens.mcheli.content.def.weapon.BuckshotPellet;
import com.ivomens.mcheli.content.def.weapon.DecayCurve;
import com.ivomens.mcheli.content.def.weapon.MineFuze;
import com.ivomens.mcheli.content.def.weapon.MuzzleFlashSpec;
import com.ivomens.mcheli.content.def.weapon.NtmPayload;
import com.ivomens.mcheli.content.def.weapon.NtmPayloadType;
import com.ivomens.mcheli.content.def.weapon.PotionEffectSpec;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.util.Color4f;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A weapon type as the game uses it: the {@link WeaponDefinition} its file reads into, the objects built from
 * it once when it loads, and the values the game resolves from settings the file left unset: a destruction
 * power, radius or airburst that follows the explosion power, a magazine that holds the whole reserve, and so
 * on. The definition keeps what the file says, so a converted file keeps what its author wrote.
 *
 * @param damageFactor    the damage multipliers by target class, or null when the file names no known target
 * @param potionEffects   the effects a hit applies, those whose effect exists
 * @param bulletDecay     how damage falls off with distance
 * @param muzzleFlashes   the flashes drawn at the muzzle, or null for none
 * @param muzzleSmoke     the smoke puffs drawn at the muzzle, or null for none
 * @param bulletColor     the tracer colour
 * @param bulletColorInWater the tracer colour under water
 * @param cartridge       the spent casing a shot throws out on the client, or null for none
 * @param behaviours      the addon guidance and projectile effects the file names, or null when it names none
 */
public record WeaponSpec(WeaponDefinition definition, @Nullable DamageFactor damageFactor,
                         List<WeaponPotionEffect> potionEffects, List<IBulletDecay> bulletDecay,
                         @Nullable List<MuzzleFlash> muzzleFlashes, @Nullable List<MuzzleFlash> muzzleSmoke,
                         Color4f bulletColor, Color4f bulletColorInWater, @Nullable Cartridge cartridge,
                         @Nullable WeaponBehaviourSet behaviours) {

    /** A self-destructing weapon fires once and never again. */
    private static final int SELF_DESTRUCT_COOLDOWN = 1_000_000;
    /** A mine without a range triggers within four blocks. */
    private static final float DEFAULT_MINE_RANGE = 4.0F;

    /**
     * One muzzle flash or smoke puff.
     *
     * @param dist  how far in front of the muzzle it is drawn
     * @param range how far apart its particles spread
     * @param num   how many particles it has
     */
    public record MuzzleFlash(float dist, float size, float range, int age, float a, float r, float g, float b,
                              int num) {
    }

    public WeaponSpec {
        potionEffects = List.copyOf(potionEffects);
        bulletDecay = List.copyOf(bulletDecay);
    }

    /**
     * Builds the run-time objects of a weapon file that was just read; {@code vehicleTargets} are the vehicle
     * classes the named damage targets stand for. Addon behaviours it names that no mod registers are reported.
     */
    public static WeaponSpec of(WeaponDefinition definition, Map<String, Class<? extends Entity>> vehicleTargets) {
        WeaponDefinition.Render render = definition.render();
        return new WeaponSpec(definition, WeaponDamageTargets.resolve(definition.damageFactors(), vehicleTargets),
                potionEffects(definition.effects().potionEffects()), bulletDecay(definition.effects().bulletDecay()),
                muzzleFlashes(render.muzzleFlashes()), muzzleFlashes(render.muzzleSmoke()),
                color4f(render.smoke().bulletColor()), color4f(render.smoke().bulletColorInWater()),
                render.spentCasings() == null ? null : Cartridge.of(render.spentCasings()),
                WeaponBehaviours.installed().resolve(definition));
    }

    public String name() {
        return this.definition.name();
    }

    public ContentId id() {
        return new ContentId(ContentType.WEAPON, name());
    }

    public WeaponDefinition.Ballistics ballistics() {
        return this.definition.ballistics();
    }

    public WeaponDefinition.Explosion explosion() {
        return this.definition.explosion();
    }

    public WeaponDefinition.Missile missile() {
        return this.definition.missile();
    }

    public WeaponDefinition.Camera camera() {
        return this.definition.camera();
    }

    public WeaponDefinition.Ammo ammo() {
        return this.definition.ammo();
    }

    public WeaponDefinition.Render render() {
        return this.definition.render();
    }

    public WeaponDefinition.Submunition submunition() {
        return this.definition.submunition();
    }

    public WeaponDefinition.Sound sound() {
        return this.definition.sound();
    }

    public WeaponDefinition.Recoil recoil() {
        return this.definition.recoil();
    }

    /** Ticks between shots; a self-destructing weapon never fires twice. */
    public int delay() {
        return this.definition.selfDestruct() ? SELF_DESTRUCT_COOLDOWN : this.definition.fireCooldown();
    }

    /** The mode the weapon is fixed to; one past its modes means none. */
    public int fixMode() {
        return this.definition.fixMode() >= this.definition.modes() ? 0 : this.definition.fixMode();
    }

    /** The beam length, as the game measures it. */
    public float length() {
        return this.definition.length();
    }

    /** The beam radius, as the game measures it. */
    public float radius() {
        return this.definition.radius();
    }

    /** The cone half-angle of a beam weapon, from its radius and length. */
    public float angle() {
        return (float) Math.toDegrees(Math.atan2(radius(), length()));
    }

    /** The explosion's block destruction power; unset, it follows the explosion power. */
    public int explosionBlock() {
        WeaponDefinition.Explosion explosion = this.definition.explosion();
        return explosion.destructionPower() < 0 ? explosion.power() : explosion.destructionPower();
    }

    /** The explosion radius; unset, it is the explosion power. */
    public float explosionRadius() {
        WeaponDefinition.Explosion explosion = this.definition.explosion();
        return explosion.radius() <= 0.0F ? explosion.power() : explosion.radius();
    }

    /** A fuel-air explosion needs an explosion power. */
    public boolean isFAE() {
        return this.definition.explosion().power() > 0 && this.definition.explosion().fuelAir();
    }

    /** A fuel-air explosion does nothing under water. */
    public int explosionInWater() {
        return isFAE() ? 0 : this.definition.explosion().powerUnderwater();
    }

    /** Only a delayed fuse bounces. */
    public float bound() {
        WeaponDefinition.Explosion explosion = this.definition.explosion();
        return explosion.fuseDelay() <= 0 ? 0.0F : explosion.fuseRebound();
    }

    /** The airburst power; unset, it follows the explosion power. */
    public int explosionAirburst() {
        WeaponDefinition.Explosion explosion = this.definition.explosion();
        return explosion.airburstPower() <= 0 ? explosion.power() : explosion.airburstPower();
    }

    /** Only rockets and AT and TV missiles draw an impact point. */
    public boolean ccip() {
        return this.definition.camera().ccip() && isCCIPSupportedType(this.definition.type());
    }

    /** Rounds per magazine: unset, or larger than the reserve, the magazine holds the whole reserve. */
    public int round() {
        WeaponDefinition.Ammo ammo = this.definition.ammo();
        return ammo.magSize() <= 0 || ammo.magSize() > ammo.maxAmmo() ? ammo.maxAmmo() : ammo.magSize();
    }

    /** Ticks before bomblets deploy; bomblets need at least one tick of flight. */
    public int bombletSTime() {
        WeaponDefinition.Submunition submunition = this.definition.submunition();
        return submunition.count() > 0 && submunition.deployTime() < 1 ? 1 : submunition.deployTime();
    }

    /** Ticks between spawned sub-munitions, at least one. */
    public int spawnBulletIntervalTick() {
        return Math.max(1, this.definition.submunition().spawnIntervalTick());
    }

    public boolean enableBulletDecay() {
        return !this.bulletDecay.isEmpty();
    }

    /** True if this bomb is configured as a persistent proximity mine. */
    public boolean isMine() {
        return this.definition.mine().fuze() != MineFuze.NONE;
    }

    /** How close a target has to come to set off the mine; a mine without a range triggers within four blocks. */
    public float mineRange() {
        float range = this.definition.mine().range();
        return isMine() && range <= 0.0F ? DEFAULT_MINE_RANGE : range;
    }

    /** Ticks before a mine arms, never negative for a mine. */
    public int mineArmDelay() {
        int delay = this.definition.mine().armDelay();
        return isMine() ? Math.max(0, delay) : delay;
    }

    /** Whether the file asks for a Hammer's Nuclear Tech payload, used only when that mod is installed. */
    public boolean useHBM() {
        return this.definition.ntm() != null;
    }

    public NtmPayloadType payloadNTM() {
        NtmPayload ntm = this.definition.ntm();
        return ntm == null ? NtmPayloadType.NONE : ntm.type();
    }

    public boolean effectOnly() {
        NtmPayload ntm = this.definition.ntm();
        return ntm != null && ntm.effectOnly();
    }

    public BuckshotPellet buckshotPayload() {
        return this.definition.buckshot().pellet();
    }

    public float getDamageFactor(Entity entity) {
        return this.damageFactor != null ? this.damageFactor.getDamageFactor(entity) : 1.0F;
    }

    /** The weapon's kind as the HUD names it. */
    public String getWeaponTypeName() {
        return switch (this.definition.type().toLowerCase()) {
            case "machinegun1", "machinegun2", "railgun" -> "MachineGun";
            case "torpedo" -> "Torpedo";
            case "cas" -> "CAS";
            case "rocket" -> "Rocket";
            case "buckshot" -> "Buckshot";
            case "asmissile" -> "AS Missile";
            case "aamissile" -> "AA Missile";
            case "tvmissile" -> "TV Missile";
            case "atmissile" -> "AT Missile";
            case "bomb" -> "Bomb";
            case "mkrocket" -> "Mk Rocket";
            case "dummy" -> "Dummy";
            case "smoke" -> "Smoke";
            case "dispenser" -> "Dispenser";
            case "targetingpod" -> "Targeting Pod";
            default -> "";
        };
    }

    public static boolean isCCIPSupportedType(String type) {
        if (type == null) {
            return false;
        }
        return type.equalsIgnoreCase("rocket") || type.equalsIgnoreCase("atmissile")
                || type.equalsIgnoreCase("tvmissile");
    }

    private static Color4f color4f(ArgbColor color) {
        return new Color4f(color.a(), color.r(), color.g(), color.b());
    }

    /** Null when there are none, as run-time code expects. */
    @Nullable
    private static List<MuzzleFlash> muzzleFlashes(List<MuzzleFlashSpec> specs) {
        if (specs.isEmpty()) {
            return null;
        }
        return specs.stream().map(spec -> new MuzzleFlash(spec.distance(), spec.size(), spec.range(), spec.age(),
                spec.a(), spec.r(), spec.g(), spec.b(), spec.count())).toList();
    }

    private static List<WeaponPotionEffect> potionEffects(List<PotionEffectSpec> specs) {
        List<WeaponPotionEffect> effects = new ArrayList<>();
        for (PotionEffectSpec spec : specs) {
            BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, spec.effect()))
                    .ifPresent(potion -> effects.add(new WeaponPotionEffect(
                            new MobEffectInstance(potion, spec.duration(), spec.amplifier(), false, true),
                            spec.startDist(), spec.endDist())));
        }
        return effects;
    }

    private static List<IBulletDecay> bulletDecay(List<DecayCurve> curves) {
        List<IBulletDecay> decay = new ArrayList<>();
        for (DecayCurve curve : curves) {
            decay.add(new BulletDecaySegmented(new ArrayList<>(curve.segments().stream()
                    .map(segment -> new BulletDecaySegmented.DecaySegment(segment.startDistance(),
                            segment.damageMultiplier()))
                    .toList())));
        }
        return decay;
    }
}
