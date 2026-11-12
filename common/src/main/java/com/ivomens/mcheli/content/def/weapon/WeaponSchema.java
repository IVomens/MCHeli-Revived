package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.LOWER_TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * The weapon file format, in the order the emitter writes it. The top-level settings are here; each YAML
 * section has its own class ({@link BallisticsSchema}, {@link SoundSchema}, {@link RecoilSchema},
 * {@link ExplosionSchema}, {@link CameraSchema}, {@link MissileSchema}, {@link AmmoSchema},
 * {@link RenderSchema}, {@link SubmunitionSchema}, {@link EffectsSchema}, {@link WeaponTypeSchema}), and the
 * legacy TXT lines that need more than one field are in {@link WeaponTxtRules}.
 * <pre>
 * DisplayName: AIM-9 Sidewinder
 * Type: aamissile
 * BaseDamage: 30
 * Ballistics: { Acceleration: 2.5, ... }
 * Missile:
 *   LockOn: { Time: 20, MaxRange: 300 }
 * Ammo: { MagSize: 2, ReloadTime: 200 }
 * </pre>
 */
public final class WeaponSchema {

    /** Target bit flags, by the word that sets them; {@code block} overrides every other target. */
    private static final int TARGET_BLOCKS = 64;
    private static final Map<Integer, List<String>> TARGET_WORDS = Map.of(
            32, List.of("planes"),
            16, List.of("helicopters"),
            8, List.of("vehicles", "tanks"),
            4, List.of("players"),
            2, List.of("monsters"),
            1, List.of("others"));
    /** Flags in the order the emitter names them. */
    private static final List<Integer> TARGET_ORDER = List.of(32, 16, 8, 4, 2, 1);

    /**
     * What the weapon may lock on to or hit, as words anywhere in the text: {@code planes}, {@code helicopters},
     * {@code vehicles} (or {@code tanks}), {@code players}, {@code monsters}, {@code others}, or {@code block}
     * alone. YAML ignores case; legacy TXT does not.
     */
    static final ValueCodec<Integer> TARGETS = ValueCodec.of(
            raw -> targetFlags(Codecs.text(raw).toLowerCase(Locale.ROOT)),
            WeaponSchema::targetFlags,
            WeaponSchema::targetWords).shaped(ValueShape.text()
            .note("Words anywhere in the text: planes, helicopters, vehicles (or tanks), players, monsters, others; "
                    + "or block alone."));

