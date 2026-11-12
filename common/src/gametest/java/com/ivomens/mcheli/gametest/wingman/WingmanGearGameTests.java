package com.ivomens.mcheli.gametest.wingman;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.vehicle.component.PartsComponent;
import com.ivomens.mcheli.wingman.WingmanService;
import com.ivomens.mcheli.wingman.roster.Roster;
import com.ivomens.mcheli.wingman.roster.Wingman;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A follower copies its leader's landing gear both ways. Both aircraft are held in place high above
 * the ground, where the gear neither folds nor unfolds by itself; the leader's gear goes up, and once
 * both are fully up, down again.
 */
public class WingmanGearGameTests {

    private static final String PLANE = "a-10";
    private static final double HEIGHT = 40.0;
    /** Fully folded: the gear turns 90 degrees. */
    private static final float GEAR_UP = 89.0F;
    /** Game ticks the whole cycle may take; folding takes 36. */
    private static final int GIVE_UP_TICKS = 300;

    public static List<TestFunction> gear() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(new TestFunction("wingman_gear", "wingman_follower_copies_gear", "mcheli:empty",
                GIVE_UP_TICKS + 100, 0, true, h -> new GearRun(h).start()));
    }

    /** One run of the test, game tick by game tick. */
    private static final class GearRun {

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 leaderAt;
        private final Vec3 followerAt;
        private AircraftEntity leader;
        private AircraftEntity follower;
        private long startTime = -1;
        private long lastTime = -1;
        private boolean lowered;

        GearRun(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.leaderAt = helper.absoluteVec(new Vec3(12.0, HEIGHT, 12.0));
            this.followerAt = helper.absoluteVec(new Vec3(2.0, HEIGHT, 2.0));
        }

        void start() {
            this.helper.onEachTick(this::tick);
        }

        private void tick() {
            long time = this.level.getGameTime();
            if (time == this.lastTime) {
                return;
            }
            this.lastTime = time;
            if (this.leader == null) {
                if (this.level.isPositionEntityTicking(BlockPos.containing(this.leaderAt))
                        && this.level.isPositionEntityTicking(BlockPos.containing(this.followerAt))) {
                    spawn();
                    this.startTime = time;
                }
                return;
            }
            if (time - this.startTime >= GIVE_UP_TICKS) {
                String state = describe();
                cleanUp();
                throw new GameTestAssertException("the follower did not copy the leader's gear: " + state);
            }
            hold(this.leader, this.leaderAt);
            hold(this.follower, this.followerAt);
            PartsComponent lead = this.leader.parts();
            PartsComponent wing = this.follower.parts();
            if (!this.lowered) {
                if (!lead.isLandingGearFolded() && lead.canFoldLandingGear()) {
                    lead.foldLandingGear();
                }
                if (lead.landingGearRotation() >= GEAR_UP && wing.landingGearRotation() >= GEAR_UP) {
                    lead.unfoldLandingGear();
                    this.lowered = !lead.isLandingGearFolded();
                }
            } else if (!wing.isLandingGearFolded()) {
                cleanUp();
                this.helper.succeed();
            }
        }

        private void spawn() {
            var spec = VehicleTypes.registry(VehicleKind.PLANE).get(PLANE).orElse(null);
            this.leader = Vehicles.spawn(this.level, spec, this.leaderAt, 0.0F, 1L);
            this.follower = Vehicles.spawn(this.level, spec, this.followerAt, 0.0F, 2L);
            if (this.leader == null || this.follower == null || this.leader.parts().landingGear() == null) {
                cleanUp();
                throw new GameTestAssertException(PLANE + " with landing gear missing");
            }
            WingmanService.of(this.level).roster().put(this.follower.getUUID(),
                    Wingman.follower(this.leader, 0, null));
        }

        private String describe() {
            return "leader folded " + this.leader.parts().isLandingGearFolded() + " at "
                    + this.leader.parts().landingGearRotation() + " deg, follower folded "
                    + this.follower.parts().isLandingGearFolded() + " at "
                    + this.follower.parts().landingGearRotation() + " deg, leader lowered " + this.lowered;
        }

        private void cleanUp() {
            Roster roster = WingmanService.of(this.level).roster();
            for (AircraftEntity aircraft : new AircraftEntity[]{this.leader, this.follower}) {
                if (aircraft != null) {
                    roster.remove(aircraft.getUUID());
                    aircraft.discard();
                }
            }
        }

        /** Keeps the aircraft where it is, so the test is about the gear alone. */
        private static void hold(AircraftEntity aircraft, Vec3 at) {
            aircraft.setPos(at.x, at.y, at.z);
            aircraft.setDeltaMovement(Vec3.ZERO);
        }
    }
}
