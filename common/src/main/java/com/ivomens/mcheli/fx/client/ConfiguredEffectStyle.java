package com.ivomens.mcheli.fx.client;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.fx.EffectStyle;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.client.legacy.LegacyEffects;
import com.ivomens.mcheli.fx.client.modern.ModernEffects;

/** The client's effect style: modern or legacy, as the particle setting says at the moment of the effect. */
public final class ConfiguredEffectStyle implements EffectStyle {

    private final Effects legacy = new LegacyEffects();
    private final Effects modern = new ModernEffects();

    @Override
    public Effects effects() {
        return McheliConfig.useModernParticles() ? this.modern : this.legacy;
    }
}
