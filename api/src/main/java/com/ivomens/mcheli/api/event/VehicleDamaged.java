package com.ivomens.mcheli.api.event;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * A vehicle is about to take a hit: a weapon's round or beam, an explosion, a crash or collision, fire, lava,
 * another mod's gun, a player's punch or any other vanilla damage. Fired once per hit, before armor and the
 * hit part's damage factor are applied. A hit that lands on a passenger seat or an extra hit box of a large
 * vehicle is passed on to the vehicle and fires this event once, for the vehicle. A hit on a crew member is
 * also passed on to the vehicle (doubled), which fires this event too.
 *
 * <p>Not fired for hits the vehicle ignores anyway: while it is invulnerable (its iron curtain, a short
 * immunity after an explosion or fire, a removed vehicle), and for the {@code inFire} and {@code cactus}
 * damage types.
 *
 * <p>Listeners can change {@link #amount()}, which then goes through armor as the original amount would, or
 * cancel the hit: a cancelled hit deals nothing, plays no sound, does not let a player break the vehicle down
 * and makes the vehicle's {@code hurt} return false. {@link VehicleDamageTaken} follows once a hit cost hit
 * points.
 *
 * <pre>{@code
 * events.register(VehicleDamaged.class, damaged -> {
 *     if (damaged.source().typeHolder().unwrapKey()
 *             .filter(key -> key.location().getNamespace().equals("othergunmod")).isPresent()) {
 *         damaged.setAmount(damaged.amount() * 0.5F);
 *     }
 * });
 * }</pre>
 *
 * <p>Server side, on the server thread. Since API 1.1.
 */
public interface VehicleDamaged extends McheliEvent, Cancellable {

    Vehicle vehicle();

    /** The damage source as the game passed it in. */
    DamageSource source();

    /** Who is responsible for the hit (the shooter, the player who punched), as {@link DamageSource#getEntity()}. */
    @Nullable
    Entity attacker();

    /** What hit (the projectile, the vehicle firing a beam), as {@link DamageSource#getDirectEntity()}. */
    @Nullable
    Entity directEntity();

    /**
     * The MCHeli weapon type of the hit, as {@link com.ivomens.mcheli.api.content.WeaponInfo#id()}: set for
     * the rounds, beams and explosions of MCHeli weapons, null for everything else.
     */
    @Nullable
    String weaponId();

    /** The damage before armor, as the listeners so far left it. */
    float amount();

    /**
     * Replaces the damage before armor. A negative amount counts as zero, which deals no damage.
     *
     * @throws IllegalArgumentException when the amount is not a number
     */
    void setAmount(float amount);
}
