package com.ivomens.mcheli.gametest.admin;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.config.AdminSettings;
import com.ivomens.mcheli.config.IntOption;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.vehicle.world.VehicleLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ivomens.mcheli.gametest.admin.AdminFixtures.expect;

/**
 * The placement limits: per player through the spawn item, per chunk, per level and per kind for a vehicle
 * about to be placed, and operators placing beyond them unless {@code OpsBypassLimits} is false. Each check
 * pins the limit just above what the level holds when it runs, so vehicles of other tests do not matter.
 */
public final class LimitGameTests {

    private static final String GUN = "12.7";

    private LimitGameTests() {
    }

    public static List<TestFunction> limits() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) return List.of();
        return List.of(
                new TestFunction("mcheli_admin_limits", "admin_limit_per_player", "mcheli:empty", 20, 0, true,
                        LimitGameTests::perPlayer),
                new TestFunction("mcheli_admin_limits", "admin_limit_chunk_level_kind", "mcheli:empty", 20, 0, true,
                        LimitGameTests::chunkLevelKind),
                new TestFunction("mcheli_admin_limits", "admin_limit_operator_bypass", "mcheli:empty", 20, 0, true,
                        LimitGameTests::operatorBypass));
    }

    /** With one vehicle per player, the spawn item places the first and refuses the second, keeping the item. */
    private static void perPlayer(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerPlayer player = AdminFixtures.player(h, "gt_limit_player");
        // the game test server puts players in creative, where placing keeps the item anyway
        player.setGameMode(GameType.SURVIVAL);
        AircraftItem item = AdminFixtures.item(VehicleKind.VEHICLE, GUN);
        ItemStack stack = new ItemStack(item, 8);
        List<AircraftEntity> placed = new ArrayList<>();
        AdminFixtures.withConfig(AdminSettings.MAX_PER_PLAYER, 1, () -> {
            placed.add(item.spawnAircraft(stack, h.getLevel(), player, h.absolutePos(new BlockPos(3, 0, 3))));
            placed.add(item.spawnAircraft(stack, h.getLevel(), player, h.absolutePos(new BlockPos(11, 0, 11))));
            return null;
        });
        AircraftEntity first = placed.get(0);
        try {
            expect(first != null && first.ownership().isOwner(player), "the first vehicle was not placed and owned");
            expect(placed.get(1) == null, "a second vehicle was placed beyond MaxVehiclesPerPlayer = 1");
            expect(stack.getCount() == 7, "the refused placement used up the item (" + stack.getCount() + " left)");
        } finally {
            placed.stream().filter(v -> v != null).forEach(AircraftEntity::discard);
            player.discard();
        }
        h.succeed();
    }

    /** A vehicle about to be placed is refused once its chunk, its level or its kind is full. */
    private static void chunkLevelKind(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerLevel level = h.getLevel();
        AircraftItem item = AdminFixtures.item(VehicleKind.VEHICLE, GUN);
        AircraftEntity placed = AdminFixtures.spawn(h, item, new Vec3(4.5, 1, 4.5), null);
        Vec3 next = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        AircraftEntity candidate = item.createAircraft(level, next.x, next.y, next.z, new ItemStack(item));
        AircraftIndex index = AircraftIndex.of(level);
        try {
            int inChunk = index.countInChunk(SectionPos.blockToSectionCoord(next.x),
                    SectionPos.blockToSectionCoord(next.z));
            expect(inChunk >= 1, "the index does not count the placed vehicle in its chunk");
            checkLimit(AdminSettings.MAX_PER_CHUNK, inChunk, candidate, "message.mcheli.limit.chunk");
            checkLimit(AdminSettings.MAX_PER_LEVEL, index.aircraft().size(), candidate, "message.mcheli.limit.level");
            checkLimit(AdminSettings.MAX_GROUND_VEHICLES, VehicleLimits.countOfKind(index, VehicleKind.VEHICLE),
                    candidate, "message.mcheli.limit.kind");
            int helicopters = VehicleLimits.countOfKind(index, VehicleKind.HELICOPTER);
            expect(AdminFixtures.withConfig(AdminSettings.MAX_HELICOPTERS, helicopters + 1,
                    () -> VehicleLimits.refusal(level, null, candidate)).isEmpty(),
                    "the helicopter limit refused a ground vehicle");
        } finally {
            candidate.discard();
            placed.discard();
        }
        h.succeed();
    }

    /** Operators place beyond the limits, unless OpsBypassLimits is false. */
    private static void operatorBypass(GameTestHelper h) {
        AdminFixtures.floor(h);
        ServerLevel level = h.getLevel();
        ServerPlayer operator = AdminFixtures.player(h, "gt_limit_op");
        ServerPlayer player = AdminFixtures.player(h, "gt_limit_other");
        AdminFixtures.makeOperator(operator);
        AircraftItem item = AdminFixtures.item(VehicleKind.VEHICLE, GUN);
        AircraftEntity placed = AdminFixtures.spawn(h, item, new Vec3(4.5, 1, 4.5), null);
        Vec3 pos = h.absoluteVec(new Vec3(11.5, 1, 11.5));
        AircraftEntity candidate = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        int full = AircraftIndex.of(level).aircraft().size();
        try {
            AdminFixtures.withConfig(AdminSettings.MAX_PER_LEVEL, full, () -> {
                expect(VehicleLimits.refusal(level, player, candidate).isPresent(), "a full level took a vehicle");
                expect(VehicleLimits.refusal(level, operator, candidate).isEmpty(),
                        "an operator was held to the level limit");
                expect(AdminFixtures.withConfig(AdminSettings.OPS_BYPASS_LIMITS, false,
                        () -> VehicleLimits.refusal(level, operator, candidate)).isPresent(),
                        "OpsBypassLimits = false still let an operator through");
                return null;
            });
        } finally {
            candidate.discard();
            placed.discard();
            AdminFixtures.removeOperator(operator);
            operator.discard();
            player.discard();
        }
        h.succeed();
    }

    /** At the count the vehicle is refused with that limit's message; one more and it is not. */
    private static void checkLimit(IntOption option, int count, AircraftEntity candidate, String message) {
        ServerLevel level = (ServerLevel) candidate.level();
        Optional<Component> full = AdminFixtures.withConfig(option, count,
                () -> VehicleLimits.refusal(level, null, candidate));
        expect(full.isPresent() && full.get().getContents() instanceof TranslatableContents key
                && key.getKey().equals(message), option.key() + " = " + count + " did not refuse with " + message);
        Optional<Component> room = AdminFixtures.withConfig(option, count + 1,
                () -> VehicleLimits.refusal(level, null, candidate));
        expect(room.isEmpty(), option.key() + " = " + (count + 1) + " refused a vehicle there is room for");
    }
}
