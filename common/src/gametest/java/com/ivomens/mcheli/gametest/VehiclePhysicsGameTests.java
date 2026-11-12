package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;
import com.ivomens.mcheli.aircraft.net.HelicopterControlHandler;
import com.ivomens.mcheli.aircraft.net.HelicopterControlMessage;
import com.ivomens.mcheli.aircraft.net.ShipControlHandler;
import com.ivomens.mcheli.aircraft.net.ShipControlMessage;
import com.ivomens.mcheli.aircraft.net.TankControlHandler;
import com.ivomens.mcheli.aircraft.net.TankControlMessage;
import com.ivomens.mcheli.aircraft.net.VehicleControlInputBuilder;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.vehicle.component.StatusWord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Vehicle physics in a real level, for the cases a golden trajectory does not pin down by itself:
 * the server hears the tank's steering keys, floats float, ships run aground without breaking up,
 * parked UAVs stay whole, and block probes give the same answer on both sides of the origin.
 * <p>
 * Each test builds its own site far from the course of the golden trajectories and keeps its chunks
 * loaded while it runs.
 */
public final class VehiclePhysicsGameTests {

    private static final String BATCH = "mcheli_physics";

    private VehiclePhysicsGameTests() {
    }

    public static List<TestFunction> physics() {
        List<TestFunction> tests = new ArrayList<>();
        if (!StressTests.SCENARIO.isEmpty()) {
            return tests;
        }
        tests.add(test("tank_steering_keys_reach_server", 200, VehiclePhysicsGameTests::tankSteeringKeysReachServer));
        tests.add(test("float_helicopters_float", 600, VehiclePhysicsGameTests::floatHelicoptersFloat));
        tests.add(test("ship_runs_aground_unharmed", 600, VehiclePhysicsGameTests::shipRunsAgroundUnharmed));
        tests.add(test("parked_uavs_stay_whole", 600, VehiclePhysicsGameTests::parkedUavsStayWhole));
        tests.add(test("block_probes_ignore_the_origin", 200, VehiclePhysicsGameTests::blockProbesIgnoreTheOrigin));
        return tests;
    }

    private static TestFunction test(String name, int timeout, Consumer<GameTestHelper> body) {
        return new TestFunction(BATCH, name, "mcheli:empty", timeout, 0, true, body);
    }

    // Tank steering

    /** The steering keys in the tank's control packet reach the server, which publishes them to everyone. */
    private static void tankSteeringKeysReachServer(GameTestHelper h) {
        Site site = new Site(h.getLevel(), new BlockPos(8192, 100, 8192));
        site.run(h, time -> {
            site.floor(-4, 28, -4, 28, Blocks.STONE);
            AircraftEntity tank = site.spawn(VehicleKind.TANK, "m1a2", new Vec3(12.5, 1.0, 12.5));
            ServerPlayer driver = board(h, tank);
            ControlInputBuilder left = new ControlInputBuilder();
            left.setMoveLeft(true);
            new TankControlHandler().handle(new TankControlMessage(left.build()), driver);
            expect(h, tank.keys().moveLeft() && !tank.keys().moveRight(), "the server did not hear the left key");
            time.after(2, () -> {
                expect(h, tank.statusWord().get(StatusWord.MOVE_LEFT),
                        "the left key was not published to the other clients");
                new TankControlHandler().handle(new TankControlMessage(new ControlInputBuilder().build()), driver);
                expect(h, !tank.keys().moveLeft(), "releasing the left key did not reach the server");
                driver.discard();
                site.release();
                h.succeed();
            });
        });
    }

    // Floats

