package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Locations;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Sound;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;

import static com.ivomens.mcheli.content.def.SoundCodec.SOUND;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Sound}: the firing sound and how it plays.
 * <pre>
 * Sound:
 *   Volume: 3.0
 *   Locations:
 *     Fire: mcheli:aim9_snd     # default: the weapon's file name + _snd
 *     Hit: mcheli:hit_snd
 * </pre>
 */
final class SoundSchema {

    /** Ticks between the shot and its sound. */
    static final Field<Sound, Integer> DELAY = Field.of("Delay", ints(0, 1000), 0, Sound::delay).txt("sounddelay");
    static final Field<Sound, Float> VOLUME = Field.of("Volume", floats(0, 1000), 1.0F, Sound::volume)
            .txt("soundvolume");
    static final Field<Sound, Float> PITCH = Field.of("Pitch", floats(0, 1), 1.0F, Sound::pitch).txt("soundpitch");
    static final Field<Sound, Float> PITCH_RANDOM =
            Field.of("PitchRandom", floats(0, 1), 0.1F, Sound::pitchRandom).txt("soundpitchrandom");
    /** Blocks within which players hear the hit sound. */
    static final Field<Sound, Integer> HIT_SOUND_RANGE =
            Field.of("HitSoundRange", INT, 100, Sound::hitSoundRange).txt("hitsoundrange");

    static final Field<Locations, ResourceLocation> FIRE = Field.named("Fire", SOUND,
            name -> Ids.of(name + "_snd"), Locations::fire)
            .txt("sound").alwaysWritten();
    static final Field<Locations, ResourceLocation> HIT =
            Field.of("Hit", SOUND, (ResourceLocation) null, Locations::hit).txt("hitsound");
    static final Field<Locations, ResourceLocation> RAILGUN = Field.of("Railgun", SOUND,
            Ids.of("railgun"), Locations::railgun).txt("railgunsound");
    static final Section<Sound, Locations> LOCATIONS = Section.of("Locations", Sound::locations, FIRE, HIT, RAILGUN)
            .unsupported("HitMetal", "WeaponSwitch");

    static final Section<WeaponDefinition, Sound> SECTION = Section.of("Sound", WeaponDefinition::sound,
            DELAY, VOLUME, PITCH, PITCH_RANDOM, HIT_SOUND_RANGE, LOCATIONS, new KeyAlias<>("Name", FIRE));

    private SoundSchema() {
    }

    static Sound build(Values v) {
        return new Sound(v.get(DELAY), v.get(VOLUME), v.get(PITCH), v.get(PITCH_RANDOM), v.get(HIT_SOUND_RANGE),
                new Locations(v.get(FIRE), v.get(HIT), v.get(RAILGUN)));
    }
}
