package com.ivomens.mcheli.sound.client;

import com.ivomens.mcheli.sound.SoundRegistry;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side of {@link SoundRegistry}.
 * <p>
 * Every sound event that ships with the mod is declared in {@code assets/mcheli/sounds.json}, and
 * addon packs contribute their own {@code sounds.json}. Content files may however reference events
 * that no {@code sounds.json} declares (a bare file name, or an inline list of sounds in YAML). For
 * those, a fallback {@link WeighedSoundEvents} is built from the content definition and inserted into
 * the sound manager whenever it is missing (after each resource reload, and lazily before playing).
 */
public class ClientSoundRegistry {

    public static final ClientSoundRegistry INSTANCE = new ClientSoundRegistry();

    /** Fallback definitions: event id to the sounds making up the event. */
    private static final Map<ResourceLocation, List<SoundSpec>> soundMap = new ConcurrentHashMap<>();
    /** Attenuation distance of the fallback sounds, the vanilla default. */
    private static final int ATTENUATION_DISTANCE = 16;
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();

    static {
        put("wrench", "wrench1", "wrench2", "wrench3");
    }

    private ClientSoundRegistry() {
    }

    private static void put(String event, String... files) {
        List<SoundSpec> list = new ArrayList<>();
        for (String file : files) {
            list.add(new SoundSpec(Ids.MOD_ID + ":" + file));
        }
        soundMap.put(Ids.of(event), list);
    }

    @SuppressWarnings("unchecked")
    public void buildFromYML(ResourceLocation id, Map<String, Object> ymlEntry) {
        List<SoundSpec> specs = new ArrayList<>();
        Object sounds = ymlEntry.get("Sounds");
        if (sounds instanceof String name) {
            specs.add(new SoundSpec(name));
        } else if (sounds instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof String name) {
                    specs.add(new SoundSpec(name));
                } else if (element instanceof Map<?, ?> map) {
                    specs.add(SoundSpec.fromYML((Map<String, Object>) map));
                }
            }
        }
        if (specs.isEmpty()) {
            specs.add(new SoundSpec(id.toString()));
        }
        soundMap.put(id, specs);
    }

    public void buildFromString(ResourceLocation id, String loc) {
        String file = id.getPath();
        if (!loc.endsWith(SoundRegistry.SND) && !SoundRegistry.validateSoundResource(id)) {
            file = file + SoundRegistry.SND;
        }
        List<SoundSpec> list = new ArrayList<>(1);
        list.add(new SoundSpec(id.getNamespace() + ":" + file));
        soundMap.putIfAbsent(id, list);
    }

    /** Inserts every fallback event that the sound manager does not know about. */
    public void commitChanges(SoundManager handler) {
        for (Map.Entry<ResourceLocation, List<SoundSpec>> entry : soundMap.entrySet()) {
            inject(handler, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Makes sure {@code id} resolves to something playable. Cheap when the event is already known.
     */
    public static void ensureRegistered(ResourceLocation id) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (soundManager.getSoundEvent(id) != null) return;

        List<SoundSpec> specs = soundMap.get(id);
        if (specs == null) {
            if (Ids.MOD_ID.equals(id.getNamespace()) && SoundRegistry.validateSoundResource(id)) {
                List<SoundSpec> list = new ArrayList<>(1);
                list.add(new SoundSpec(id.toString()));
                soundMap.put(id, list);
                specs = list;
            } else {
                if (WARNED.add(id)) {
                    ModLogger.warn("Sound event is not declared in any sounds.json: {}", id);
                }
                return;
            }
        }
        inject(soundManager, id, specs);
    }

    private static void inject(SoundManager soundManager, ResourceLocation id, List<SoundSpec> specs) {
        if (soundManager.registry.get(id) != null) return;

        WeighedSoundEvents events = new WeighedSoundEvents(id, null);
        int added = 0;
        for (SoundSpec spec : specs) {
            if (spec.name == null || spec.name.isEmpty()) continue;
            ResourceLocation file = spec.name.contains(":")
                    ? ResourceLocation.parse(spec.name)
                    : Ids.of(spec.name);
            events.addSound(new Sound(file, ConstantFloat.of(spec.volume), ConstantFloat.of(spec.pitch), spec.weight,
                    Sound.Type.FILE, spec.streaming, false, ATTENUATION_DISTANCE));
            added++;
        }
        if (added > 0) {
            soundManager.registry.put(id, events);
        }
    }
}
