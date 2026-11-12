package com.ivomens.mcheli.core.content.def.throwable;

import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition.Recipe;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition.Smoke;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition.Sound;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition.Velocity;
import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.LocalizedName;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;
import java.util.Locale;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * The throwable file format, top to bottom in the order the emitter writes it. Each setting names its YAML
 * key, its legacy TXT key, its range and its default:
 * <pre>
 * Author: EMB4
 * DisplayName: { ja_JP: ..., DEFAULT: M18 smoke (Green) }
 * TimeFuse: 40          # ticks; TXT gives seconds
 * Smoke:
 *   Count: 6
 *   Color: '#FF14C814'
 *   Velocity: { Vertical: 0.7, Horizontal: 0.7 }
 * </pre>
 * TXT gives the three times in seconds; they become ticks once the file is read, including their defaults
 * (a TXT grenade without {@code AliveTime} lives 10 seconds, a YAML one 10 ticks).
 */
public final class ThrowableSchema {

    private static final int TICKS_PER_SECOND = 20;

    static final Field<ThrowableDefinition, String> AUTHOR =
            Field.of("Author", Codecs.AUTHOR, "", ThrowableDefinition::author);
    static final Field<ThrowableDefinition, LocalizedName> DISPLAY_NAME =
            Field.named("DisplayName", LocalizedName.CODEC, LocalizedName::of, ThrowableDefinition::displayName)
                    .txt("displayname");
    /** Numeric item ids only matter for very old packs. */
    static final Field<ThrowableDefinition, Integer> ITEM_ID =
            Field.of("ItemID", Codecs.STRICT_INT, 0, ThrowableDefinition::itemId).txt(ints(0, 65535), "itemid");
    static final Field<ThrowableDefinition, Integer> STACK_SIZE =
            Field.of("StackSize", ints(1, 64), 1, ThrowableDefinition::stackSize).txt("stacksize");

    static final Field<Sound, Float> SOUND_VOLUME =
            Field.of("Volume", floats(0, 1000), 1.0F, Sound::volume).alias("Vol").txt("soundvolume");
    static final Field<Sound, Float> SOUND_PITCH =
            Field.of("Pitch", floats(0, 1), 1.0F, Sound::pitch).txt("soundpitch");
    static final Section<ThrowableDefinition, Sound> SOUND =
            Section.of("Sound", ThrowableDefinition::sound, SOUND_VOLUME, SOUND_PITCH);

    static final Field<Recipe, Boolean> RECIPE_SHAPED = Field.of("isShaped", BOOLEAN, true, Recipe::shaped);
    static final Field<Recipe, List<String>> RECIPE_PATTERN =
            Field.of("Pattern", recipeLines(), List.of(), Recipe::pattern);
    static final Section<ThrowableDefinition, Recipe> RECIPE =
            Section.of("Recipe", ThrowableDefinition::recipe, RECIPE_SHAPED, RECIPE_PATTERN).alias("Recepie");

