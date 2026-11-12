package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.HitBox;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Splash;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.WheelHitBox;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtKey.TxtRule;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legacy TXT lines of the top level that set more than one value or add to a list. Lines with too few
 * values are ignored, as they always were.
 */
final class VehicleTxtRules {

    private static final int REPAIR_MAX_VALUE = 10_000_000;
    private static final int DROP_INTERVAL = 12;
    /** Values of a {@code BoundingBox} and a {@code BoundingEraBox} line. */
    private static final int BOX_VALUES = 5;
    private static final int ERA_BOX_VALUES = 11;
    private static final float MAX_ERA = 100_000.0F;

    /** {@code AddParticleSplash = x, y, z[, count[, size[, acceleration[, age[, motion[, gravity]]]]]]}. */
    static final TxtTuple<Splash> SPLASH = TxtTuple.of(ViewSchemas.SPLASH).minimum(3)
            .vector(ViewSchemas.SPLASH_POSITION)
            .value(ViewSchemas.SPLASH_COUNT).value(ViewSchemas.SPLASH_SIZE).value(ViewSchemas.SPLASH_ACCELERATION)
            .value(ViewSchemas.SPLASH_AGE).value(ViewSchemas.SPLASH_MOTION).value(ViewSchemas.SPLASH_GRAVITY);

    private VehicleTxtRules() {
    }

    static void register(Schema.Builder<VehicleFile> builder, KindFields kind) {
        builder.txtRule("adddisplayname", VehicleTxtRules::displayName)
                .txtRule("addparticlesplash", SPLASH.appendingTo(ViewSchemas.SPLASHES))
                .txtRule("addtexture", (values, text, context) ->
                        values.append(ViewSchemas.TEXTURES, text.toLowerCase(Locale.ROOT)))
                .txtRule("addrecipe", (values, text, context) -> recipe(values, text, true))
                .txtRule("addshapelessrecipe", (values, text, context) -> recipe(values, text, false))
                .txtRule("repairothervehicles", VehicleTxtRules::repair)
                .txtRule("turretposition", vector(VehicleSchema.TURRET_POSITION))
                .txtRule("unmountposition", vector(VehicleSchema.UNMOUNT_POSITION))
                .txtRule("flareoption", vector(FeaturesSchema.FLARE_POSITION))
                .txtRule("mobdropoption", VehicleTxtRules::mobDrop)
                .txtRule("uav", (values, text, context) -> uav(values, text, false))
                .txtRule("smalluav", (values, text, context) -> uav(values, text, true))
                .txtRule("setwheelpos", (values, text, context) -> wheels(values, text, kind))
                .txtRule("boundingbox", VehicleTxtRules::boundingBox)
                .txtRule("boundingerabox", VehicleTxtRules::reactiveArmorBox);
    }

    /** {@code AddDisplayName = language, name}: the name in one language. */
    private static void displayName(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length == 2) {
            values.set(VehicleSchema.DISPLAY_NAME, values.get(VehicleSchema.DISPLAY_NAME)
                    .with(parts[0].toLowerCase(Locale.ROOT).trim(), parts[1].trim()));
        }
    }

    /** {@code AddRecipe} and {@code AddShapelessRecipe} add a pattern line; the last one decides the shape. */
    private static void recipe(Values values, String text, boolean shaped) {
        values.set(VehicleSchema.RECIPE_SHAPED, shaped);
        values.append(VehicleSchema.RECIPE_PATTERN, text.toUpperCase(Locale.ROOT));
    }

    /** {@code RepairOtherVehicles = range[, value]}. */
    private static void repair(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        values.set(VehicleSchema.REPAIR_RANGE, VehicleSchema.REPAIR_RANGE.codec().fromText(parts[0], 0.0F,
                context));
        if (parts.length >= 2) {
            values.set(VehicleSchema.REPAIR_VALUE, Codecs.clamp(Codecs.parseInt(parts[1]), 0, REPAIR_MAX_VALUE,
                    context));
        }
    }

    /** A line of three or more values that sets a position from the first three. */
    private static TxtRule vector(Field<?, Vec3> field) {
        return (values, text, context) -> {
            String[] parts = Codecs.splitTxt(text);
            if (parts.length >= 3) {
                values.set(field, Vectors.VEC3.fromText(PartTxtRules.join(parts, 0), null, context));
            }
        };
    }

    /** {@code MobDropOption = x, y, z[, interval]}: where the crew jumps out, every 12 ticks unless given. */
    private static void mobDrop(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length >= 3) {
            values.set(FeaturesSchema.DROP_POSITION, Vectors.VEC3.fromText(PartTxtRules.join(parts, 0), null,
                    context));
            values.set(FeaturesSchema.DROP_INTERVAL, parts.length >= 4 ? Codecs.parseInt(parts[3]) : DROP_INTERVAL);
        }
    }

    /** {@code UAV = true} makes a full-size UAV, {@code SmallUAV = true} a small one. */
    private static void uav(Values values, String text, boolean small) {
        values.set(ViewSchemas.UAV, text.equalsIgnoreCase("true"));
        values.set(ViewSchemas.SMALL_UAV, small);
    }

    /**
     * {@code SetWheelPos = x, y, z, z, ...}: wheels on both sides, {@code x} out from the middle, at each
     * {@code z}.
     */
    private static void wheels(Values values, String text, KindFields kind) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < 4) {
            return;
        }
        float x = Math.abs(Codecs.parseFloat(parts[0]));
        float y = Codecs.parseFloat(parts[1]);
        List<WheelHitBox> wheels = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            wheels.add(new WheelHitBox(new Vec3(x, y, Codecs.parseFloat(parts[i])), 1.0F));
        }
        values.set(kind.wheels(), List.copyOf(wheels));
    }

    /** {@code BoundingBox = x, y, z, width, height[, damageFactor]}: a box as deep as it is wide. */
    private static void boundingBox(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < BOX_VALUES) {
            return;
        }
        float width = Codecs.parseFloat(parts[3]);
        float damageFactor = parts.length >= 6 ? Codecs.parseFloat(parts[5]) : 1.0F;
        values.append(HitBoxSchema.HIT_BOXES, new HitBox(HitBoxType.DEFAULT,
                Vectors.TXT_FLOATS.fromText(PartTxtRules.join(parts, 0), null, context),
                new Vec3(width, Codecs.parseFloat(parts[4]), width), damageFactor, "", false, 0.0F, 0.0F));
    }

    /**
     * {@code BoundingEraBox = x, y, z, width, height, depth, damageFactor, explosion, type, name, minDamage}: an
     * explosive reactive armour tile; a type it does not know is {@code DEFAULT}.
     */
    private static void reactiveArmorBox(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length < ERA_BOX_VALUES) {
            return;
        }
        values.append(HitBoxSchema.HIT_BOXES, new HitBox(HitBoxSchema.BOX_TYPE.fromText(parts[8], null, context),
                Vectors.TXT_FLOATS.fromText(PartTxtRules.join(parts, 0), null, context),
                Vectors.TXT_FLOATS.fromText(PartTxtRules.join(parts, 3), null, context),
                Codecs.parseFloat(parts[6]), parts[9],
                true, Codecs.clamp(Codecs.parseFloat(parts[7]), 0.0F, MAX_ERA, context),
                Codecs.clamp(Codecs.parseFloat(parts[10]), 0.0F, MAX_ERA, context)));
    }
}
