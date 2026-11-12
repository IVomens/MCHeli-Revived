package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.VehicleInfo;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.event.SeatBoarded;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.event.VehicleRemoved;
import com.ivomens.mcheli.api.event.VehicleSpawned;
import com.ivomens.mcheli.api.event.WeaponFired;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.pack.AddonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The public API as an addon uses it, through {@link McheliApi} only where it can: a content pack registered
 * by a {@link com.ivomens.mcheli.api.content.ContentPackProvider} ({@link TestContentPacks}) loads, the
 * lookups find vehicles and content, and the events fire where they should, with {@code WeaponFired}
 * cancelling a shot. The example addon in examples/example-addon does the same from its own jar, with its own
 * game tests (./gradlew :example-addon:runGameTestServer).
 */
public final class ApiGameTests {

    private static final String BATCH = "mcheli_api";
    private static final String HELICOPTER = "ah-64";

    private ApiGameTests() {
    }

    public static List<TestFunction> api() {
        // stress runs (-Dmcheli.stress=...) run nothing else
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "api_mod_content_pack_loads", "mcheli:empty", 20, 0, true,
                        ApiGameTests::modContentPackLoads),
                new TestFunction(BATCH, "api_events_fire_and_cancel", "mcheli:empty", 600, 0, true,
                        ApiGameTests::eventsFireAndCancel));
    }

    /** The pack the test files register loads after the bundled content, under the registering mod's id. */
    private static void modContentPackLoads(GameTestHelper h) {
        McheliApi api = McheliApi.get();
        WeaponInfo weapon = api.content().weapon(TestContentPacks.WEAPON)
                .orElseThrow(() -> new GameTestAssertException("the registered pack's weapon did not load"));
        check(TestContentPacks.modId().equals(weapon.packId()), "pack id " + weapon.packId());
        check("API test gun".equals(weapon.displayName()) && "machinegun2".equals(weapon.type()),
                "weapon read wrong: " + weapon.displayName() + ", " + weapon.type());
        check(api.content().weapons().stream().anyMatch(w -> w.id().equals(TestContentPacks.WEAPON)),
                "weapon missing from the list");
        VehicleInfo heli = api.content().vehicle(VehicleKind.HELICOPTER, HELICOPTER)
                .orElseThrow(() -> new GameTestAssertException(HELICOPTER + " missing"));
        check(AddonManager.BUILTIN_ADDON_DOMAIN.equals(heli.packId()), "bundled pack id " + heli.packId());
        check(api.apiVersion().matches("\\d+\\.\\d+\\.\\d+.*"), "api version " + api.apiVersion());
        h.succeed();
    }

    /**
     * A helicopter spawns, a player boards it and fires; a listener cancels the first shots, then lets them
     * through. Every listener only looks at this test's helicopter, as other tests run at the same time.
     */
    private static void eventsFireAndCancel(GameTestHelper h) {
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

        McheliApi api = McheliApi.get();
        Events events = new Events(ac);
        List<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(api.events().register(VehicleSpawned.class, e -> events.spawned |= e.vehicle() == ac));
        subscriptions.add(api.events().register(SeatBoarded.class, e -> {
            if (e.vehicle() == ac && e.rider() == player && e.seatId() == 0) events.boarded = true;
        }));
        subscriptions.add(api.events().register(WeaponFired.class, events::onFired));
        subscriptions.add(api.events().register(VehicleRemoved.class, e -> {
            if (e.vehicle() == ac) events.removal = e.reason();
        }));

        level.addFreshEntity(ac);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (ac.isRemoved()) return;
            if (ac.seats().seatIdOf(player) < 0) {
                if (tick[0] % 10 == 5) ac.boarding().interact(player, InteractionHand.MAIN_HAND);
                return;
            }
            boolean fired = ac.weapons().fire(player);
            check(events.problem == null, events.problem);
            if (events.cancelling) {
                check(!fired, "a cancelled shot fired");
                if (events.cancelled >= 3) events.cancelling = false;
            } else if (fired && events.allowed > 0) {
                check(events.spawned, "VehicleSpawned did not fire");
                check(events.boarded, "SeatBoarded did not fire for the pilot");
                check(api.vehicleOf(player).map(v -> v == ac).orElse(false), "vehicleOf(pilot) is not the helicopter");
                check(api.vehicles(level).contains(ac), "vehicles(level) misses the helicopter");
                Optional<VehicleInfo> type = api.content().vehicleOf(ac);
                check(type.map(t -> t.id().equals(HELICOPTER)).orElse(false), "content().vehicleOf " + type);
                ac.discard();
            }
        });
        h.succeedWhen(() -> {
            check(events.removal == Entity.RemovalReason.DISCARDED, "VehicleRemoved not seen yet: " + events.removal);
            subscriptions.forEach(Subscription::unsubscribe);
            player.discard();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** What the listeners saw of the test's helicopter. */
    private static final class Events {

        private final AircraftEntity vehicle;
        private boolean spawned;
        private boolean boarded;
        private boolean cancelling = true;
        private int cancelled;
        private int allowed;
        private Entity.RemovalReason removal;
        /** A listener cannot fail the test itself: the bus logs what it throws. */
        private String problem;

        Events(AircraftEntity vehicle) {
            this.vehicle = vehicle;
        }

        void onFired(WeaponFired fired) {
            if (fired.platform() != this.vehicle) {
                return;
            }
            if (fired.vehicle() != this.vehicle || fired.weaponId().isEmpty() || fired.shooter() == null) {
                this.problem = "WeaponFired names the wrong vehicle, weapon or shooter";
            }
            if (this.cancelling) {
                fired.setCancelled(true);
                this.cancelled++;
            } else {
                this.allowed++;
            }
        }
    }
}
