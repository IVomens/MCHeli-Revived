package com.ivomens.mcheli.content.def.weapon;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The subsections of a weapon's {@code NTM} section, one record each. {@link NtmPayloadReader} documents the
 * keys; the defaults and presets are those of Hammer's Nuclear Tech.
 */
public final class NtmSections {

    private NtmSections() {
    }

    /**
     * {@code NTM.VNT}: a configurable explosion.
     *
     * @param attributes the {@code Attributes} section as written
     * @param preset     the named explosion preset, or null
     */
    public record Vnt(Map<String, Object> attributes, @Nullable String preset, VntEffect effect) {
    }

    /** {@code NTM.VNT.Effect}: the particle effect of a VNT explosion. */
    public record VntEffect(boolean small, int cloudCount, float cloudScale, float cloudSpeedMult, float waveScale,
                            int debrisCount, int debrisSize, int debrisRetry, float debrisVelocity,
                            float debrisHorizontalDeviation, float debrisVerticalOffset, float soundRange) {

        public static VntEffect medium() {
            return new VntEffect(false, 10, 2F, 0.5F, 25F, 5, 8, 20, 0.75F, 1F, -2F, 150F);
        }

        public static VntEffect standard() {
            return new VntEffect(false, 15, 5F, 1F, 45F, 10, 16, 50, 1F, 3F, -2F, 200F);
        }

        public static VntEffect large() {
            return new VntEffect(false, 30, 6.5F, 2F, 65F, 25, 16, 50, 1.25F, 3F, -2F, 350F);
        }
    }

    /** {@code NTM.NT}: a classic explosion, with its attributes and ray resolution. */
    public record Nt(List<String> attributes, int resolution) {

        public Nt {
            attributes = List.copyOf(attributes);
        }
    }

    /**
     * {@code NTM.MiniNuke}: a small nuclear explosion.
     *
     * @param useTorex the large mushroom cloud instead of the mini nuke's
     * @param safe     no radiation and no block damage
     */
    public record MiniNuke(boolean useTorex, boolean safe, float blastRadius, float killRadius, float radiationLevel,
                           String particle, int shrapnelCount, int resolution, List<String> attributes) {

        public static final List<String> DEFAULT_ATTRIBUTES = List.of("FIRE", "NOPARTICLE", "NOSOUND", "NODROP",
                "NOHURT");
        public static final MiniNuke DEFAULTS = new MiniNuke(false, false, 0F, 0F, 1F, "Muke", 25, 64,
                DEFAULT_ATTRIBUTES);
        public static final MiniNuke SAFE = new MiniNuke(false, true, 0F, 45F, 2F, "Muke", 25, 64,
                DEFAULT_ATTRIBUTES);
        public static final MiniNuke TOTS = new MiniNuke(false, false, 10F, 30F, 1F, "TinyTot", 0, 32,
                DEFAULT_ATTRIBUTES);
        public static final MiniNuke LOW = new MiniNuke(false, false, 15F, 45F, 2F, "Muke", 25, 64,
                DEFAULT_ATTRIBUTES);
        public static final MiniNuke MEDIUM = new MiniNuke(false, false, 20F, 55F, 3F, "Muke", 25, 64,
                DEFAULT_ATTRIBUTES);
        public static final MiniNuke HIGH = new MiniNuke(true, false, 35F, 0F, 1F, "Muke", 0, 64,
                DEFAULT_ATTRIBUTES);

        public MiniNuke {
            attributes = List.copyOf(attributes);
        }
    }

    /** {@code NTM.Mist}: a cloud of a fluid. */
    public record Mist(String fluidType, int cloudCount, float width, float height, int areaSpread, int lifetime,
                       int lifetimeVariance) {

        public static final Mist DEFAULTS = new Mist("None", 1, 10F, 5F, 0, 80, 0);
    }

    /**
     * {@code NTM.Fire}: a spread of fire.
     *
     * @param fireType one of DIESEL, FIRE, BALEFIRE, PHOSPHORUS, OXY, BLACK
     */
    public record Fire(String fireType, int count, float width, float height, int areaSpread, int lifetime,
                       int lifetimeVariance, boolean particles) {

        public static final Fire DEFAULTS = new Fire("DIESEL", 1, 5.0F, 3.0F, 0, 150, 0, true);
    }
}
