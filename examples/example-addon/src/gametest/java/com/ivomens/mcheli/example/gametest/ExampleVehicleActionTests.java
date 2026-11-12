package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.action.ActionResult;
import com.ivomens.mcheli.api.vehicle.action.SpawnResult;
import com.ivomens.mcheli.api.vehicle.action.VehicleControl;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.ExampleSorties;
import com.ivomens.mcheli.vehicledata.VehicleDataMessage;
import com.ivomens.mcheli.vehicledata.VehicleDataStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * An addon changing vehicles through the API alone: it spawns a helicopter with a pilot, damages, repairs,
 * refuels, rearms and empties its gun, ejects the pilot and locks it; and the addon's sortie count
 * ({@link ExampleSorties}) counts the pilot, is saved and loaded with the helicopter and reaches a client copy,
 * while the last pilot's name stays on the server. Only the sync check reaches into MCHeli, to stand in for a
 * client.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleVehicleActionTests {

    private static final String HELICOPTER = "ah-64";

    private ExampleVehicleActionTests() {
    }

    /** Spawn with a pilot, then damage, repair, refuel, rearm, eject and lock through a VehicleControl. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void addonChangesAVehicle(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vehicle vehicle = spawnWithPilot(h, player);
        VehicleControl control = McheliApi.get().vehicleActions().control(vehicle);

        check(control.damage(h.getLevel().damageSources().generic(), 40.0F) == ActionResult.SUCCESS
                && control.health() < control.maxHealth(), "the hit did no damage");
        check(control.repair() == ActionResult.SUCCESS && control.health() == control.maxHealth(), "repair");
        check(control.refuel() == ActionResult.SUCCESS && control.fuel() == control.fuelCapacity(), "refuel");
        check(control.rearm() == ActionResult.SUCCESS && control.ammo(0) == control.maxAmmo(0), "rearm");
        check(control.setAmmo(0, 0) == ActionResult.SUCCESS && control.ammo(0) == 0, "empty the gun");
        check(control.eject(0) == ActionResult.SUCCESS && player.getVehicle() == null, "the pilot was not ejected");
        check(control.owner().filter(player.getUUID()::equals).isPresent(), "the spawner's owner was not kept");
        check(control.setLocked(true) == ActionResult.SUCCESS && control.isLocked(), "could not lock");
        check(control.clearOwner() == ActionResult.SUCCESS && !control.isLocked(), "clearing the owner kept the lock");
        vehicle.entity().discard();
        player.discard();
        h.succeed();
    }

    /** The pilot counts as a sortie; the count is saved and loaded, and reaches a client, the pilot's name not. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sortiesAreSavedAndSynced(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vehicle vehicle = spawnWithPilot(h, player);
        check(vehicle.data().get(ExampleSorties.SORTIES) == 1, "sorties " + vehicle.data().get(ExampleSorties.SORTIES));
        String pilot = player.getGameProfile().getName();
        check(pilot.equals(vehicle.data().get(ExampleSorties.LAST_PILOT)), "last pilot");

        CompoundTag saved = new CompoundTag();
        vehicle.entity().saveWithoutId(saved);
        saved.putString("id", EntityType.getKey(vehicle.entity().getType()).toString());
        Vehicle loaded = (Vehicle) EntityType.create(saved, level).orElseThrow();
        check(loaded.data().get(ExampleSorties.SORTIES) == 1 && pilot.equals(loaded.data().get(ExampleSorties.LAST_PILOT)),
                "lost in the save: " + loaded.data().get(ExampleSorties.SORTIES));

        List<VehicleDataMessage> sync = ((VehicleDataStore) vehicle.data()).changeMessages();
        // a fresh copy of the entity type, which never joins the level, stands in for the client's
        Vehicle client = (Vehicle) vehicle.entity().getType().create(level);
        sync.forEach(message -> ((VehicleDataStore) client.data()).receive(message.entries()));
        check(client.data().get(ExampleSorties.SORTIES) == 1, "the client copy has no sorties");
        check(client.data().get(ExampleSorties.LAST_PILOT).isEmpty(), "the pilot's name reached the client");

        player.stopRiding();
        List.of(vehicle, loaded, client).forEach(v -> v.entity().discard());
        player.discard();
        h.succeed();
    }

    private static Vehicle spawnWithPilot(GameTestHelper h, ServerPlayer player) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        SpawnResult result = McheliApi.get().vehicleActions()
                .spawner(h.getLevel(), VehicleKind.HELICOPTER, HELICOPTER, h.absoluteVec(new Vec3(8, 1, 8)))
                .owner(player).rider(0, player).spawn();
        check(result.isSpawned() && result.riders().get(0) == ActionResult.SUCCESS,
                "spawn: " + result.status() + ", " + result.riders());
        return result.vehicle().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
