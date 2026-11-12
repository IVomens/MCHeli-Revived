package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.core.content.schema.Ref;
import net.minecraft.world.phys.Vec3;

/**
 * The smaller list entries of a vehicle file: camera positions, weapon mounts, hit boxes, wheel hit boxes and
 * splash particles.
 */
public final class CrewEntries {

    private CrewEntries() {
    }

    /**
     * A camera position: an entry of {@code Camera.Pos}, or the {@code Camera} of a seat or rack.
     *
     * @param position      where the camera sits, above the vehicle's model origin
     * @param fixedRotation the camera looks along {@code yaw} and {@code pitch}
     */
    public record CameraSpec(Vec3 position, boolean fixedRotation, float yaw, float pitch) {
    }

    /**
     * One entry of YAML {@code Weapons}: a weapon of the given type, mounted on the vehicle. Mounts of one type
     * that follow each other form one weapon the crew selects together.
     *
     * @param weapon     the weapon, by its file name in the weapons folder
     * @param position   where it fires from, above the vehicle's model origin
     * @param seat       the seat that fires it, 0 for the pilot
     * @param pilotCanUse the pilot may fire it from the pilot seat as well
     * @param defaultYaw the turret's resting yaw, from -180 to 180
     */
    public record Mount(Ref<WeaponDefinition> weapon, Vec3 position, float yaw, float pitch, boolean pilotCanUse,
                        int seat,
                        float defaultYaw, float minYaw, float maxYaw, float minPitch, float maxPitch,
                        boolean turret) {
    }

    /**
     * One entry of YAML {@code BoundingBoxes}: an extra hit box.
     *
     * @param size          width, height and depth
     * @param reactiveArmor an explosive reactive armour tile that pops on a hit
     * @param eraExplosion  the explosion when the tile pops
     * @param eraMinDamage  the damage that makes it pop
     */
    public record HitBox(HitBoxType type, Vec3 position, Vec3 size, float damageFactor, String name,
                         boolean reactiveArmor, float eraExplosion, float eraMinDamage) {
    }

    /** One entry of YAML {@code Wheels.Hitboxes}: where the vehicle touches the ground. */
    public record WheelHitBox(Vec3 position, float scale) {
    }

    /** One entry of YAML {@code Render.SplashParticles}: spray thrown up on water. */
    public record Splash(Vec3 position, int count, float size, float acceleration, int age, float motion,
                         float gravity) {
    }
}
