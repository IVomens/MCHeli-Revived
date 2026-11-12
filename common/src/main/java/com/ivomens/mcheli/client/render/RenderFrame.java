package com.ivomens.mcheli.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One open {@link LegacyGL} frame: the pose stack and buffers vanilla handed to the renderer, its light and
 * overlay, and the current state with the states {@code pushAttrib} saved.
 */
final class RenderFrame {

    final RenderState state = new RenderState();
    final Deque<RenderState> savedStates = new ArrayDeque<>();
    /** Spare states for {@code LegacyGL.pushAttrib()}, which bullet renderers call per bullet and frame. */
    final Deque<RenderState> statePool = new ArrayDeque<>();
    PoseStack pose;
    MultiBufferSource buffers;
    int light;
    int overlay;
    /** Pixels one block covers at the vehicle being drawn; see {@code LegacyGL.setDetailPixelsPerBlock}. */
    float detailPixelsPerBlock = Float.POSITIVE_INFINITY;

    void reset(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        this.pose = pose;
        this.buffers = buffers;
        this.light = light;
        this.overlay = overlay;
        this.detailPixelsPerBlock = Float.POSITIVE_INFINITY;
        this.state.reset();
        while (!this.savedStates.isEmpty()) {
            this.statePool.push(this.savedStates.pop());
        }
    }

    void release() {
        this.pose = null;
        this.buffers = null;
    }

    int effectiveLight() {
        return this.state.fullBright ? LightTexture.FULL_BRIGHT : this.light;
    }
}