    /** The name shown in game; the file name when left out. */
    static final Field<WeaponDefinition, String> DISPLAY_NAME =
            Field.named("DisplayName", TEXT, name -> name, WeaponDefinition::displayName).txt("displayname")
                    .alwaysWritten();
    /**
     * The weapon kind, such as {@code machinegun1}, {@code aamissile} or {@code bomb}. A TXT {@code Type} line is
     * read by {@link WeaponTxtRules#type}, which also changes the gravity of bombs and dispensers.
     */
    static final Field<WeaponDefinition, String> TYPE = Field.of("Type", LOWER_TEXT, "", WeaponDefinition::type);
    /** Weapons of one group fire together. */
    static final Field<WeaponDefinition, String> GROUP = Field.of("Group", LOWER_TEXT, "", WeaponDefinition::group)
            .txt("group");
    /** Damage of a direct hit, before the damage factors. */
    static final Field<WeaponDefinition, Integer> BASE_DAMAGE =
            Field.of("BaseDamage", INT, 0, WeaponDefinition::baseDamage).txt("power");
    /** Ticks between shots. TXT clamps it to 0..100000. */
    static final Field<WeaponDefinition, Integer> FIRE_COOLDOWN =
            Field.of("FireCooldown", INT, 10, WeaponDefinition::fireCooldown).txt(ints(0, 100_000), "delay");
    /** What the weapon may lock on to or hit. */
    static final Field<WeaponDefinition, Integer> TARGET =
            Field.of("Target", TARGETS, 1, WeaponDefinition::target).txt("target");
    /** Fire modes the weapon cycles through. */
    static final Field<WeaponDefinition, Integer> MODES = Field.of("Mode", ints(0, 1000), 0, WeaponDefinition::modes)
            .txt("modenum");
    /** The mode the weapon is fixed to, if below the mode count. */
    static final Field<WeaponDefinition, Integer> FIX_MODE =
            Field.of("FixMode", ints(0, 10), 0, WeaponDefinition::fixMode).txt("fixmode");
    /** Only a seated gunner may fire it. */
    static final Field<WeaponDefinition, Boolean> RIDABLE_ONLY =
            Field.of("RidableOnly", BOOLEAN, false, WeaponDefinition::ridableOnly).txt("ridableonly");
    /** Random spread; 0 is perfectly accurate. */
    static final Field<WeaponDefinition, Float> ACCURACY =
            Field.of("Accuracy", floats(0, 1000), 0.0F, WeaponDefinition::accuracy).txt("accuracy");
    /** Fires once and destroys itself, as a demolition charge does. */
    static final Field<WeaponDefinition, Boolean> SELF_DESTRUCT =
            Field.of("SelfDestruct", BOOLEAN, false, WeaponDefinition::selfDestruct).txt("destruct");
    /** The projectile keeps the chunks it flies through loaded. */
    static final Field<WeaponDefinition, Boolean> CAN_LOAD_CHUNKS =
            Field.of("CanLoadChunks", BOOLEAN, false, WeaponDefinition::canLoadChunks).txt("enablechunkloader");
    /** Length of a beam or cone weapon. */
    static final Field<WeaponDefinition, Integer> LENGTH =
            Field.of("Length", ints(1, 300), 0, WeaponDefinition::length).txt("length");
    /** Radius of a beam or cone weapon. */
    static final Field<WeaponDefinition, Integer> RADIUS =
            Field.of("Radius", ints(1, 1000), 0, WeaponDefinition::radius).txt("radius");
    /** Ticks a spotted target stays marked; the game keeps it one tick longer than written. */
    static final Field<WeaponDefinition, Integer> MARK_TIME = Field.of("MarkTime",
            ints(1, 30_000).map(written -> written + 1, kept -> kept - 1), 0, WeaponDefinition::markTime)
            .txt("marktime");
    static final Field<WeaponDefinition, Map<String, Float>> DAMAGE_FACTOR =
            Field.of("DamageFactor", DamageFactors.CODEC, Map.of(), WeaponDefinition::damageFactors);
    static final Field<WeaponDefinition, NtmPayload> NTM =
            Field.of("NTM", NtmPayload.CODEC, (NtmPayload) null, WeaponDefinition::ntm);
    /** An addon mod's guidance law that steers the projectiles instead of the type's own guidance. */
    static final Field<WeaponDefinition, BehaviourRef> GUIDANCE =
            Field.of("Guidance", BehaviourRef.CODEC, (BehaviourRef) null, WeaponDefinition::guidance);
    /** Addon mods' effects each projectile runs, in order. */
    static final Field<WeaponDefinition, List<BehaviourRef>> PROJECTILE_EFFECTS =
            Field.of("ProjectileEffects", BehaviourRef.LIST_CODEC, List.of(), WeaponDefinition::projectileEffects);

