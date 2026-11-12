package com.ivomens.mcheli.combat.guidance;

import com.ivomens.mcheli.api.target.TargetSignature;
import com.ivomens.mcheli.api.vehicle.Targetable;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Which entities a seeker accepts as a target.
 *
 * @param air         locks targets in the air
 * @param ground      locks targets on the ground (see {@link #isOnGround})
 * @param water       locks targets in water
 * @param minHeight   a target closer than this above solid ground counts as on the ground
 * @param ridableOnly only vehicles, never creatures or players on foot
 * @param missiles    also locks guided missiles fired by someone else
 * @param heatSeeking flares are targets of their own, and a vehicle dropping them is not
 * @param radar       chaff clouds are targets of their own; chaff and a lock-breaking jammer hide a vehicle,
 *                    and flares do not unless the seeker also seeks heat
 */
public record TargetFilter(boolean air, boolean ground, boolean water, int minHeight, boolean ridableOnly,
                           boolean missiles, boolean heatSeeking, boolean radar) {

    /** The filter a weapon's definition describes, on top of what its kind always locks. */
    public static TargetFilter of(WeaponSpec info, boolean air, boolean ground) {
        return new TargetFilter(air, ground, info.missile().lockOn().lockInWater(),
                info.missile().lockOn().minHeight(), info.definition().ridableOnly(),
                info.missile().lockOn().canLockMissile(), info.missile().heat().heatMissile(),
                        info.missile().radar().radarMissile());
    }

    public TargetFilter withWater(boolean locksInWater) {
        return new TargetFilter(this.air, this.ground, locksInWater, this.minHeight, this.ridableOnly, this.missiles,
                this.heatSeeking, this.radar);
    }

    public TargetFilter withHeatSeeking(boolean seeksHeat) {
        return new TargetFilter(this.air, this.ground, this.water, this.minHeight, this.ridableOnly, this.missiles,
                seeksHeat, this.radar);
    }

    public TargetFilter withRadar(boolean radarGuided) {
        return new TargetFilter(this.air, this.ground, this.water, this.minHeight, this.ridableOnly, this.missiles,
                this.heatSeeking, radarGuided);
    }

    /**
     * Whether the seeker of {@code shooter} may lock the entity.
     *
     * @param shooter who is locking; never locks its own missiles. Null when unknown.
     */
    public boolean accepts(@Nullable Entity entity, @Nullable Entity shooter) {
        if (entity == null) return false;
        // riding players are locked through their vehicle
        if (entity instanceof Player && (entity.getVehicle() != null || this.ridableOnly)) return false;
        if (ForeignVehicles.isCamera(entity)) return false;
        ExternalTargets external = ExternalTargets.get();
        if (external.isActive()) {
            if (external.isExcluded(entity)) return false;
            ExternalTarget registered = external.find(entity);
            if (registered != null) return this.acceptsExternal(entity, registered.signature(entity));
        }
        Boolean special = this.special(entity, shooter);
        if (special != null) return special;
        if (!(entity instanceof LivingEntity || entity instanceof Vehicle || ForeignVehicles.isVehicle(entity))) {
            return false;
        }
        if (!this.water && entity.isInWater()) return false;
        return this.acceptsAltitude(entity);
    }

    /** Whether the entity is where this seeker looks: in the air, on the ground, or both. */
    public boolean acceptsAltitude(Entity entity) {
        boolean onGround = isOnGround(entity, this.minHeight);
        return (this.ground || !onGround) && (this.air || onGround);
    }

    /** The decision for decoys, vehicles that hide and missiles; null to judge the entity as a plain target. */
    @Nullable
    private Boolean special(Entity entity, @Nullable Entity shooter) {
        return switch (entity) {
            case FlareEntity flare -> this.heatSeeking ? Boolean.TRUE : null;
            case ChaffEntity chaff -> this.radar ? Boolean.TRUE : null;
            case Vehicle vehicle -> this.isHidden(vehicle.signature()) ? Boolean.FALSE : null;
            case ProjectileEntity projectile -> this.missiles && projectile.kind().isGuidedMissile()
                    ? shooter != projectile.shooter() : null;
            default -> null;
        };
    }

    private boolean isHidden(Targetable signature) {
        return this.isHidden(signature.announcedChaffTime() > 0, signature.isBreakingRadarLocks(),
                signature.isFlareUsing());
    }

    private boolean isHidden(boolean chaff, boolean jamming, boolean flares) {
        if (this.radar && chaff) return true;
        if (this.radar && jamming) return true;
        return (this.heatSeeking || !this.radar) && flares;
    }

    /**
     * Whether the seeker may lock an addon's target with this signature (null: no target right now): one it
     * can see at all, not hidden by its decoys, and where the seeker looks.
     */
    private boolean acceptsExternal(Entity entity, @Nullable TargetSignature signature) {
        return signature != null && !this.isHidden(signature.isChaffUsing(), false, signature.isFlareUsing())
                && this.keepsExternal(entity, signature);
    }

    /**
     * Whether a lock on an addon's target holds: as for a vehicle, chaff breaks a radar lock, flares break none,
     * and the target has to stay visible and where the seeker looks.
     */
    public boolean keepsExternal(Entity entity, @Nullable TargetSignature signature) {
        if (signature == null || this.visibility(signature) <= 0.0F) return false;
        if (this.radar && signature.isChaffUsing()) return false;
        if (!this.water && entity.isInWater()) return false;
        return switch (signature.domain()) {
            case AIR -> this.air;
            case GROUND -> this.ground;
            case BY_HEIGHT -> this.acceptsAltitude(entity);
        };
    }

    /**
     * How much of an addon's target this seeker sees, 0 to 1: the heat signature for a heat seeker, the radar
     * cross-section for a radar seeker, the stronger for one that uses both, all of it for any other.
     */
    private float visibility(TargetSignature signature) {
        if (this.heatSeeking && this.radar) return Math.max(signature.heat(), signature.radarCrossSection());
        if (this.heatSeeking) return signature.heat();
        if (this.radar) return signature.radarCrossSection();
        return 1.0F;
    }

    /**
     * The stealth this seeker sees in the entity, 0 for none to 1 for invisible: an addon's target's is what its
     * signature hides from this seeker, anything else's as {@link #stealthOf}.
     */
    public float stealthFor(@Nullable Entity entity) {
        if (entity != null) {
            ExternalTargets external = ExternalTargets.get();
            ExternalTarget registered = external.isActive() ? external.find(entity) : null;
            if (registered != null) {
                TargetSignature signature = registered.signature(entity);
                return signature != null ? 1.0F - this.visibility(signature) : 1.0F;
            }
        }
        return stealthOf(entity);
    }

    /**
     * Whether the entity stands on something, or has a solid block within {@code height} blocks below
     * it. Water does not count, so ships and low flyers over the sea stay air targets.
     */
    public static boolean isOnGround(@Nullable Entity entity, int height) {
        if (entity == null || entity.isRemoved()) return false;
        if (entity.onGround()) return true;
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(entity.getX() + 0.5);
        int z = Mth.floor(entity.getZ() + 0.5);
        int baseY = Mth.floor(entity.getY() + 0.5);
        for (int i = 0; i < height; i++) {
            pos.set(x, baseY - i, z);
            if (!level.isLoaded(pos)) break;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the projectile is a guided munition: anything with a seeker, or a missile of a guided kind. */
    public static boolean isGuidedThreat(ProjectileEntity projectile) {
        WeaponSpec info = projectile.spec();
        return info.missile().heat().heatMissile() || info.missile().radar().radarMissile()
                || info.missile().radar().passiveRadar() || info.missile().radar().activeRadar()
                || info.missile().laserGuidance() || info.missile().gpsMissile() || projectile.kind().isGuidedMissile();
    }

    /**
     * Whether flares decoy the weapon's missiles: heat seekers, and missiles that are no radar missile either,
     * as McHeli let flares decoy every missile.
     */
    public static boolean decoyedByFlares(WeaponSpec info) {
        return info.missile().heat().heatMissile() || !info.missile().radar().radarMissile();
    }

    /** Whether chaff decoys the weapon's missiles: radar missiles. */
    public static boolean decoyedByChaff(WeaponSpec info) {
        return info.missile().radar().radarMissile();
    }

    /** Whether the projectile is homing on the vehicle or on someone aboard it. */
    public static boolean isHomingOn(ProjectileEntity projectile, Vehicle vehicle) {
        Entity target = projectile.target();
        return target != null && (vehicle.seats().isAboard(target) || target.equals(vehicle.entity()));
    }

    /** Stealth of the entity, or of the vehicle it rides: 0 for none, 1 for invisible to seekers. */
    public static float stealthOf(@Nullable Entity entity) {
        if (entity instanceof Vehicle vehicle) return vehicle.signature().stealth();
        if (entity != null && entity.getVehicle() instanceof Vehicle vehicle) return vehicle.signature().stealth();
        return 0.0F;
    }
}
