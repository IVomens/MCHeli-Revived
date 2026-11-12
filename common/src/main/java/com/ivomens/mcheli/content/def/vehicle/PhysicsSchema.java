package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Physics.BodySize;
import com.ivomens.mcheli.content.def.vehicle.Physics.GroundTilt;
import com.ivomens.mcheli.content.def.vehicle.Physics.Mobility;
import com.ivomens.mcheli.content.def.vehicle.Physics.RotationLimits;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.api.vehicle.VehicleKind;

import java.util.function.Function;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;

/**
 * YAML {@code PhysicalProperties}: how the vehicle moves.
 * <pre>
 * PhysicalProperties:
 *   Speed: 0.8                # at most 0.8 for helicopters and ground vehicles, 1.8 for the others
 *   OnGroundPitch: 2.5        # nose up while standing, in degrees
 *   Mobility: { Yaw: 0.7, Pitch: 0.7, Roll: 0.7 }
 *   RotationLimits:           # being there limits pitch and roll
 *     Pitch: { Min: -20, Max: 20 }
 * </pre>
 * The speed and the pitch limits depend on the kind, see {@link KindFields}.
 */
final class PhysicsSchema {

    static final Field<Physics, Boolean> CAN_FLOAT = Field.of("CanFloat", BOOLEAN, false, Physics::canFloat)
            .txt("float");
    /** How deep the vehicle floats; kept with the sign the game uses, the opposite of the file's. */
    static final Field<Physics, Float> FLOAT_OFFSET = Field.of("FloatOffset", FLOAT.map(PhysicsSchema::negate,
            PhysicsSchema::negate), 0.0F, Physics::floatOffset).txt("floatoffset");
    static final Field<Physics, Float> MOTION_FACTOR =
            Field.of("MotionFactor", floats(0, 1), 0.96F, Physics::motionFactor).txt("motionfactor");
    static final Field<Physics, Float> GRAVITY = Field.of("Gravity", floats(-50, 50), -0.04F, Physics::gravity)
            .txt("gravity");
    static final Field<Physics, Float> ROTATION_SNAP =
            Field.of("RotationSnapValue", floats(-5, 5), -0.6F, Physics::rotationSnap).txt("autopilotrot");
    static final Field<Physics, Float> GRAVITY_IN_WATER =
            Field.of("GravityInWater", floats(-50, 50), -0.04F, Physics::gravityInWater).txt("gravityinwater");
    static final Field<Physics, Float> STEP_HEIGHT =
            Field.of("StepHeight", floats(0, 1000), 0.0F, Physics::stepHeight).txt("stepheight");
    /** Also {@code AircraftFeatures.CanRotateOnGround}. */
    static final Field<Physics, Boolean> CAN_ROT_ON_GROUND =
            Field.of("CanRotOnGround", BOOLEAN, true, Physics::canRotOnGround).txt("canrotonground");
    static final Field<Physics, Boolean> CAN_MOVE_ON_GROUND =
            Field.of("CanMoveOnGround", BOOLEAN, true, Physics::canMoveOnGround).txt("canmoveonground");
    /**
     * Nose-up pitch while standing, in degrees; kept with the sign the game uses, the opposite of the file's.
     */
    static final Field<Physics, Float> ON_GROUND_PITCH = Field.of("OnGroundPitch",
            floats(-90, 90).map(PhysicsSchema::negate, PhysicsSchema::negate), 0.0F, Physics::onGroundPitch)
            .txt("ongroundpitch");
    static final Field<Physics, Float> PIVOT_TURN_THROTTLE =
            Field.of("PivotTurnThrottle", floats(0, 1), 0.0F, Physics::pivotTurnThrottle).txt("pivotturnthrottle");

    static final Field<Mobility, Float> MOBILITY_YAW = mobility("Yaw", Mobility::yaw, "mobilityyaw");
    static final Field<Mobility, Float> MOBILITY_PITCH = mobility("Pitch", Mobility::pitch, "mobilitypitch");
    static final Field<Mobility, Float> MOBILITY_ROLL = mobility("Roll", Mobility::roll, "mobilityroll");
    static final Field<Mobility, Float> MOBILITY_YAW_ON_GROUND =
            mobility("YawOnGround", Mobility::yawOnGround, "mobilityyawonground");
    static final Section<Physics, Mobility> MOBILITY = Section.of("Mobility", Physics::mobility, MOBILITY_YAW,
            MOBILITY_PITCH, MOBILITY_ROLL, MOBILITY_YAW_ON_GROUND);

