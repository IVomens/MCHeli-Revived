package com.ivomens.mcheli.hud.client.widget;

/** Which HUD was drawn under the widgets, so they go where it leaves room. */
public enum HudLayout {
    /** The modern HUD's flight layout: tapes, pitch ladder, system bars and weapon card. */
    FLIGHT,
    /** The modern HUD's gunner sight. */
    SIGHT,
    /** A legacy scripted HUD, which may draw anywhere. */
    LEGACY
}
