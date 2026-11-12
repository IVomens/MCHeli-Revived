package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.VehicleFile.RadarNames;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Recipe;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Repair;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.LocalizedName;
import com.ivomens.mcheli.core.content.schema.Member;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * The vehicle file format, in the order the emitter writes it. Every kind shares the top level; the kind adds
 * its own feature section and part types, and some defaults and limits depend on it ({@link KindFields}). Each
 * YAML section has its own class: {@link FuelSchema}, {@link ViewSchemas} ({@code Camera}, {@code Sound},
 * {@code Render}, {@code Armor}, {@code Uav}), {@link PhysicsSchema}, {@link CrewSchema} ({@code Weapons},
 * {@code Seats}, {@code Racks}), {@link HitBoxSchema} ({@code BoundingBoxes}, {@code Wheels}),
 * {@link FeaturesSchema}, {@link KindFeaturesSchema} and {@link ComponentsSchema}. The legacy TXT lines that need
 * more than one field are in {@link VehicleTxtRules}, {@link CrewTxtRules}, {@link PartTxtRules},
 * {@link GearTxtRules} and {@link KindTxtRules}.
 * <pre>
 * DisplayName: { DEFAULT: AH-64D Apache Longbow, ja_jp: AH-64D アパッチ・ロングボウ }
 * Category: M.A
 * MaxHP: 180
 * PhysicalProperties: { Speed: 0.8, OnGroundPitch: 3.5 }
 * Seats: [...]
 * HeliFeatures: { IsFoldableBlade: true }
 * Components: { HeliRotor: [...] }
 * </pre>
 */
public final class VehicleSchema {

    /** The name shown in game; the file name when left out. */
    static final Field<VehicleFile, LocalizedName> DISPLAY_NAME = Field.named("DisplayName", LocalizedName.CODEC,
            LocalizedName::of, VehicleFile::displayName).txt("displayname").alwaysWritten();
    /** Who made the vehicle; shown in its tooltip. */
    static final Field<VehicleFile, String> AUTHOR = Field.of("Author", Codecs.AUTHOR, "", VehicleFile::author)
            .alias("Authors");
    /** The numeric item id of 1.7 packs; kept for converted packs and not used by the game. */
    static final Field<VehicleFile, Integer> ITEM_ID = Field.of("ItemID", Codecs.STRICT_INT, 0,
            VehicleFile::itemId).txt(ints(0, 65_535), "itemid");
    /** The category shown in the tooltip, such as M.A; the creative tab is sorted by it. */
    static final Field<VehicleFile, String> CATEGORY = Field.of("Category", VehicleCodecs.CATEGORY, "zzz",
            VehicleFile::category).txt("category");
    /** TXT: the last of {@code AddRecipe} (shaped) and {@code AddShapelessRecipe} decides. */
    static final Field<Recipe, Boolean> RECIPE_SHAPED = Field.of("isShaped", BOOLEAN, true, Recipe::shaped);
    /** TXT: each {@code AddRecipe} or {@code AddShapelessRecipe} line adds a line, in upper case. */
    static final Field<Recipe, List<String>> RECIPE_PATTERN = Field.of("Pattern", VehicleCodecs.PATTERN, List.of(),
            Recipe::pattern);
    static final Section<VehicleFile, Recipe> RECIPE = Section.of("Recepie", VehicleFile::recipe, RECIPE_SHAPED,
            RECIPE_PATTERN).alias("Recipe");
    /** Whether players can get in. */
    static final Field<VehicleFile, Boolean> CAN_RIDE = Field.of("CanRide", BOOLEAN, true, VehicleFile::canRide)
            .txt(VehicleCodecs.ON_UNLESS_FALSE, "canride");
    /** Degrees per tick, or null for the kind's own; the game takes 0.01 off it. */
    static final Field<VehicleFile, Float> ROTOR_SPEED = Field.of("RotorSpeed", floats(-10_000, 10_000), null,
            VehicleFile::rotorSpeed).txt("rotorspeed");
    static final Field<VehicleFile, Vec3> TURRET_POSITION = Field.of("TurretPosition", Vectors.VEC3, Vec3.ZERO,
            VehicleFile::turretPosition);
    /** Where riders are put when they leave; next to their seat when left out. */
    static final Field<VehicleFile, Vec3> UNMOUNT_POSITION = Field.of("GlobalUnmountPos", Vectors.VEC3, null,
            VehicleFile::unmountPosition);
    /** Only players in creative mode can place it. */
    static final Field<VehicleFile, Boolean> CREATIVE_ONLY = Field.of("CreativeOnly", BOOLEAN, false,
            VehicleFile::creativeOnly).txt("creativeonly");
    /** The AI crew regenerate health while someone rides in the vehicle. */
    static final Field<VehicleFile, Boolean> REGENERATION = Field.of("Regeneration", BOOLEAN, false,
            VehicleFile::regeneration).txt("regeneration");
    /** Nothing damages the vehicle. */
    static final Field<VehicleFile, Boolean> INVULNERABLE = Field.of("Invulnerable", BOOLEAN, false,
            VehicleFile::invulnerable).txt("invulnerable");
    /** Hit points when undamaged. */
    static final Field<VehicleFile, Integer> MAX_HP = Field.of("MaxHP", ints(1, 1_000_000_000), 50,
            VehicleFile::maxHp).txt("maxhp");
    /** How hard the vehicle is to lock on to, from 0 (not at all) to 1. */
    static final Field<VehicleFile, Float> STEALTH = Field.of("Stealth", floats(0, 1), 0.0F, VehicleFile::stealth)
            .txt("stealth");
    /** Blocks within which the vehicle resupplies the ammunition of others; 0 for none. */
    static final Field<VehicleFile, Float> AMMO_SUPPLY_RANGE = Field.of("AmmoSupplyRange", floats(0, 1000), 0.0F,
            VehicleFile::ammoSupplyRange).txt("ammosupplyrange");
    static final Field<Repair, Float> REPAIR_RANGE = Field.of("Range", floats(0, 1000), 0.0F, Repair::range)
            .alwaysWritten();
    static final Field<Repair, Integer> REPAIR_VALUE = Field.of("Value", ints(0, 10_000_000), 10, Repair::value)
            .alwaysWritten();
    /** TXT: {@code RepairOtherVehicles = range[, value]}. */
    static final Section<VehicleFile, Repair> REPAIR = Section.of("RepairOtherVehicles",
            VehicleFile::repairOtherVehicles, REPAIR_RANGE, REPAIR_VALUE);
    static final Field<VehicleFile, String> NAME_MODERN_AA = radarName("NameOnModernAARadar",
            RadarNames::modernAirToAir, "nameonmodernaaradar");
    static final Field<VehicleFile, String> NAME_EARLY_AA = radarName("NameOnEarlyAARadar",
            RadarNames::earlyAirToAir, "nameonearlyaaradar");
    static final Field<VehicleFile, String> NAME_MODERN_AS = radarName("NameOnModernASRadar",
            RadarNames::modernAirToSurface, "nameonmodernasradar");
    static final Field<VehicleFile, String> NAME_EARLY_AS = radarName("NameOnEarlyASRadar",
            RadarNames::earlyAirToSurface, "nameonearlyasradar");
    /** Size of the explosion when the vehicle crashes. */
    static final Field<VehicleFile, Integer> EXPLOSION_SIZE = Field.of("ExplosionSizeByCrash", ints(0, 100), 5,
            VehicleFile::explosionSizeByCrash).txt("explosionsizebycrash");
    static final Field<VehicleFile, Float> THROTTLE_DOWN_FACTOR = Field.of("ThrottleDownFactor", floats(0, 10),
            1.0F, VehicleFile::throttleDownFactor).txt("throttledownfactor");

