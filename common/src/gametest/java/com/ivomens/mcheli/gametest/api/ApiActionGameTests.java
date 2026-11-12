package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.event.VehicleDestroyed;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnStatus;
import com.ivomens.mcheli.api.vehicle.action.VehicleActions;
import com.ivomens.mcheli.api.vehicle.action.VehicleControl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The API's vehicle actions as an addon uses them: spawning (and the ways a spawn is refused), damage through
 * the vehicle's own damage handling, repair, fuel, ammunition, crew, owner and lock, and destruction.
 * {@link ApiDataGameTests} covers the addon data.
 */
public final class ApiActionGameTests {

    private static final String BATCH = "mcheli_api_actions";
    static final String HELICOPTER = "ah-64";

    private ApiActionGameTests() {
    }

    public static List<TestFunction> actions() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "api_actions_spawn_refusals", "mcheli:empty", 20, 0, true,
                        ApiActionGameTests::spawnRefusals),
                new TestFunction(BATCH, "api_actions_change_a_vehicle", "mcheli:empty", 40, 0, true,
                        ApiActionGameTests::changeAVehicle));
    }

    /** Unknown types, missing seats and blocked spots are refused without placing anything. */
    private static void spawnRefusals(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        VehicleActions actions = McheliApi.get().vehicleActions();
        Vec3 pos = h.absoluteVec(new Vec3(8, 1, 8));
        int before = McheliApi.get().vehicles(level).size();
        expect(actions.spawner(level, VehicleKind.HELICOPTER, "no-such-type", pos).spawn().status()
                == SpawnStatus.UNKNOWN_TYPE, "an unknown type spawned");
        expect(actions.spawner(level, VehicleKind.PLANE, HELICOPTER, pos).spawn().status()
                == SpawnStatus.UNKNOWN_TYPE, "a helicopter spawned as a plane");
        SpawnResult noSeat = actions.spawner(level, VehicleKind.HELICOPTER, HELICOPTER, pos)
                .rider(99, h.makeMockServerPlayerInLevel()).spawn();
        expect(noSeat.status() == SpawnStatus.INVALID_SEAT && noSeat.vehicle().isEmpty(), "seat 99: " + noSeat);
        Vec3 inFloor = h.absoluteVec(new Vec3(8, 0, 8));
        expect(actions.spawner(level, VehicleKind.HELICOPTER, HELICOPTER, inFloor).spawn().status()
                == SpawnStatus.OBSTRUCTED, "a vehicle spawned inside the floor");
        expect(McheliApi.get().vehicles(level).size() == before, "a refused spawn left a vehicle behind");
        SpawnResult forced = actions.spawner(level, VehicleKind.HELICOPTER, HELICOPTER, inFloor)
                .requireFreeSpace(false).spawn();
        expect(forced.isSpawned(), "requireFreeSpace(false) did not spawn: " + forced.status());
        forced.vehicle().orElseThrow().entity().discard();
        h.succeed();
    }

    /** One helicopter, spawned with a pilot and a passenger, goes through every action. */
    private static void changeAVehicle(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Pig pig = Objects.requireNonNull(EntityType.PIG.create(level));
        Vec3 pos = h.absoluteVec(new Vec3(8, 1, 8));
        pig.moveTo(pos.x + 3, pos.y, pos.z);
        level.addFreshEntity(pig);

        SpawnResult result = McheliApi.get().vehicleActions()
                .spawner(level, VehicleKind.HELICOPTER, HELICOPTER, pos)
                .rotation(90.0F, 0.0F).owner(player).rider(0, player).rider(1, pig).spawn();
        expect(result.isSpawned(), "the helicopter did not spawn: " + result.status());
        Vehicle vehicle = result.vehicle().orElseThrow();
        expect(result.riders().get(0) == ActionResult.SUCCESS && result.riders().get(1) == ActionResult.SUCCESS,
                "riders: " + result.riders());
        expect(vehicle.seats().riderOf(0) == player && vehicle.seats().riderOf(1) == pig, "the riders are not aboard");
        expect(Math.abs(vehicle.entity().getYRot() - 90.0F) < 0.01F, "yaw " + vehicle.entity().getYRot());
        VehicleControl control = McheliApi.get().vehicleActions().control(vehicle);
        expect(control.owner().filter(player.getUUID()::equals).isPresent(), "the owner is " + control.owner());

        health(level, control);
        fuelAndAmmo(control);
        crew(control, player, pig);
        ownership(control, player);
        destruction(control, player, vehicle);
        vehicle.entity().discard();
        pig.discard();
        player.discard();
        h.succeed();
    }

    private static void health(ServerLevel level, VehicleControl control) {
        int full = control.health();
        expect(full == control.maxHealth(), "a new vehicle is damaged: " + full);
        expect(control.repair() == ActionResult.NO_CHANGE, "repaired a vehicle at full health");
        expect(control.damage(level.damageSources().generic(), 40.0F) == ActionResult.SUCCESS, "the hit was refused");
        expect(control.health() < full, "the hit did no damage");
        expect(control.repair(1) == ActionResult.SUCCESS, "repair(1)");
        expect(control.repair() == ActionResult.SUCCESS && control.health() == full, "health " + control.health());
    }

    private static void fuelAndAmmo(VehicleControl control) {
        expect(control.fuelCapacity() > 0, "the helicopter has no tank");
        expect(control.setFuel(10) == ActionResult.SUCCESS && control.fuel() == 10, "fuel " + control.fuel());
        expect(control.refuel() == ActionResult.SUCCESS && control.fuel() == control.fuelCapacity(),
                "refuel: " + control.fuel());
        expect(control.refuel() == ActionResult.NO_CHANGE, "refuelled a full tank");
        expect(control.weaponCount() > 0 && control.weapon(0).isPresent(), "the helicopter has no weapons");
        expect(control.setAmmo(0, 1) == ActionResult.SUCCESS && control.ammo(0) == 1, "ammo " + control.ammo(0));
        expect(control.rearm() == ActionResult.SUCCESS && control.ammo(0) == control.maxAmmo(0),
                "rearm: " + control.ammo(0) + " of " + control.maxAmmo(0));
        expect(control.setAmmo(control.weaponCount(), 1) == ActionResult.NO_SUCH_WEAPON, "a weapon slot too many");
    }

    private static void crew(VehicleControl control, ServerPlayer player, Pig pig) {
        expect(control.board(pig, 1) == ActionResult.NO_CHANGE, "boarding the seat it sits in");
        expect(control.board(player, 1) == ActionResult.SEAT_OCCUPIED, "boarding a taken seat");
        expect(control.eject(1) == ActionResult.SUCCESS && pig.getVehicle() == null, "the pig was not ejected");
        expect(control.eject(1) == ActionResult.SEAT_EMPTY, "ejecting an empty seat");
        expect(control.eject(99) == ActionResult.INVALID_SEAT, "ejecting seat 99");
        expect(control.board(pig, 1) == ActionResult.SUCCESS, "the pig could not board again");
        expect(control.dismount(pig) == ActionResult.SUCCESS && pig.getVehicle() == null, "dismount");
        expect(control.dismount(pig) == ActionResult.NOT_ABOARD, "dismounting someone not aboard");
        expect(control.board(pig, 1) == ActionResult.SUCCESS, "the pig could not board a third time");
        expect(control.ejectAll() == 2 && player.getVehicle() == null && pig.getVehicle() == null,
                "ejectAll left someone aboard");
    }

    private static void ownership(VehicleControl control, ServerPlayer player) {
        expect(control.mayChangeLock(player), "the owner may not change the lock");
        expect(control.setLocked(true) == ActionResult.SUCCESS && control.isLocked(), "could not lock");
        expect(control.setLocked(true) == ActionResult.NO_CHANGE, "locked twice");
        expect(control.mayBoard(player, 0), "the owner may not board the locked vehicle");
        expect(control.clearOwner() == ActionResult.SUCCESS && control.owner().isEmpty() && !control.isLocked(),
                "clearOwner left an owner or a lock");
        expect(control.setLocked(true) == ActionResult.NO_OWNER, "locked a vehicle without an owner");
        UUID someone = UUID.randomUUID();
        expect(control.setOwner(someone, "someone") == ActionResult.SUCCESS
                && control.ownerName().orElse("").equals("someone"), "setOwner(uuid, name)");
        expect(!control.mayChangeLock(player), "a stranger may change the lock");
        expect(control.setOwner(player) == ActionResult.SUCCESS && control.setLocked(false) == ActionResult.NO_CHANGE,
                "setOwner(player)");
    }

    private static void destruction(VehicleControl control, ServerPlayer player, Vehicle vehicle) {
        Entity[] attacker = {null};
        Subscription destroyed = McheliApi.get().events().register(VehicleDestroyed.class, event -> {
            if (event.vehicle() == vehicle) {
                attacker[0] = event.attacker();
            }
        });
        try {
            expect(control.destroy(player) == ActionResult.SUCCESS && vehicle.isDestroyed(), "not destroyed");
            expect(attacker[0] == player, "VehicleDestroyed named " + attacker[0]);
            expect(control.destroy(null) == ActionResult.NO_CHANGE, "destroyed a wreck");
            expect(control.repair() == ActionResult.VEHICLE_DESTROYED, "repaired a wreck");
            expect(control.board(player, 0) == ActionResult.VEHICLE_DESTROYED, "boarded a wreck");
        } finally {
            destroyed.unsubscribe();
        }
    }

    static void floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
