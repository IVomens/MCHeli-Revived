package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.api.event.CrewEjected;
import com.ivomens.mcheli.api.event.MissileWarned;
import com.ivomens.mcheli.api.event.SeekerType;
import com.ivomens.mcheli.api.event.VehicleDamageTaken;
import com.ivomens.mcheli.api.event.VehicleDamaged;
import com.ivomens.mcheli.api.event.VehicleEnteredWater;
import com.ivomens.mcheli.api.event.VehicleLanded;
import com.ivomens.mcheli.api.event.VehicleLeftWater;
import com.ivomens.mcheli.api.event.VehicleRearmed;
import com.ivomens.mcheli.api.event.VehicleRefueled;
import com.ivomens.mcheli.api.event.VehicleTookOff;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.registry.McheliItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ivomens.mcheli.gametest.api.ApiTestScene.check;

/**
 * The API 1.1 events about what happens to a vehicle, each fired where it should and once: damage (through a
 * seat too, with the weapon named, changed and cancelled by listeners), takeoff and landing without counting a
 * hop, entering and leaving water, the missile warning naming a heat seeker, an ejection, and rearming and
 * refuelling. Every listener only looks at its own test's vehicle, as other tests run at the same time.
 */
public final class ApiEventGameTests {

    private static final String BATCH = "mcheli_api_events";
    private static final String HELICOPTER = "ah-64";

    private ApiEventGameTests() {
    }

