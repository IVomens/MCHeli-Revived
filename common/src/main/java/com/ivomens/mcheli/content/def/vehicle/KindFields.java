package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.WheelHitBox;
import com.ivomens.mcheli.content.def.vehicle.Physics.RotationLimits;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.CameraSettings;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Sound;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Wheels;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * The settings whose default or range depends on the vehicle kind. {@link VehicleSchema#forKind} declares
 * them once per kind and places them in their sections next to the shared settings.
 *
 * @param speed     {@code PhysicalProperties.Speed}, at most the kind's top speed
 * @param minPitch  {@code PhysicalProperties.RotationLimits.Pitch.Min}
 * @param maxPitch  {@code PhysicalProperties.RotationLimits.Pitch.Max}
 * @param minRoll   {@code PhysicalProperties.RotationLimits.Roll.Min}
 * @param maxRoll   {@code PhysicalProperties.RotationLimits.Roll.Max}
 * @param zoom      {@code Camera.Zoom}
 * @param soundRange {@code Sound.Range}
 * @param wheels    {@code Wheels.Hitboxes}
 */
record KindFields(KindProfile profile, Field<Physics, Float> speed, Field<RotationLimits, Float> minPitch,
                  Field<RotationLimits, Float> maxPitch, Field<RotationLimits, Float> minRoll,
                  Field<RotationLimits, Float> maxRoll, Field<CameraSettings, Integer> zoom,
                  Field<Sound, Float> soundRange, Field<Wheels, List<WheelHitBox>> wheels) {

    static KindFields of(VehicleKind kind) {
        KindProfile profile = KindProfile.of(kind);
        return new KindFields(profile,
                Field.of("Speed", floats(0.0F, profile.maxSpeed()), 0.1F, Physics::speed).txt("speed"),
                Field.of("Min", floats(profile.minPitch(), 0.0F), profile.minPitch(), RotationLimits::minPitch),
                Field.of("Max", floats(0.0F, profile.maxPitch()), profile.maxPitch(), RotationLimits::maxPitch),
                Field.of("Min", floats(KindProfile.MIN_ROLL, 0.0F), profile.minPitch(), RotationLimits::minRoll),
                Field.of("Max", floats(0.0F, KindProfile.MAX_ROLL), profile.maxPitch(), RotationLimits::maxRoll),
                Field.of("Zoom", ints(1, 10), profile.zoom(), CameraSettings::zoom).alias("CameraZoom")
                        .txt("camerazoom"),
                Field.of("Range", floats(1.0F, 1000.0F), profile.soundRange(), Sound::range).txt("soundrange"),
                Field.listOf("Hitboxes", () -> HitBoxSchema.WHEEL_HIT_BOX, profile.wheels(), Wheels::hitBoxes));
    }
}
