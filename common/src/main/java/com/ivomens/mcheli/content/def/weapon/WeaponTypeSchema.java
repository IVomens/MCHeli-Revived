package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Buckshot;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.DispenseItem;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Mine;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.LOWER_TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * Sections only one weapon type reads: {@code DispenseItem} for dispensers, {@code Buckshot} for buckshot,
 * {@code Mine} for bombs that settle and wait.
 */
final class WeaponTypeSchema {

    private static final Map<String, MineFuze> FUZE_NAMES = Map.of(
            "PROXIMITYPLAYER", MineFuze.PROXIMITY_PLAYER,
            "PROXIMITY_PLAYER", MineFuze.PROXIMITY_PLAYER,
            "PLAYER", MineFuze.PROXIMITY_PLAYER,
            "PROXIMITYVEHICLE", MineFuze.PROXIMITY_VEHICLE,
            "PROXIMITY_VEHICLE", MineFuze.PROXIMITY_VEHICLE,
            "VEHICLE", MineFuze.PROXIMITY_VEHICLE);

    /** {@code ProximityPlayer} or {@code ProximityVehicle}, any case; any other name is not a mine. */
    static final ValueCodec<MineFuze> FUZE = ValueCodec.of(
            raw -> FUZE_NAMES.getOrDefault(Codecs.text(raw).trim().toUpperCase(Locale.ROOT), MineFuze.NONE),
            text -> FUZE_NAMES.getOrDefault(text.trim().toUpperCase(Locale.ROOT), MineFuze.NONE),
            MineFuze::name).shaped(ValueShape.suggested(List.of("ProximityPlayer", "ProximityVehicle")));

    /** {@code Rocket}, any case; anything else fires bullets. */
    static final ValueCodec<BuckshotPellet> PELLET = ValueCodec.of(
            raw -> pellet(Codecs.text(raw)),
            WeaponTypeSchema::pellet,
            BuckshotPellet::name).shaped(ValueShape.suggested(List.of("Bullet", "Rocket")));

    /** The item a dispenser places or uses, such as {@code water_bucket}. */
    static final Field<DispenseItem, String> DISPENSE_LOCATION =
            Field.of("Location", LOWER_TEXT, (String) null, DispenseItem::location).alias("Loc", "Name");
    static final Field<DispenseItem, Integer> DISPENSE_META =
            Field.of("Meta", INT, 0, DispenseItem::meta).alias("Damage");
    /** Blocks around the impact the dispenser covers. */
    static final Field<DispenseItem, Integer> DISPENSE_RANGE =
            Field.of("DispenseRange", ints(1, 100), 1, DispenseItem::range).txt("dispenserange");
    static final Section<WeaponDefinition, DispenseItem> DISPENSE_ITEM = Section.of("DispenseItem",
                    WeaponDefinition::dispenseItem, DISPENSE_LOCATION, DISPENSE_META, DISPENSE_RANGE)
            .requires(DISPENSE_LOCATION);

    static final Field<Buckshot, Integer> BUCKSHOT_COUNT =
            Field.of("Count", ints(1, 1000), 6, Buckshot::count);
    static final Field<Buckshot, BuckshotPellet> BUCKSHOT_PELLET =
            Field.of("PayloadType", PELLET, BuckshotPellet.BULLET, Buckshot::pellet).alias("Payload");
    static final Section<WeaponDefinition, Buckshot> BUCKSHOT = Section.of("Buckshot", WeaponDefinition::buckshot,
            BUCKSHOT_COUNT, BUCKSHOT_PELLET);

    static final Field<Mine, MineFuze> MINE_FUZE =
            Field.of("Fuze", FUZE, MineFuze.NONE, Mine::fuze).alias("Type", "MineType");
    /** Blocks within which the trigger sets the mine off. */
    static final Field<Mine, Float> MINE_RANGE = Field.of("Range", floats(0, 256), 4.0F, Mine::range);
    /** Ticks after landing before the mine can go off. */
    static final Field<Mine, Integer> MINE_ARM_DELAY = Field.of("ArmDelay", ints(0, 1_000_000), 40, Mine::armDelay);
    static final Section<WeaponDefinition, Mine> MINE = Section.of("Mine", WeaponDefinition::mine,
            MINE_FUZE, MINE_RANGE, MINE_ARM_DELAY);

    private WeaponTypeSchema() {
    }

    static DispenseItem dispenseItem(Values v) {
        return new DispenseItem(v.get(DISPENSE_LOCATION), v.get(DISPENSE_META), v.get(DISPENSE_RANGE));
    }

    static Buckshot buckshot(Values v) {
        return new Buckshot(v.get(BUCKSHOT_COUNT), v.get(BUCKSHOT_PELLET));
    }

    static Mine mine(Values v) {
        return new Mine(v.get(MINE_FUZE), v.get(MINE_RANGE), v.get(MINE_ARM_DELAY));
    }

    private static BuckshotPellet pellet(String text) {
        return text.trim().equalsIgnoreCase(BuckshotPellet.ROCKET.name()) ? BuckshotPellet.ROCKET
                : BuckshotPellet.BULLET;
    }
}
