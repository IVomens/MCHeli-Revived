package com.ivomens.mcheli.sound;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of every sound event name MCHeli uses: the built-in list below plus every name that
 * content files declare. On the client, content-declared sounds are additionally defined through
 * {@link ClientSounds} so events that are not covered by any {@code sounds.json} can still be resolved.
 */
public final class SoundRegistry {

    /** Suffix of weapon sound file names. */
    public static final String SND = "_snd";
    /** Sounds the mod itself uses; content files add theirs while they are parsed. */
    private static final String[] BUILT_IN_SOUNDS = {
            "a-10_snd", "a10gau8_snd", "alert", "boat", "bomb_snd", "cannon_1_snd", "cannon_2_snd", "cannon_3_snd",
            "cannon_4_snd", "chain", "chain_ct", "eject_seat", "fim92_reload", "fim92_snd", "gau-8_snd", "gltd",
            "gun_h1_snd", "gun_h2_snd", "gun_h3_snd", "gun_h4_snd", "gun_h5_snd", "gun_h6_snd", "gun_h7_snd",
            "gun_l1_snd", "gun_l2_snd", "gun_l3_snd", "gun_l4_snd", "hawk_snd", "heli", "helidmg", "heli_k", "hit",
            "locked", "lockon", "mbtrun", "mbt_run", "missile_1_snd", "missile_2_snd", "missile_3_snd",
            "missile_4_snd", "mk19_l_snd", "mk19_r_snd", "mw-1_snd", "ng", "pi", "plane", "plane_cc", "plane_cv",
            "plastic_bomb_snd", "prop", "r44_heli", "radicon_heli", "rocket", "rocket_snd", "rr_griffon",
            "rr_merlin", "sa-2_snd", "smoke_snd", "tank", "tank_gte", "turboprop", "vehicle_drive", "vehicle_run",
            "xm301_snd", "zoom", "wrench", "iron_curtain", "aps_activate", "aps_deactivate", "aps_shoot"
    };
    public static final SoundRegistry INSTANCE = new SoundRegistry();

    public final Set<ResourceLocation> soundSet = ConcurrentHashMap.newKeySet();

    private SoundRegistry() {
        for (String sound : BUILT_IN_SOUNDS) {
            this.soundSet.add(rl(sound));
        }
    }

    private static ResourceLocation rl(String path) {
        return Ids.of(path);
    }

    /** Client only: whether {@code assets/<ns>/sounds/<path>.ogg} exists in the loaded resource packs. */
    public static boolean validateSoundResource(ResourceLocation sound) {
        return ClientSounds.INSTANCE.hasOgg(sound);
    }

    public ResourceLocation parseSound(Map<String, Object> ymlEntry) {
        String eventName = ((String) ymlEntry.get("Name")).trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = rl(stripDomain(eventName));
        soundSet.add(id);
        ClientSounds.INSTANCE.define(id, ymlEntry);
        return id;
    }

    public ResourceLocation parseSound(String loc) {
        ResourceLocation id = rl(stripDomain(loc.trim().toLowerCase(Locale.ROOT)));
        soundSet.add(id);
        ClientSounds.INSTANCE.define(id, loc);
        return id;
    }

    private static String stripDomain(String loc) {
        String prefix = Ids.MOD_ID + ":";
        return loc.startsWith(prefix) ? loc.substring(prefix.length()) : loc;
    }
}
