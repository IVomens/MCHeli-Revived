package com.ivomens.mcheli.gametest.network;

import com.ivomens.mcheli.combat.projectile.FlightModel;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.projectile.ProjectileSync;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.core.ballistics.ProjectileForces;
import com.ivomens.mcheli.gametest.bench.MockPlayer;
import com.ivomens.mcheli.gametest.snapshot.ForcedArea;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.util.ModLogger;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rounds and rockets are flown by every client from what it was told when it started tracking them
 * ({@link ProjectileSync}). A copy of each, built from the packets an observer got and flown the way a
 * client flies it (forces, then the move of the tick's steps), stays where the server's projectile is
 * along the whole flight: for an observer that saw the shot fired and for one that started tracking the
 * projectile in mid-flight. Few position packets are sent for it.
 * <p>
 * A rocket an addon's guidance law steers ({@code api_test_climber} of the game tests' pack) is flown by the
 * clients the same way, but the server tells them its position and motion each tick the law turned it.
 */
public final class ProjectileSyncGameTests {

    private static final String BATCH = "mcheli_projectile_sync";
    /** The spawn packet rounds the motion to 1/8000 of a block a tick; this covers the drift between corrections. */
    private static final double TOLERANCE = 0.05;
    /**
     * A steered projectile is corrected every tick, and the height of a correction is smoothed over a few ticks
     * ({@code ProjectileEntity#lerpTo}): a copy that joins in mid-flight is off by up to a block until then.
     */
    private static final double STEERED_TOLERANCE = 1.0;
    private static final int FLIGHT_TICKS = 60;
    /** The second observer joins this many ticks into the flight. */
    private static final int LATE_JOIN = 12;
    /** Fired steeply upwards, so the flight stays above the test's loaded chunks. */
    private static final float PITCH = -86.0F;
    /** The steered rocket climbs out less steeply, so its guidance law has a turn to make. */
    private static final float STEERED_PITCH = -30.0F;

    private ProjectileSyncGameTests() {
    }

    public static List<TestFunction> projectiles() {
        return List.of(
                test("gun_round_with_gravity_follows_server", ProjectileKind.BULLET, "152mm_howitzer"),
                test("fast_gun_round_follows_server", ProjectileKind.BULLET, "gau-8"),
                test("rocket_follows_server", ProjectileKind.ROCKET, "hydra70"),
                new TestFunction(BATCH + "_api_test_climber", "addon_guided_rocket_follows_server", "mcheli:empty",
                        200, 0, true, h -> fly(h, ProjectileKind.ROCKET, "api_test_climber", true)));
    }

    /**
     * Each test is a batch of its own: tests of a batch run side by side, and one that ends releases the
     * chunks it kept loaded, which would stop a projectile of its neighbour in mid-flight.
     */
    private static TestFunction test(String name, ProjectileKind kind, String weapon) {
        return new TestFunction(BATCH + "_" + weapon, name, "mcheli:empty", 200, 0, true,
                h -> fly(h, kind, weapon, false));
    }

