package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.combat.projectile.guidance.AirToAirGuidance;
import com.ivomens.mcheli.combat.projectile.guidance.AntiTankGuidance;
import com.ivomens.mcheli.combat.projectile.guidance.BombRun;
import com.ivomens.mcheli.combat.projectile.guidance.CarrierDrift;
import com.ivomens.mcheli.combat.projectile.guidance.Guidance;
import com.ivomens.mcheli.combat.projectile.guidance.GunRoundFuze;
import com.ivomens.mcheli.combat.projectile.guidance.PointAttackGuidance;
import com.ivomens.mcheli.combat.projectile.guidance.TorpedoRun;
import com.ivomens.mcheli.combat.projectile.guidance.TvGuidance;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * What a projectile is: its registered entity type (the id saved worlds and clients know it by), how
 * it steers after each move, the smoke it trails, how it splits into bomblets and its special role.
 * Weapons pick a kind; everything else about a shot comes from the weapon definition.
 * <p>
 * Each kind keeps the entity type it always had, so worlds, commands and client renderers see the same
 * ids ({@code mcheli:bullet}, {@code mcheli:aa_missile} ...), while one class, {@link ProjectileEntity},
 * does the work for all of them.
 */
public enum ProjectileKind {

    /** Gun rounds; fast ones move several steps per tick. Explosive rounds have a simple proximity fuze. */
    BULLET(CombatEntityTypes.BULLET, new GunRoundFuze(), Trail.NONE, Submunition.NONE, Role.PROJECTILE),
    /** Unguided rockets, which may split into more rockets. */
    ROCKET(CombatEntityTypes.ROCKET, Guidance.NONE, Trail.MAIN_ROCKET, Submunition.ROCKETS, Role.PROJECTILE),
    AA_MISSILE(CombatEntityTypes.AA_MISSILE, new AirToAirGuidance(), Trail.MISSILE, Submunition.NONE, Role.PROJECTILE),
    AS_MISSILE(CombatEntityTypes.AS_MISSILE, new PointAttackGuidance(), Trail.CRUISE_MISSILE, Submunition.SCATTERED,
            Role.PROJECTILE),
    AT_MISSILE(CombatEntityTypes.AT_MISSILE, new AntiTankGuidance(), Trail.MISSILE, Submunition.NONE, Role.PROJECTILE),
    TV_MISSILE(CombatEntityTypes.TV_MISSILE, new TvGuidance(), Trail.STEERED_MISSILE, Submunition.ROCKETS,
            Role.PROJECTILE),
    BOMB(CombatEntityTypes.BOMB, new BombRun(), Trail.NONE, Submunition.FALLING, Role.MINE),
    TORPEDO(CombatEntityTypes.TORPEDO, new TorpedoRun(), Trail.UNDERWATER, Submunition.NONE, Role.PROJECTILE),
    MARKER_ROCKET(CombatEntityTypes.MARKER_ROCKET, Guidance.NONE, Trail.NONE, Submunition.NONE, Role.MARKER),
    DISPENSED_ITEM(CombatEntityTypes.DISPENSED_ITEM, new CarrierDrift(), Trail.CONTAINER, Submunition.FALLING,
            Role.DISPENSER);

    /** What a projectile does beyond flying and exploding. */
    public enum Role {
        PROJECTILE,
        /** A bomb whose weapon has a mine fuze settles where it lands and waits (see {@link LandMine}). */
        MINE,
        /** Marks where it lands with smoke and calls a bomb strike in (see {@link MarkerSmoke}). */
        MARKER,
        /** Uses an item on the blocks around where it lands (see {@link ItemDispenser}). */
        DISPENSER
    }

    private final Supplier<EntityType<ProjectileEntity>> type;
    private final Guidance guidance;
    private final Trail trail;
    private final Submunition submunition;
    private final Role role;

    ProjectileKind(Supplier<EntityType<ProjectileEntity>> type, Guidance guidance, Trail trail, Submunition submunition,
                   Role role) {
        this.type = type;
        this.guidance = guidance;
        this.trail = trail;
        this.submunition = submunition;
        this.role = role;
    }

    /** The kind an entity type was registered for. */
    public static ProjectileKind of(EntityType<?> entityType) {
        for (ProjectileKind kind : values()) {
            if (kind.type.get() == entityType) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no projectile kind for " + EntityType.getKey(entityType));
    }

    public EntityType<ProjectileEntity> entityType() {
        return this.type.get();
    }

    public Guidance guidance() {
        return this.guidance;
    }

    public Trail trail() {
        return this.trail;
    }

    public Submunition submunition() {
        return this.submunition;
    }

    public Role role() {
        return this.role;
    }

    /** A missile that homes on its target: seekers that lock missiles accept it, and the gunner shoots at it. */
    public boolean isGuidedMissile() {
        return this == AA_MISSILE || this == AT_MISSILE || this == AS_MISSILE || this == TV_MISSILE;
    }

    /**
     * Whether a fast weapon moves this projectile several steps per tick: rounds and rockets, the rockets
     * a rocket splits into included (they take the weapon before they are marked as bomblets).
     */
    boolean movesInSteps() {
        return this == BULLET || this == ROCKET;
    }

    /**
     * Whether clients fly the projectile themselves from what they were told when it appeared: rounds and
     * rockets, which nothing but gravity, drag and their motor steers, and which every client flies the
     * way the server does. The server sends their position again only when something it alone knows
     * changed the flight, and now and then (see {@link ProjectileSync}).
     */
    public boolean isFlownByClients() {
        return this == BULLET || this == ROCKET;
    }
}
