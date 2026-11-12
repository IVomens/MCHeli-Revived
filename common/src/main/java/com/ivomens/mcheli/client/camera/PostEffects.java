package com.ivomens.mcheli.client.camera;

import com.ivomens.mcheli.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The camera post effects: night and thermal vision. A camera mode says which effect it wants with
 * {@link #use}; the effect is loaded whenever the view switches to a new camera entity, which drops the
 * game's current post chain.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class PostEffects {

    /** The post effect the current camera mode wants, or null for none. */
    @Nullable
    private ResourceLocation wanted;
    private ResourceLocation lastRequested;
    private String lastRequestedName;

    /** Sets the effect {@link #applyWanted} applies; null also removes ours right away. */
    public void use(@Nullable ResourceLocation effect) {
        this.wanted = effect;
        if (effect == null) {
            deactivate();
        }
    }

    /** Applies the wanted effect, if any; a new camera entity needs it applied again. */
    void applyWanted() {
        if (this.wanted != null) {
            activate(this.wanted);
        }
    }

    /** Loads the post chain unless it is already the active one; cheap enough to call every frame. */
    private void activate(ResourceLocation location) {
        if (location != this.lastRequested) {
            this.lastRequested = location;
            this.lastRequestedName = location.toString();
        }
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        PostChain current = renderer.currentEffect();
        if (current == null || !current.getName().equals(this.lastRequestedName)) {
            renderer.loadEffect(location);
        }
    }

    /** Removes the active post effect if it is one of ours; vanilla's spectator effects stay. */
    private static void deactivate() {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        PostChain current = renderer.currentEffect();
        if (current != null && current.getName().startsWith(Ids.MOD_ID + ":")) {
            renderer.shutdownEffect();
        }
    }
}