    private static void fly(GameTestHelper h, ProjectileKind kind, String weapon, boolean steered) {
        ServerLevel level = h.getLevel();
        // above the barrier box the test runs in
        Vec3 muzzle = h.absoluteVec(new Vec3(4, 24, 4));
        // a projectile stands still while its chunk ticks no entities, and its clients' copies fly on, as
        // they always did; the flight is kept in chunks that tick, so the copies are compared with a flight
        ChunkPos centre = new ChunkPos(BlockPos.containing(muzzle));
        ForcedArea area = new ForcedArea(level, new ChunkPos(centre.x - 2, centre.z - 2),
                new ChunkPos(centre.x + 2, centre.z + 2), (int) muzzle.y);
        area.force(true);
        area.awaitReady();
        MockPlayer shooter = join(level, "sync_shooter_" + weapon, muzzle);
        List<MockPlayer> observers = new ArrayList<>(List.of(shooter));
        List<Copy> copies = new ArrayList<>();
        ProjectileEntity[] projectile = {null};
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            for (MockPlayer observer : observers) {
                observer.acknowledgeChunks();
            }
            if (tick[0] == 20) {
                projectile[0] = fire(h, kind, weapon, muzzle, shooter, steered ? STEERED_PITCH : PITCH);
                copies.add(new Copy("the shooter's copy", projectile[0], steered));
            } else if (tick[0] == 20 + LATE_JOIN) {
                MockPlayer late = join(level, "sync_late_" + weapon, muzzle.add(6, 0, 6));
                observers.add(late);
                copies.add(new Copy("the late observer's copy", projectile[0], steered));
            }
            for (int i = 0; i < copies.size(); i++) {
                Copy copy = copies.get(i);
                observers.get(i).drain(copy::accept);
                copy.flyOneTick();
                copy.compare();
            }
            if (tick[0] == 20 + FLIGHT_TICKS) {
                List<String> failures = new ArrayList<>();
                if (projectile[0].isRemoved()) {
                    failures.add("the projectile was removed (" + projectile[0].getRemovalReason() + ") after "
                            + projectile[0].tickCount + " ticks at " + Vehicles.vec(projectile[0].position()));
                }
                copies.forEach(copy -> copy.check(failures));
                observers.forEach(MockPlayer::leave);
                area.force(false);
                projectile[0].discard();
                if (failures.isEmpty()) {
                    h.succeed();
                } else {
                    h.fail(String.join("; ", failures));
                }
            }
        });
    }

    private static ProjectileEntity fire(GameTestHelper h, ProjectileKind kind, String weapon, Vec3 muzzle,
                                         MockPlayer shooter, float pitch) {
        WeaponSpec spec = Weapons.get(weapon).orElseThrow(() -> new IllegalStateException("no weapon " + weapon));
        Vec3 direction = Vec3.directionFromRotation(pitch, 10.0F);
        ProjectileEntity projectile = ProjectileEntity.create(kind, h.getLevel(), muzzle, direction, 10.0F, pitch,
                spec.ballistics().acceleration());
        projectile.setWeapon(weapon);
        projectile.setShooter(shooter.player(), shooter.player());
        h.getLevel().addFreshEntity(projectile);
        return projectile;
    }

    private static MockPlayer join(ServerLevel level, String name, Vec3 at) {
        MockPlayer player = MockPlayer.join(level, name, 4);
        player.player().moveTo(at.x, at.y, at.z);
        level.getChunkSource().move(player.player());
        return player;
    }

    /** One client's copy of the projectile: what it was told, flown as a client flies it in the open air. */
    private static final class Copy {

        private final String name;
        private final ProjectileEntity server;
        private final WeaponSpec spec;
        /** An addon's guidance steers it: the server sends it every tick it turns. */
        private final boolean steered;
        @Nullable
        private Vec3 position;
        private Vec3 motion = Vec3.ZERO;
        private int tickCount;
        private int spawnAge = -1;
        private int catchUp;
        private boolean caughtUp;
        private int positionPackets;
        private int comparedTicks;
        private double worst;
        private String worstAt = "";

        Copy(String name, ProjectileEntity server, boolean steered) {
            this.name = name;
            this.server = server;
            this.spec = server.spec();
            this.steered = steered;
        }

        void accept(Object sent) {
            try {
                if (sent instanceof ClientboundBundlePacket bundle) {
                    bundle.subPackets().forEach(this::packet);
                } else if (sent instanceof Packet<?> packet) {
                    packet(packet);
                }
            } finally {
                ReferenceCountUtil.release(sent);
            }
        }

        private void packet(Packet<?> packet) {
            int id = this.server.getId();
            if (packet instanceof ClientboundAddEntityPacket add && add.getId() == id) {
                this.position = new Vec3(add.getX(), add.getY(), add.getZ());
                // the motion comes with the spawn packet, not in a motion packet of its own
                this.motion = new Vec3(add.getXa(), add.getYa(), add.getZa());
                // the ticks flown, times two, plus the tick the copy has to catch up
                this.tickCount = add.getData() >> 1;
                this.spawnAge = add.getData() >> 1;
                this.catchUp = add.getData() & 1;
                this.caughtUp = this.catchUp > 0;
            } else if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == id) {
                this.motion = new Vec3(motion.getXa(), motion.getYa(), motion.getZa());
            } else if (packet instanceof ClientboundTeleportEntityPacket teleport && teleport.getId() == id
                    && this.position != null) {
                // ProjectileEntity#lerpTo: the height is smoothed, the rest taken as it is
                this.position = new Vec3(teleport.getX(), (teleport.getY() + this.position.y * 2.0) / 3.0,
                        teleport.getZ());
                this.positionPackets++;
            } else if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()
                    && move.getEntity(this.server.level()) == this.server) {
                this.positionPackets++;
            }
        }

        /** The client's tick, and the tick a copy that started in mid-flight owes. */
        void flyOneTick() {
            if (this.position == null) {
                return;
            }
            fly();
            for (; this.catchUp > 0; this.catchUp--) {
                fly();
            }
        }

        /** The client's flight of a round or rocket in the air: forces, then the move. */
        private void fly() {
            this.tickCount++;
            Vector3d m = new Vector3d(this.motion.x, this.motion.y, this.motion.z);
            if (ProjectileForces.isBoosting(this.tickCount, this.spec.ballistics().speedFactorStartTick(),
                    this.spec.ballistics().speedFactorEndTick())) {
                ProjectileForces.boost(m, this.spec.ballistics().speedFactor());
            }
            ProjectileForces.fall(m, FlightModel.gravity(this.server, this.spec));
            ProjectileForces.drag(m, this.spec.missile().ballistic().dragInAir());
            this.motion = new Vec3(m.x, m.y, m.z);
            double steps = this.server.stepFactor();
            this.position = this.position.add(m.x * steps, m.y * steps, m.z * steps);
        }

        void compare() {
            if (this.position == null || this.server.isRemoved()) {
                return;
            }
            this.comparedTicks++;
            double off = this.position.distanceTo(this.server.position());
            if (off > this.worst) {
                this.worst = off;
                this.worstAt = "tick " + this.tickCount + " (server " + this.server.tickCount + "): copy " + Vehicles.vec(this.position) + ", server "
                        + Vehicles.vec(this.server.position());
            }
        }

        void check(List<String> failures) {
            if (this.position == null) {
                failures.add(this.name + ": the projectile never reached the observer");
                return;
            }
            ModLogger.get().info("[projectile sync] {}: spawned at tick {}{}, {} position packets in {} ticks, "
                    + "at most {} blocks off", this.name, this.spawnAge, this.caughtUp ? " (caught up a tick)" : "",
                    this.positionPackets, this.comparedTicks, this.worst);
            boolean joinedSteered = this.steered && this.name.contains("late");
            if (this.worst > (joinedSteered ? STEERED_TOLERANCE : TOLERANCE)) {
                failures.add(this.name + " was " + this.worst + " blocks off at " + this.worstAt);
            }
            int allowed = this.comparedTicks / ProjectileSync.CORRECTION_INTERVAL + 2;
            if (this.steered && this.positionPackets < this.comparedTicks / 2) {
                failures.add(this.name + " got only " + this.positionPackets + " position packets in "
                        + this.comparedTicks + " ticks of steering");
            } else if (!this.steered && this.positionPackets > allowed) {
                failures.add(this.name + " got " + this.positionPackets + " position packets in " + this.comparedTicks
                        + " ticks, at most " + allowed + " expected");
            }
            if (this.name.contains("late") && this.spawnAge <= 0) {
                failures.add(this.name + " was spawned with age " + this.spawnAge + ", not in mid-flight");
            }
        }
    }
}
