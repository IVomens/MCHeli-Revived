package com.ivomens.mcheli.content.def;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.sound.SoundRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** How content files name a sound; shared by weapons and vehicles. */
public final class SoundCodec {

    /**
     * A sound id, or a YAML map that also defines the sound for the client (see
     * {@code SoundRegistry#parseSound(Map)}). Reading registers the sound; the emitter writes the id.
     */
    public static final ValueCodec<ResourceLocation> SOUND = ValueCodec.of(
            SoundCodec::fromYaml,
            SoundRegistry.INSTANCE::parseSound,
            ResourceLocation::toString).shaped(ValueShape.either(ValueShape.text(),
                    ValueShape.mapOf(ValueShape.anything()))
            .note("A sound id such as 'mcheli:gun', or a map that also defines the sound for the client."));

    private SoundCodec() {
    }

    @SuppressWarnings("unchecked")
    private static ResourceLocation fromYaml(Object raw) {
        if (raw instanceof Map<?, ?> definition) {
            return SoundRegistry.INSTANCE.parseSound((Map<String, Object>) definition);
        }
        return SoundRegistry.INSTANCE.parseSound(Codecs.require(raw, String.class, "a sound name or a map"));
    }
}
