package com.ivomens.mcheli.bench;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The vehicles of the in-game benchmark, on the integrated server: the parked rows, the moving lane and
 * the helicopter the player sits in for the cockpit steps (see {@link BenchmarkLayout}). Every method
 * runs on the server thread; the client benchmark hands it work with {@code server.execute}.
 * <p>
 * Every vehicle is tagged {@value #TAG}, so one left behind by a crash can be found and is removed by
 * the next benchmark ({@code /kill @e[tag=mcheli_benchmark]} also works).
 */
public final class BenchmarkScene {

    public static final String TAG = "mcheli_benchmark";
    /** Every fifth parked helicopter is damaged enough to trail smoke. */
    private static final int SMOKE_EVERY = 5;
    private static final double SMOKE_DAMAGE = 0.6;
    private static final double LANE_HALF_LENGTH = 40.0;
    private static final double LANE_SPEED = 0.01;
    private static final double LANE_HELICOPTER_HEIGHT = 4.0;
    private static final double FORCED_MARGIN = 96.0;

    private final ServerLevel level;
    private final Vec3 start;
    private final List<VehicleSpec> helicopters = specs(VehicleKind.HELICOPTER);
    private final List<VehicleSpec> planes = specs(VehicleKind.PLANE);
    private final List<VehicleSpec> tanks = specs(VehicleKind.TANK);
    private final List<AircraftEntity> parked = new ArrayList<>();
    private final List<AircraftEntity> moving = new ArrayList<>();
    private final Set<ChunkPos> forced = new HashSet<>();
    private final WorldState saved;
    @Nullable
    private AircraftEntity cameraHelicopter;
    private int ticks;

    private BenchmarkScene(ServerLevel level, Vec3 start, WorldState saved) {
        this.level = level;
        this.start = start;
        this.saved = saved;
    }

    /**
     * Saves the world and player state it changes, then sets a still noon without weather, lets the
     * player fly, removes vehicles a crashed benchmark left behind and parks the camera helicopter.
     */
    public static BenchmarkScene begin(ServerPlayer player, Vec3 start) {
        ServerLevel level = player.serverLevel();
        BenchmarkScene scene = new BenchmarkScene(level, start, WorldState.capture(player));
        WorldState.prepare(player);
        scene.removeLeftovers(new BenchmarkLayout(start, 0).orbitRadius() * 3);
        scene.forceAround(new BenchmarkLayout(start, 0).centre(), FORCED_MARGIN);
        scene.cameraHelicopter = scene.spawn(scene.cameraHelicopterSpec(), start, 0.0F);
        return scene;
    }

    /** Replaces the vehicles of the previous size with {@code count} new ones. */
    public void populate(int count) {
        clearVehicles();
        BenchmarkLayout layout = new BenchmarkLayout(this.start, count);
        forceAround(layout.centre(), layout.orbitRadius() + FORCED_MARGIN);
        for (int i = 0; i < layout.parked(); i++) {
            AircraftEntity vehicle = spawn(mixedSpec(i), layout.parkedPosition(i), 0.0F);
            if (vehicle == null) {
                continue;
            }
            this.parked.add(vehicle);
            if (i % 3 == 0 && (i / 3) % SMOKE_EVERY == 0) {
                vehicle.damage().setDamageTaken((int) (vehicle.damage().maxHp() * SMOKE_DAMAGE));
            }
        }
        Vec3 lane = layout.laneCentre();
        for (int i = 0; i < layout.moving(); i++) {
            List<VehicleSpec> kind = i % 2 == 0 ? this.tanks : this.helicopters;
            AircraftEntity vehicle = spawn(kind.get(i / 2 % kind.size()), lane, 0.0F);
            if (vehicle != null) {
                this.moving.add(vehicle);
            }
        }
        this.ticks = 0;
    }

    /** Moves the lane vehicles one step back or forth; helicopters run their rotors for the downwash. */
    public void tick() {
        this.ticks++;
        BenchmarkLayout layout = new BenchmarkLayout(this.start, 0);
        Vec3 lane = layout.laneCentre();
        for (int i = 0; i < this.moving.size(); i++) {
            AircraftEntity vehicle = this.moving.get(i);
            double phase = this.ticks * LANE_SPEED * 2 * Math.PI + i * 0.7;
            double velocity = Math.cos(phase) * LANE_HALF_LENGTH * LANE_SPEED * 2 * Math.PI;
            double y = lane.y + (i % 2 == 0 ? 0.0 : LANE_HELICOPTER_HEIGHT);
            vehicle.setPos(lane.x + Math.sin(phase) * LANE_HALF_LENGTH, y, lane.z + (i - this.moving.size() / 2.0) * 1.5);
            vehicle.setDeltaMovement(velocity, 0.0, 0.0);
            vehicle.setYRot(velocity >= 0 ? -90.0F : 90.0F);
            if (i % 2 == 1) {
                vehicle.engine().setThrottle(1.0);
            }
        }
    }

    /** Seats the player as the pilot of the camera helicopter. */
    public void seatPilot(ServerPlayer player) {
        if (this.cameraHelicopter != null && player.getVehicle() == null) {
            player.teleportTo(this.cameraHelicopter.getX(), this.cameraHelicopter.getY(), this.cameraHelicopter.getZ());
            this.cameraHelicopter.boarding().interact(player, InteractionHand.MAIN_HAND);
        }
    }

    /** Takes the player out of any vehicle and puts it at {@code at}, flying. */
    public void moveCamera(ServerPlayer player, Vec3 at, float yaw, float pitch) {
        player.stopRiding();
        player.teleportTo(this.level, at.x, at.y, at.z, yaw, pitch);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /** Removes everything the benchmark spawned and puts the world and the player back as they were. */
    public void end(ServerPlayer player) {
        player.stopRiding();
        clearVehicles();
        if (this.cameraHelicopter != null) {
            this.cameraHelicopter.discard();
        }
        for (ChunkPos chunk : this.forced) {
            this.level.setChunkForced(chunk.x, chunk.z, false);
        }
        this.forced.clear();
        this.saved.restore(player);
    }

    /** Removes the vehicles only, for a player who already left the world. */
    public void abandon() {
        clearVehicles();
        if (this.cameraHelicopter != null) {
            this.cameraHelicopter.discard();
        }
        for (ChunkPos chunk : this.forced) {
            this.level.setChunkForced(chunk.x, chunk.z, false);
        }
        this.saved.restoreWorld(this.level);
    }

    private void clearVehicles() {
        this.parked.forEach(Entity::discard);
        this.moving.forEach(Entity::discard);
        this.parked.clear();
        this.moving.clear();
    }

    private void removeLeftovers(double radius) {
        AABB area = new AABB(this.start, this.start).inflate(radius, 256, radius);
        for (Entity entity : this.level.getEntities((Entity) null, area, e -> e.getTags().contains(TAG))) {
            entity.discard();
        }
    }

    private void forceAround(Vec3 centre, double radius) {
        int minX = (int) Math.floor(centre.x - radius) >> 4;
        int maxX = (int) Math.floor(centre.x + radius) >> 4;
        int minZ = (int) Math.floor(centre.z - radius) >> 4;
        int maxZ = (int) Math.floor(centre.z + radius) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (this.forced.add(new ChunkPos(x, z))) {
                    this.level.setChunkForced(x, z, true);
                }
            }
        }
    }

    /** Helicopter, plane, tank, helicopter...: each kind's types in name order. */
    private VehicleSpec mixedSpec(int index) {
        List<VehicleSpec> kind = switch (index % 3) {
            case 0 -> this.helicopters;
            case 1 -> this.planes;
            default -> this.tanks;
        };
        return kind.get(index / 3 % kind.size());
    }

    private VehicleSpec cameraHelicopterSpec() {
        return VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(this.helicopters.getFirst());
    }

    /** Spawns on the ground below {@code at} (loading the chunk), facing {@code yaw}; null if it cannot. */
    @Nullable
    private AircraftEntity spawn(VehicleSpec spec, Vec3 at, float yaw) {
        if (!(VehicleTypes.item(spec).orElse(null) instanceof AircraftItem item)) {
            return null;
        }
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        this.level.getChunk(x >> 4, z >> 4);
        double ground = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        AircraftEntity vehicle = item.createAircraft(this.level, at.x, Math.max(ground, at.y), at.z, new ItemStack(item));
        if (vehicle == null) {
            return null;
        }
        vehicle.initRotationYaw(yaw);
        vehicle.addTag(TAG);
        return this.level.addFreshEntity(vehicle) ? vehicle : null;
    }

    private static List<VehicleSpec> specs(VehicleKind kind) {
        List<VehicleSpec> specs = new ArrayList<>();
        for (VehicleSpec spec : VehicleTypes.registry(kind).values()) {
            if (spec.file().canRide() && !spec.isUAV() && VehicleTypes.item(spec).orElse(null) instanceof AircraftItem) {
                specs.add(spec);
            }
        }
        specs.sort(Comparator.comparing(VehicleSpec::name));
        return specs;
    }
}