    public static List<TestFunction> events() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction(BATCH, "api_vehicle_damaged_once_per_hit", "mcheli:empty", 100, 0, true,
                        ApiEventGameTests::vehicleDamagedOncePerHit),
                new TestFunction(BATCH, "api_takeoff_and_landing_ignore_hops", "mcheli:empty", 400, 0, true,
                        ApiEventGameTests::takeoffAndLanding),
                new TestFunction(BATCH, "api_water_entered_and_left", "mcheli:empty", 300, 0, true,
                        ApiEventGameTests::waterEnteredAndLeft),
                new TestFunction(BATCH, "api_missile_warning_names_the_seeker", "mcheli:empty", 300, 0, true,
                        ApiEventGameTests::missileWarningNamesTheSeeker),
                new TestFunction(BATCH, "api_ejection_rearm_refuel", "mcheli:empty", 300, 0, true,
                        ApiEventGameTests::ejectionRearmAndRefuel));
    }

    /**
     * Hits on the hull and on a passenger seat each fire one {@code VehicleDamaged} and one
     * {@code VehicleDamageTaken}; a projectile's and a beam's weapon is named; a listener can halve the
     * amount, zero it or cancel the hit, and then no hit points go.
     */
    private static void vehicleDamagedOncePerHit(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        AircraftEntity ac = scene.spawn(VehicleKind.HELICOPTER, HELICOPTER, new Vec3(8, 1, 8));
        DamageLog log = new DamageLog(ac);
        scene.listen(VehicleDamaged.class, log::onDamaged);
        scene.listen(VehicleDamaged.class, log::afterOthers);
        scene.listen(VehicleDamageTaken.class, log::onTaken);
        h.runAfterDelay(20, () -> {
            ServerLevel level = h.getLevel();
            DamageSource generic = level.damageSources().generic();
            SeatEntity seat = firstSeat(ac);
            int before = ac.damage().damageTaken();
            check(ac.hurt(generic, 40.0F), "a plain hit did not land");
            check(log.damaged == 1 && log.taken == 1, "one hit fired " + log.damaged + " and " + log.taken + " events");
            check(ac.damage().damageTaken() > before && log.lastHp == ac.damage().hp(), "the hit points are off");
            seat.hurt(generic, 40.0F);
            check(log.damaged == 2 && log.taken == 2, "a hit on a seat fired " + (log.damaged - 1) + " events");

            ProjectileEntity round = ProjectileEntity.create(ProjectileKind.AA_MISSILE, level, ac.position(),
                    new Vec3(0, 0, 1), 0.0F, 0.0F, 0.0);
            round.setWeapon("aim9");
            ac.hurt(ModDamageSources.bullet(level, round, null), 1.0F);
            check("aim9".equals(log.lastWeapon), "a missile's hit was named " + log.lastWeapon);
            ac.hurt(ModDamageSources.beam(level, ac, null, "test_beam"), 1.0F);
            check("test_beam".equals(log.lastWeapon), "a beam's hit was named " + log.lastWeapon);
            ac.hurt(generic, 1.0F);
            check(log.lastWeapon == null, "a plain hit was named " + log.lastWeapon);

            log.mode = DamageLog.Mode.HALVE;
            ac.hurt(generic, 30.0F);
            check(log.lastAmountSeen == 15.0F, "the halved amount reached later listeners as " + log.lastAmountSeen);
            for (DamageLog.Mode stop : List.of(DamageLog.Mode.ZERO, DamageLog.Mode.CANCEL)) {
                log.mode = stop;
                int taken = ac.damage().damageTaken();
                int events = log.taken;
                boolean landed = ac.hurt(generic, 40.0F);
                check(!landed && ac.damage().damageTaken() == taken && log.taken == events,
                        stop + ": the hit still cost hit points");
            }
            scene.unsubscribeAll();
            ac.discard();
            h.succeed();
        });
    }

    private static SeatEntity firstSeat(AircraftEntity ac) {
        for (SeatEntity seat : ac.seats().all()) {
            if (seat != null) {
                return seat;
            }
        }
        throw new IllegalStateException(ac + " has no passenger seat");
    }

    /**
     * A helicopter settles on the ground, is held in the air for two seconds (one takeoff), dropped (one
     * landing), then lifted for less than a second twice (no takeoff).
     */
    private static void takeoffAndLanding(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        AircraftEntity ac = scene.spawn(VehicleKind.HELICOPTER, HELICOPTER, new Vec3(8, 1, 8));
        List<String> seen = new ArrayList<>();
        scene.listen(VehicleTookOff.class, e -> {
            if (e.vehicle() == ac) seen.add("took off");
        });
        scene.listen(VehicleLanded.class, e -> {
            if (e.vehicle() == ac) seen.add(e.onWater() ? "landed on water" : "landed");
        });
        Vec3 ground = h.absoluteVec(new Vec3(8, 1, 8));
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            ac.rest().wake();
            int t = tick[0];
            // the hops are held up for 8 ticks and fall back within a few more, under the 20 of a takeoff
            boolean held = t >= 40 && t < 80 || t >= 150 && t < 158 || t >= 200 && t < 208;
            if (held) {
                ac.setPos(ground.x, ground.y + (t < 80 ? 4 : 1), ground.z);
                ac.setDeltaMovement(Vec3.ZERO);
            }
            if (t == 79) {
                check(seen.equals(List.of("took off")), "after two seconds in the air: " + seen);
            }
        });
        h.runAfterDelay(280, () -> {
            check(seen.equals(List.of("took off", "landed")), "events: " + seen);
            scene.unsubscribeAll();
            ac.discard();
            h.succeed();
        });
    }

    /** A helicopter lowered into a pool enters the water once and leaves it once when put back on the floor. */
    private static void waterEnteredAndLeft(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        for (int x = 18; x < 30; x++) {
            for (int z = 2; z < 14; z++) {
                for (int y = 0; y < 4; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        AircraftEntity ac = scene.spawn(VehicleKind.HELICOPTER, HELICOPTER, new Vec3(8, 1, 8));
        List<String> seen = new ArrayList<>();
        scene.listen(VehicleEnteredWater.class, e -> {
            if (e.vehicle() == ac) seen.add("entered");
        });
        scene.listen(VehicleLeftWater.class, e -> {
            if (e.vehicle() == ac) seen.add("left");
        });
        Vec3 pool = h.absoluteVec(new Vec3(24, 1.5, 8));
        Vec3 floor = h.absoluteVec(new Vec3(8, 1, 8));
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            ac.rest().wake();
            int t = tick[0];
            // not long in the pool: a helicopter that cannot float takes flooding damage
            Vec3 at = t >= 30 && t < 60 ? pool : t >= 60 && t < 65 ? floor : null;
            if (at != null) {
                ac.setPos(at.x, at.y, at.z);
                ac.setDeltaMovement(Vec3.ZERO);
            }
        });
        h.runAfterDelay(120, () -> {
            check(seen.equals(List.of("entered", "left")), "events: " + seen);
            scene.unsubscribeAll();
            ac.discard();
            h.succeed();
        });
    }

    /** A jet's crew is warned of an AIM-9 hanging behind it, named as a heat seeker, with the missile listed. */
    private static void missileWarningNamesTheSeeker(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        AircraftEntity jet = scene.spawn(VehicleKind.PLANE, "f-35a", new Vec3(8, 1, 8));
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        Vec3 standing = h.absoluteVec(new Vec3(10, 1, 8));
        pilot.moveTo(standing.x, standing.y, standing.z);
        List<String> seen = new ArrayList<>();
        ProjectileEntity[] missile = {null};
        scene.listen(MissileWarned.class, e -> {
            if (e.vehicle() == jet) seen.add(e.seeker() + " " + e.missiles().contains(missile[0]));
        });
        Vec3 hanging = h.absoluteVec(new Vec3(8, 12, 30));
        ArmorStand launcher = new ArmorStand(h.getLevel(), hanging.x, hanging.y - 3, hanging.z);
        launcher.setInvisible(true);
        launcher.setNoGravity(true);
        h.getLevel().addFreshEntity(launcher);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (jet.seats().seatIdOf(pilot) < 0) {
                if (tick[0] % 10 == 5) jet.boarding().interact(pilot, InteractionHand.MAIN_HAND);
                return;
            }
            if (missile[0] == null || missile[0].isRemoved()) {
                missile[0] = ProjectileEntity.create(ProjectileKind.AA_MISSILE, h.getLevel(), hanging,
                        new Vec3(0, 0, -1), 0.0F, 0.0F, 0.0);
                missile[0].setWeapon("aim9");
                missile[0].setShooter(launcher, null);
                h.getLevel().addFreshEntity(missile[0]);
                missile[0].setTarget(jet);
            }
            missile[0].setPos(hanging.x, hanging.y, hanging.z);
            missile[0].setDeltaMovement(Vec3.ZERO);
        });
        h.succeedWhen(() -> {
            check(seen.contains(SeekerType.HEAT + " true"), "warnings: " + seen);
            scene.unsubscribeAll();
            if (missile[0] != null) missile[0].discard();
            launcher.discard();
            jet.discard();
            pilot.discard();
        });
    }

    /**
     * An A-10's pilot ejects with a parachute from the cargo; a helicopter's pilot pays for a weapon's reserve
     * with its round items and its tank is charged.
     */
    private static void ejectionRearmAndRefuel(GameTestHelper h) {
        ApiTestScene scene = new ApiTestScene(h).floor();
        AircraftEntity plane = scene.spawn(VehicleKind.PLANE, "a-10", new Vec3(8, 1, 8));
        AircraftEntity heli = scene.spawn(VehicleKind.HELICOPTER, HELICOPTER, new Vec3(22, 1, 8));
        ServerPlayer jumper = h.makeMockServerPlayerInLevel();
        ServerPlayer gunner = h.makeMockServerPlayerInLevel();
        Vec3 near = h.absoluteVec(new Vec3(14, 1, 8));
        jumper.moveTo(near.x, near.y, near.z);
        gunner.moveTo(near.x, near.y, near.z);
        List<String> seen = new ArrayList<>();
        scene.listen(CrewEjected.class, e -> {
            if (e.vehicle() == plane) seen.add("ejected " + (e.rider() == jumper) + " " + e.seatId());
        });
        scene.listen(VehicleRearmed.class, e -> {
            if (e.vehicle() == heli) seen.add("rearmed " + (e.supplier() == gunner) + " " + (e.rounds() > 0));
        });
        scene.listen(VehicleRefueled.class, e -> {
            if (e.vehicle() == heli) seen.add("refueled " + e.amount() + " " + (e.supplier() == null));
        });
        plane.getGuiInventory().getItemHandler().setStackInSlot(AircraftInventory.SLOT_PARACHUTE0,
                new ItemStack(McheliItems.PARACHUTE.get()));
        int[] tick = {0};
        boolean[] done = {false, false};
        h.onEachTick(() -> {
            tick[0]++;
            if (!done[0]) {
                done[0] = boardThen(plane, jumper, tick[0], () -> plane.ejectionSeats().ejectSeat(jumper));
            }
            if (!done[1]) {
                done[1] = boardThen(heli, gunner, tick[0], () -> rearmAndRefuel(heli, gunner));
            }
        });
        h.succeedWhen(() -> {
            check(done[0] && done[1], "still boarding");
            check(seen.contains("ejected true 0"), "events: " + seen);
            check(seen.contains("rearmed true true"), "events: " + seen);
            check(seen.contains("refueled 100 true"), "events: " + seen);
            scene.unsubscribeAll();
            plane.discard();
            heli.discard();
            jumper.discard();
            gunner.discard();
        });
    }

    /** Clicks the vehicle every half second until the player flies it, then runs the action once. */
    private static boolean boardThen(AircraftEntity vehicle, ServerPlayer player, int tick, Runnable action) {
        if (vehicle.seats().seatIdOf(player) != 0) {
            if (tick % 10 == 5) vehicle.boarding().interact(player, InteractionHand.MAIN_HAND);
            return false;
        }
        action.run();
        return true;
    }

    /** Empties a weapon that can be restocked, hands the pilot its round items, and pays for it. */
    private static void rearmAndRefuel(AircraftEntity heli, ServerPlayer pilot) {
        WeaponGroup restockable = null;
        for (int i = 0; i < heli.weapons().count() && restockable == null; i++) {
            WeaponGroup weapon = heli.weapons().get(i);
            if (weapon.spec().ammo().resupplyCount() > 0 && weapon.maxAmmo() > 0) {
                restockable = weapon;
            }
        }
        Objects.requireNonNull(restockable, "the helicopter has no weapon to restock").setAmmo(0);
        restockable.setReserveAmmo(0);
        for (ContentBindings.RoundStack round : ContentBindings.rounds(restockable.spec().id())) {
            pilot.getInventory().add(round.stack().copyWithCount(round.count()));
        }
        heli.ammoSupply().supply(restockable);
        heli.fuel().setAmount(0);
        heli.fuel().charge(100, false);
    }

    /** What the damage listeners saw of the test's vehicle. */
    private static final class DamageLog {

        enum Mode { PASS, HALVE, ZERO, CANCEL }

        private final AircraftEntity vehicle;
        private Mode mode = Mode.PASS;
        private int damaged;
        private int taken;
        private int lastHp = -1;
        private String lastWeapon;
        private float lastAmountSeen;

        DamageLog(AircraftEntity vehicle) {
            this.vehicle = vehicle;
        }

        void onDamaged(VehicleDamaged event) {
            if (event.vehicle() != this.vehicle) {
                return;
            }
            this.damaged++;
            this.lastWeapon = event.weaponId();
            switch (this.mode) {
                case HALVE -> event.setAmount(event.amount() / 2.0F);
                case ZERO -> event.setAmount(0.0F);
                case CANCEL -> event.setCancelled(true);
                default -> {
                }
            }
        }

        /** Registered after {@link #onDamaged}, so it sees what that listener made of the hit. */
        void afterOthers(VehicleDamaged event) {
            if (event.vehicle() == this.vehicle) {
                this.lastAmountSeen = event.amount();
            }
        }

        void onTaken(VehicleDamageTaken event) {
            if (event.vehicle() == this.vehicle) {
                this.taken++;
                this.lastHp = event.hp();
            }
        }
    }
}
