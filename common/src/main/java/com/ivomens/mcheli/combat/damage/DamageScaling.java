package com.ivomens.mcheli.combat.damage;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.config.DamageFactors;
import com.ivomens.mcheli.config.McheliConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The config's damage factors applied to a hit. Each table is picked by the kind of entity hit; its
 * entries match parts of the class names of the entities involved.
 */
public final class DamageScaling {

    /** {@code Class.toString()} of entity classes, cached because damage lookups run on every hit. */
    private static final ClassValue<String> CLASS_NAMES = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return type.toString();
        }
    };

    /** Damage dealt to an MCHeli vehicle by something else, scaled by the {@code Damage*ByExternal} lines. */
    public static float byExternal(Entity target, DamageSource source, float damage) {
        DamageFactors.Table table = switch (kindOf(target)) {
            case HELICOPTER, PLANE -> DamageFactors.Table.AIRCRAFT_BY_EXTERNAL;
            case TANK -> DamageFactors.Table.TANK_BY_EXTERNAL;
            case VEHICLE -> DamageFactors.Table.VEHICLE_BY_EXTERNAL;
            case null, default -> DamageFactors.Table.OTHER_BY_EXTERNAL;
        };
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        return McheliConfig.combat().damage().apply(table, damage,
                name -> classNameContains(attacker, name) || classNameContains(direct, name));
    }

    /** Damage MCHeli weapons deal to an entity, scaled by the {@code DamageVs*} lines. */
    public static float vsEntity(Entity target, DamageSource source, float damage) {
        if (target == null) {
            return damage;
        }
        String targetName = CLASS_NAMES.get(target.getClass());
        DamageFactors.Table table;
        VehicleKind kind = kindOf(target);
        if (kind == VehicleKind.HELICOPTER || kind == VehicleKind.PLANE) {
            table = DamageFactors.Table.VS_AIRCRAFT;
        } else if (kind == VehicleKind.TANK) {
            table = DamageFactors.Table.VS_TANK;
        } else if (kind == VehicleKind.VEHICLE) {
            table = DamageFactors.Table.VS_VEHICLE;
        } else if (targetName.indexOf("com.ivomens.mcheli.") > 0) {
            table = DamageFactors.Table.VS_OTHER;
        } else if (target instanceof Player) {
            table = DamageFactors.Table.VS_PLAYER;
        } else if (target instanceof LivingEntity) {
            table = DamageFactors.Table.VS_LIVING;
        } else {
            table = DamageFactors.Table.VS_ENTITY;
        }
        return McheliConfig.combat().damage().apply(table, damage, name -> targetName.indexOf(name) > 0);
    }

    private static boolean classNameContains(@Nullable Entity entity, String name) {
        return entity != null && CLASS_NAMES.get(entity.getClass()).indexOf(name) > 0;
    }

    private DamageScaling() {
    }

    /** The kind of vehicle the entity is, or null for anything that is not a vehicle. */
    @Nullable
    private static VehicleKind kindOf(@Nullable Entity entity) {
        return entity instanceof Vehicle vehicle ? vehicle.kind() : null;
    }
}