    static final Field<ThrowableDefinition, Integer> POWER =
            Field.of("Power", ints(0, Integer.MAX_VALUE), 0, ThrowableDefinition::power).txt(Codecs.INT, "power");
    static final Field<ThrowableDefinition, Float> ACCELERATION =
            Field.of("Acceleration", floats(0, 100), 1.0F, ThrowableDefinition::acceleration).txt("acceleration");
    static final Field<ThrowableDefinition, Float> ACCELERATION_IN_WATER =
            Field.of("AccelerationInWater", floats(0, 100), 1.0F, ThrowableDefinition::accelerationInWater)
                    .txt("accelerationinwater");
    /** Throwing speed when a dispenser throws it. */
    static final Field<ThrowableDefinition, Float> DISPENSE_ACCELERATION =
            Field.of("DispenseAcceleration", floats(0, 1000), 1.0F, ThrowableDefinition::dispenseAcceleration)
                    .txt("dispenseacceleration");
    static final Field<ThrowableDefinition, Integer> EXPLOSION =
            Field.of("Explosion", ints(0, 50), 0, ThrowableDefinition::explosion).txt("explosion");
    /** Ticks between the first impact and the explosion. */
    static final Field<ThrowableDefinition, Integer> DELAY_FUSE =
            Field.of("DelayFuse", ints(0, 100000), 0, ThrowableDefinition::delayFuse).txt("delayfuse")
                    .afterTxt(ThrowableSchema::secondsToTicks);
    static final Field<ThrowableDefinition, Float> BOUND =
            Field.of("Bound", floats(0, 100000), 0.2F, ThrowableDefinition::bound).txt("bound");
    /** Ticks from the throw to the explosion; 0 waits for an impact. */
    static final Field<ThrowableDefinition, Integer> TIME_FUSE =
            Field.of("TimeFuse", ints(0, 100000), 0, ThrowableDefinition::timeFuse).txt("timefuse")
                    .afterTxt(ThrowableSchema::secondsToTicks);
    static final Field<ThrowableDefinition, Boolean> FLAMING =
            Field.of("Flaming", BOOLEAN, false, ThrowableDefinition::flaming).txt("flaming");
    static final Field<ThrowableDefinition, Float> PROXIMITY_FUSE_DIST =
            Field.of("ProximityFuseDist", floats(0, 20), 0.0F, ThrowableDefinition::proximityFuseDist)
                    .txt("proximityfusedist");
    static final Field<ThrowableDefinition, Float> ACCURACY =
            Field.of("Accuracy", floats(0, 1000), 0.0F, ThrowableDefinition::accuracy).txt("accuracy");
    /** Ticks the grenade (or its smoke) exists. */
    static final Field<ThrowableDefinition, Integer> ALIVE_TIME =
            Field.of("AliveTime", ints(0, 1_000_000), 10, ThrowableDefinition::aliveTime).txt("alivetime")
                    .afterTxt(ThrowableSchema::secondsToTicks);
    static final Field<ThrowableDefinition, Integer> BOMBLET =
            Field.of("Bomblet", ints(0, 1000), 0, ThrowableDefinition::bomblet).txt("bomblet");
    static final Field<ThrowableDefinition, Float> BOMBLET_SPREAD =
            Field.of("BombletSpread", floats(0, 1000), 0.3F, ThrowableDefinition::bombletSpread).txt("bombletdiff");
    static final Field<ThrowableDefinition, Float> GRAVITY =
            Field.of("Gravity", floats(-50, 50), 0.0F, ThrowableDefinition::gravity).txt("gravity");
    static final Field<ThrowableDefinition, Float> GRAVITY_IN_WATER =
            Field.of("GravityInWater", floats(-50, 50), -0.04F, ThrowableDefinition::gravityInWater)
                    .txt("gravityinwater");

    static final Field<Smoke, Boolean> SMOKE_DISABLED =
            Field.of("DisableSmoke", BOOLEAN, true, Smoke::disabled).txt("disablesmoke");
    static final Field<Smoke, Float> SMOKE_SIZE =
            Field.of("Size", floats(0, 1000), 10.0F, Smoke::size).txt("smokesize");
    static final Field<Smoke, Integer> SMOKE_COUNT =
            Field.of("Count", ints(0, 1000), 0, Smoke::count).txt("smokenum");
    /** TXT {@code SmokeColor = r, g, b} (0 to 255, always opaque); see {@link #smokeColorLine}. */
    static final Field<Smoke, ArgbColor> SMOKE_COLOR =
            Field.of("Color", ArgbColor.CODEC, ArgbColor.WHITE, Smoke::color).alwaysWritten();
    static final Field<Velocity, Float> SMOKE_VERTICAL =
            Field.of("Vertical", floats(-100, 100), 1.0F, Velocity::vertical).alias("Y").txt("smokevelocityvertical");
    static final Field<Velocity, Float> SMOKE_HORIZONTAL =
            Field.of("Horizontal", floats(0, 1000), 1.0F, Velocity::horizontal).alias("X")
                    .txt("smokevelocityhorizontal");
    static final Section<Smoke, Velocity> SMOKE_VELOCITY =
            Section.of("Velocity", Smoke::velocity, SMOKE_VERTICAL, SMOKE_HORIZONTAL);
    static final Section<ThrowableDefinition, Smoke> SMOKE = Section.of("Smoke", ThrowableDefinition::smoke,
            SMOKE_DISABLED, SMOKE_SIZE, SMOKE_COUNT, SMOKE_COLOR, SMOKE_VELOCITY);

