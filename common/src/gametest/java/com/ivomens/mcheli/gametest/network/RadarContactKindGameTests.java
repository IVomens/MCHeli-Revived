package com.ivomens.mcheli.gametest.network;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.radar.EntityInfo;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server tells the radar instruments each entity is: every bundled vehicle (drones and vertical
 * take-off planes included) is the kind of vehicle it is built as, every projectile kind, the decoys,
 * and anything else.
 */
public final class RadarContactKindGameTests {

    private static final String BATCH = "mcheli_network";

    private RadarContactKindGameTests() {
    }

    public static List<TestFunction> kinds() {
        return List.of(new TestFunction(BATCH, "radar_contact_kinds", "mcheli:empty", 20, 0, true,
                RadarContactKindGameTests::radarContactKinds));
    }

    private static void radarContactKinds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        List<String> failures = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            expectVehicles(level, kind, failures);
        }
        for (ProjectileKind kind : ProjectileKind.values()) {
            expect(new ProjectileEntity(kind.entityType(), level), projectileKind(kind), kind.name(), failures);
        }
        expect(CombatEntityTypes.CHAFF.get().create(level), RadarContactKind.CHAFF, "chaff", failures);
        expect(CombatEntityTypes.FLARE.get().create(level), RadarContactKind.FLARE, "flare", failures);
        expect(EntityType.PIG.create(level), RadarContactKind.OTHER, "pig", failures);
        if (failures.isEmpty()) {
            h.succeed();
        } else {
            h.fail(String.join("; ", failures));
        }
    }

    private static void expectVehicles(ServerLevel level, VehicleKind kind, List<String> failures) {
        int checked = 0;
        for (VehicleSpec spec : VehicleTypes.registry(kind).values()) {
            if (VehicleTypes.item(spec).orElse(null) instanceof AircraftItem item) {
                AircraftEntity vehicle = item.createAircraft(level, 0, 0, 0, new ItemStack(item));
                if (vehicle != null) {
                    expect(vehicle, vehicleKind(kind), kind + " " + spec.name(), failures);
                    checked++;
                }
            }
        }
        if (checked == 0) {
            failures.add("no bundled " + kind + " to check");
        }
    }

    private static RadarContactKind vehicleKind(VehicleKind kind) {
        return switch (kind) {
            case HELICOPTER -> RadarContactKind.HELICOPTER;
            case PLANE -> RadarContactKind.PLANE;
            case SHIP -> RadarContactKind.SHIP;
            case TANK -> RadarContactKind.TANK;
            case VEHICLE -> RadarContactKind.GROUND_VEHICLE;
        };
    }

    private static RadarContactKind projectileKind(ProjectileKind kind) {
        return switch (kind) {
            case AA_MISSILE -> RadarContactKind.AIR_TO_AIR_MISSILE;
            case AS_MISSILE, AT_MISSILE, TV_MISSILE -> RadarContactKind.OTHER_MISSILE;
            case BULLET, ROCKET, BOMB, TORPEDO, MARKER_ROCKET, DISPENSED_ITEM -> RadarContactKind.OTHER;
        };
    }

    private static void expect(Entity entity, RadarContactKind expected, String what, List<String> failures) {
        RadarContactKind actual = EntityInfo.kindOf(entity);
        if (actual != expected) {
            failures.add(what + " is " + actual + ", expected " + expected);
        }
        if (EntityInfo.of(entity).kind() != actual) {
            failures.add(what + ": the snapshot's kind is not the entity's");
        }
    }
}
