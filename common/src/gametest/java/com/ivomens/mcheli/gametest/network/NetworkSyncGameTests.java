package com.ivomens.mcheli.gametest.network;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContact;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactsMessage;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.gametest.bench.Fleets;
import com.ivomens.mcheli.gametest.bench.MockPlayer;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.radar.EntityInfo;
import com.ivomens.mcheli.radar.client.EntityInfoClientTracker;
import com.ivomens.mcheli.radar.client.RadarClientMessages;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What players are sent about vehicles, read back the way a client reads it: radar contacts reach a
 * player aboard a vehicle (and appear, move and stop being reported when their entity goes) but not a
 * player on foot; an observer's copy of a moving vehicle ends where the server's is; the crew of a
 * vehicle, in the pilot's place and in a seat, follow their own vehicle.
 */
public final class NetworkSyncGameTests {

    private static final String BATCH = "mcheli_network";
    /** The client's copy of a radar position is fixed point, 1/256 of a block. */
    private static final double RADAR_TOLERANCE = 1.0 / 256;
    /**
     * Vanilla sends no move smaller than about 1/360 of a block, in fixed point of 1/4096 of a block;
     * the copy may be that far behind.
     */
    private static final double MOVE_TOLERANCE = 0.004;
    /** The server's latest positions the copy is compared with: this tick's packets may be on their way. */
    private static final int POSITIONS_KEPT = 3;

    private NetworkSyncGameTests() {
    }

    public static List<TestFunction> network() {
        return List.of(
                new TestFunction(BATCH, "radar_contacts_reach_crew_only", "mcheli:empty", 400, 0, true,
                        NetworkSyncGameTests::radarContactsReachCrewOnly),
                new TestFunction(BATCH, "observer_sees_vehicle_converge", "mcheli:empty", 400, 0, true,
                        NetworkSyncGameTests::observerSeesVehicleConverge),
                new TestFunction(BATCH, "crew_follow_own_vehicle", "mcheli:empty", 400, 0, true,
                        NetworkSyncGameTests::crewFollowOwnVehicle));
    }

