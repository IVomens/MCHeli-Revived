package com.ivomens.mcheli.gametest.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.config.AdminSettings;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.golden.GoldenMode;
import com.ivomens.mcheli.vehicle.component.VehicleNbtCodec;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.ivomens.mcheli.gametest.admin.AdminFixtures.expect;

/**
 * Vehicle ownership and locking: who may fly a locked vehicle, sit in its other seats and pick it up, and
 * that saves from before owners existed load without an owner and unlocked.
 */
public final class OwnershipGameTests {

    private static final String BATCH = "mcheli_admin_lock";
    private static final String HELICOPTER = "ah-64";
    /** Clicks a player makes to board: the first ones may only open the canopy. */
    private static final int CLICKS = 6;
    private static final int CLICK_INTERVAL = 10;
    private static final int PHASE = CLICKS * CLICK_INTERVAL + 5;

    private OwnershipGameTests() {
    }

    public static List<TestFunction> ownership() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) return List.of();
        return List.of(
                new TestFunction(BATCH, "admin_lock_pilot_seat", "mcheli:empty", 6 * PHASE, 0, true,
                        OwnershipGameTests::pilotSeat),
                new TestFunction(BATCH, "admin_lock_passenger_seats", "mcheli:empty", 3 * PHASE, 0, true,
                        OwnershipGameTests::passengerSeats),
                new TestFunction(BATCH, "admin_lock_old_save_unlocked", "mcheli:empty", 20, 0, true,
                        OwnershipGameTests::oldSaveLoadsUnlocked));
    }

    /** The owner, a teammate and an operator may fly a locked vehicle, a stranger may not, nor pick it up. */
    private static void pilotSeat(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerPlayer owner = AdminFixtures.player(h, "gt_lock_owner");
        ServerPlayer mate = AdminFixtures.player(h, "gt_lock_mate");
        ServerPlayer stranger = AdminFixtures.player(h, "gt_lock_stranger");
        ServerPlayer operator = AdminFixtures.player(h, "gt_lock_op");
        AdminFixtures.makeOperator(operator);
        Scoreboard scoreboard = h.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("gt_lock_team");
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(mate.getScoreboardName(), team);
        AircraftItem item = AdminFixtures.item(VehicleKind.HELICOPTER, HELICOPTER);
        AircraftEntity vehicle = AdminFixtures.spawn(h, item, new Vec3(8, 2, 8), owner);
        expect(vehicle.ownership().setLocked(true), "the owner could not lock the vehicle");

        clickToBoard(h, vehicle, stranger, 5);
        h.runAfterDelay(5 + PHASE - 1, () -> {
            expect(stranger.getVehicle() == null, "a stranger boarded a locked vehicle");
            expect(!vehicle.ownership().mayDismantle(stranger), "a stranger may pick up a locked vehicle");
            boolean teamOff = AdminFixtures.withConfig(AdminSettings.LOCK_ALLOWS_TEAM, false, () -> {
                vehicle.ownership().publish();
                return vehicle.ownership().mayBoard(mate, 0);
            });
            vehicle.ownership().publish();
            expect(!teamOff, "a teammate may board although LockAllowsTeam = false");
        });
        boardAndLeave(h, vehicle, mate, 5 + PHASE, "the owner's teammate");
        boardAndLeave(h, vehicle, operator, 5 + 2 * PHASE, "an operator");
        boardAndLeave(h, vehicle, owner, 5 + 3 * PHASE, "the owner");
        h.runAfterDelay(5 + 4 * PHASE + 5, () -> {
            expect(vehicle.ownership().mayDismantle(owner), "the owner may not pick up their vehicle");
            vehicle.ownership().setLocked(false);
            expect(vehicle.ownership().mayBoard(stranger, 0), "a stranger may not board an unlocked vehicle");
            AdminFixtures.removeOperator(operator);
            scoreboard.removePlayerTeam(team);
            vehicle.discard();
            List.of(owner, mate, stranger, operator).forEach(ServerPlayer::discard);
            h.succeed();
        });
    }

    /** Passengers may board a locked vehicle, unless LockPassengerSeats is set. */
    private static void passengerSeats(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerPlayer owner = AdminFixtures.player(h, "gt_seat_owner");
        ServerPlayer stranger = AdminFixtures.player(h, "gt_seat_stranger");
        AircraftItem item = AdminFixtures.item(VehicleKind.HELICOPTER, HELICOPTER);
        AircraftEntity vehicle = AdminFixtures.spawn(h, item, new Vec3(8, 2, 8), owner);
        vehicle.ownership().setLocked(true);
        clickToBoard(h, vehicle, owner, 5);
        h.runAfterDelay(5 + PHASE, () -> {
            expect(vehicle.getRiddenByEntity() == owner, "the owner could not board");
            vehicle.boarding().interact(stranger, InteractionHand.MAIN_HAND);
            expect(vehicle.seats().seatIdOf(stranger) > 0, "a passenger could not board a locked vehicle");
            stranger.stopRiding();
            SeatEntity seat = vehicle.seats().all()[0];
            boolean seated = AdminFixtures.withConfig(AdminSettings.LOCK_PASSENGER_SEATS, true, () -> {
                vehicle.ownership().publish();
                vehicle.boarding().interact(stranger, InteractionHand.MAIN_HAND);
                seat.interact(stranger, InteractionHand.MAIN_HAND);
                return stranger.getVehicle() != null;
            });
            vehicle.ownership().publish();
            stranger.stopRiding();
            owner.stopRiding();
            vehicle.discard();
            owner.discard();
            stranger.discard();
            expect(!seated, "a stranger took a passenger seat although LockPassengerSeats = true");
            h.succeed();
        });
    }

    /**
     * A vehicle saved before owners were recorded (the committed save fixture) loads without an owner and
     * unlocked, saves no owner keys, and an owner and lock survive a save of their own.
     */
    private static void oldSaveLoadsUnlocked(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        CompoundTag old = read(GoldenMode.fixtureDir().resolve("helicopter").resolve(HELICOPTER + ".fresh.snbt"));
        expect(!old.contains(VehicleNbtCodec.OWNER), "the fixture already has an owner");
        AircraftEntity loaded = (AircraftEntity) EntityType.create(old, level).orElseThrow();
        ServerPlayer stranger = AdminFixtures.player(h, "gt_old_save");
        expect(!loaded.ownership().hasOwner() && !loaded.ownership().isLocked(), "an old save loaded locked");
        expect(loaded.ownership().mayBoard(stranger, 0), "anyone may board a vehicle from an old save");
        CompoundTag resaved = new CompoundTag();
        loaded.saveWithoutId(resaved);
        expect(!resaved.contains(VehicleNbtCodec.OWNER) && !resaved.contains(VehicleNbtCodec.LOCKED),
                "a vehicle without an owner saved owner keys");

        loaded.ownership().setOwner(stranger);
        loaded.ownership().setLocked(true);
        CompoundTag owned = new CompoundTag();
        loaded.saveWithoutId(owned);
        owned.putString("id", EntityType.getKey(loaded.getType()).toString());
        AircraftEntity copy = (AircraftEntity) EntityType.create(owned, level).orElseThrow();
        expect(copy.ownership().isOwner(stranger) && copy.ownership().isLocked()
                && "gt_old_save".equals(copy.ownership().ownerName()), "owner or lock lost in a save");
        loaded.discard();
        copy.discard();
        stranger.discard();
        h.succeed();
    }

    private static void boardAndLeave(GameTestHelper h, AircraftEntity vehicle, ServerPlayer player, int start,
            String who) {
        clickToBoard(h, vehicle, player, start);
        h.runAfterDelay(start + PHASE - 1, () -> {
            expect(vehicle.getRiddenByEntity() == player, who + " could not board the locked vehicle");
            player.stopRiding();
        });
    }

    /** Clicks the vehicle like a player would, until they are aboard. */
    private static void clickToBoard(GameTestHelper h, AircraftEntity vehicle, ServerPlayer player, int start) {
        for (int i = 0; i < CLICKS; i++) {
            h.runAfterDelay(start + i * CLICK_INTERVAL, () -> {
                if (player.getVehicle() == null) {
                    vehicle.boarding().interact(player, InteractionHand.MAIN_HAND);
                }
            });
        }
    }

    private static CompoundTag read(Path file) {
        try {
            return TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | CommandSyntaxException e) {
            throw new AssertionError("could not read " + file + ": " + e);
        }
    }
}
