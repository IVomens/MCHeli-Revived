package com.ivomens.mcheli.combat.guidance;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * The entities of other mods that MCHeli treats like its own: their vehicles can be locked on to, their
 * bullets and grenades shot down by an APS, and their camera entities never targeted. Those mods are
 * not dependencies, so they are recognised by a fragment of the class name; this is the one place that
 * lists them.
 * <p>
 * A class's answers are worked out once and remembered for as long as the class is loaded.
 */
public final class ForeignVehicles {

    private static final List<String> VEHICLES = List.of("EntityVehicle", "EntityPlane", "EntityMecha", "EntityAAGun");
    private static final List<String> PROJECTILES = List.of("EntityBullet", "EntityBaseBullet", "EntityGrenade");
    private static final String GRENADE = "EntityGrenade";
    private static final String CAMERA = "EntityCamera";

    private static final ClassValue<Kind> KINDS = new ClassValue<>() {
        @Override
        protected Kind computeValue(Class<?> type) {
            return Kind.of(type.getName());
        }
    };

    private ForeignVehicles() {
    }

    /** A vehicle of another mod that seekers may lock on to. */
    public static boolean isVehicle(Entity entity) {
        return KINDS.get(entity.getClass()).vehicle;
    }

    /** A bullet or grenade of another mod that an APS shoots down. */
    public static boolean isProjectile(Entity entity) {
        return KINDS.get(entity.getClass()).projectile;
    }

    /** A grenade of another mod, which goes off when an APS hits it. */
    public static boolean isGrenade(Entity entity) {
        return KINDS.get(entity.getClass()).grenade;
    }

    /** The camera entity of another mod, never a target. */
    public static boolean isCamera(Entity entity) {
        return KINDS.get(entity.getClass()).camera;
    }

    private record Kind(boolean vehicle, boolean projectile, boolean grenade, boolean camera) {

        static Kind of(String className) {
            return new Kind(containsAny(className, VEHICLES), containsAny(className, PROJECTILES),
                    className.contains(GRENADE), className.contains(CAMERA));
        }

        private static boolean containsAny(String className, List<String> fragments) {
            for (String fragment : fragments) {
                if (className.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }
}
