package com.ivomens.mcheli.config;

import com.ivomens.mcheli.core.sim.port.SimSettings;

/**
 * The part of the server's config a client has to respect, as last sent by the server. Until the
 * first sync a client uses {@link #DEFAULTS}.
 */
public record ServerSettings(boolean enableCamDistChange, boolean enableEntityMarker, boolean enablePVP,
                             double stingerLockRange, boolean enableDebugBoundingBox, boolean enableRotationLimit,
                             float pitchLimitMax, float pitchLimitMin, float rollLimit, boolean enableLiveries) {

    public static final ServerSettings DEFAULTS = new ServerSettings(true, true, true, 120.0, true, false, 10.0F,
            10.0F, 35.0F, true);

    /** What a server with this config sends; PvP comes from the server itself. */
    public static ServerSettings of(McheliSettings config, boolean pvp) {
        return new ServerSettings(!config.controls().disableCameraDistChange(), config.render().displayEntityMarker(),
                pvp, config.combat().stingerLockRange(), config.debug().debugBoundingBox(),
                config.flight().enableRotationLimit(), config.flight().pitchLimitMax(), config.flight().pitchLimitMin(),
                config.flight().rollLimit(), config.liveries().enabled());
    }

    /** The helicopter tilt limit, for the physics. */
    public SimSettings.TiltLimit tiltLimit() {
        return new SimSettings.TiltLimit(this.enableRotationLimit, this.pitchLimitMin, this.pitchLimitMax,
                this.rollLimit);
    }
}
