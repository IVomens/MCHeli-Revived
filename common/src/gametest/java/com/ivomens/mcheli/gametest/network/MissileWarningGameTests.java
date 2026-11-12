package com.ivomens.mcheli.gametest.network;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.net.RwrThreat;
import com.ivomens.mcheli.combat.weapon.net.RwrThreatsMessage;
import com.ivomens.mcheli.gametest.bench.MockPlayer;
import com.ivomens.mcheli.gametest.snapshot.Vehicles;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The missile warning names the seeker of each missile homing on a vehicle: the crew of a jet that carries
 * flares and chaff are told that an AIM-120 is a radar missile and an AIM-9 a heat seeker.
 */
public final class MissileWarningGameTests {

    private static final String BATCH = "mcheli_network";
    private static final String JET = "f-35a";
    private static final int CHECK_TICK = 80;

    private MissileWarningGameTests() {
    }

    public static List<TestFunction> seekers() {
        return List.of(new TestFunction(BATCH, "missile_warning_names_seekers", "mcheli:empty", 200, 0, true,
                MissileWarningGameTests::missileWarningNamesSeekers));
    }

    private static void missileWarningNamesSeekers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        VehicleSpec jet = VehicleTypes.registry(VehicleKind.PLANE).get(JET).orElseThrow();
        AircraftEntity ride = Vehicles.spawn(level, jet, h.absoluteVec(new Vec3(8, 1, 8)), 0, 7);
        if (ride == null) {
            throw new IllegalStateException("cannot spawn " + JET);
        }
        MockPlayer crew = MockPlayer.join(level, "missile_warning_crew", 4);
        Vec3 standing = h.absoluteVec(new Vec3(9, 1, 8));
        crew.player().moveTo(standing.x, standing.y, standing.z);
        level.getChunkSource().move(crew.player());
        crew.openModChannels();
        HangingMissile radar = new HangingMissile(h, "aim120", ride, new Vec3(8, 12, 40));
        HangingMissile heat = new HangingMissile(h, "aim9", ride, new Vec3(-16, 12, 8));
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] % 10 == 5 && ride.seats().seatIdOf(crew.player()) < 0) {
                ride.boarding().interact(crew.player(), InteractionHand.MAIN_HAND);
            }
            radar.keepHanging();
            heat.keepHanging();
            crew.drain(sent -> threats(sent, ride.getId(), threat -> {
                radar.record(threat);
                heat.record(threat);
            }));
        });
        h.runAfterDelay(CHECK_TICK, () -> {
            List<String> failures = new ArrayList<>();
            if (ride.seats().seatIdOf(crew.player()) < 0) {
                failures.add("the crew never boarded the " + JET);
            }
            radar.expect(ThreatSeeker.RADAR, failures);
            heat.expect(ThreatSeeker.HEAT, failures);
            radar.discard();
            heat.discard();
            crew.leave();
            ride.discard();
            if (failures.isEmpty()) {
                h.succeed();
            } else {
                h.fail(String.join("; ", failures));
            }
        });
    }

    private static void threats(Object sent, int receiverId, java.util.function.Consumer<RwrThreat> handler) {
        try {
            if (sent instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof RwrThreatsMessage message && message.receiverId() == receiverId) {
                message.threats().forEach(handler);
            }
        } finally {
            ReferenceCountUtil.release(sent);
        }
    }

    /** A missile of a weapon homing on the vehicle, held in place; a new one takes its place should it go. */
    private static final class HangingMissile {

        private final GameTestHelper h;
        private final String weapon;
        private final AircraftEntity target;
        private final Vec3 at;
        private final Set<Integer> ids = new HashSet<>();
        private final Set<ThreatSeeker> seen = EnumSet.noneOf(ThreatSeeker.class);
        private final Set<Byte> modes = new HashSet<>();
        @Nullable
        private ProjectileEntity current;
        @Nullable
        private Entity launcher;

        HangingMissile(GameTestHelper h, String weapon, AircraftEntity target, Vec3 at) {
            this.h = h;
            this.weapon = weapon;
            this.target = target;
            this.at = at;
        }

        /** An unseen stand the missile counts as fired from, so it stays in range and is not removed. */
        private Entity launcher(Vec3 at) {
            if (this.launcher == null || this.launcher.isRemoved()) {
                ArmorStand stand = new ArmorStand(this.h.getLevel(), at.x, at.y - 3, at.z);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                this.h.getLevel().addFreshEntity(stand);
                this.launcher = stand;
            }
            return this.launcher;
        }

        void keepHanging() {
            Vec3 position = this.h.absoluteVec(this.at);
            if (this.current == null || this.current.isRemoved()) {
                this.current = ProjectileEntity.create(ProjectileKind.AA_MISSILE, this.h.getLevel(), position,
                        new Vec3(0, 0, 1), 0.0F, 0.0F, 0.0);
                this.current.setWeapon(this.weapon);
                this.current.setShooter(launcher(position), null);
                this.h.getLevel().addFreshEntity(this.current);
                this.current.setTarget(this.target);
                this.ids.add(this.current.getId());
            }
            this.current.setPos(position.x, position.y, position.z);
            this.current.setDeltaMovement(Vec3.ZERO);
        }

        void record(RwrThreat threat) {
            if (this.ids.contains(threat.emitterId())) {
                this.seen.add(threat.kind().seeker());
                this.modes.add(threat.kind().threatMode());
            }
        }

        void expect(ThreatSeeker seeker, List<String> failures) {
            if (!this.seen.equals(EnumSet.of(seeker))) {
                failures.add("the " + this.weapon + " was reported as " + this.seen + ", expected " + seeker);
            }
            if (!this.modes.equals(Set.of(RWRThreatEvent.MODE_MSL_ACTIVE))) {
                failures.add("the " + this.weapon + " was reported in modes " + this.modes);
            }
        }

        void discard() {
            if (this.current != null) {
                this.current.discard();
            }
            if (this.launcher != null) {
                this.launcher.discard();
            }
        }
    }
}
