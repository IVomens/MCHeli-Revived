package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.event.WeaponFired;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.ExampleConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Path;

/**
 * The example addon running as a mod of its own next to the MCHeli mod jar: MCHeli found its content pack
 * provider in the other jar and loaded the pack, and the addon's {@code WeaponFired} listener counts shots and
 * cancels the ones fired inside spawn protection while its config says so.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleAddonGameTests {

    private static final String HELICOPTER = "ah-64";

    private ExampleAddonGameTests() {
    }

    /** The pack in the addon's jar loaded, under the addon's pack id, and the two mods are separate files. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void examplePackLoadsFromTheAddonJar(GameTestHelper h) {
        Path mcheli = ModList.get().getModFileById("mcheli").getFile().getFilePath();
        Path addon = ModList.get().getModFileById(ExampleAddon.MOD_ID).getFile().getFilePath();
        check(!mcheli.equals(addon), "MCHeli and the addon are loaded from the same file " + addon);

        WeaponInfo weapon = McheliApi.get().content().weapon("example_cannon")
                .orElseThrow(() -> new GameTestAssertException("example_cannon did not load"));
        check(ExampleAddon.MOD_ID.equals(weapon.packId()), "example_cannon came from pack " + weapon.packId());
        check("Example 30mm cannon".equals(weapon.displayName()) && "machinegun2".equals(weapon.type()),
                "example_cannon read wrong: " + weapon.displayName() + ", " + weapon.type());
        h.succeed();
    }

    /**
     * A player fires a helicopter's gun three ways: outside spawn protection (counted), inside it with the
     * addon's option on (cancelled, not counted), and inside it with the option off (counted).
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void addonCountsAndCancelsShots(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HELICOPTER + " has no item");
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.5, 8));
        AircraftEntity ac = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.moveTo(pos.x + 2, pos.y, pos.z);
        level.addFreshEntity(ac);

        Shots shots = new Shots(ac);
        // registered after the addon's listener, so it sees the addon's decision
        Subscription subscription = McheliApi.get().events().register(WeaponFired.class, shots::onFired);
        BoundingBox area = BoundingBox.fromCorners(h.absolutePos(new BlockPos(-8, -8, -8)),
                h.absolutePos(new BlockPos(24, 24, 24)));
        ExampleConfig.BLOCK_FIRE_IN_SPAWN_PROTECTION.set(true);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (shots.phase == Phase.DONE) {
                return;
            }
            if (ac.seats().seatIdOf(player) < 0) {
                if (tick[0] % 10 == 5) ac.boarding().interact(player, InteractionHand.MAIN_HAND);
                return;
            }
            boolean fired = ac.weapons().fire(player);
            check(shots.problem == null, shots.problem);
            check(!fired || shots.phase != Phase.PROTECTED, "a shot fired inside spawn protection");
            if (shots.seenInPhase >= 2) {
                shots.next();
                switch (shots.phase) {
                    case PROTECTED -> TestSpawnProtection.protect(level, area);
                    case PROTECTION_OFF -> ExampleConfig.BLOCK_FIRE_IN_SPAWN_PROTECTION.set(false);
                    default -> {
                    }
                }
            }
        });
        h.succeedWhen(() -> {
            check(shots.problem == null, shots.problem);
            check(shots.phase == Phase.DONE, "still at " + shots.phase);
            subscription.unsubscribe();
            TestSpawnProtection.clear();
            ExampleConfig.BLOCK_FIRE_IN_SPAWN_PROTECTION.set(true);
            ac.discard();
            player.discard();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private enum Phase { OPEN, PROTECTED, PROTECTION_OFF, DONE }

    /** What the test's listener saw of the helicopter's shots, checked against the addon's counter. */
    private static final class Shots {

        private final AircraftEntity vehicle;
        private Phase phase = Phase.OPEN;
        private int seenInPhase;
        private long counted = ExampleAddon.shotsFired();
        /** A listener cannot fail the test itself: the bus logs what it throws. */
        private String problem;

        Shots(AircraftEntity vehicle) {
            this.vehicle = vehicle;
        }

        void next() {
            this.phase = Phase.values()[this.phase.ordinal() + 1];
            this.seenInPhase = 0;
        }

        void onFired(WeaponFired fired) {
            if (fired.platform() != this.vehicle || this.phase == Phase.DONE || this.problem != null) {
                return;
            }
            boolean blocked = this.phase == Phase.PROTECTED;
            long now = ExampleAddon.shotsFired();
            if (fired.isCancelled() != blocked) {
                this.problem = this.phase + ": the addon " + (blocked ? "let the shot through" : "cancelled the shot");
            } else if (now != this.counted + (blocked ? 0 : 1)) {
                this.problem = this.phase + ": the addon counted " + (now - this.counted) + " shots for one";
            }
            this.counted = now;
            this.seenInPhase++;
        }
    }
}
