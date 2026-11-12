package com.ivomens.mcheli.config;

import net.minecraft.util.Mth;

/** How vehicles, markers and effects are drawn. Client side. */
public record RenderSettings(int disableItemRender, boolean hideKeybind, double renderDistanceWeight,
                             double mobRenderDistanceWeight, boolean disableShader, boolean defaultExplosionParticle,
                             int aliveTimeOfCartridge, String hitMarkColor, float hitMarkAlpha, int hitMarkRgb,
                             boolean smoothShading, boolean enableModEntityRender,
                             boolean disableRenderLivingSpecials, boolean enableReplaceTextureManager,
                             boolean displayEntityMarker, double entityMarkerSize, double blockMarkerSize,
                             boolean replaceRenderViewEntity, String particleStyle, String vehicleRenderer) {

    private static final float DEFAULT_HIT_MARK_ALPHA = 1.0F;
    private static final int DEFAULT_HIT_MARK_RGB = 0xFF0000;

    public static final IntOption DISABLE_ITEM_RENDER = IntOption.of("DisableItemRender", 1)
            .describe("DisableItemRender = 0 ~ 3 (1 = Recommended)");
    public static final BoolOption HIDE_KEYBIND = BoolOption.of("HideKeybind", false);
    public static final DoubleOption RENDER_DISTANCE_WEIGHT = DoubleOption.of("RenderDistanceWeight", 10.0);
    public static final DoubleOption MOB_RENDER_DISTANCE_WEIGHT = DoubleOption.of("MobRenderDistanceWeight", 1.0)
            .range(0.1, 10.0);
    public static final BoolOption DISABLE_SHADER = BoolOption.of("DisableShader", false);
    public static final BoolOption DEFAULT_EXPLOSION_PARTICLE = BoolOption.of("DefaultExplosionParticle", false);
    public static final IntOption ALIVE_TIME_OF_CARTRIDGE = IntOption.of("AliveTimeOfCartridge", 200);
    public static final StringOption HIT_MARK_COLOR = StringOption.of("HitMarkColor", "255, 255, 0, 0")
            .describe("HitMarkColor = Alpha, Red, Green, Blue");
    public static final BoolOption SMOOTH_SHADING = BoolOption.of("SmoothShading", true);
    public static final BoolOption ENABLE_MOD_ENTITY_RENDER = BoolOption.of("EnableModEntityRender", true);
    public static final BoolOption DISABLE_RENDER_LIVING_SPECIALS = BoolOption.of("DisableRenderLivingSpecials", true);
    public static final BoolOption ENABLE_REPLACE_TEXTURE_MANAGER = BoolOption.of("EnableReplaceTextureManager", true);
    public static final BoolOption DISPLAY_ENTITY_MARKER = BoolOption.of("DisplayEntityMarker", true);
    public static final DoubleOption ENTITY_MARKER_SIZE = DoubleOption.of("EntityMarkerSize", 10.0).atLeast(0.0);
    public static final DoubleOption BLOCK_MARKER_SIZE = DoubleOption.of("BlockMarkerSize", 10.0).atLeast(0.0);
    public static final BoolOption REPLACE_RENDER_VIEW_ENTITY = BoolOption.of("ReplaceRenderViewEntity", true);
    public static final StringOption PARTICLE_STYLE = StringOption.of("ParticleStyle", "modern")
            .describe("ParticleStyle = modern or legacy. Client side. 'legacy' uses the original pixel smoke and"
                    + " vanilla particles.");
    public static final StringOption VEHICLE_RENDERER = StringOption.of("VehicleRenderer", "immediate")
            .describe("VehicleRenderer = immediate, auto or flywheel. Client side. 'immediate' always uses the"
                    + " built-in renderer; 'auto' instances vehicle models with Flywheel when it is installed and"
                    + " runs its indirect backend (OpenGL 4.6), and keeps the built-in renderer on its instancing"
                    + " backend (macOS, older drivers), where it was far slower; 'flywheel' uses Flywheel on any"
                    + " backend, for testing (experimental).");

    static RenderSettings from(ConfigValues v) {
        String hitMarkColor = v.get(HIT_MARK_COLOR);
        int[] argb = parseArgb(hitMarkColor);
        float hitMarkAlpha = argb == null ? DEFAULT_HIT_MARK_ALPHA : argb[0] / 255.0F;
        int hitMarkRgb = argb == null ? DEFAULT_HIT_MARK_RGB : argb[1] << 16 | argb[2] << 8 | argb[3];
        return new RenderSettings(v.get(DISABLE_ITEM_RENDER), v.get(HIDE_KEYBIND), v.get(RENDER_DISTANCE_WEIGHT),
                v.get(MOB_RENDER_DISTANCE_WEIGHT), v.get(DISABLE_SHADER), v.get(DEFAULT_EXPLOSION_PARTICLE),
                v.get(ALIVE_TIME_OF_CARTRIDGE), hitMarkColor, hitMarkAlpha, hitMarkRgb, v.get(SMOOTH_SHADING),
                v.get(ENABLE_MOD_ENTITY_RENDER), v.get(DISABLE_RENDER_LIVING_SPECIALS),
                v.get(ENABLE_REPLACE_TEXTURE_MANAGER), v.get(DISPLAY_ENTITY_MARKER), v.get(ENTITY_MARKER_SIZE),
                v.get(BLOCK_MARKER_SIZE), v.get(REPLACE_RENDER_VIEW_ENTITY), v.get(PARTICLE_STYLE),
                v.get(VEHICLE_RENDERER));
    }

    /** The hit marker colour as written by the config screen. */
    public static String formatHitMarkColor(int alpha, int red, int green, int blue) {
        return alpha + ", " + red + ", " + green + ", " + blue;
    }

    public boolean useModernParticles() {
        return !"legacy".equalsIgnoreCase(this.particleStyle.trim());
    }

    /**
     * Whether Flywheel may draw vehicle models: {@code auto} (where Flywheel runs a backend that suits
     * vehicles, see {@code VehicleRenderBackend}) or {@code flywheel}. Anything else keeps the built-in
     * renderer.
     */
    public boolean allowsFlywheelVehicleRenderer() {
        return isAutoVehicleRenderer() || forcesFlywheelVehicleRenderer();
    }

    /** {@code VehicleRenderer = auto}: Flywheel draws vehicles only on a backend that suits them. */
    public boolean isAutoVehicleRenderer() {
        return "auto".equalsIgnoreCase(this.vehicleRenderer.trim());
    }

    /** {@code VehicleRenderer = flywheel}: Flywheel draws vehicles on whatever backend it runs. */
    public boolean forcesFlywheelVehicleRenderer() {
        return "flywheel".equalsIgnoreCase(this.vehicleRenderer.trim());
    }

    /** "alpha, red, green, blue", each clamped to 0-255; null when the text is not four whole numbers. */
    private static int[] parseArgb(String text) {
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length != 4) {
            return null;
        }
        int[] argb = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                argb[i] = Mth.clamp(Integer.parseInt(parts[i].trim()), 0, 255);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return argb;
    }
}
