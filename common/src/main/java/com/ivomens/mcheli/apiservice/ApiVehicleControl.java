package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.net.AmmoCounts;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.api.vehicle.action.VehicleControl;
import com.ivomens.mcheli.api.vehicle.data.MutableVehicleData;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.vehicle.component.DamageComponent;
import com.ivomens.mcheli.vehicle.component.FuelComponent;
import com.ivomens.mcheli.vehicle.component.FuelFluids;
import com.ivomens.mcheli.vehicle.component.OwnershipComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The API's handle on one server vehicle; every action goes through the vehicle's own components. */
final class ApiVehicleControl implements VehicleControl {

    private final AircraftEntity vehicle;

    ApiVehicleControl(AircraftEntity vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public Vehicle vehicle() {
        return this.vehicle;
    }

    /** Why the vehicle cannot be repaired, refuelled or rearmed; null when it can. */
    @Nullable
    private ActionResult unusable() {
        if (this.vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        return this.vehicle.isDestroyed() ? ActionResult.VEHICLE_DESTROYED : null;
    }

    // Health

    @Override
    public int health() {
        return this.vehicle.damage().hp();
    }

    @Override
    public int maxHealth() {
        return this.vehicle.damage().maxHp();
    }

    @Override
    public ActionResult repair() {
        return repair(Math.max(1, this.vehicle.damage().damageTaken()));
    }

    @Override
    public ActionResult repair(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("repair amount must be positive: " + amount);
        }
        ActionResult refusal = unusable();
        if (refusal != null) {
            return refusal;
        }
        DamageComponent damage = this.vehicle.damage();
        return damage.repair(Math.min(amount, damage.damageTaken())) ? ActionResult.SUCCESS : ActionResult.NO_CHANGE;
    }

    @Override
    public ActionResult damage(DamageSource source, float amount) {
        Objects.requireNonNull(source, "source");
        if (this.vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        return this.vehicle.hurt(source, amount) ? ActionResult.SUCCESS : ActionResult.REFUSED;
    }

    @Override
    public ActionResult destroy(@Nullable Entity attacker) {
        if (this.vehicle.isRemoved()) {
            return ActionResult.VEHICLE_REMOVED;
        }
        if (this.vehicle.isDestroyed()) {
            return ActionResult.NO_CHANGE;
        }
        this.vehicle.damage().destroyBy(attacker);
        return ActionResult.SUCCESS;
    }

    // Fuel

    @Override
    public int fuel() {
        return this.vehicle.fuel().amount();
    }

    @Override
    public int fuelCapacity() {
        return Math.max(0, this.vehicle.fuel().capacity());
    }

    @Override
    public ActionResult refuel() {
        return setFuel(fuelCapacity());
    }

    @Override
    public ActionResult setFuel(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("fuel amount must not be negative: " + amount);
        }
        ActionResult refusal = unusable();
        if (refusal != null) {
            return refusal;
        }
        FuelComponent fuel = this.vehicle.fuel();
        if (fuelCapacity() <= 0) {
            return ActionResult.NO_FUEL_TANK;
        }
        int target = Math.min(amount, fuelCapacity());
        if (target == fuel.amount()) {
            return ActionResult.NO_CHANGE;
        }
        if (!fuel.isElectric() && fuel.fuelType().isBlank()) {
            fuel.setFuelType(FuelFluids.DEFAULT_FUEL_TYPE);
        }
        fuel.setAmount(target);
        return ActionResult.SUCCESS;
    }

    // Weapons

    @Override
    public int weaponCount() {
        return this.vehicle.weapons().count();
    }

    @Nullable
    private WeaponGroup weaponIn(int slot) {
        return slot >= 0 && slot < weaponCount() ? this.vehicle.weapons().get(slot) : null;
    }

    @Override
    public Optional<WeaponInfo> weapon(int slot) {
        WeaponGroup weapon = weaponIn(slot);
        return weapon == null ? Optional.empty() : McheliApi.get().content().weapon(weapon.spec().name());
    }

    @Override
    public int ammo(int slot) {
        WeaponGroup weapon = weaponIn(slot);
        return weapon == null ? 0 : weapon.ammo() + weapon.reserveAmmo();
    }

    @Override
    public int maxAmmo(int slot) {
        WeaponGroup weapon = weaponIn(slot);
        return weapon == null ? 0 : Math.max(0, weapon.maxAmmo());
    }

    @Override
    public ActionResult rearm() {
        ActionResult result = weaponCount() > 0 ? ActionResult.NO_CHANGE : ActionResult.NO_SUCH_WEAPON;
        for (int slot = 0; slot < weaponCount(); slot++) {
            ActionResult one = rearm(slot);
            if (!one.isSuccess()) {
                return one;
            }
            if (one == ActionResult.SUCCESS) {
                result = one;
            }
        }
        return result;
    }

    @Override
    public ActionResult rearm(int slot) {
        return setAmmo(slot, maxAmmo(slot));
    }

    @Override
    public ActionResult setAmmo(int slot, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("ammo amount must not be negative: " + amount);
        }
        ActionResult refusal = unusable();
        if (refusal != null) {
            return refusal;
        }
        WeaponGroup weapon = weaponIn(slot);
        if (weapon == null) {
            return ActionResult.NO_SUCH_WEAPON;
        }
        int target = Math.min(amount, maxAmmo(slot));
        if (target == ammo(slot)) {
            return ActionResult.NO_CHANGE;
        }
        // as a saved vehicle loads: everything in the reserve, then the magazine filled from it
        weapon.setAmmo(0);
        weapon.setReserveAmmo(target);
        weapon.reloadMagazine();
        AmmoCounts.send(this.vehicle, null, slot);
        return ActionResult.SUCCESS;
    }

