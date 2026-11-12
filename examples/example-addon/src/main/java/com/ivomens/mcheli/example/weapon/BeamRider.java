package com.ivomens.mcheli.example.weapon;

import com.ivomens.mcheli.api.weapon.GuidanceContext;
import com.ivomens.mcheli.api.weapon.GuidanceLaw;
import com.ivomens.mcheli.api.weapon.SteeringCommand;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Beam riding: the missile flies along the gunner's line of sight, as if riding a laser beam from the gunner's eye.
 * Each tick it steers at the point of the beam {@code Lead} blocks ahead of itself, so it closes onto the beam and
 * then follows it wherever the gunner looks. It needs no lock and ignores flares and chaff; once the gunner is gone
 * or looks more than {@code MaxRange} blocks along, it flies on straight.
 *
 * <pre>
 * Guidance:
 *   Id: mcheli_example:beam_rider
 *   Lead: 8
 *   MaxRange: 400
 * </pre>
 *
 * @param lead     how far ahead of the missile along the beam it aims, in blocks
 * @param maxRange how far along the beam the guidance reaches, in blocks
 */
public record BeamRider(double lead, double maxRange) implements GuidanceLaw {

    /** The settings, decoded with an ordinary {@code Codec} from the tag MCHeli hands the factory. */
    static final Codec<BeamRider> SETTINGS = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.5, 64.0).optionalFieldOf("Lead", 8.0).forGetter(BeamRider::lead),
            Codec.doubleRange(1.0, 2000.0).optionalFieldOf("MaxRange", 400.0).forGetter(BeamRider::maxRange)
    ).apply(instance, BeamRider::new));

    static BeamRider fromSettings(CompoundTag settings) {
        return SETTINGS.parse(NbtOps.INSTANCE, settings).getOrThrow(IllegalArgumentException::new);
    }

    @Override
    public SteeringCommand steer(GuidanceContext missile) {
        Entity gunner = missile.shooter();
        if (!(gunner instanceof LivingEntity living) || !living.isAlive()) {
            return SteeringCommand.keepCourse();
        }
        Vec3 eye = living.getEyePosition();
        Vec3 beam = living.getLookAngle();
        // how far along the beam the missile is, then a little further
        double along = missile.position().subtract(eye).dot(beam) + this.lead;
        if (along > this.maxRange) {
            return SteeringCommand.keepCourse();
        }
        return SteeringCommand.steerTowards(eye.add(beam.scale(Math.max(along, this.lead))));
    }

    /** A beam rider has no seeker to fool: it keeps riding the beam and simply forgets any target. */
    @Override
    public SteeringCommand decoyed(GuidanceContext missile) {
        missile.setTarget(null);
        return this.steer(missile);
    }
}
