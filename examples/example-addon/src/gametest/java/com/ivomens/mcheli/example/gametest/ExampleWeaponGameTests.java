package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.weapon.ExampleBehaviours;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The addon's weapon behaviours from its own jar: MCHeli found its {@code WeaponBehaviourProvider}, the weapons of
 * its pack picked the behaviours up, the beam-riding missile flies onto its gunner's line of sight, and the EMP
 * rocket stops a helicopter's engine for its four seconds.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleWeaponGameTests {

    private static final String BEAM_RIDER = "example_beam_rider";
    private static final String EMP_ROCKET = "example_emp_rocket";
    private static final String HELICOPTER = "ah-64";
    /** The pulse lasts {@code Seconds: 4}. */
    private static final int STALL_TICKS = 80;

    private ExampleWeaponGameTests() {
    }

    /** The weapons of the addon's pack name behaviours its provider registered. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exampleWeaponsUseTheAddonBehaviours(GameTestHelper h) {
        WeaponInfo beamRider = weapon(BEAM_RIDER);
        check(beamRider.guidance().equals(Optional.of(ExampleBehaviours.BEAM_RIDER)), "beam rider guidance "
                + beamRider.guidance());
        WeaponInfo emp = weapon(EMP_ROCKET);
        check(emp.projectileEffects().equals(List.of(ExampleBehaviours.EMP)), "EMP effects "
                + emp.projectileEffects());
        h.succeed();
    }

    /**
     * A gunner looks east; the missile is launched three blocks to the side of his line of sight, angled off it.
     * The law brings it onto the line and keeps it there.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void beamRiderFliesAlongTheGunnersSight(GameTestHelper h) {
        ServerPlayer gunner = h.makeMockServerPlayerInLevel();
        Vec3 eyeAt = h.absoluteVec(new Vec3(2, 12, 8));
        gunner.moveTo(eyeAt.x, eyeAt.y - gunner.getEyeHeight(), eyeAt.z, -90.0F, 0.0F);
        gunner.setYHeadRot(-90.0F);
        Vec3 eye = gunner.getEyePosition();
        Vec3 beam = gunner.getLookAngle();
        check(beam.x > 0.99, "the gunner looks along " + beam);
        ProjectileEntity missile = launch(h, BEAM_RIDER, eye.add(1, 0, 3), new Vec3(1, 0, 0.3), 0.8, gunner);
        double startOff = offBeam(missile, eye, beam);
        h.runAfterDelay(30, () -> {
            double off = offBeam(missile, eye, beam);
            check(!missile.isRemoved(), "the missile is gone: " + missile.getRemovalReason());
            check(startOff > 2.5 && off < 1.0, "the missile is " + off + " blocks off the beam (started "
                    + startOff + " off)");
            missile.discard();
            gunner.discard();
            h.succeed();
        });
    }

    /** The EMP rocket bursts over a parked helicopter: no blast, and its engine is dead for four seconds. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void empRocketStopsAnEngine(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            h.fail(HELICOPTER + " has no item");
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(8, 1.5, 8));
        AircraftEntity heli = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        h.getLevel().addFreshEntity(heli);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 10) {
                launch(h, EMP_ROCKET, pos.add(0, 3, 0), new Vec3(0, -1, 0), 0.5, null).detonateHere();
                check(heli.engine().isStalled() && !heli.engine().canRun(), "the engine still runs");
                h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 0, 8));
            } else if (tick[0] == 10 + STALL_TICKS - 5) {
                check(heli.engine().isStalled(), "the engine came back early");
            } else if (tick[0] == 10 + STALL_TICKS + 2) {
                check(!heli.engine().isStalled(), "the engine stayed dead");
                check(!heli.isDestroyed(), "the pulse destroyed the helicopter");
                heli.discard();
                h.succeed();
            }
        });
    }

    /** How far the missile is from the gunner's line of sight. */
    private static double offBeam(Entity missile, Vec3 eye, Vec3 beam) {
        Vec3 relative = missile.position().subtract(eye);
        return relative.subtract(beam.scale(relative.dot(beam))).length();
    }

    private static ProjectileEntity launch(GameTestHelper h, String weapon, Vec3 at, Vec3 direction, double speed,
                                           ServerPlayer shooter) {
        WeaponSpec spec = Weapons.get(weapon).orElseThrow(() -> new GameTestAssertException(weapon + " not loaded"));
        ProjectileEntity rocket = ProjectileEntity.create(ProjectileKind.ROCKET, h.getLevel(), at, direction, -90.0F,
                0.0F, speed);
        rocket.setWeapon(weapon);
        rocket.warhead().set(spec.definition().baseDamage(), spec.explosion().power(), spec.explosionInWater(), 0);
        rocket.setShooter(shooter, shooter);
        h.getLevel().addFreshEntity(rocket);
        return rocket;
    }

    private static WeaponInfo weapon(String id) {
        return McheliApi.get().content().weapon(id)
                .orElseThrow(() -> new GameTestAssertException(id + " did not load"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
