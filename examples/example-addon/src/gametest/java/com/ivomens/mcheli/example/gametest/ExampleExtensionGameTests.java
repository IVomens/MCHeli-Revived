package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensions;
import com.ivomens.mcheli.example.BeaconBehaviour;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.ExampleExtensions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The example addon's vehicle extensions, from its own jar: the pack's {@code example_hovercraft} hovers and flies
 * as {@code HovercraftModel} says, and its beacon behaviour pulses on the server.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleExtensionGameTests {

    private static final String HOVERCRAFT = "example_hovercraft";
    /** The file's settings. */
    private static final double HOVER_HEIGHT = 1.0;
    private static final double THRUST = 0.02;
    private static final double DRAG = 0.96;
    private static final int BEACON_INTERVAL = 10;
    private static final int FORWARD_FROM = 40;
    private static final int FORWARD_TICKS = 20;

    private ExampleExtensionGameTests() {
    }

    /**
     * The hovercraft rises to its cushion height over a stone floor, then the forward key is held for a while: it
     * covers exactly the distance the model's thrust and drag give, and its beacon pulsed every ten ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void hovercraftFliesAndBeaconPulses(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        if (!(VehicleTypes.get(VehicleKind.VEHICLE, HOVERCRAFT).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HOVERCRAFT + " has no item");
            return;
        }
        Vec3 spawn = h.absoluteVec(new Vec3(8, 1.5, 3));
        double groundTop = h.absoluteVec(new Vec3(0, 1, 0)).y;
        AircraftEntity ac = item.createAircraft(level, spawn.x, spawn.y, spawn.z, new ItemStack(item));
        ac.initRotationYaw(0.0F);
        level.addFreshEntity(ac);

        VehicleExtensions extensions = McheliApi.get().vehicleExtensions();
        long pulsesBefore = BeaconBehaviour.serverPulses();
        Vec3[] forwardStart = new Vec3[1];
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == FORWARD_FROM) {
                check(Math.abs(ac.getY() - groundTop - HOVER_HEIGHT) < 0.01,
                        "hovers " + (ac.getY() - groundTop) + " above the ground, not " + HOVER_HEIGHT);
                forwardStart[0] = ac.position();
                ac.keys().holdThrottleUp(true);
            } else if (tick[0] == FORWARD_FROM + FORWARD_TICKS) {
                ac.keys().holdThrottleUp(false);
                double flown = ac.position().subtract(forwardStart[0]).horizontalDistance();
                check(Math.abs(flown - expectedDistance()) < 1.0E-6,
                        "flew " + flown + " with the key held, the model says " + expectedDistance());
                check(ac.getZ() > forwardStart[0].z, "did not fly south (yaw 0)");
            }
        });
        h.succeedWhen(() -> {
            check(tick[0] > FORWARD_FROM + FORWARD_TICKS, "still flying");
            check(extensions.movementModelOf(ac).equals(Optional.of(ExampleExtensions.HOVERCRAFT)),
                    "movementModelOf " + extensions.movementModelOf(ac));
            check(extensions.behavioursOf(ac).equals(List.of(ExampleExtensions.BEACON)),
                    "behavioursOf " + extensions.behavioursOf(ac));
            long pulses = BeaconBehaviour.serverPulses() - pulsesBefore;
            check(pulses >= tick[0] / BEACON_INTERVAL - 1, "the beacon pulsed " + pulses + " times in " + tick[0]);
            ac.discard();
        });
    }

    /** The model's glide with the key held: thrust, then drag, every tick from rest. */
    private static double expectedDistance() {
        double speed = 0.0;
        double distance = 0.0;
        for (int i = 0; i < FORWARD_TICKS; i++) {
            speed += THRUST;
            distance += speed;
            speed *= DRAG;
        }
        return distance;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
