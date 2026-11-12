package com.ivomens.mcheli.gametest.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.ivomens.mcheli.gametest.admin.AdminFixtures.expect;

/** The operator commands: {@code /mcheli vehicles list|remove|removeall} and {@code /mcheli perf}. */
public final class AdminCommandGameTests {

    private static final String BATCH = "mcheli_admin_commands";
    private static final int SEATS_SPAWNED = 20;

    private AdminCommandGameTests() {
    }

    public static List<TestFunction> commands() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) return List.of();
        return List.of(
                new TestFunction(BATCH, "admin_vehicles_remove", "mcheli:empty", 60, 0, true,
                        AdminCommandGameTests::removeEjectsRiders),
                new TestFunction(BATCH, "admin_vehicles_removeall", "mcheli:empty", 20, 0, true,
                        AdminCommandGameTests::removeAllOfKind),
                new TestFunction(BATCH, "admin_perf_report", "mcheli:empty", 60, 0, true,
                        AdminCommandGameTests::perfReports));
    }

    /** {@code list} finds the owner's vehicle; {@code remove radius} ejects its crew and removes it, dropping nothing. */
    private static void removeEjectsRiders(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerPlayer owner = AdminFixtures.player(h, "gt_rm_owner");
        ServerPlayer passenger = AdminFixtures.player(h, "gt_rm_passenger");
        AircraftItem item = AdminFixtures.item(VehicleKind.HELICOPTER, "ah-64");
        AircraftEntity vehicle = AdminFixtures.spawn(h, item, new Vec3(8, 2, 8), owner);
        h.runAfterDelay(SEATS_SPAWNED, () -> {
            SeatEntity seat = vehicle.seats().all()[0];
            expect(seat != null, "the seats were not spawned");
            owner.startRiding(vehicle, true);
            passenger.startRiding(seat, true);
            expect(vehicle.getRiddenByEntity() == owner && passenger.getVehicle() == seat, "could not seat the crew");

            List<Component> output = new ArrayList<>();
            int listed = run(h, output, "mcheli vehicles list gt_rm_owner");
            expect(listed == 1, "list found " + listed + " vehicles of the owner instead of 1");
            int removed = run(h, output, "mcheli vehicles remove radius 3");
            expect(removed >= 1, "remove radius removed nothing");
            expect(vehicle.isRemoved() && seat.isRemoved(), "the vehicle or its seat is still there");
            expect(owner.getVehicle() == null && passenger.getVehicle() == null, "the crew was not ejected");
            AABB around = vehicle.getBoundingBox().inflate(8.0);
            expect(h.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(),
                    "removing dropped items although RemoveDropsItem = false");
            owner.discard();
            passenger.discard();
            h.succeed();
        });
    }

    /** {@code removeall <kind>} needs {@code confirm} and removes the vehicles of that kind. */
    private static void removeAllOfKind(GameTestHelper h) {
        AdminFixtures.floor(h);
        AircraftEntity ship = AdminFixtures.spawn(h, AdminFixtures.item(VehicleKind.SHIP, "cb90"), new Vec3(8, 2, 8),
                null);
        List<Component> output = new ArrayList<>();
        boolean unconfirmed;
        try {
            run(h, output, "mcheli vehicles removeall ship");
            unconfirmed = true;
        } catch (GameTestAssertException expected) {
            unconfirmed = false;
        }
        expect(!unconfirmed && !ship.isRemoved(), "removeall ran without confirm");
        int removed = run(h, output, "mcheli vehicles removeall ship confirm");
        expect(removed >= 1 && ship.isRemoved(), "removeall ship confirm did not remove the ship");
        h.succeed();
    }

    /** {@code /mcheli perf} reports the levels, the tick times and the vehicle that ticked. */
    private static void perfReports(GameTestHelper h) {
        AdminFixtures.floor(h);
        AircraftEntity vehicle = AdminFixtures.spawn(h, AdminFixtures.item(VehicleKind.HELICOPTER, "ah-64"),
                new Vec3(8, 2, 8), null);
        h.runAfterDelay(SEATS_SPAWNED, () -> {
            List<Component> output = new ArrayList<>();
            int lines = run(h, output, "mcheli perf");
            vehicle.discard();
            expect(lines >= 5 && output.size() == lines, "perf printed " + output.size() + " lines");
            expect(keys(output).contains("commands.mcheli.perf.header"), "perf printed no header");
            expect(keys(output).contains("commands.mcheli.perf.time"), "perf printed no tick times");
            expect(keys(output).contains("commands.mcheli.perf.vehicle"), "perf listed no vehicle");
            h.succeed();
        });
    }

    private static List<String> keys(List<Component> output) {
        List<String> keys = new ArrayList<>();
        for (Component line : output) {
            if (line.getContents() instanceof TranslatableContents translatable) {
                keys.add(translatable.getKey());
            }
        }
        return keys;
    }

    /** Runs the command as an operator console at the test's centre and collects what it prints. */
    static int run(GameTestHelper h, List<Component> output, String command) {
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack().withSource(sink)
                .withPermission(4).withLevel(h.getLevel()).withPosition(h.absoluteVec(new Vec3(8, 2, 8)));
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
    }
}
