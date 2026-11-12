package com.ivomens.mcheli.sound.client;

import java.util.Locale;
import java.util.Map;

/** One sound file of a sound event, as a content file or {@code sounds.json} declares it. */
final class SoundSpec {
    String name;
    float volume = 1f;
    float pitch = 1f;
    int weight = 1;
    boolean streaming = false;

    public SoundSpec() {
    }

    public SoundSpec(String name) {
        this.name = name;
    }

    public static SoundSpec fromYML(Map<String, Object> map) {
        SoundSpec spec = new SoundSpec();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            switch (entry.getKey()) {
                case "Name" -> spec.name = ((String) value).trim().toLowerCase(Locale.ROOT);
                case "Vol", "Volume" -> spec.volume = Math.max(0F, ((Number) value).floatValue());
                case "Pitch" -> spec.pitch = Math.max(0F, ((Number) value).floatValue());
                case "Weight" -> spec.weight = Math.max(1, ((Number) value).intValue());
                case "isStreamed", "Stream" -> spec.streaming = (Boolean) value;
                default -> {
                }
            }
        }
        return spec;
    }
}
