package com.ivomens.mcheli.vehicle.settings;

import com.ivomens.mcheli.config.AdminSettings;
import com.ivomens.mcheli.config.CombatSettings;
import com.ivomens.mcheli.config.ControlSettings;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.config.VehicleSettings;
import com.ivomens.mcheli.config.wingman.WingmanSettings;
import com.ivomens.mcheli.core.sim.port.SimSettings;

/**
 * Everything the vehicle layer reads from the settings, as one immutable snapshot. A vehicle's
 * components get the part they need (a {@code Supplier} of one of the records below) through their
 * constructors and never read the config themselves; {@link VehicleSettingsHolder} publishes a new
 * snapshot whenever the config is loaded, reloaded or synced.
 *
 * @param sim        the physics settings, built once per snapshot
 * @param durability how vehicles take damage, heal and end
 * @param supply     whether they run out of ammunition and fuel
 * @param carriage   minecarts, racks and the placed position hold
 * @param locks      owners locking their vehicles
 * @param uav        the UAV control range
 * @param testMode   the debug test mode, in which anyone may fire a vehicle's unguided weapons
 * @param client     this client's own preferences
 */
public record VehicleSettingsSnapshot(SimSettings sim, DurabilityRules durability, SupplyRules supply,
                                      CarriageRules carriage, LockRules locks, UavRules uav, boolean testMode,
                                      ClientPreferences client) {

    /**
     * The snapshot of the given settings.
     *
     * @param config  {@code mcheli.cfg}
     * @param server  the server settings (on a client, as the server last sent them)
     * @param wingman {@code mcheli-wingman.cfg}
     */
    public static VehicleSettingsSnapshot of(McheliSettings config, ServerSettings server, WingmanSettings wingman) {
        VehicleSettings vehicles = config.vehicles();
        CombatSettings combat = config.combat();
        AdminSettings admin = config.admin();
        ControlSettings controls = config.controls();
        SimSettings sim = new SimSettings(false, config.flight().quaternionRotation(), controls.flightSimMode(),
                server.tiltLimit());
        DurabilityRules durability = new DurabilityRules(vehicles.preventingBroken(), vehicles.breakableOnlyPickaxe(),
                vehicles.dropItemInCreativeMode(), vehicles.autoRepairHp(), combat.collisionDamage(),
                combat.collisionDamageFactor(), vehicles.killPassengersWhenDestroyed(), vehicles.despawnCount());
        SupplyRules supply = new SupplyRules(combat.infinityAmmo(), vehicles.infinityFuel());
        CarriageRules carriage = new CarriageRules(vehicles.mountMinecartHeli(), vehicles.mountMinecartPlane(),
                vehicles.mountMinecartTank(), vehicles.mountMinecartVehicle(), vehicles.fixVehicleAtPlacedPoint(),
                vehicles.enablePutRackInFlying());
        LockRules locks = new LockRules(admin.vehicleLocking(), admin.lockAllowsTeam(), admin.lockPassengerSeats(),
                admin.commandPermission());
        PilotSettings pilot = new PilotSettings(vehicles.dismountAll(), controls.autoThrottleDownHeli(),
                controls.autoThrottleDownPlane(), controls.autoThrottleDownTank());
        ClientPreferences client = new ClientPreferences(pilot, !config.render().disableShader(),
                config.flight().fixedStepAttitude(), AssistSettings.of(config.handling()));
        return new VehicleSettingsSnapshot(sim, durability, supply, carriage, locks,
                new UavRules(wingman.uavControllerRange()), config.debug().testMode(), client);
    }
}