    public static final Schema<WeaponDefinition> SCHEMA = Schema.builder(
                    Section.root(DISPLAY_NAME, TYPE, GROUP, BASE_DAMAGE, BallisticsSchema.SECTION, SoundSchema.SECTION,
                                    RecoilSchema.SECTION, ExplosionSchema.SECTION, CameraSchema.SECTION,
                                    MissileSchema.SECTION, AmmoSchema.SECTION, FIRE_COOLDOWN, TARGET, MODES, FIX_MODE,
                                    RIDABLE_ONLY, ACCURACY, SELF_DESTRUCT, CAN_LOAD_CHUNKS, RenderSchema.SECTION,
                                    SubmunitionSchema.SECTION, EffectsSchema.SECTION, WeaponTypeSchema.DISPENSE_ITEM,
                                    LENGTH, RADIUS, MARK_TIME, DAMAGE_FACTOR, WeaponTypeSchema.BUCKSHOT,
                                    WeaponTypeSchema.MINE, NTM, GUIDANCE, PROJECTILE_EFFECTS)
                            .unsupported("SwitchCooldown", "CrossType", "Damage"),
                    WeaponSchema::build)
            .txtRule("type", WeaponTxtRules::type)
            .txtRule("recoilbufcount", WeaponTxtRules::recoilBuffer)
            .txtRule("item", WeaponTxtRules::ammoItem)
            .txtRule("addmuzzleflash", WeaponTxtRules::muzzleFlash)
            .txtRule("addmuzzleflashsmoke", WeaponTxtRules::muzzleSmoke)
            .txtRule("setcartridge", WeaponTxtRules::cartridge)
            .txtRule("bulletcolor", WeaponTxtRules::color)
            .txtRule("smokecolor", WeaponTxtRules::color)
            .txtRule("bulletcolorinwater", WeaponTxtRules::colorInWater)
            .txtRule("dispenseitem", WeaponTxtRules::dispenseItem)
            .txtRule("damagefactor", WeaponTxtRules::damageFactor)
            .txtRule("addpotioneffect", WeaponTxtRules::potionEffect)
            .txtRule("bulletdecay", WeaponTxtRules::bulletDecay)
            .txtUnsupported("modelbulletendtick", "trajectoryparticleendtick", "initmaxdegreeofmissile",
                    "semiactiveradar", "initturningfactor", "weaponswitchcount", "weaponswitchsound",
                    "explosiondamagevsliving", "explosiondamagevsplayer", "explosiondamagevsplane",
                    "explosiondamagevsvehicle", "explosiondamagevstank", "explosiondamagevsheli",
                    "explosiondamagevsship", "disabledestroyblock", "crosstype", "cciptexture", "hitsoundiron",
                    "enablemortarradar", "camerafollowlockentity", "camerafollowstrength", "armemitterlostgracetick",
                    "armmemorytimetick", "armcruiseenable", "armcruisestartdistance", "armcruiseterminalradius",
                    "armcruiseterminalheight", "enabledatalink", "onlydatalink", "enablehms", "rcsfactor",
                    "rcstimefactor", "markerrocketspawnnum", "markerrocketspawndiff", "markerrocketspawnheight",
                    "markerrocketspawnspeed", "enableexhaustflare", "aheadsolveintervaltick", "canistertype",
                    "ballisticmissile", "ballisticarcfactor", "ballisticarcminheight", "ballisticarcmaxheight",
                    "ballisticmindistance", "ballisticlateralsine", "ballisticlateralamplitude",
                    "ballisticlateralwaves", "ballisticlateralphasedeg", "ballisticlateralstartratio",
                    "ballisticlateralendratio", "ballisticterminalnoweavedist", "ballisticterminalcylinderradius")
            .build();

    private WeaponSchema() {
    }

    private static WeaponDefinition build(Values v) {
        return new WeaponDefinition(v.name(), v.get(DISPLAY_NAME), v.get(TYPE), v.get(GROUP), v.get(BASE_DAMAGE),
                BallisticsSchema.build(v), SoundSchema.build(v), RecoilSchema.build(v), ExplosionSchema.build(v),
                CameraSchema.build(v), MissileSchema.build(v), AmmoSchema.build(v), v.get(FIRE_COOLDOWN),
                v.get(TARGET), v.get(MODES), v.get(FIX_MODE), v.get(RIDABLE_ONLY), v.get(ACCURACY),
                v.get(SELF_DESTRUCT), v.get(CAN_LOAD_CHUNKS), RenderSchema.build(v), SubmunitionSchema.build(v),
                EffectsSchema.build(v), WeaponTypeSchema.dispenseItem(v), v.get(LENGTH), v.get(RADIUS),
                v.get(MARK_TIME), v.get(DAMAGE_FACTOR), WeaponTypeSchema.buckshot(v), WeaponTypeSchema.mine(v),
                v.get(NTM), v.get(GUIDANCE), v.get(PROJECTILE_EFFECTS));
    }

    private static int targetFlags(String text) {
        if (text.contains("block")) {
            return TARGET_BLOCKS;
        }
        int flags = 0;
        for (Map.Entry<Integer, List<String>> target : TARGET_WORDS.entrySet()) {
            if (target.getValue().stream().anyMatch(text::contains)) {
                flags |= target.getKey();
            }
        }
        return flags;
    }

    private static String targetWords(int flags) {
        if ((flags & TARGET_BLOCKS) != 0) {
            return "Block";
        }
        List<String> words = new ArrayList<>();
        for (int flag : TARGET_ORDER) {
            if ((flags & flag) != 0) {
                String word = TARGET_WORDS.get(flag).getFirst();
                words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(",", words);
    }
}