    // Crew

    @Override
    public ActionResult board(Entity rider, int seatId) {
        return CrewActions.board(this.vehicle, Objects.requireNonNull(rider, "rider"), seatId);
    }

    @Override
    public ActionResult eject(int seatId) {
        return CrewActions.eject(this.vehicle, seatId);
    }

    @Override
    public int ejectAll() {
        return CrewActions.ejectAll(this.vehicle);
    }

    @Override
    public ActionResult dismount(Entity rider) {
        return CrewActions.dismount(this.vehicle, Objects.requireNonNull(rider, "rider"));
    }

    // Owner and lock

    @Override
    public Optional<UUID> owner() {
        return this.vehicle.ownership().owner();
    }

    @Override
    public Optional<String> ownerName() {
        OwnershipComponent ownership = this.vehicle.ownership();
        return ownership.hasOwner() ? Optional.of(ownership.ownerName()) : Optional.empty();
    }

    @Override
    public ActionResult setOwner(Player owner) {
        return setOwner(owner.getUUID(), owner.getGameProfile().getName());
    }

    @Override
    public ActionResult setOwner(UUID owner, String name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        OwnershipComponent ownership = this.vehicle.ownership();
        if (ownership.owner().filter(owner::equals).isPresent() && ownership.ownerName().equals(name)) {
            return ActionResult.NO_CHANGE;
        }
        ownership.setOwner(owner, name);
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult clearOwner() {
        OwnershipComponent ownership = this.vehicle.ownership();
        if (!ownership.hasOwner()) {
            return ActionResult.NO_CHANGE;
        }
        ownership.clearOwner();
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean isLocked() {
        return this.vehicle.ownership().isLocked();
    }

    @Override
    public ActionResult setLocked(boolean locked) {
        OwnershipComponent ownership = this.vehicle.ownership();
        boolean wasLocked = ownership.isLocked();
        if (!locked) {
            // also forgets a lock that waits for the server to allow locking again
            ownership.setLocked(false);
            return wasLocked ? ActionResult.SUCCESS : ActionResult.NO_CHANGE;
        }
        if (wasLocked) {
            return ActionResult.NO_CHANGE;
        }
        if (!McheliConfig.admin().vehicleLocking()) {
            return ActionResult.LOCKING_DISABLED;
        }
        if (!ownership.hasOwner()) {
            return ActionResult.NO_OWNER;
        }
        return ownership.setLocked(true) ? ActionResult.SUCCESS : ActionResult.REFUSED;
    }

    @Override
    public boolean mayBoard(Player player, int seatId) {
        return this.vehicle.ownership().mayBoard(player, seatId);
    }

    @Override
    public boolean mayChangeLock(Player player) {
        return this.vehicle.ownership().mayChangeLock(player);
    }

    // Addon data

    @Override
    public MutableVehicleData data() {
        return this.vehicle.data();
    }
}
