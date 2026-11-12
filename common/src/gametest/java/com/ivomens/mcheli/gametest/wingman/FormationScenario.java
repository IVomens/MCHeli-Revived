package com.ivomens.mcheli.gametest.wingman;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.golden.TestTerrain;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Two helicopters parked on the runway are put in formation behind a leader that flies straight
 * down the course, 30 blocks up, at a steady 0.6 blocks per tick. The leader is a floating armor
 * stand the test moves each tick, so its path is exact; the followers are flown by the wingman code
 * alone. Every
 * {@value #SAMPLE_INTERVAL} ticks the recording lists each follower's position and its distance
 * to its formation slot. Each follower has to be within {@value #IN_SLOT} blocks of its slot by tick
 * {@value #CONVERGE_BY} and stay there to the end.
 */
final class FormationScenario implements WingmanScenario {

    private static final int TICKS = 600;
    private static final int SAMPLE_INTERVAL = 20;
    private static final double LEADER_SPEED = 0.6;
    private static final double LEADER_HEIGHT = 30.0;
    /** Formation slots of the default V: right and left of the leader, one rank back. */
    private static final double SLOT_SIDE = 20.0;
    private static final double SLOT_REAR = 30.0;
    /** A follower closer than this to its slot counts as in formation. */
    private static final double IN_SLOT = 5.0;
    /** The tick by which every follower has to be in its slot: climbing 30 blocks and closing about 50. */
    private static final int CONVERGE_BY = 300;
    private static final String[] FOLLOWERS = {"ah-64", "uh-1c"};

    private ArmorStand leader;
    private Roster roster;
    private final List<AircraftEntity> followers = new ArrayList<>();
    private final int[] convergedAt = new int[FOLLOWERS.length];

    @Override
    public String name() {
        return "formation_follow";
    }

    @Override
    public int ticks() {
        return TICKS;
    }

    @Override
    public void setUp(ServerLevel level, TestTerrain terrain) {
        this.roster = WingmanService.of(level).roster();
        Vec3 start = terrain.absolute(new Vec3(12.0, 1.0 + LEADER_HEIGHT, 60.0));
        this.leader = new ArmorStand(EntityType.ARMOR_STAND, level);
        this.leader.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        this.leader.setNoGravity(true);
        this.leader.setInvisible(true);
        this.leader.setDeltaMovement(0.0, 0.0, LEADER_SPEED);
        level.addFreshEntity(this.leader);
        for (int i = 0; i < FOLLOWERS.length; i++) {
            Vec3 parked = terrain.absolute(new Vec3(6.0 + 12.0 * i, 1.0, 8.0));
            AircraftEntity heli = Vehicles.spawn(level,
                    VehicleTypes.registry(VehicleKind.HELICOPTER).get(FOLLOWERS[i]).orElse(null), parked, 0.0F,
                    WingmanScenarioTests.SEED + i);
            if (heli == null) {
                throw new IllegalStateException("could not spawn " + FOLLOWERS[i]);
            }
            heli.fuel().setAmount(heli.fuel().capacity());
            this.roster.put(heli.getUUID(), Wingman.follower(this.leader, i, null));
            this.followers.add(heli);
            this.convergedAt[i] = -1;
        }
    }

    @Override
    public void afterTick(int tick, List<String> lines) {
        // an armor stand does not move by its motion, so it is moved here; the motion tells the
        // wingman code the leader is flying
        Vec3 moved = this.leader.position().add(0.0, 0.0, LEADER_SPEED);
        this.leader.setPos(moved.x, moved.y, moved.z);
        this.leader.setDeltaMovement(0.0, 0.0, LEADER_SPEED);
        for (int i = 0; i < this.followers.size(); i++) {
            double distance = this.followers.get(i).position().distanceTo(slot(i));
            if (this.convergedAt[i] < 0 && distance < IN_SLOT) {
                this.convergedAt[i] = tick;
                lines.add("t" + tick + " " + FOLLOWERS[i] + " reached its slot");
            } else if (this.convergedAt[i] >= 0 && distance >= IN_SLOT) {
                throw new IllegalStateException(FOLLOWERS[i] + " left its slot at tick " + tick + ", " + distance
                        + " blocks away");
            } else if (this.convergedAt[i] < 0 && tick >= CONVERGE_BY) {
                throw new IllegalStateException(FOLLOWERS[i] + " is still " + distance
                        + " blocks from its slot at tick " + tick);
            }
        }
        if (tick % SAMPLE_INTERVAL != 0) {
            return;
        }
        lines.add("t" + tick + " leader " + Vehicles.vec(this.leader.position()));
        for (int i = 0; i < this.followers.size(); i++) {
            AircraftEntity heli = this.followers.get(i);
            Wingman entry = this.roster.get(heli.getUUID());
            lines.add("t" + tick + " " + FOLLOWERS[i] + " " + Vehicles.vec(heli.position()) + " to slot "
                    + heli.position().distanceTo(slot(i)) + " throttle " + heli.engine().throttle() + " yaw "
                    + heli.getYRot() + " state "
                    + (entry == null ? "released" : entry.statusName() + "/" + entry.autopilot().phase()));
        }
    }

    /** The slot the wingman code aims for: {@code Formation.slotPosition} with the default settings. */
    private Vec3 slot(int index) {
        double side = index % 2 == 0 ? 1.0 : -1.0;
        int rank = index / 2 + 1;
        return this.leader.position().add(SLOT_SIDE * side * rank, 0.0, -SLOT_REAR * rank);
    }

    @Override
    public void tearDown(ServerLevel level) {
        for (AircraftEntity heli : this.followers) {
            this.roster.remove(heli.getUUID());
            heli.discard();
        }
        this.followers.clear();
        if (this.leader != null) {
            this.leader.discard();
        }
    }
}
