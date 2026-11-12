package com.ivomens.mcheli.fx;

import com.ivomens.mcheli.platform.Services;

/** Picks the {@link Effects} to draw: the client's configured style, nothing on a dedicated server. */
@FunctionalInterface
public interface EffectStyle {

    EffectStyle INSTANCE = Services.sided(EffectStyle.class, () -> Effects.NONE);

    Effects effects();
}
