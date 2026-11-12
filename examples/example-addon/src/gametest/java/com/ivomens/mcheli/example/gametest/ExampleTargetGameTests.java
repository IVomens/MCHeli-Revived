package com.ivomens.mcheli.example.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.api.wingman.WingmanAttack;
import com.ivomens.mcheli.api.wingman.WingmanOrderResult;
import com.ivomens.mcheli.api.wingman.WingmanState;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.example.ExampleAddon;
import com.ivomens.mcheli.example.ExampleTargets;
import com.ivomens.mcheli.example.ExampleWingmen;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The addon's target drones ({@link ExampleTargets}) and wingman orders ({@link ExampleWingmen}), seen from
 * MCHeli: a radar missile's seeker locks a tagged armor stand and the drone hears of the lock and of the missile,
 * its chaff hides it from radar, and a wingman sent after it takes the order.
 */
@GameTestHolder(ExampleAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleTargetGameTests {

    private static final String RADAR_MISSILE = "aim120";
    private static final String HELICOPTER = "ah-64";

    private ExampleTargetGameTests() {
    }

    /** A radar missile's seeker locks the drone, the drone hears of the lock and the missile, and chaff hides it. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void radarMissileLocksTheDrone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        Vec3 standing = h.absoluteVec(new Vec3(8, 2, 2));
        pilot.moveTo(standing.x, standing.y, standing.z, 0.0F, 0.0F);
        ArmorStand drone = drone(level, h.absoluteVec(new Vec3(8, 12, 14)));
        try {
            check(McheliApi.get().targets().signatureOf(drone).isPresent(), "the drone is no target");
            LockOnSeeker seeker = radarSeeker(level);
            check(Math.abs(seeker.filter().stealthFor(drone) - 0.4F) < 1.0E-4F, "the drone's radar return");
            long locks = ExampleTargets.locksHeard();
            aim(pilot, drone);
            for (int tick = 0; tick < seeker.lockTicks() + 5 && !seeker.isLockComplete(); tick++) {
                seeker.track(pilot);
            }
            check(seeker.isLockComplete() && seeker.target() == drone, "the seeker did not lock the drone: count "
                    + seeker.lockCount() + " of " + seeker.lockTicks() + ", target " + seeker.target());
            check(ExampleTargets.locksHeard() == locks + 1, "the drone did not hear of the lock");

            long missiles = ExampleTargets.missilesHeard();
            ProjectileEntity missile = new ProjectileEntity(ProjectileKind.AA_MISSILE.entityType(), level);
            missile.setWeapon(RADAR_MISSILE);
            missile.setShooter(pilot, pilot);
            missile.setTarget(drone);
            check(ExampleTargets.missilesHeard() == missiles + 1, "the drone did not hear of the missile");
            missile.setTarget(null);
            missile.discard();

            drone.addTag(ExampleTargets.CHAFF_TAG);
            check(!radarSeeker(level).canLock(drone, pilot), "the drone's chaff did not hide it from radar");
            h.succeed();
        } finally {
            drone.discard();
            pilot.discard();
        }
    }

    /** A player flying a helicopter takes another as wingman and sends it after the drone. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void wingmanAttacksTheDrone(GameTestHelper h) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        AircraftEntity lead = helicopter(level, h.absoluteVec(new Vec3(4, 1.2, 8)));
        AircraftEntity wing = helicopter(level, h.absoluteVec(new Vec3(12, 1.2, 8)));
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        ArmorStand drone = drone(level, h.absoluteVec(new Vec3(8, 12, 14)));
        UUID id = wing.getUUID();
        try {
            check(pilot.startRiding(lead, true), "the pilot could not board");
            WingmanOrderResult follow = McheliApi.get().wingmen().follow(pilot, wing);
            check(follow == WingmanOrderResult.DONE, "follow: " + follow);
            check(ExampleWingmen.attackWithFormation(pilot, drone) == 1, "the wingman did not take the order");
            WingmanState state = McheliApi.get().wingmen().get(level.getServer(), id).orElseThrow();
            check(state.attack() == WingmanAttack.TARGET && drone.getUUID().equals(state.attackTarget()),
                    "the wingman reads " + state.attack() + " " + state.attackTarget());
            check(McheliApi.get().wingmen().release(pilot, id) == WingmanOrderResult.DONE, "release");
            h.succeed();
        } finally {
            McheliApi.get().wingmen().release(pilot, id);
            pilot.stopRiding();
            lead.discard();
            wing.discard();
            drone.discard();
            pilot.discard();
        }
    }

    private static ArmorStand drone(ServerLevel level, Vec3 at) {
        ArmorStand drone = new ArmorStand(EntityType.ARMOR_STAND, level);
        drone.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setNoGravity(true);
        drone.addTag(ExampleTargets.DRONE_TAG);
        level.addFreshEntity(drone);
        return drone;
    }

    /** The seeker of the bundled radar missile, flagged as radar guided whatever its file says. */
    private static LockOnSeeker radarSeeker(ServerLevel level) {
        WeaponMount mount = WeaponMount.create(level, RADAR_MISSILE, Vec3.ZERO, 0.0F, 0.0F, false, null);
        LockOnSeeker seeker = mount != null ? mount.seeker() : null;
        if (seeker == null) {
            throw new GameTestAssertException(RADAR_MISSILE + " is no seeker weapon");
        }
        return seeker.withFilter(seeker.filter().withHeatSeeking(false).withRadar(true));
    }

    private static AircraftEntity helicopter(ServerLevel level, Vec3 at) {
        if (!(VehicleTypes.get(VehicleKind.HELICOPTER, HELICOPTER).flatMap(VehicleTypes::item).orElse(null)
                instanceof AircraftItem item)) {
            throw new GameTestAssertException(HELICOPTER + " has no item");
        }
        AircraftEntity aircraft = item.createAircraft(level, at.x, at.y, at.z, new ItemStack(item));
        level.addFreshEntity(aircraft);
        return aircraft;
    }

    private static void aim(Entity shooter, Entity target) {
        double dx = target.getX() - shooter.getX();
        double dy = target.getY() + target.getBbHeight() / 2.0F - shooter.getY();
        double dz = target.getZ() - shooter.getZ();
        shooter.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        shooter.setXRot(-(float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
