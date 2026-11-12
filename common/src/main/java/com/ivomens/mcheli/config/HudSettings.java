package com.ivomens.mcheli.config;

/** The aircraft HUD. Client side. */
public record HudSettings(String style, String color, double scale, boolean displayInThirdPerson,
                          boolean autoScaleAircraftGui) {

    private static final int DEFAULT_COLOR = 0xFF7FFFD4;

    public static final StringOption STYLE = StringOption.of("HudStyle", "modern")
            .describe("HudStyle = modern or legacy. Client side. 'legacy' draws the scripted HUD files from content"
                    + " packs exactly as before.");
    public static final StringOption COLOR = StringOption.of("HudColor", "7FFFD4")
            .describe("HudColor = colour of the modern HUD as RRGGBB hex (default 7FFFD4, hologram green).");
    public static final DoubleOption SCALE = DoubleOption.of("HudScale", 1.0).range(0.5, 2.0)
            .describe("HudScale = size of the modern HUD, 0.5 to 2.0.");
    public static final BoolOption DISPLAY_IN_THIRD_PERSON = BoolOption.of("DisplayHUDThirdPerson", false);
    public static final BoolOption AUTO_SCALE_AIRCRAFT_GUI = BoolOption.of("ExperimentalAutoScaleAircraftGui", false)
            .describe("ExperimentalAutoScaleAircraftGui = true maps hardcoded aircraft HUD coordinates from the"
                    + " default 427x240 canvas to the current GUI resolution.");

    static HudSettings from(ConfigValues v) {
        return new HudSettings(v.get(STYLE), v.get(COLOR), v.get(SCALE), v.get(DISPLAY_IN_THIRD_PERSON),
                v.get(AUTO_SCALE_AIRCRAFT_GUI));
    }

    public boolean useModernHud() {
        return !"legacy".equalsIgnoreCase(this.style.trim());
    }

    /** The modern HUD colour as opaque ARGB; the default when the option is not valid hex. */
    public int colorArgb() {
        try {
            return 0xFF000000 | Integer.parseInt(this.color.trim().replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }
}
