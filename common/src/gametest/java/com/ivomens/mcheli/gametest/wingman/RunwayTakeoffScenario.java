package com.ivomens.mcheli.gametest.wingman;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.golden.TestTerrain;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.wingman.McHeliWingman;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.airfield.AirfieldData;
import com.ivomens.mcheli.wingman.airfield.Markers;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.wingman.block.WingmanMarkerTileEntity;
import com.ivomens.mcheli.core.wingman.brain.FlightPhase;
import com.ivomens.mcheli.core.wingman.brain.NodeMission;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * An unmanned Cessna on the runway threshold gets a node mission: take off from the runway between
 * two marker blocks set into the runway, then fly on down the course. The wingman autopilot taxis,
 * aligns, rolls and climbs on its own. The recording follows its autopilot state and flight path
 * until it has been climbing for
 * a while.
 */
final class RunwayTakeoffScenario implements WingmanScenario {

    private static final int TICKS = 700;
    private static final int SAMPLE_INTERVAL = 10;
    private static final String RUNWAY = "gametest_runway";
    private static final String PLANE = "cessna172";
    /** The far runway end, on the runway centreline. */
    private static final Vec3 RUNWAY_END = new Vec3(12.0, 1.0, 300.0);
    /**
     * Where the mission flies on to, high enough that the cruise keeps clear of the ground on its own:
     * the golden trajectories leave their course 60 blocks above this one, and whether it is there
     * depends on which tests ran before.
     */
    private static final Vec3 FLY_TO = new Vec3(12.0, 100.0, 700.0);

    private AircraftEntity plane;
    private Roster roster;
    private BlockPos start;
    private BlockPos end;
    private FlightPhase lastState;

    @Override
    public String name() {
        return "runway_takeoff";
    }

    @Override
    public int ticks() {
        return TICKS;
    }

    @Override
    public void setUp(ServerLevel level, TestTerrain terrain) {
        this.roster = WingmanService.of(level).roster();
        this.start = BlockPos.containing(terrain.absolute(TestTerrain.RUNWAY_START)).below();
        this.end = BlockPos.containing(terrain.absolute(RUNWAY_END)).below();
        registerMarker(level, this.start, MarkerType.RUNWAY_A);
        registerMarker(level, this.end, MarkerType.RUNWAY_B);

        this.plane = Vehicles.spawn(level, VehicleTypes.registry(VehicleKind.PLANE).get(PLANE).orElse(null),
                terrain.absolute(TestTerrain.RUNWAY_START), 0.0F, WingmanScenarioTests.SEED);
        if (this.plane == null) {
            throw new IllegalStateException("could not spawn " + PLANE);
        }
        this.plane.fuel().setAmount(this.plane.fuel().capacity());
        Vec3 target = terrain.absolute(FLY_TO);
        Wingman entry = Wingman.autonomous(null);
        entry.autopilot().startNodeMission(List.of(MissionNode.takeoff(RUNWAY),
                MissionNode.flyTo(target.x, target.y, target.z)));
        this.roster.put(this.plane.getUUID(), entry);
        this.lastState = null;
    }

    /** Sets a marker block into the runway surface, as a player would, and names it. */
    private static void registerMarker(ServerLevel level, BlockPos pos, MarkerType type) {
        level.setBlock(pos, McHeliWingman.MARKER_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof WingmanMarkerTileEntity marker)) {
            throw new IllegalStateException("no marker block entity at " + pos);
        }
        marker.setMarkerType(type);
        marker.setMarkerId(RUNWAY);
        AirfieldData.of(level).putMarker(Markers.of(pos, marker));
    }

    @Override
    public void afterTick(int tick, List<String> lines) {
        Wingman entry = this.roster.get(this.plane.getUUID());
        FlightPhase state = entry == null ? null : entry.autopilot().phase();
        boolean changed = state != this.lastState;
        if (changed) {
            lines.add("t" + tick + " state " + state + " node " + (entry == null ? "-" : nodeIndex(entry)));
            this.lastState = state;
        }
        if (changed || tick % SAMPLE_INTERVAL == 0) {
            lines.add("t" + tick + " " + Vehicles.vec(this.plane.position()) + " motion "
                    + Vehicles.vec(this.plane.getDeltaMovement()) + " throttle " + this.plane.engine().throttle()
                    + " yaw " + this.plane.getYRot() + " pitch " + this.plane.getXRot() + " onGround "
                    + this.plane.onGround());
        }
    }

    /** The node the mission is on; once it is done there is no node list, so its last index is unknown. */
    private static Object nodeIndex(Wingman entry) {
        NodeMission mission = entry.autopilot().mission();
        return mission != null ? mission.index() : "-";
    }

    @Override
    public void tearDown(ServerLevel level) {
        if (this.plane != null) {
            this.roster.remove(this.plane.getUUID());
            this.plane.discard();
        }
        for (BlockPos marker : new BlockPos[]{this.start, this.end}) {
            AirfieldData.of(level).removeMarker(marker);
            level.setBlock(marker, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
