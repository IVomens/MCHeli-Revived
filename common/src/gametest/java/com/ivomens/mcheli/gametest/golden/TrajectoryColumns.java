package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.Parts;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import com.ivomens.mcheli.tank.TankEntity;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Function;

/**
 * The columns of a golden trajectory, read from the vehicle after each level tick. Doubles and
 * floats are written with {@code Double.toString} / {@code Float.toString}, so they read back to the
 * same bits; a value a vehicle type does not have is written as an empty cell.
 */
final class TrajectoryColumns {

    record Column(String name, Function<AircraftEntity, String> value) {
    }

    /** Positions are written relative to the course origin, so both courses share one format. */
    static List<Column> all(BlockPos origin) {
        return List.of(
                new Column("x", v -> d(v.getX() - origin.getX())),
                new Column("y", v -> d(v.getY() - origin.getY())),
                new Column("z", v -> d(v.getZ() - origin.getZ())),
                new Column("vx", v -> d(v.getDeltaMovement().x)),
                new Column("vy", v -> d(v.getDeltaMovement().y)),
                new Column("vz", v -> d(v.getDeltaMovement().z)),
                new Column("yaw", v -> f(v.getYRot())),
                new Column("pitch", v -> f(v.getXRot())),
                new Column("roll", v -> f(v.roll())),
                new Column("throttle", v -> d(v.engine().throttle())),
                new Column("syncedThrottle", v -> d(v.engine().syncedThrottle())),
                new Column("speed", v -> d(v.physics().cruiseSpeed())),
                new Column("onGround", v -> b(v.onGround())),
                new Column("horizontalCollision", v -> b(v.horizontalCollision)),
                new Column("inWater", v -> b(v.isInWater())),
                new Column("waterDepth", v -> d(v.getWaterDepth())),
                new Column("gear", v -> part(v.parts().landingGear())),
                new Column("canopy", v -> part(v.parts().canopy())),
                new Column("hatch", v -> part(v.parts().hatch())),
                new Column("nozzle", v -> v instanceof AbstractPlaneEntity plane ? part(plane.nozzles().part()) : ""),
                new Column("wing", v -> v instanceof AbstractPlaneEntity plane ? part(plane.wings().part()) : ""),
                new Column("bladesFolded", v -> v instanceof HelicopterEntity heli ? b(heli.blades().isFolded()) : ""),
                new Column("rotor", TrajectoryColumns::rotor),
                // tanks never built up a key steering impulse; the column stays for the file format
                new Column("tankSteer", v -> v instanceof TankEntity ? f(0.0F) : ""),
                new Column("terrainPitch", v -> v instanceof TankEntity tank ? f(tank.wheels().targetPitch()) : ""),
                new Column("terrainRoll", v -> v instanceof TankEntity tank ? f(tank.wheels().targetRoll()) : ""),
                new Column("crewed", v -> b(v.getRiddenByEntity() != null)),
                new Column("resting", v -> b(v.rest().isResting())),
                new Column("hp", v -> Integer.toString(v.damage().hp())),
                new Column("fuel", v -> Integer.toString(v.fuel().amount())),
                new Column("destroyed", v -> b(v.isDestroyed())),
                new Column("removed", v -> b(v.isRemoved())));
    }

    static String header(List<Column> columns) {
        StringBuilder line = new StringBuilder("tick");
        for (Column column : columns) {
            line.append(',').append(column.name());
        }
        return line.toString();
    }

    static String row(int tick, AircraftEntity vehicle, List<Column> columns) {
        StringBuilder line = new StringBuilder(Integer.toString(tick));
        for (Column column : columns) {
            line.append(',').append(column.value().apply(vehicle));
        }
        return line.toString();
    }

    /** A part as "status:rotation", e.g. {@code 1:42.5}; rotation alone would hide the switch tick. */
    private static String part(Parts part) {
        return part == null ? "" : b(part.getStatus()) + ":" + f(part.rotation());
    }

    private static String rotor(AircraftEntity vehicle) {
        return switch (vehicle) {
            case HelicopterEntity heli -> d(heli.blades().angle());
            case AbstractPlaneEntity plane -> f(plane.propeller().angle());
            case TankEntity tank -> f(tank.propeller().angle());
            default -> "";
        };
    }

    private static String d(double value) {
        return Double.toString(value);
    }

    private static String f(float value) {
        return Float.toString(value);
    }

    private static String b(boolean value) {
        return value ? "1" : "0";
    }
}