    public static final Schema<ThrowableDefinition> SCHEMA = Schema.builder(
                    Section.root(AUTHOR, DISPLAY_NAME, ITEM_ID, STACK_SIZE, SOUND, RECIPE, POWER, ACCELERATION,
                            ACCELERATION_IN_WATER, DISPENSE_ACCELERATION, EXPLOSION, DELAY_FUSE, BOUND, TIME_FUSE,
                            FLAMING, PROXIMITY_FUSE_DIST, ACCURACY, ALIVE_TIME, BOMBLET, BOMBLET_SPREAD, GRAVITY,
                            GRAVITY_IN_WATER, SMOKE).unsupported("Particle"),
                    ThrowableSchema::build)
            .txtRule("adddisplayname", ThrowableSchema::displayNameLine)
            .txtRule("addrecipe", (values, text, context) -> recipeLine(values, text, true))
            .txtRule("addshapelessrecipe", (values, text, context) -> recipeLine(values, text, false))
            .txtRule("smokecolor", ThrowableSchema::smokeColorLine)
            .txtUnsupported("particle")
            .build();

    private ThrowableSchema() {
    }

    private static ThrowableDefinition build(Values v) {
        return new ThrowableDefinition(v.name(), v.get(AUTHOR), v.get(DISPLAY_NAME), v.get(ITEM_ID),
                v.get(STACK_SIZE), new Sound(v.get(SOUND_VOLUME), v.get(SOUND_PITCH)),
                new Recipe(v.get(RECIPE_SHAPED), v.get(RECIPE_PATTERN)), v.get(POWER), v.get(ACCELERATION),
                v.get(ACCELERATION_IN_WATER), v.get(DISPENSE_ACCELERATION), v.get(EXPLOSION), v.get(DELAY_FUSE),
                v.get(BOUND), v.get(TIME_FUSE), v.get(FLAMING), v.get(PROXIMITY_FUSE_DIST), v.get(ACCURACY),
                v.get(ALIVE_TIME), v.get(BOMBLET), v.get(BOMBLET_SPREAD), v.get(GRAVITY), v.get(GRAVITY_IN_WATER),
                new Smoke(v.get(SMOKE_DISABLED), v.get(SMOKE_SIZE), v.get(SMOKE_COUNT), v.get(SMOKE_COLOR),
                        new Velocity(v.get(SMOKE_VERTICAL), v.get(SMOKE_HORIZONTAL))));
    }

    private static int secondsToTicks(int seconds) {
        return seconds * TICKS_PER_SECOND;
    }

    /** Recipe lines are upper case, as the recipe builder expects item names. */
    private static ValueCodec<List<String>> recipeLines() {
        return ValueCodec.of(
                raw -> Codecs.list(raw).stream()
                        .map(line -> Codecs.text(line).toUpperCase(Locale.ROOT).trim())
                        .toList(),
                text -> List.of(text.toUpperCase(Locale.ROOT)),
                lines -> lines).shaped(ValueShape.listOf(ValueShape.text()));
    }

    /** TXT {@code AddDisplayName = ja_JP, name}; a line without exactly two values is ignored. */
    private static void displayNameLine(Values values, String text, ReadContext context) {
        String[] parts = Codecs.splitTxt(text);
        if (parts.length == 2) {
            values.set(DISPLAY_NAME, values.get(DISPLAY_NAME).with(parts[0].trim(), parts[1].trim()));
        }
    }

    /** TXT {@code AddRecipe} and {@code AddShapelessRecipe}: each adds a line, the last one decides the shape. */
    private static void recipeLine(Values values, String text, boolean shaped) {
        values.set(RECIPE_SHAPED, shaped);
        values.append(RECIPE_PATTERN, text.toUpperCase(Locale.ROOT));
    }

    /** TXT {@code SmokeColor = r, g, b}, each 0 to 255; fewer than three values are ignored. */
    private static void smokeColorLine(Values values, String text, ReadContext context) {
        String[] channels = Codecs.splitTxt(text);
        if (channels.length >= 3) {
            values.set(SMOKE_COLOR, new ArgbColor(1.0F, ArgbColor.txtChannel(channels[0], context),
                    ArgbColor.txtChannel(channels[1], context), ArgbColor.txtChannel(channels[2], context)));
        }
    }
}
