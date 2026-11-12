package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;
import net.minecraft.client.resources.language.I18n;

/**
 * The words and colours the HUDs use for missile warnings: the seeker of a missile and the countermeasure
 * to drop, shared by the RWR scope and the modern HUD so they read the same.
 */
public final class ThreatLabels {

    /** Heat seekers: orange, the colour of a flare. */
    public static final int HEAT_COLOR = 0xFFFF9A2E;
    /** Radar missiles: cyan. */
    public static final int RADAR_COLOR = 0xFF4FD2FF;
    /** Both seekers. */
    public static final int DUAL_COLOR = 0xFFFF66E0;
    public static final int OTHER_COLOR = 0xFFE0E0E0;
    /** The cue blinks on and off every this many ticks. */
    private static final int CUE_BLINK_TICKS = 4;

    private ThreatLabels() {
    }

    /** The short tag of a missile's seeker ("IR", "RDR"), empty for a radar threat. */
    public static String seeker(ThreatSeeker seeker) {
        return switch (seeker) {
            case NONE -> "";
            case HEAT -> I18n.get("mcheli.hud.seeker.heat");
            case RADAR -> I18n.get("mcheli.hud.seeker.radar");
            case DUAL -> I18n.get("mcheli.hud.seeker.dual");
            case OTHER -> I18n.get("mcheli.hud.seeker.other");
        };
    }

    public static int color(ThreatSeeker seeker) {
        return switch (seeker) {
            case HEAT -> HEAT_COLOR;
            case RADAR -> RADAR_COLOR;
            case DUAL -> DUAL_COLOR;
            case NONE, OTHER -> OTHER_COLOR;
        };
    }

    /**
     * The countermeasures to drop against the incoming missiles ("FLARE", "CHAFF", or both), empty when
     * none of those the vehicle carries would help.
     */
    public static String cue(IncomingMissiles incoming, boolean carriesFlares, boolean carriesChaff) {
        boolean flares = incoming.cueFlares(carriesFlares);
        boolean chaff = incoming.cueChaff(carriesChaff);
        if (flares && chaff) {
            return I18n.get("mcheli.hud.cue.flare_chaff");
        }
        if (flares) {
            return I18n.get("mcheli.hud.cue.flare");
        }
        return chaff ? I18n.get("mcheli.hud.cue.chaff") : "";
    }

    /** The colour of a cue: the colour of the seeker it answers. */
    public static int cueColor(IncomingMissiles incoming, boolean carriesFlares, boolean carriesChaff) {
        boolean flares = incoming.cueFlares(carriesFlares);
        boolean chaff = incoming.cueChaff(carriesChaff);
        return flares && chaff ? DUAL_COLOR : flares ? HEAT_COLOR : RADAR_COLOR;
    }

    /** Whether a blinking cue is lit at the game time. */
    public static boolean cueLit(long gameTime) {
        return (gameTime / CUE_BLINK_TICKS & 1L) == 0L;
    }

    /** The counts as the modern HUD shows them next to the warning, "IR 2  RDR 1"; empty if unknown. */
    public static String counts(IncomingMissiles incoming) {
        StringBuilder text = new StringBuilder();
        if (incoming.heat() > 0) {
            text.append(I18n.get("mcheli.hud.seeker.heat")).append(' ').append(incoming.heat());
        }
        if (incoming.radar() > 0) {
            if (!text.isEmpty()) {
                text.append("  ");
            }
            text.append(I18n.get("mcheli.hud.seeker.radar")).append(' ').append(incoming.radar());
        }
        return text.toString();
    }
}