    /**
     * Helicopters with floats stay on the water: parked on it, and landed on it by a pilot who
     * closes the throttle and then lets go of the keys.
     */
    private static void floatHelicoptersFloat(GameTestHelper h) {
        List<VehicleSpec> floaters = VehicleTypes.registry(VehicleKind.HELICOPTER).values().stream()
                .filter(info -> info.file().physics().canFloat()).toList();
        if (floaters.isEmpty()) {
            h.fail("no helicopter with floats");
            return;
        }
        Site site = new Site(h.getLevel(), new BlockPos(8704, 100, 8192));
        int length = floaters.size() * 24 + 8;
        site.run(h, time -> {
            site.pool(0, length, 0, 64, 4);
            List<AircraftEntity> helis = new ArrayList<>();
            List<ServerPlayer> pilots = new ArrayList<>();
            for (int i = 0; i < floaters.size(); i++) {
                helis.add(site.spawn(floaters.get(i), new Vec3(12.5 + 24 * i, 1.0, 16.5)));
                AircraftEntity flown = site.spawn(floaters.get(i), new Vec3(12.5 + 24 * i, 9.0, 44.5));
                flown.fuel().setAmount(flown.fuel().capacity());
                helis.add(flown);
                ServerPlayer pilot = board(h, flown);
                pilots.add(pilot);
                ControlInputBuilder down = new ControlInputBuilder();
                down.setThrottleDown(true);
                new HelicopterControlHandler().handle(new HelicopterControlMessage(down.build(),
                        new VehicleControlInputBuilder().buildExtras()), pilot);
            }
            time.after(100, () -> {
                for (ServerPlayer pilot : pilots) {
                    new HelicopterControlHandler().handle(new HelicopterControlMessage(
                            new ControlInputBuilder().build(), new VehicleControlInputBuilder().buildExtras()), pilot);
                }
            });
            time.after(400, () -> {
                double surface = site.origin().getY() + 1.0;
                for (AircraftEntity heli : helis) {
                    String name = heli.spec().name() + (heli.getRiddenByEntity() != null ? " (flown)" : "");
                    expect(h, heli.getY() > surface - 1.5, name + " sank to " + (heli.getY() - surface));
                    expect(h, heli.damage().hp() == heli.damage().maxHp() && !heli.isDestroyed(),
                            name + " was damaged: hp " + heli.damage().hp());
                }
                pilots.forEach(ServerPlayer::discard);
                site.release();
                h.succeed();
            });
        });
    }

    // Running aground

    /** A ship driven up a beach at full speed stops there without damage. */
    private static void shipRunsAgroundUnharmed(GameTestHelper h) {
        Site site = new Site(h.getLevel(), new BlockPos(9216, 100, 8192));
        site.run(h, time -> {
            site.pool(0, 24, 0, 72, 3);
            // the far end of the basin is a sand shelf level with the water
            site.box(1, 23, -2, 0, 56, 71, Blocks.SAND);
            AircraftEntity ship = site.spawn(VehicleKind.SHIP, "cb90", new Vec3(12.5, 1.0, 8.5));
            ship.fuel().setAmount(ship.fuel().capacity());
            ServerPlayer pilot = board(h, ship);
            ControlInputBuilder ahead = new ControlInputBuilder();
            ahead.setThrottleUp(true);
            new ShipControlHandler().handle(new ShipControlMessage(ahead.build()), pilot);
            time.after(300, () -> {
                expect(h, ship.getZ() - site.origin().getZ() > 50.0, "the ship never reached the beach: z "
                        + (ship.getZ() - site.origin().getZ()));
                expect(h, ship.damage().hp() == ship.damage().maxHp(),
                        "running aground damaged the ship: hp " + ship.damage().hp() + "/" + ship.damage().maxHp());
                pilot.discard();
                site.release();
                h.succeed();
            });
        });
    }

    // Parked UAVs

