package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.hud.script.HudInstruction;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What the scripted HUDs of one viewer keep from one frame to the next. Content stays immutable; this is the
 * part that does not.
 * <ul>
 *     <li><b>The carried colour.</b> A {@code Color} command sets the colour of what is drawn after it, and the
 *     last one of a frame is still the colour at the start of the next. Scripts rely on it: commands before a
 *     script's first {@code Color} draw in the colour of the previous frame.</li>
 *     <li><b>Broken scripts.</b> A script that fails while drawing is logged and not drawn again until the HUDs
 *     reload, so it does not log every frame.</li>
 *     <li><b>Reported texts.</b> A {@code DrawString} whose format does not fit its values in a frame is logged
 *     once and skipped from then on; the rest of its HUD still draws.</li>
 *     <li><b>Texture sizes</b>, read from the resources the first time each {@code DrawTexture} draws.</li>
 * </ul>
 * Reloaded HUDs are new objects, so they start unbroken; the old ones are forgotten with them.
 */
public final class HudSession {

    /** The colour before any {@code Color} ran: opaque black. */
    static final int INITIAL_COLOR = 0xFF000000;
    private static final int FALLBACK_TEXTURE_SIZE = 256;

    private int color = INITIAL_COLOR;
    private final Set<Hud> broken = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<HudInstruction> reportedTexts = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<HudInstruction.DrawTexture, int[]> textureSizes = new WeakHashMap<>();

    /** The carried colour, ARGB. */
    public int color() {
        return this.color;
    }

    void setColor(int argb) {
        this.color = argb;
    }

    boolean isBroken(Hud hud) {
        return this.broken.contains(hud);
    }

    void markBroken(Hud hud) {
        this.broken.add(hud);
    }

    /** Whether {@code text} failed before; the first time it is remembered, and the caller logs it. */
    boolean alreadyReported(HudInstruction text) {
        return !this.reportedTexts.add(text);
    }

    /** Width and height in texels, read once; a texture that cannot be read counts as 256 by 256. */
    int[] textureSize(HudInstruction.DrawTexture drawn, ResourceLocation texture, HudCanvas canvas) {
        return this.textureSizes.computeIfAbsent(drawn, unused -> {
            int[] size = canvas.textureSize(texture);
            return new int[]{size[0] > 0 ? size[0] : FALLBACK_TEXTURE_SIZE, size[1] > 0 ? size[1] : FALLBACK_TEXTURE_SIZE};
        });
    }
}
