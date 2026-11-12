package com.ivomens.mcheli.hud.script;

import java.util.List;

/**
 * A HUD file compiled for drawing: its commands as a flat list of {@link HudInstruction}s, formulas compiled
 * and blocks lowered to gates or jumps. Immutable; what a frame changes while it runs lives in the renderer.
 *
 * @param file            the file it was compiled from, for messages
 * @param ignoreAutoScale draw at the design size even when the player auto-scales the HUD
 * @param code            the instructions; jump targets are indices into it
 */
public record HudScript(String file, boolean ignoreAutoScale, List<HudInstruction> code) {

    public HudScript {
        code = List.copyOf(code);
    }
}