    /** Every UAV placed on the ground and left alone stays whole. */
    private static void parkedUavsStayWhole(GameTestHelper h) {
        List<VehicleSpec> uavs = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            uavs.addAll(VehicleTypes.registry(kind).values().stream()
                    .filter(info -> info.isUAV() && !info.file().uav().targetDrone()).toList());
        }
        if (uavs.isEmpty()) {
            h.fail("no UAV definitions");
            return;
        }
        Site site = new Site(h.getLevel(), new BlockPos(9728, 100, 8192));
        int length = Math.min(uavs.size(), 6) * 32 + 16;
        site.run(h, time -> {
            site.floor(-8, length, -8, (uavs.size() / 6 + 1) * 32 + 8, Blocks.STONE);
            List<AircraftEntity> parked = new ArrayList<>();
            for (int i = 0; i < uavs.size(); i++) {
                parked.add(site.spawn(uavs.get(i), new Vec3(16.5 + 32 * (i % 6), 1.0, 16.5 + 32 * (i / 6))));
            }
            time.after(400, () -> {
                List<String> broken = new ArrayList<>();
                for (AircraftEntity uav : parked) {
                    if (uav.isRemoved() || uav.isDestroyed() || uav.damage().hp() < uav.damage().maxHp()) {
                        broken.add(uav.spec().name() + " (hp " + uav.damage().hp() + "/" + uav.damage().maxHp()
                                + (uav.isDestroyed() ? ", destroyed" : "") + (uav.isRemoved() ? ", removed" : "")
                                + ", y " + String.format("%.2f", uav.getY() - site.origin().getY()) + ")");
                    }
                }
                expect(h, broken.isEmpty(), "parked UAVs were damaged: " + broken);
                site.release();
                h.succeed();
            });
        });
    }

    // Negative coordinates

    /**
     * The block probes the physics uses find the same block at the same place relative to it, on
     * either side of the origin.
     */
    private static void blockProbesIgnoreTheOrigin(GameTestHelper h) {
        Site positive = new Site(h.getLevel(), new BlockPos(10240, 100, 8192));
        // a whole number of chunks away, so the two sites line up block for block
        Site negative = new Site(h.getLevel(), new BlockPos(-10240, 100, -8192));
        for (Site site : List.of(positive, negative)) {
            site.box(-2, 16, -4, 4, -2, 16, Blocks.AIR);
            site.box(5, 5, 0, 0, 0, 7, Blocks.STONE);
        }
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double dx = 3.0 + i * 0.1;
            for (double dz : new double[] {3.3, 3.7, 4.5}) {
                boolean above = probe(positive, dx, dz);
                boolean below = probe(negative, dx, dz);
                if (above != below) {
                    differences.add(String.format("(%.1f, %.1f): %s at positive, %s at negative", dx, dz,
                            above, below));
                }
            }
        }
        expect(h, differences.isEmpty(), "probes depend on the side of the origin: " + differences);
        h.succeed();
    }

    /** Whether the probe the physics uses for "something below" finds the stone from 1.2 blocks up. */
    private static boolean probe(Site site, double dx, double dz) {
        Vec3 at = site.absolute(dx, 1.2, dz);
        return Terrain.firstBlockY(site.level(), at.x, at.y, at.z, 1, -3, true) != Blocks.AIR;
    }

    // Helpers

    private static ServerPlayer board(GameTestHelper h, AircraftEntity vehicle) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        for (int click = 0; click < 10 && player.getVehicle() != vehicle; click++) {
            player.moveTo(vehicle.getX() + 1.0, vehicle.getY(), vehicle.getZ());
            vehicle.boarding().interact(player, InteractionHand.MAIN_HAND);
        }
        if (player.getVehicle() != vehicle) {
            throw new IllegalStateException("could not board " + vehicle.spec().name());
        }
        return player;
    }

    private static void expect(GameTestHelper h, boolean condition, String message) {
        if (!condition) {
            h.fail(message);
        }
    }

    /** Steps to run a number of ticks after a site is ready. */
    private static final class Timeline {

        private final Map<Integer, List<Runnable>> steps = new HashMap<>();
        private int now = -1;

        boolean started() {
            return this.now >= 0;
        }

        void start() {
            this.now = 0;
        }

        /** Runs the step {@code ticks} ticks from now; 0 runs it later in this tick. */
        void after(int ticks, Runnable step) {
            this.steps.computeIfAbsent(this.now + ticks, t -> new ArrayList<>()).add(step);
        }

        void tick() {
            List<Runnable> due = this.steps.remove(this.now);
            while (due != null) {
                due.forEach(Runnable::run);
                due = this.steps.remove(this.now);
            }
            this.now++;
        }
    }

    /** A patch of the world far from everything else, with its chunks kept loaded. */
    private static final class Site {

        private static final int RADIUS_CHUNKS = 6;
        private final ServerLevel level;
        private final BlockPos origin;

        Site(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        ServerLevel level() {
            return this.level;
        }

        BlockPos origin() {
            return this.origin;
        }

        Vec3 absolute(double x, double y, double z) {
            return new Vec3(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
        }

        /**
         * Loads the site, clears it of earlier runs' entities, then hands {@code script} a timeline that
         * counts ticks from there. Everything runs from the test's own tick, never from a scheduled task
         * that schedules another.
         */
        void run(GameTestHelper h, Consumer<Timeline> script) {
            forEachChunk(chunk -> this.level.setChunkForced(chunk.x, chunk.z, true));
            Timeline timeline = new Timeline();
            int[] waited = {0};
            h.onEachTick(() -> {
                if (timeline.started()) {
                    timeline.tick();
                    return;
                }
                if (ready()) {
                    for (Entity entity : this.level.getEntities((Entity) null, area(), e -> !(e instanceof Player))) {
                        entity.discard();
                    }
                    timeline.start();
                    script.accept(timeline);
                    timeline.tick();
                } else if (++waited[0] > 100) {
                    h.fail("the site at " + this.origin + " did not load");
                }
            });
        }

        private boolean ready() {
            boolean[] ready = {true};
            forEachChunk(chunk -> ready[0] &= this.level.isPositionEntityTicking(
                    chunk.getMiddleBlockPosition(this.origin.getY())));
            return ready[0];
        }

        void release() {
            for (Entity entity : this.level.getEntities((Entity) null, area(), e -> !(e instanceof Player))) {
                entity.discard();
            }
            forEachChunk(chunk -> this.level.setChunkForced(chunk.x, chunk.z, false));
        }

        private void forEachChunk(Consumer<ChunkPos> action) {
            ChunkPos centre = new ChunkPos(this.origin);
            for (int x = -1; x <= RADIUS_CHUNKS * 2; x++) {
                for (int z = -1; z <= RADIUS_CHUNKS * 2; z++) {
                    action.accept(new ChunkPos(centre.x + x, centre.z + z));
                }
            }
        }

        private AABB area() {
            ChunkPos centre = new ChunkPos(this.origin);
            return new AABB(centre.getMinBlockX() - 16, this.level.getMinBuildHeight(), centre.getMinBlockZ() - 16,
                    centre.getMinBlockX() + (RADIUS_CHUNKS * 2 + 1) * 16, this.level.getMaxBuildHeight(),
                    centre.getMinBlockZ() + (RADIUS_CHUNKS * 2 + 1) * 16);
        }

        /** A stone floor at the origin's height, with air above it. */
        void floor(int x0, int x1, int z0, int z1, Block block) {
            box(x0, x1, -1, 16, z0, z1, Blocks.AIR);
            box(x0, x1, 0, 0, z0, z1, block);
        }

        /** A stone basin whose water is level with the top of the floor. */
        void pool(int x0, int x1, int z0, int z1, int depth) {
            box(x0 - 1, x1 + 1, -depth - 1, 16, z0 - 1, z1 + 1, Blocks.AIR);
            box(x0 - 1, x1 + 1, -depth - 1, 0, z0 - 1, z1 + 1, Blocks.STONE);
            box(x0, x1, -depth, 0, z0, z1, Blocks.WATER);
        }

        /** Fills a box, both corners included, without updating neighbours, so water stays put. */
        void box(int x0, int x1, int y0, int y1, int z0, int z1, Block block) {
            BlockState state = block.defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        pos.set(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
                        this.level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }

        AircraftEntity spawn(VehicleKind kind, String name, Vec3 at) {
            VehicleSpec info = VehicleTypes.registry(kind).get(name).orElseThrow(
                    () -> new IllegalStateException(name + " is not loaded"));
            return spawn(info, at);
        }

        AircraftEntity spawn(VehicleSpec info, Vec3 at) {
            if (!(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
                throw new IllegalStateException(info.name() + " has no item");
            }
            Vec3 pos = absolute(at.x, at.y, at.z);
            AircraftEntity vehicle = item.createAircraft(this.level, pos.x, pos.y, pos.z, new ItemStack(item));
            if (vehicle == null) {
                throw new IllegalStateException("could not create " + info.name());
            }
            this.level.addFreshEntity(vehicle);
            return vehicle;
        }
    }
}
