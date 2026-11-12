package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.net.ControlInput;
import com.ivomens.mcheli.gametest.golden.PinnedSettings;
import com.ivomens.mcheli.aircraft.net.GroundVehicleControlHandler;
import com.ivomens.mcheli.aircraft.net.GroundVehicleControlMessage;
import com.ivomens.mcheli.aircraft.net.HelicopterControlHandler;
import com.ivomens.mcheli.aircraft.net.HelicopterControlMessage;
import com.ivomens.mcheli.aircraft.net.PlaneControlHandler;
import com.ivomens.mcheli.aircraft.net.PlaneControlMessage;
import com.ivomens.mcheli.aircraft.net.ShipControlHandler;
import com.ivomens.mcheli.aircraft.net.ShipControlMessage;
import com.ivomens.mcheli.aircraft.net.TankControlHandler;
import com.ivomens.mcheli.aircraft.net.TankControlMessage;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.ship.ShipEntity;
import com.ivomens.mcheli.tank.TankEntity;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A vehicle that carries the weapon, parked at the range origin facing +z, with a pilot who boards
 * it, selects the weapon and fires through the same control packet a client sends.
 */
final class VehicleMount implements Mount {

    private static final int CLICK_INTERVAL = 5;
    private static final int MAX_CLICKS = 40;

    private final AircraftEntity vehicle;
    private final ServerPlayer pilot;
    private final String weaponName;
    private int weaponId = -1;
    private int ticks;
    private int clicks;

    private VehicleMount(AircraftEntity vehicle, ServerPlayer pilot, String weaponName) {
        this.vehicle = vehicle;
        this.pilot = pilot;
        this.weaponName = weaponName;
    }

    @Nullable
    static VehicleMount create(ServerLevel level, VehicleSpec info, String weaponName, ServerPlayer pilot, long seed,
                               PinnedSettings settings) {
        AircraftEntity vehicle = Vehicles.spawn(level, info, WeaponRange.at(0.0, 1.0, 0.0), 0.0F, seed);
        if (vehicle == null) {
            return null;
        }
        settings.pinVehicle(vehicle);
        return new VehicleMount(vehicle, pilot, weaponName);
    }

    @Override
    public boolean prepare() {
        this.ticks++;
        if (this.pilot.getVehicle() == null) {
            if (this.ticks % CLICK_INTERVAL == 0) {
                board();
            }
            return false;
        }
        if (this.weaponId < 0) {
            this.weaponId = findWeapon();
            this.vehicle.weapons().select(this.pilot, this.weaponId);
            return false;
        }
        WeaponGroup weapons = weapons();
        return weapons.canFire() && (weapons.magazineSize() <= 0 || weapons.ammo() > 0);
    }

    private void board() {
        if (++this.clicks > MAX_CLICKS) {
            throw new IllegalStateException("pilot could not board " + this.vehicle.spec().name());
        }
        this.pilot.moveTo(this.vehicle.getX() + 1.0, this.vehicle.getY(), this.vehicle.getZ());
        this.vehicle.boarding().interact(this.pilot, InteractionHand.MAIN_HAND);
    }

    private int findWeapon() {
        for (int i = 0; i < this.vehicle.weapons().count(); i++) {
            if (this.vehicle.weapons().get(i).isNamed(this.weaponName)) {
                return i;
            }
        }
        throw new IllegalStateException(this.vehicle.spec().name() + " has no weapon " + this.weaponName);
    }

    /**
     * The control packet a client sends with the trigger held, aiming straight ahead, or down at the floor
     * for close air support.
     */
    @Override
    public void fire(@Nullable Entity target) {
        boolean vehicleData = this.vehicle instanceof HelicopterEntity || this.vehicle instanceof GroundVehicleEntity;
        float pitch = 0.0F;
        Vec3 surface = Arsenal.surfaceAimedAt(this.weaponName);
        if (surface != null) {
            Vec3 from = this.pilot.position();
            pitch = (float) Math.toDegrees(Math.atan2(from.y - surface.y,
                    Math.hypot(surface.x - from.x, surface.z - from.z)));
            this.pilot.setXRot(pitch);
        }
        VehicleControlInputBuilder data = new VehicleControlInputBuilder();
        data.setUseWeapon(true);
        data.setUseWeaponPos(this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ());
        data.setUseWeaponUserYaw(this.vehicle.getYRot());
        data.setUseWeaponUserPitch(pitch);
        data.setFireRequest(target != null ? FireRequest.locked(target.getId(), 0) : FireRequest.NONE);
        deliver(data);
    }

    /** Hands the input to the server handler of the vehicle's type, as its client would send it. */
    private void deliver(VehicleControlInputBuilder data) {
        ControlInput input = data.build();
        switch (this.vehicle) {
            case HelicopterEntity heli -> new HelicopterControlHandler().handle(
                    new HelicopterControlMessage(input, data.buildExtras()), this.pilot);
            case ShipEntity ship -> new ShipControlHandler().handle(new ShipControlMessage(input), this.pilot);
            case PlaneEntity plane -> new PlaneControlHandler().handle(new PlaneControlMessage(input), this.pilot);
            case TankEntity tank -> new TankControlHandler().handle(new TankControlMessage(input), this.pilot);
            case GroundVehicleEntity ground -> new GroundVehicleControlHandler().handle(
                    new GroundVehicleControlMessage(input, data.buildExtras()), this.pilot);
            default -> throw new IllegalArgumentException("no control message for " + this.vehicle);
        }
    }

    @Override
    public void tick() {
        // the vehicle ticks its own weapons
    }

    @Override
    public WeaponGroup weapons() {
        return this.vehicle.weapons().get(this.weaponId);
    }

    AircraftEntity vehicle() {
        return this.vehicle;
    }

    @Override
    public String describe() {
        return "vehicle " + this.vehicle.spec().name() + " (" + this.vehicle.getClass().getSimpleName()
                + "), pilot seat, weapon #" + this.weaponId;
    }

    @Override
    public void remove() {
        this.pilot.stopRiding();
        this.pilot.server.getPlayerList().remove(this.pilot);
        this.pilot.discard();
        this.vehicle.discard();
    }
}
