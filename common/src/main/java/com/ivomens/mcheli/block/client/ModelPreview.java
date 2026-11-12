package com.ivomens.mcheli.block.client;

import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.ModelBounds;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.vehicle.client.render.ImmediateVehicleRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

/**
 * The drafting table's model preview: the selected vehicle drawn as if being drafted, its wireframe
 * running ahead of the filled faces, then turned with the left mouse button, zoomed with the right and
 * moved with the middle one.
 */
final class ModelPreview {

    /** The longest side of a model fills this many GUI pixels at zoom 1. */
    private static final double VIEW_SIZE = 162.0;
    private static final float MAX_OFFSET = 1000.0F;
    private static final float MIN_ZOOM = 0.1F;
    private static final float MAX_ZOOM = 10.0F;
    /** Faces of the "being drawn" animation added per frame. */
    private static final int FACES_PER_FRAME = 20;
    private static final int FINISHED = 10_000_000;

    private float zoom = 1.0F;
    private float rotX;
    private float rotY;
    private float posX;
    private float posY;
    private int drawnFaces;

    /** Shows a model from scratch at these angles; planes and ships are modelled facing another way. */
    void reset(float rotX, float rotY) {
        this.zoom = 1.0F;
        this.rotX = rotX;
        this.rotY = rotY;
        this.posX = 0.0F;
        this.posY = 0.0F;
    }

    /** Centres the model again, and draws it again from the first face when it is a different one. */
    void show(boolean sameModel) {
        this.posX = 0.0F;
        this.posY = 0.0F;
        if (!sameModel) {
            this.drawnFaces = 0;
        }
    }

    void drag(int button, double dragX, double dragY) {
        double dy = -dragY;
        switch (button) {
            case 0 -> {
                this.rotX = wrap((float) (this.rotX - dy / 2.0));
                this.rotY = wrap((float) (this.rotY - dragX / 2.0));
            }
            case 2 -> {
                this.posX = Mth.clamp((float) (this.posX + dragX / 2.0), -MAX_OFFSET, MAX_OFFSET);
                this.posY = Mth.clamp((float) (this.posY - dy / 2.0), -MAX_OFFSET, MAX_OFFSET);
            }
            case 1 -> this.zoom = Mth.clamp((float) (this.zoom + dy / 100.0), MIN_ZOOM, MAX_ZOOM);
            default -> {
            }
        }
    }

    private static float wrap(float angle) {
        if (angle > 360.0F) angle -= 360.0F;
        if (angle < -360.0F) angle += 360.0F;
        return angle;
    }

    /** Draws the recipe's model centred on {@code (centerX, centerY)}. */
    void render(GuiGraphics g, CurrentRecipe recipe, int centerX, int centerY) {
        BakedModelBase model = recipe.getModel();
        ModelBounds bounds = model.bounds();
        double scale = VIEW_SIZE / (Mth.abs(bounds.size()) < 0.01 ? 0.01 : bounds.size());
        g.flush();
        LegacyGL.begin(g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
        try {
            LegacyGL.pushMatrix();
            double cx = (bounds.maxX() - bounds.minX()) * 0.5 + bounds.minX();
            double cy = (bounds.maxY() - bounds.minY()) * 0.5 + bounds.minY();
            double cz = (bounds.maxZ() - bounds.minZ()) * 0.5 + bounds.minZ();
            if (recipe.modelRot() == 0) {
                LegacyGL.translate(cx * scale, cz * scale, 0.0);
            } else {
                LegacyGL.translate(cz * scale, cy * scale, 0.0);
            }
            LegacyGL.translate(centerX + this.posX, centerY + this.posY, 550.0);
            LegacyGL.rotate(this.rotX, 1.0F, 0.0F, 0.0F);
            LegacyGL.rotate(this.rotY, 0.0F, 1.0F, 0.0F);
            LegacyGL.scale(scale * this.zoom, scale * this.zoom, -scale * this.zoom);
            LegacyGL.disableLighting();
            LegacyGL.enableBlend();
            LegacyGL.bindTexture(recipe.getModelTexture());
            int faces = model.getFaceNum();
            if (this.drawnFaces < faces * 2) {
                LegacyGL.color(0.1F, 0.1F, 0.1F, 1.0F);
                model.renderAllLine(Math.max(this.drawnFaces - faces, 0), this.drawnFaces);
            }
            if (this.drawnFaces >= faces) {
                LegacyGL.color(1.0F, 1.0F, 1.0F, 1.0F);
                model.renderAll(0, this.drawnFaces - faces);
                ImmediateVehicleRenderer.renderTracksAtRest(recipe.vehicleSpec());
            }
            LegacyGL.enableLighting();
            LegacyGL.popMatrix();
        } finally {
            LegacyGL.end();
        }
        g.flush();
        if (this.drawnFaces < FINISHED) {
            this.drawnFaces += FACES_PER_FRAME;
        }
    }
}