    /**
     * The legacy TXT keys an in-game reload does not read again: a reloaded vehicle keeps its item, name,
     * recipe, inventory, sound and UAV kind until the game restarts.
     */
    public static final List<String> FIXED_ON_RELOAD = List.of("DisplayName", "AddDisplayName", "ItemID",
            "AddRecipe", "AddShapelessRecipe", "InventorySize", "Sound", "UAV", "SmallUAV", "TargetDrone", "Category");

    private static final Map<VehicleKind, Schema<VehicleFile>> SCHEMAS = schemas();

    private VehicleSchema() {
    }

    /** The format of one kind's files. */
    public static Schema<VehicleFile> forKind(VehicleKind kind) {
        return SCHEMAS.get(kind);
    }

    private static Map<VehicleKind, Schema<VehicleFile>> schemas() {
        Map<VehicleKind, Schema<VehicleFile>> schemas = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            schemas.put(kind, schema(kind, KindFields.of(kind)));
        }
        return schemas;
    }

    private static Schema<VehicleFile> schema(VehicleKind kind, KindFields fields) {
        Schema.Builder<VehicleFile> builder = Schema.builder(root(kind, fields), v -> build(v, kind, fields));
        VehicleTxtRules.register(builder, fields);
        CrewTxtRules.register(builder, fields.profile());
        PartTxtRules.register(builder);
        KindTxtRules.register(builder, kind);
        PhysicsSchema.txtRules(builder, fields, kind);
        builder.txtUnsupported("nameonadvancedaaradar", "impactanglecoefficient", "radarminscanaltitude",
                        "radarmaxscanaltitude", "radarsearchtype", "radartrackazimuthdeg", "radartrackelevationdeg",
                        "radarretargetcooldowntick", "radarpanelfillalpha", "radarfollowturretyaw",
                        "radardetectchancebase", "radargainfactor", "gainfactor", "radarrcsfactor",
                        "radarrcsfrontfactor", "radarrcssidefactor", "radarrcsrearfactor", "radarrcstimefactor",
                        "radarcontactholdtick", "radarelevationreference", "radarelevationcoverage", "hudtype",
                        "weapongrouptype", "hasmaintenance", "maintenanceusetime", "maintenancewaittime", "hasdircm",
                        "hasphotoelectricjammer", "addsign", "addpartturretweaponbay", "addpartturretrotation")
                .reloadableExcept(FIXED_ON_RELOAD.toArray(new String[0]));
        if (kind == VehicleKind.PLANE || kind == VehicleKind.SHIP) {
            builder.txtUnsupported("enableautopilot");
        }
        return builder.build();
    }

    private static Section<VehicleFile, VehicleFile> root(VehicleKind kind, KindFields fields) {
        List<Member<VehicleFile>> members = new ArrayList<>(List.<Member<VehicleFile>>of(DISPLAY_NAME, AUTHOR,
                        ITEM_ID, CATEGORY, RECIPE, CAN_RIDE, ROTOR_SPEED, TURRET_POSITION,
                        UNMOUNT_POSITION, CREATIVE_ONLY, REGENERATION, INVULNERABLE, FuelSchema.MAX_FUEL,
                        FuelSchema.FUEL_TYPES, MAX_HP, STEALTH, FuelSchema.CONSUMPTION, FuelSchema.SUPPLY_RANGE,
                        FuelSchema.SUPPLY, FuelSchema.ELECTRIC_SECTION, AMMO_SUPPLY_RANGE, REPAIR, NAME_MODERN_AA,
                        NAME_EARLY_AA, NAME_MODERN_AS, NAME_EARLY_AS,
                        KeyAlias.spelled("RadarType", FeaturesSchema.RADAR_TYPE,
                                VehicleCodecs.radarType(RadarType.MODERN_AA)),
                        KeyAlias.spelled("RWRType", FeaturesSchema.RWR_TYPE, VehicleCodecs.rwrType(RWRType.DIGITAL)),
                        EXPLOSION_SIZE, THROTTLE_DOWN_FACTOR, ViewSchemas.camera(fields), ViewSchemas.sound(fields),
                        PhysicsSchema.section(fields), ViewSchemas.RENDER, ViewSchemas.ARMOR, CrewSchema.WEAPONS,
                        CrewSchema.SEATS, CrewSchema.GUNNER_HUD, CrewSchema.RACKS, CrewSchema.EXCLUSIVE_SEATS,
                        CrewSchema.RIDE_RACKS, HitBoxSchema.HIT_BOXES, HitBoxSchema.wheels(fields),
                        ViewSchemas.UAV_SECTION, FeaturesSchema.SECTION, KindFeaturesSchema.section(kind),
                        ComponentsSchema.section(kind), AddonSchema.BEHAVIOURS));
        if (kind == VehicleKind.VEHICLE) {
            members.add(AddonSchema.MOVEMENT);
        }
        return Section.root(members)
                .unsupported("NameOnAdvancedAARadar", "HUDType", "HudType", "WeaponGroupType");
    }

    private static VehicleFile build(Values v, VehicleKind kind, KindFields fields) {
        return new VehicleFile(v.name(), kind, v.get(DISPLAY_NAME), v.get(AUTHOR), v.get(ITEM_ID), v.get(CATEGORY),
                new Recipe(v.get(RECIPE_SHAPED), v.get(RECIPE_PATTERN)), v.get(CAN_RIDE), v.get(ROTOR_SPEED),
                v.get(TURRET_POSITION), v.get(UNMOUNT_POSITION), v.get(CREATIVE_ONLY), v.get(REGENERATION),
                v.get(INVULNERABLE), FuelSchema.build(v), v.get(MAX_HP), v.get(STEALTH), v.get(AMMO_SUPPLY_RANGE),
                new Repair(v.get(REPAIR_RANGE), v.get(REPAIR_VALUE)),
                new RadarNames(v.get(NAME_MODERN_AA), v.get(NAME_EARLY_AA), v.get(NAME_MODERN_AS),
                        v.get(NAME_EARLY_AS)),
                v.get(EXPLOSION_SIZE), v.get(THROTTLE_DOWN_FACTOR), ViewSchemas.camera(v, fields),
                ViewSchemas.sound(v, fields), PhysicsSchema.build(v, fields), ViewSchemas.render(v),
                ViewSchemas.armor(v), v.get(CrewSchema.WEAPONS), CrewSchema.build(v), v.get(HitBoxSchema.HIT_BOXES),
                HitBoxSchema.wheels(v, fields), ViewSchemas.uav(v), FeaturesSchema.build(v),
                KindFeaturesSchema.build(v), ComponentsSchema.build(v),
                AddonSchema.build(v, kind == VehicleKind.VEHICLE));
    }

    private static Field<VehicleFile, String> radarName(String key, Function<RadarNames, String> getter,
                                                        String txtKey) {
        return Field.of(key, TEXT, "", (VehicleFile file) -> getter.apply(file.radarNames())).txt(txtKey);
    }
}
