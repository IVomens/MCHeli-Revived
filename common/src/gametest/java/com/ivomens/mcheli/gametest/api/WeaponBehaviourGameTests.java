package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.projectile.behaviour.WeaponBehaviourSet;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.Diagnostic;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Addon weapon behaviours (the API's {@code com.ivomens.mcheli.api.weapon}) as the weapons of the test pack use
 * the ones {@link TestWeaponBehaviours} registers: a guidance law turns within the weapon's turn rate and loses a
 * target outside its seeker cone, effects run in order with their settings at each point of a projectile's life
 * and can replace the explosion, an effect stalls a helicopter's engine for a while, and ids nobody registered
 * leave a weapon with its built-in behaviour and a warning in the content report.
 */
public final class WeaponBehaviourGameTests {

    private static final String BATCH = "mcheli_api_behaviours";
    private static final String CLIMBER = "api_test_climber";
    private static final String TRACKER = "api_test_tracker";
    private static final String RECORDED = "api_test_recorded";
    private static final String EMP = "api_test_emp";
    private static final String HELICOPTER = "ah-64";

    private WeaponBehaviourGameTests() {
    }

    public static List<TestFunction> behaviours() {
        if (!System.getProperty("mcheli.stress", "").isEmpty()) {
            return List.of();
        }
        return List.of(
                test("api_behaviour_guidance_turns_within_rate", 100,
                        WeaponBehaviourGameTests::guidanceTurnsWithinRate),
                test("api_behaviour_track_keeps_the_seeker_cone", 60,
                        WeaponBehaviourGameTests::trackKeepsSeekerCone),
                test("api_behaviour_effects_run_in_order", 100, WeaponBehaviourGameTests::effectsRunInOrder),
                test("api_behaviour_effects_hear_expiry", 40, WeaponBehaviourGameTests::effectsHearExpiry),
                test("api_behaviour_emp_stalls_an_engine", 200, WeaponBehaviourGameTests::empStallsAnEngine),
                test("api_behaviour_unknown_ids_fall_back", 20, WeaponBehaviourGameTests::unknownIdsFallBack));
    }

    private static TestFunction test(String name, int ticks, Consumer<GameTestHelper> body) {
        return new TestFunction(BATCH, name, "mcheli:empty", ticks, 0, true, body);
    }

    /** TurningFactor 0.1 turns a level rocket a tenth of the way up per tick, and in time it flies straight up. */
    private static void guidanceTurnsWithinRate(GameTestHelper h) {
        WeaponInfo info = McheliApi.get().content().weapon(CLIMBER).orElseThrow();
        check(info.guidance().equals(Optional.of(TestWeaponBehaviours.CLIMB)), "guidance " + info.guidance());
        ProjectileEntity rocket = launch(h, CLIMBER, new Vec3(4, 20, 4), new Vec3(1, 0, 0));
        double[] firstTurn = {Double.NaN};
        h.onEachTick(() -> {
            if (rocket.isRemoved()) {
                return;
            }
            double up = rocket.getDeltaMovement().normalize().y;
            if (rocket.age() == 1) {
                firstTurn[0] = up;
            } else if (rocket.age() == 40) {
                check(firstTurn[0] > 0.05 && firstTurn[0] < 0.3, "the first tick turned it to " + firstTurn[0]);
                check(up > 0.9, "after 40 ticks it climbs at only " + up);
                rocket.discard();
                h.succeed();
            }
        });
    }

    /** A target ahead stays locked and is steered at; one behind the rocket is outside its 45 degree cone. */
    private static void trackKeepsSeekerCone(GameTestHelper h) {
        ArmorStand ahead = stand(h, new Vec3(24, 24, 2));
        ArmorStand behind = stand(h, new Vec3(-16, 20, 6));
        ProjectileEntity seeing = launch(h, TRACKER, new Vec3(4, 20, 2), new Vec3(1, 0, 0), ahead);
        ProjectileEntity blind = launch(h, TRACKER, new Vec3(4, 20, 6), new Vec3(1, 0, 0), behind);
        h.runAfterDelay(5, () -> {
            check(seeing.target() == ahead, "lost the target ahead");
            check(seeing.getDeltaMovement().y > 0.01, "did not steer up towards the target ahead");
            check(blind.target() == null, "kept the target behind it");
            check(Math.abs(blind.getDeltaMovement().normalize().x - 1.0) < 1.0E-6, "steered without a lock");
            seeing.discard();
            blind.discard();
            ahead.discard();
            behind.discard();
            h.succeed();
        });
    }

    /**
     * Two recorders in file order: spawn, a tick, the block hit and the detonation each reach the first and then
     * the second, the second sees that the first cancelled the explosion, and the floor stays whole.
     */
    private static void effectsRunInOrder(GameTestHelper h) {
        WeaponInfo info = McheliApi.get().content().weapon(RECORDED).orElseThrow();
        check(info.projectileEffects().equals(List.of(TestWeaponBehaviours.RECORDER, TestWeaponBehaviours.RECORDER)),
                "effects " + info.projectileEffects());
        floor(h);
        ProjectileEntity rocket = launch(h, RECORDED, new Vec3(8.5, 6.5, 8.5), new Vec3(0, -1, 0));
        h.succeedWhen(() -> {
            check(rocket.isRemoved(), "still flying");
            List<String> expected = List.of("a:spawn", "b:spawn", "a:tick", "b:tick", "a:block", "b:block",
                    "a:detonate", "b:detonate:cancelled");
            check(TestWeaponBehaviours.recorded(rocket).equals(expected), "heard " + TestWeaponBehaviours
                    .recorded(rocket));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 0, 8));
        });
    }

    /** A projectile removed in flight ends without going off: its effects hear the expiry and no detonation. */
    private static void effectsHearExpiry(GameTestHelper h) {
        ProjectileEntity rocket = launch(h, RECORDED, new Vec3(4, 20, 4), new Vec3(0, 1, 0));
        h.runAfterDelay(3, () -> {
            rocket.discard();
            List<String> expected = List.of("a:spawn", "b:spawn", "a:tick", "b:tick", "a:expire", "b:expire");
            check(TestWeaponBehaviours.recorded(rocket).equals(expected), "heard " + TestWeaponBehaviours
                    .recorded(rocket));
            h.succeed();
        });
    }

    /** The EMP rocket bursts over a parked helicopter: its engine stalls for 30 ticks, then can run as before. */
    private static void empStallsAnEngine(GameTestHelper h) {
        floor(h);
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HELICOPTER + " has no item");
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.5, 8));
        AircraftEntity ac = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        h.getLevel().addFreshEntity(ac);
        int[] stalledAt = {-1};
        boolean[] couldRun = {false};
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 10) {
                couldRun[0] = ac.engine().canRun();
                launch(h, EMP, new Vec3(8, 5, 8), new Vec3(0, -1, 0)).detonateHere();
                stalledAt[0] = tick[0];
                check(ac.engine().isStalled() && !ac.engine().canRun(), "the engine was not stalled");
            } else if (stalledAt[0] > 0 && tick[0] == stalledAt[0] + 20) {
                check(ac.engine().isStalled(), "the stall ended early");
            } else if (stalledAt[0] > 0 && tick[0] == stalledAt[0] + 32) {
                check(!ac.engine().isStalled(), "the stall did not end");
                check(ac.engine().canRun() == couldRun[0], "the engine cannot run as before");
                check(!ac.isDestroyed(), "the EMP destroyed the helicopter");
                ac.discard();
                h.succeed();
            }
        });
    }

    /**
     * A weapon naming behaviours nobody registered, or settings a factory rejects, keeps its built-in behaviour;
     * the content report says why.
     */
    private static void unknownIdsFallBack(GameTestHelper h) {
        WeaponSpec unknown;
        WeaponSpec rejected;
        ContentReport report;
        try (ReportCollector collector = ReportCollector.open("Weapon behaviour game test")) {
            unknown = read(collector, "gametest_unknown_behaviours", List.of("Type: aamissile",
                    "Guidance: nobody:homing", "ProjectileEffects: [nobody:emp, mcheli_gametest:recorder]"));
            rejected = read(collector, "gametest_rejected_behaviours", List.of("Type: rocket",
                    "Guidance: { Id: mcheli_gametest:climb, Height: -1 }"));
            report = collector.finish();
        }
        WeaponBehaviourSet behaviours = unknown.behaviours();
        check(behaviours != null && behaviours.guidance() == null, "an unknown guidance was used");
        check(behaviours.effectIds().equals(List.of(TestWeaponBehaviours.RECORDER)), "effects " + behaviours
                .effectIds());
        check(rejected.behaviours() != null && rejected.behaviours().guidance() == null,
                "a guidance that rejected its settings was used");
        List<String> warnings = report.diagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.WARNING).map(Diagnostic::message).toList();
        for (String expected : List.of("nobody:homing", "nobody:emp", "mcheli_gametest:climb")) {
            check(warnings.stream().anyMatch(message -> message.contains(expected)),
                    "no warning names " + expected + ": " + warnings);
        }
        h.succeed();
    }

    private static WeaponSpec read(ReportCollector collector, String name, List<String> lines) {
        try (ReportCollector.FileScope scope = ReportCollector.enterFile("gametest", name + ".yml")) {
            AddonResourceLocation location = new AddonResourceLocation(Ids.of(name), "gametest");
            ContentEntry<?> entry = ContentFormat.of(ContentType.WEAPON).read(ContentType.WEAPON, location,
                    "assets/mcheli/weapons/" + name + ".yml", ContentFormat.YAML, lines, false);
            return Weapons.registry().bind(entry).value();
        } catch (Exception e) {
            throw new GameTestAssertException("could not read " + name + ": " + e);
        }
    }

    private static ProjectileEntity launch(GameTestHelper h, String weapon, Vec3 relative, Vec3 direction) {
        return launch(h, weapon, relative, direction, null);
    }

    private static ProjectileEntity launch(GameTestHelper h, String weapon, Vec3 relative, Vec3 direction,
                                           @Nullable Entity target) {
        WeaponSpec spec = Weapons.get(weapon).orElseThrow(() -> new GameTestAssertException(weapon + " not loaded"));
        ProjectileEntity rocket = ProjectileEntity.create(ProjectileKind.ROCKET, h.getLevel(), h.absoluteVec(relative),
                direction, 0.0F, 0.0F, spec.ballistics().acceleration());
        rocket.setWeapon(weapon);
        rocket.warhead().set(spec.definition().baseDamage(), spec.explosion().power(), spec.explosionInWater(), 0);
        if (target != null) {
            rocket.setTarget(target);
        }
        h.getLevel().addFreshEntity(rocket);
        return rocket;
    }

    private static ArmorStand stand(GameTestHelper h, Vec3 relative) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(h.getLevel());
        Vec3 at = h.absoluteVec(relative);
        stand.moveTo(at.x, at.y, at.z);
        stand.setNoGravity(true);
        h.getLevel().addFreshEntity(stand);
        return stand;
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
