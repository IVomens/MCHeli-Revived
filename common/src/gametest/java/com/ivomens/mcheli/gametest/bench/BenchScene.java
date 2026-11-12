package com.ivomens.mcheli.gametest.bench;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The vehicles of one benchmark tier and what keeps them busy: mock players aboard (pilots that hold
 * full throttle, gunners that cycle and fire every weapon), AI gunners, and wingmen following leaders
 * that circle the field. {@link Workload} decides who goes where; {@link #tick} runs every server tick.
 */
final class BenchScene {

    /** Boarding is retried every 10 ticks until then, as a vehicle may still be settling. */
    private static final int LAST_BOARDING_TICK = 65;
    private static final double LEADER_SPEED = 0.6;
    private static final int FOLLOWERS_PER_LEADER = 4;

    private final ServerLevel level;
    private final Vec3 origin;
    private final int tierIndex;
    private final long seed;
    private final List<AircraftEntity> vehicles = new ArrayList<>();
    private final List<Crew> crew = new ArrayList<>();
    private final List<AircraftEntity> flyers = new ArrayList<>();
    private final List<GunnerEntity> gunners = new ArrayList<>();
    private final List<ArmorStand> leaders = new ArrayList<>();
    private final List<AircraftEntity> followers = new ArrayList<>();
    private int ticks;

    /** A mock player and the vehicle it boards; a shooter fires, a pilot flies. */
    private record Crew(AircraftEntity vehicle, MockPlayer player, boolean shooter) {
    }

    BenchScene(ServerLevel level, Vec3 origin, int tierIndex, long seed) {
        this.level = level;
        this.origin = origin;
        this.tierIndex = tierIndex;
        this.seed = seed;
    }

    Vec3 origin() {
        return this.origin;
    }

    List<AircraftEntity> vehicles() {
        return this.vehicles;
    }

    /** Spawns one vehicle at grid cell {@code index} of a square grid, {@code height} above the ground. */
    AircraftEntity spawn(VehicleSpec spec, int index, int side, double spacing, double height, float yaw) {
        double x = this.origin.x + (index % side - side / 2.0) * spacing;
        double z = this.origin.z + (index / side - side / 2.0) * spacing;
        AircraftEntity vehicle = Vehicles.spawn(this.level, spec, new Vec3(x, this.origin.y + height, z), yaw,
                this.seed + index);
        if (vehicle != null) {
            this.vehicles.add(vehicle);
        }
        return vehicle;
    }

    /** A mock player boards the vehicle and holds full throttle. */
    void pilot(AircraftEntity vehicle) {
        this.crew.add(new Crew(vehicle, board(vehicle), false));
        this.flyers.add(vehicle);
    }

    /** A mock player boards the vehicle and fires every weapon in turn. */
    void shooter(AircraftEntity vehicle) {
        this.crew.add(new Crew(vehicle, board(vehicle), true));
    }

    private MockPlayer board(AircraftEntity vehicle) {
        MockPlayer player = MockPlayer.join(this.level, "bench" + this.tierIndex + "_" + this.crew.size(), 2);
        player.player().moveTo(vehicle.getX() + 1, vehicle.getY(), vehicle.getZ());
        return player;
    }

    /** An AI gunner takes the vehicle's first free seat, looking for monsters. */
    void gunner(AircraftEntity vehicle) {
        GunnerEntity gunner = new GunnerEntity(this.level, vehicle.getX(), vehicle.getY(), vehicle.getZ());
        // placed as in creative, so removing it drops no spawn item
        net.minecraft.nbt.CompoundTag creative = new net.minecraft.nbt.CompoundTag();
        gunner.addAdditionalSaveData(creative);
        creative.putBoolean("Creative", true);
        gunner.readAdditionalSaveData(creative);
        this.level.addFreshEntity(gunner);
        if (gunner.startRiding(vehicle)) {
            this.gunners.add(gunner);
        } else {
            gunner.discard();
        }
    }

    /** The vehicle joins the formation of the current leader, a new leader every few followers. */
    void wingman(AircraftEntity vehicle) {
        int slot = this.followers.size() % FOLLOWERS_PER_LEADER;
        if (slot == 0) {
            ArmorStand leader = new ArmorStand(EntityType.ARMOR_STAND, this.level);
            leader.setNoGravity(true);
            leader.setInvisible(true);
            this.leaders.add(leader);
            placeLeader(leader, this.leaders.size() - 1, 0);
            this.level.addFreshEntity(leader);
        }
        vehicle.fuel().setAmount(vehicle.fuel().capacity());
        WingmanService.of(this.level).roster().put(vehicle.getUUID(),
                Wingman.follower(this.leaders.getLast(), slot, null));
        this.followers.add(vehicle);
    }

    void tick() {
        this.ticks++;
        if (this.ticks <= LAST_BOARDING_TICK && this.ticks % 10 == 5) {
            for (Crew c : this.crew) {
                if (c.vehicle().seats().seatIdOf(c.player().player()) < 0) {
                    c.vehicle().boarding().interact(c.player().player(), InteractionHand.MAIN_HAND);
                }
            }
        }
        for (AircraftEntity vehicle : this.flyers) {
            fly(vehicle);
        }
        for (Crew c : this.crew) {
            if (c.shooter()) {
                fire(c.vehicle(), c.player());
            }
        }
        for (int i = 0; i < this.leaders.size(); i++) {
            placeLeader(this.leaders.get(i), i, this.ticks);
        }
    }

    private static void fly(AircraftEntity vehicle) {
        vehicle.engine().setThrottle(1.0);
        if (vehicle instanceof PlaneEntity && vehicle.getDeltaMovement().horizontalDistanceSqr() < 0.25) {
            // planes need airspeed to stay up; give them a push along their heading
            vehicle.setDeltaMovement(Vec3.directionFromRotation(0, vehicle.getYRot()).scale(1.2));
        }
    }

    private void fire(AircraftEntity vehicle, MockPlayer player) {
        var p = player.player();
        if (vehicle.isRemoved() || vehicle.seats().seatIdOf(p) < 0 || vehicle.weapons().count() == 0) {
            return;
        }
        if (this.ticks % 40 == 0) {
            vehicle.weapons().select(p, (this.ticks / 40) % vehicle.weapons().count());
        }
        vehicle.weapons().fire(p);
    }

    /** Leaders fly circles 30 blocks up, each on its own radius and phase. */
    private void placeLeader(ArmorStand leader, int index, int tick) {
        double radius = 60.0 + 12.0 * index;
        double angle = tick * LEADER_SPEED / radius + index * 0.9;
        double x = this.origin.x + Math.cos(angle) * radius;
        double z = this.origin.z + Math.sin(angle) * radius;
        leader.setPos(x, this.origin.y + 30.0, z);
        leader.setDeltaMovement(-Math.sin(angle) * LEADER_SPEED, 0.0, Math.cos(angle) * LEADER_SPEED);
    }

    int crewAboard() {
        int n = 0;
        for (Crew c : this.crew) {
            if (!c.vehicle().isRemoved() && c.vehicle().seats().seatIdOf(c.player().player()) >= 0) {
                n++;
            }
        }
        return n;
    }

    int gunnerCount() {
        return this.gunners.size();
    }

    int wingmanCount() {
        return this.followers.size();
    }

    int alive() {
        int n = 0;
        for (AircraftEntity vehicle : this.vehicles) {
            if (!vehicle.isRemoved()) {
                n++;
            }
        }
        return n;
    }

    /** The first player to board, whose traffic the benchmark measures as a pilot's; null without crew. */
    @Nullable
    MockPlayer firstCrew() {
        return this.crew.isEmpty() ? null : this.crew.getFirst().player();
    }

    /** Every mock player of the scene, for draining their connections. */
    void forEachPlayer(java.util.function.Consumer<MockPlayer> action) {
        for (Crew c : this.crew) {
            action.accept(c.player());
        }
    }

    /** Lets the players go; the entities are removed by the caller. */
    void tearDown() {
        for (AircraftEntity follower : this.followers) {
            WingmanService.of(this.level).roster().remove(follower.getUUID());
        }
        for (Crew c : this.crew) {
            c.player().leave();
        }
    }
}
