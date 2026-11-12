package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.net.AmmoCountsRequestMessage;
import com.ivomens.mcheli.combat.weapon.net.WeaponCounters;
import com.ivomens.mcheli.combat.weapon.net.WeaponStateMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * Which weapon each seat has selected, and which seat operates each weapon. Owned by
 * {@link WeaponComponent}, which offers it to the rest of the mod.
 */
final class SeatWeaponSelection {

    /** Weapons whose target mask includes one of these bits stay on their configured seat. */
    private static final int SEAT_BOUND_TARGETS = 195;
    /** Seat id that no rider has: weapons parked there in single player test setups. */
    private static final int NO_SEAT = 1000;
    private static final double WEAPON_SYNC_RANGE = 150.0;

    private final AircraftEntity vehicle;
    private final WeaponComponent weapons;
    private final SeatsComponent seats;
    /** The debug test mode, in which anyone may fire unguided weapons without a type. */
    private final BooleanSupplier testMode;
    /** Selected weapon id per seat (0 = pilot), -1 for none. */
    private int[] currentWeaponIds = new int[0];

    SeatWeaponSelection(AircraftEntity vehicle, WeaponComponent weapons, SeatsComponent seats,
            BooleanSupplier testMode) {
        this.vehicle = vehicle;
        this.weapons = weapons;
        this.seats = seats;
        this.testMode = testMode;
    }

    void reset(int seatCount) {
        this.currentWeaponIds = new int[seatCount];
        Arrays.fill(this.currentWeaponIds, -1);
    }

    int currentId(@Nullable Entity entity) {
        if (!WeaponComponent.isOperator(entity)) {
            return -1;
        }
        return idForSeat(this.seats.seatIdOf(entity));
    }

    int idForSeat(int seatId) {
        return seatId >= 0 && seatId < this.currentWeaponIds.length ? this.currentWeaponIds[seatId] : -1;
    }

    int pilotWeaponId() {
        return this.currentWeaponIds.length > 0 ? this.currentWeaponIds[0] : -1;
    }

    void clearPilot() {
        this.currentWeaponIds[0] = -1;
    }

    /**
     * The seat a weapon is operated from. Unguided weapons without a type can be fired by anyone in
     * single player or test mode, which parks them on a seat nobody has.
     */
    int seatOf(@Nullable WeaponSpec weaponInfo, VehicleParts.Weapon mount) {
        boolean seatBound = weaponInfo == null || (weaponInfo.definition().target() & SEAT_BOUND_TARGETS) != 0
                || !weaponInfo.definition().type().isEmpty() || !LocalClient.get().isSingleplayer()
                        && !this.testMode.getAsBoolean();
        return seatBound ? mount.seatID : NO_SEAT;
    }

    /** The entity aiming a weapon: its seat's operator, or the pilot for pilot-usable weapons on an empty seat. */
    @Nullable
    Entity operator(int weaponId, VehicleParts.Weapon mount) {
        SeatsComponent seats = this.seats;
        Entity entity = seats.riderOf(seatOf(this.weapons.infoById(weaponId), mount));
        if (mount.canUsePilot && !WeaponComponent.isOperator(entity)) {
            entity = seats.riderOf(0);
        }
        return entity;
    }

    @Nullable
    Player playerUsing(int weaponId) {
        if (weaponId < 0 || weaponId >= this.weapons.count()) {
            return null;
        }
        for (int seatId = 0; seatId < this.currentWeaponIds.length; seatId++) {
            if (this.currentWeaponIds[seatId] == weaponId
                    && this.seats.riderOf(seatId) instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Next weapon (in the given direction) that the entity's seat can use, or -1 if there is none. */
    int nextId(@Nullable Entity entity, int step) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        int seatId = this.seats.seatIdOf(entity);
        if (info == null || seatId < 0) {
            return -1;
        }

        int weaponCount = this.weapons.count();
        int id = currentId(entity);
        for (int i = 0; i < weaponCount; i++) {
            id = step >= 0 ? (id + 1) % weaponCount : (id > 0 ? id - 1 : weaponCount - 1);
            VehicleParts.Weapon mount = info.armament().getWeaponById(id);
            if (mount == null) {
                continue;
            }
            int weaponSeat = seatOf(this.weapons.infoById(id), mount);
            boolean pilotTakesOver = seatId == 0 && mount.canUsePilot
                    && !WeaponComponent.isOperator(this.seats.riderOf(weaponSeat));
            if (weaponSeat < this.seats.count() + 2 && (weaponSeat == seatId || pilotTakesOver)) {
                return id;
            }
        }
        return -1;
    }

    void select(Entity entity, int id) {
        AircraftEntity ac = this.vehicle;
        int seatId = this.seats.seatIdOf(entity);
        int weaponCount = this.weapons.count();
        if (!this.seats.isValidSeatId(seatId) || weaponCount <= 0 || this.currentWeaponIds.length <= 0) {
            return;
        }
        if (id < 0) {
            // deselecting: nothing is reloaded, the previous weapon keeps its magazine as is
            this.currentWeaponIds[seatId] = -1;
        }
        if (id >= weaponCount) {
            id = weaponCount - 1;
        }

        this.weapons.current(entity).reload();
        this.currentWeaponIds[seatId] = id;
        WeaponGroup selected = this.weapons.current(entity);
        selected.onSelected(this.weapons.hasInfiniteAmmo(entity));
        if (ac.level() instanceof ServerLevel serverLevel) {
            Network.sendToAllAround(serverLevel, ac.position(), WEAPON_SYNC_RANGE, stateMessage(seatId, id, selected));
        }
    }

    WeaponStateMessage stateMessage(int seatId, int weaponId, WeaponGroup weapon) {
        return new WeaponStateMessage(this.vehicle.getId(), seatId, weaponId, new WeaponCounters(
                (short) weapon.ammo(), (short) weapon.reserveAmmo(), (short) weapon.cooldown(),
                (short) weapon.reloadTicks()));
    }

    /** Applies a weapon selection received from the other side. */
    void applySelection(int seatId, int id) {
        if (seatId < 0 || seatId >= this.currentWeaponIds.length || this.weapons.count() <= 0) {
            return;
        }
        this.currentWeaponIds[seatId] = Math.min(id, this.weapons.count() - 1);
    }

    /** Selects the first usable weapon for a rider who just took a seat. */
    void selectFirst(Entity entity) {
        AircraftEntity ac = this.vehicle;
        int seatId = this.seats.seatIdOf(entity);
        if (seatId < 0 || seatId >= this.currentWeaponIds.length) {
            return;
        }
        this.currentWeaponIds[seatId] = -1;
        if (WeaponComponent.isOperator(entity)) {
            this.currentWeaponIds[seatId] = nextId(entity, 1);
            select(entity, currentId(entity));
            if (ac.level().isClientSide) {
                Network.sendToServer(new AmmoCountsRequestMessage(ac.getId()));
            }
        }
    }

    boolean isSelectedByAnySeat(int weaponId) {
        for (int selected : this.currentWeaponIds) {
            if (selected == weaponId) {
                return true;
            }
        }
        return false;
    }

    /** True while someone aboard has one of these weapons selected. */
    boolean isAnySelectedByRider(Integer[] weaponIds) {
        boolean selected = false;
        for (int weaponId : weaponIds) {
            for (int seatId = 0; seatId < this.currentWeaponIds.length; seatId++) {
                if (weaponId == this.currentWeaponIds[seatId] && this.seats.riderOf(seatId) != null) {
                    selected = true;
                }
            }
        }
        return selected;
    }
}
