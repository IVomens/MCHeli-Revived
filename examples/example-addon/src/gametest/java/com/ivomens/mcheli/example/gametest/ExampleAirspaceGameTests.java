package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.event.VehicleBoardAttempt;
import com.ivomens.mcheli.api.event.VehicleDamaged;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's airspace rules ({@code ExampleAirspace}): a player inside spawn protection cannot take a
 * helicopter's pilot seat until the protection goes, and vehicles take half damage from the configured mod's
 * damage types. In a batch of their own, as the spawn protection of the tests is one area at a time.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleAirspaceGameTests {

    private static final String BATCH = "mcheli_example_airspace";
    private static final String HELICOPTER = "ah-64";

    private ExampleAirspaceGameTests() {
    }

    /** Clicks every half second: refused while the area is protected, seated once it no longer is. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void noFlyZoneKeepsPilotsOut(GameTestHelper h) {
        AircraftEntity ac = spawnHelicopter(h);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vec3 near = h.absoluteVec(new Vec3(10, 1, 8));
        player.moveTo(near.x, near.y, near.z);
        TestSpawnProtection.protect(h.getLevel(), BoundingBox.fromCorners(h.absolutePos(new BlockPos(-8, -8, -8)),
                h.absolutePos(new BlockPos(24, 24, 24))));
        int[] refused = {0};
        // registered after the addon's listener, so it sees the addon's decision
        Subscription subscription = McheliApi.get().events().register(VehicleBoardAttempt.class, attempt -> {
            if (attempt.vehicle() == ac && attempt.isCancelled()) {
                refused[0]++;
            }
        });
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (refused[0] >= 2) {
                TestSpawnProtection.clear();
            }
            if (ac.seats().seatIdOf(player) < 0 && tick[0] % 10 == 5) {
                ac.boarding().interact(player, InteractionHand.MAIN_HAND);
            }
            check(refused[0] >= 2 || ac.seats().seatIdOf(player) < 0,
                    "a pilot boarded inside the no-fly zone after " + refused[0] + " refusals");
        });
        h.succeedWhen(() -> {
            check(ac.seats().seatIdOf(player) == 0, "not seated yet after " + refused[0] + " refusals");
            subscription.unsubscribe();
            TestSpawnProtection.clear();
            ac.discard();
            player.discard();
        });
    }

    /** With {@code halfDamageFromMod} naming vanilla, a later listener sees a vanilla hit halved; empty, whole. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void halvesDamageFromTheConfiguredMod(GameTestHelper h) {
        AircraftEntity ac = spawnHelicopter(h);
        float[] seen = {-1.0F};
        // registered after the addon's listener, so it sees the addon's change
        Subscription subscription = McheliApi.get().events().register(VehicleDamaged.class, damaged -> {
            if (damaged.vehicle() == ac) {
                seen[0] = damaged.amount();
                damaged.setCancelled(true);
            }
        });
        h.runAfterDelay(5, () -> {
            try {
                ExampleConfig.HALF_DAMAGE_FROM_MOD.set("minecraft");
                ac.hurt(h.getLevel().damageSources().generic(), 10.0F);
                check(seen[0] == 5.0F, "vanilla damage reached later listeners as " + seen[0]);
                ExampleConfig.HALF_DAMAGE_FROM_MOD.set("");
                ac.hurt(h.getLevel().damageSources().generic(), 10.0F);
                check(seen[0] == 10.0F, "with the option empty, damage reached later listeners as " + seen[0]);
            } finally {
                ExampleConfig.HALF_DAMAGE_FROM_MOD.set("");
                subscription.unsubscribe();
                ac.discard();
            }
            h.succeed();
        });
    }

    private static AircraftEntity spawnHelicopter(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            throw new GameTestAssertException(HELICOPTER + " has no item");
        }
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.5, 8));
        AircraftEntity ac = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        if (ac == null) {
            throw new GameTestAssertException("cannot create " + HELICOPTER);
        }
        level.addFreshEntity(ac);
        return ac;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