    static final Field<GroundTilt, Float> TILT_PITCH =
            Field.of("Pitch", floats(0, 180), 0.0F, GroundTilt::pitch).txt("ongroundpitchfactor");
    static final Field<GroundTilt, Float> TILT_ROLL =
            Field.of("Roll", floats(0, 180), 0.0F, GroundTilt::roll).txt("ongroundrollfactor");
    static final Section<Physics, GroundTilt> GROUND_TILT = Section.of("GroundPitchFactors", Physics::groundTilt,
            TILT_PITCH, TILT_ROLL);

    static final Field<BodySize, Float> BODY_HEIGHT =
            Field.of("Height", floats(0.1F, 1000), 0.7F, BodySize::height).txt("height");
    static final Field<BodySize, Float> BODY_WIDTH =
            Field.of("Width", floats(0.1F, 1000), 2.0F, BodySize::width).txt("width");
    static final Section<Physics, BodySize> BODY_SIZE = Section.of("BodySize", Physics::bodySize, BODY_HEIGHT,
            BODY_WIDTH);

    /** Set by being there, and by any TXT rotation limit line. */
    static final Field<RotationLimits, Boolean> LIMITED =
            Field.of("Enabled", BOOLEAN, false, RotationLimits::limited);

    private PhysicsSchema() {
    }

    static Section<VehicleFile, Physics> section(KindFields kind) {
        Section<RotationLimits, RotationLimits> pitch = Section.of("Pitch", Function.identity(), kind.minPitch(),
                kind.maxPitch());
        Section<RotationLimits, RotationLimits> roll = Section.of("Roll", Function.identity(), kind.minRoll(),
                kind.maxRoll());
        Section<Physics, RotationLimits> limits = Section.of("RotationLimits", Physics::rotationLimits, LIMITED,
                pitch, roll).enabledBy(LIMITED);
        return Section.of("PhysicalProperties", VehicleFile::physics, kind.speed(), CAN_FLOAT, FLOAT_OFFSET,
                MOTION_FACTOR, GRAVITY, ROTATION_SNAP, GRAVITY_IN_WATER, STEP_HEIGHT, CAN_ROT_ON_GROUND,
                CAN_MOVE_ON_GROUND, ON_GROUND_PITCH, PIVOT_TURN_THROTTLE, MOBILITY, GROUND_TILT, BODY_SIZE, limits);
    }

    static Physics build(Values v, KindFields kind) {
        return new Physics(v.get(kind.speed()), v.get(CAN_FLOAT), v.get(FLOAT_OFFSET), v.get(MOTION_FACTOR),
                v.get(GRAVITY), v.get(ROTATION_SNAP), v.get(GRAVITY_IN_WATER), v.get(STEP_HEIGHT),
                v.get(CAN_ROT_ON_GROUND), v.get(CAN_MOVE_ON_GROUND), v.get(ON_GROUND_PITCH),
                v.get(PIVOT_TURN_THROTTLE),
                new Mobility(v.get(MOBILITY_YAW), v.get(MOBILITY_PITCH), v.get(MOBILITY_ROLL),
                        v.get(MOBILITY_YAW_ON_GROUND)),
                new GroundTilt(v.get(TILT_PITCH), v.get(TILT_ROLL)),
                new BodySize(v.get(BODY_HEIGHT), v.get(BODY_WIDTH)),
                new RotationLimits(v.get(LIMITED), v.get(kind.minPitch()), v.get(kind.maxPitch()),
                        v.get(kind.minRoll()), v.get(kind.maxRoll())));
    }

    /**
     * Legacy TXT rotation limits: each line also switches the limits on. Ground vehicles also accept
     * {@code RotationPitchMin} and {@code RotationPitchMax}.
     */
    static void txtRules(Schema.Builder<VehicleFile> builder, KindFields kind, VehicleKind vehicleKind) {
        builder.txtRule("minrotationpitch", limit(kind.minPitch()))
                .txtRule("maxrotationpitch", limit(kind.maxPitch()))
                .txtRule("minrotationroll", limit(kind.minRoll()))
                .txtRule("maxrotationroll", limit(kind.maxRoll()));
        if (vehicleKind == VehicleKind.VEHICLE) {
            builder.txtRule("rotationpitchmin", limit(kind.minPitch()))
                    .txtRule("rotationpitchmax", limit(kind.maxPitch()));
        }
    }

    private static TxtRule limit(Field<RotationLimits, Float> field) {
        return (values, text, context) -> {
            values.set(LIMITED, true);
            values.set(field, field.codec().fromText(text, values.get(field), context));
        };
    }

    /** Exact both ways, so a written value comes back as it was, {@code -0.0} included. */
    private static float negate(float value) {
        return -value;
    }

    private static Field<Mobility, Float> mobility(String key, Function<Mobility, Float> getter, String txtKey) {
        return Field.of(key, floats(0, 100), 1.0F, getter).txt(txtKey);
    }
}