    /**
     * A player in a tank gets the radar picture: another tank appears where it is, as a tank, follows it
     * when it moves, and is no longer reported once it is gone; contacts keep being refreshed meanwhile.
     * An air-to-air missile overhead arrives as one. A player on foot gets nothing.
     */
    private static void radarContactsReachCrewOnly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AircraftEntity ride = spawnTank(h, new Vec3(2, 1, 2), 0);
        AircraftEntity contact = spawnTank(h, new Vec3(14, 1, 14), 1);
        MockPlayer crew = join(level, "radar_crew", h.absoluteVec(new Vec3(3, 1, 2)));
        MockPlayer walker = join(level, "radar_walker", h.absoluteVec(new Vec3(8, 1, 8)));
        EntityInfoClientTracker radar = new EntityInfoClientTracker();
        RadarLog log = new RadarLog(contact.getId());
        MissileOverhead missile = new MissileOverhead(h, new Vec3(8, 20, 8));
        int[] tick = {0};
        List<String> failures = new ArrayList<>();
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] <= 90) {
                missile.keepFlying();
            }
            if (tick[0] % 10 == 5 && RidingCheck.notAboard(ride, crew)) {
                ride.boarding().interact(crew.player(), InteractionHand.MAIN_HAND);
            }
            crew.drain(sent -> radarMessages(sent, message -> {
                RadarClientMessages.updateRadar(message, radar);
                log.record(message, tick[0]);
                missile.record(message);
            }));
            walker.drain(sent -> radarMessages(sent, message -> failures.add("a player on foot got radar contacts")));
        });
        h.runAfterDelay(100, () -> {
            expectContactAt(h, radar, contact, failures, "where it stands");
            missile.expectSeenAsAirToAir(failures);
        });
        h.runAfterDelay(101, () -> contact.setPos(contact.getX() + 20, contact.getY(), contact.getZ()));
        h.runAfterDelay(160, () -> expectContactAt(h, radar, contact, failures, "after it moved"));
        h.runAfterDelay(161, () -> {
            log.removedAt(tick[0]);
            contact.discard();
        });
        h.runAfterDelay(260, () -> {
            if (log.mentionedAfterRemoval()) {
                failures.add("the removed contact was still reported");
            }
            if (!log.refreshedSince(tick[0] - 60)) {
                failures.add("no radar snapshot in the last three seconds; the client's contacts would expire");
            }
            crew.leave();
            walker.leave();
            ride.discard();
            finish(h, failures);
        });
    }

    /**
     * Once an observer 20 blocks away tracks a vehicle, the vehicle is thrown up and lands; the
     * observer's copy, built from the packets alone, ends where the server's vehicle is.
     */
    private static void observerSeesVehicleConverge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AircraftEntity vehicle = spawnTank(h, new Vec3(4, 1, 4), 2);
        MockPlayer observer = join(level, "move_observer", h.absoluteVec(new Vec3(4, 1, 24)));
        ClientCopy copy = new ClientCopy(vehicle);
        int[] thrownAt = {-1};
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            observer.acknowledgeChunks();
            level.getChunkSource().move(observer.player());
            observer.drain(copy::accept);
            copy.noteServerPosition();
            if (thrownAt[0] < 0 && copy.tracked()) {
                thrownAt[0] = tick[0];
                vehicle.setPos(vehicle.getX(), vehicle.getY() + 10, vehicle.getZ());
                vehicle.setDeltaMovement(0.4, 0, 0.3);
            } else if (thrownAt[0] > 0 && tick[0] == thrownAt[0] + 120) {
                List<String> failures = new ArrayList<>();
                copy.expectConverged("the observer's copy", failures);
                observer.leave();
                vehicle.discard();
                finish(h, failures);
            }
        });
    }

    /**
     * The driver and a crew member in a seat of a moving tank both follow it: their copies of the tank
     * end where it is, and both get the radar picture.
     */
    private static void crewFollowOwnVehicle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AircraftEntity tank = spawnTank(h, new Vec3(4, 1, 4), 3);
        MockPlayer driver = join(level, "own_driver", h.absoluteVec(new Vec3(5, 1, 4)));
        MockPlayer gunner = join(level, "own_gunner", h.absoluteVec(new Vec3(5, 1, 5)));
        ClientCopy driverCopy = new ClientCopy(tank);
        ClientCopy gunnerCopy = new ClientCopy(tank);
        int[] radarMessages = new int[2];
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] % 10 == 5 && tick[0] < 60) {
                if (RidingCheck.notAboard(tank, driver)) {
                    tank.boarding().interact(driver.player(), InteractionHand.MAIN_HAND);
                } else if (RidingCheck.notAboard(tank, gunner)) {
                    tank.boarding().interact(gunner.player(), InteractionHand.MAIN_HAND);
                }
            }
            if (tick[0] > 60 && tick[0] < 140) {
                tank.setPos(tank.getX() + 0.1, tank.getY(), tank.getZ());
            }
            driver.drain(sent -> {
                radarMessages(sent, message -> radarMessages[0]++);
                driverCopy.accept(sent);
            });
            gunner.drain(sent -> {
                radarMessages(sent, message -> radarMessages[1]++);
                gunnerCopy.accept(sent);
            });
            driverCopy.noteServerPosition();
            gunnerCopy.noteServerPosition();
        });
        h.runAfterDelay(220, () -> {
            List<String> failures = new ArrayList<>();
            if (tank.getRiddenByEntity() != driver.player()) {
                failures.add("the driver did not board");
            }
            if (!(gunner.player().getVehicle() instanceof SeatEntity)) {
                failures.add("the gunner did not take a seat");
            }
            driverCopy.expectConverged("the driver's copy", failures);
            gunnerCopy.expectConverged("the gunner's copy", failures);
            if (radarMessages[0] == 0 || radarMessages[1] == 0) {
                failures.add("radar snapshots: driver " + radarMessages[0] + ", gunner " + radarMessages[1]);
            }
            driver.leave();
            gunner.leave();
            tank.discard();
            finish(h, failures);
        });
    }

    private static AircraftEntity spawnTank(GameTestHelper h, Vec3 at, long seed) {
        List<VehicleSpec> tanks = Fleets.manned(VehicleKind.TANK);
        AircraftEntity tank = Vehicles.spawn(h.getLevel(), tanks.getFirst(), h.absoluteVec(at), 0, seed);
        if (tank == null) {
            throw new IllegalStateException("no tank to spawn");
        }
        return tank;
    }

    private static MockPlayer join(ServerLevel level, String name, Vec3 at) {
        MockPlayer player = MockPlayer.join(level, name, 4);
        player.player().moveTo(at.x, at.y, at.z);
        level.getChunkSource().move(player.player());
        player.openModChannels();
        return player;
    }

    private static void radarMessages(Object sent, Consumer<RadarContactsMessage> handler) {
        try {
            if (sent instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof RadarContactsMessage message) {
                handler.accept(message);
            }
        } finally {
            ReferenceCountUtil.release(sent);
        }
    }

    private static void expectContactAt(GameTestHelper h, EntityInfoClientTracker radar, Entity contact,
                                        List<String> failures, String when) {
        EntityInfo info = radar.getEntityInfo(contact.getId());
        if (info == null) {
            failures.add("the contact is not on the radar " + when);
        } else if (new Vec3(info.x(), info.y(), info.z()).distanceTo(contact.position()) > RADAR_TOLERANCE) {
            failures.add("the radar shows the contact at " + Vehicles.vec(new Vec3(info.x(), info.y(), info.z()))
                    + " " + when + ", it is at " + Vehicles.vec(contact.position()));
        } else if (info.kind() != RadarContactKind.TANK) {
            failures.add("the radar shows the tank as " + info.kind());
        }
    }

    private static void finish(GameTestHelper h, List<String> failures) {
        if (failures.isEmpty()) {
            h.succeed();
        } else {
            h.fail(String.join("; ", failures));
        }
    }

    /** Whether a player is still to board. */
    private static final class RidingCheck {

        static boolean notAboard(AircraftEntity vehicle, MockPlayer player) {
            return vehicle.seats().seatIdOf(player.player()) < 0;
        }
    }

    /**
     * An air-to-air missile hanging in the air. Without a weapon it is removed on its second tick, so a
     * new one takes its place whenever it goes.
     */
    private static final class MissileOverhead {

        private final GameTestHelper h;
        private final Vec3 at;
        private final Set<Integer> ids = new HashSet<>();
        private final List<RadarContactKind> kindsSeen = new ArrayList<>();
        @Nullable
        private ProjectileEntity current;

        MissileOverhead(GameTestHelper h, Vec3 at) {
            this.h = h;
            this.at = at;
        }

        void keepFlying() {
            if (this.current == null || this.current.isRemoved()) {
                this.current = ProjectileEntity.create(ProjectileKind.AA_MISSILE, this.h.getLevel(),
                        this.h.absoluteVec(this.at), new Vec3(0, 0, 1), 0.0F, 0.0F, 0.0);
                this.h.getLevel().addFreshEntity(this.current);
                this.ids.add(this.current.getId());
            }
        }

        void record(RadarContactsMessage message) {
            for (RadarContact contact : message.introduced()) {
                if (this.ids.contains(contact.entityId())) {
                    this.kindsSeen.add(contact.kind());
                }
            }
        }

        void expectSeenAsAirToAir(List<String> failures) {
            if (this.kindsSeen.isEmpty()) {
                failures.add("no air-to-air missile reached the radar");
            } else if (this.kindsSeen.stream().anyMatch(kind -> kind != RadarContactKind.AIR_TO_AIR_MISSILE)) {
                failures.add("the radar showed air-to-air missiles as " + this.kindsSeen);
            }
        }
    }

    /** When the radar messages mentioned one contact. */
    private static final class RadarLog {

        private final int contactId;
        private int removedAt = Integer.MAX_VALUE;
        private int lastMention = -1;
        private int lastMessage = -1;

        RadarLog(int contactId) {
            this.contactId = contactId;
        }

        void record(RadarContactsMessage message, int tick) {
            this.lastMessage = tick;
            boolean mentioned = message.fixes().stream().anyMatch(fix -> fix.entityId() == this.contactId)
                    || java.util.Arrays.stream(message.unchanged()).anyMatch(id -> id == this.contactId);
            if (mentioned) {
                this.lastMention = tick;
            }
        }

        void removedAt(int tick) {
            this.removedAt = tick;
        }

        /** Snapshots already on their way when the entity went are allowed for a few ticks. */
        boolean mentionedAfterRemoval() {
            return this.lastMention > this.removedAt + 10;
        }

        boolean refreshedSince(int tick) {
            return this.lastMessage >= tick;
        }
    }

    /** One entity's position as a client rebuilds it from teleport and move packets. */
    private static final class ClientCopy {

        private final Entity entity;
        private final ServerLevel level;
        private final VecDeltaCodec codec = new VecDeltaCodec();
        private Vec3 position;
        private int updates;
        private boolean tracked;
        private final java.util.ArrayDeque<Vec3> serverPositions = new java.util.ArrayDeque<>();

        ClientCopy(Entity entity) {
            this.entity = entity;
            this.level = (ServerLevel) entity.level();
            this.position = entity.position();
            this.codec.setBase(this.position);
        }

        void accept(Object sent) {
            if (sent instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::packet);
            } else if (sent instanceof Packet<?> packet) {
                packet(packet);
            }
        }

        private void packet(@Nullable Packet<?> packet) {
            if (packet instanceof ClientboundAddEntityPacket add && add.getId() == this.entity.getId()) {
                // the observer started tracking the entity: where it is now
                this.position = new Vec3(add.getX(), add.getY(), add.getZ());
                this.codec.setBase(this.position);
                this.tracked = true;
            } else if (packet instanceof ClientboundTeleportEntityPacket teleport
                    && teleport.getId() == this.entity.getId()) {
                this.position = new Vec3(teleport.getX(), teleport.getY(), teleport.getZ());
                this.codec.setBase(this.position);
                this.updates++;
            } else if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()
                    && move.getEntity(this.level) == this.entity) {
                this.position = this.codec.decode(move.getXa(), move.getYa(), move.getZa());
                this.codec.setBase(this.position);
                this.updates++;
            }
        }

        /** Whether the client was told about the entity. */
        boolean tracked() {
            return this.tracked;
        }

        /** Called once a tick, after the packets were read. */
        void noteServerPosition() {
            this.serverPositions.addFirst(this.entity.position());
            if (this.serverPositions.size() > POSITIONS_KEPT) {
                this.serverPositions.removeLast();
            }
        }

        /** The copy is where the server's entity was in one of the last ticks. */
        void expectConverged(String what, List<String> failures) {
            double closest = this.serverPositions.stream().mapToDouble(this.position::distanceTo).min()
                    .orElse(Double.MAX_VALUE);
            if (this.updates == 0) {
                failures.add(what + " got no position updates");
            } else if (closest > MOVE_TOLERANCE) {
                failures.add(what + " is at " + Vehicles.vec(this.position) + " after " + this.updates
                        + " updates, the server's at " + Vehicles.vec(this.entity.position()));
            }
        }

    }
}
