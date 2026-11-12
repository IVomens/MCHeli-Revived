package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.client.model.BakedPart;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.vehicle.client.VehicleModels;

import javax.annotation.Nullable;

/**
 * Resolves a vehicle's parts to their meshes and sends them to a {@link PartSink}, skipping the ones
 * too small to see.
 * <p>
 * Level of detail: vehicles stay visible as far as vanilla renders them, but a part whose on-screen
 * size would drop below {@link #MIN_PART_PIXELS} is skipped together with its animation.
 *
 * @param pixelsPerBlock pixels one block covers at the vehicle; infinite draws every part
 */
record PartDrawer(VehicleModels models, float pixelsPerBlock, PartSink sink) {

    /** Parts smaller than this on screen (half their bounding-box diagonal, in pixels) are skipped. */
    private static final float MIN_PART_PIXELS = 1.5F;

    /** Whether the part exists and is big enough on screen to be drawn. */
    boolean isVisible(VehicleParts.DrawnPart part) {
        IModelCustom partModel = this.models.part(part);
        if (partModel != null) {
            return !(partModel instanceof BakedModelBase model) || this.isBigEnough(model.getExtentRadius());
        }
        if (this.models.body() instanceof BakedModelBase model) {
            BakedPart group = model.findPrefixedPart(part.modelName);
            return group != null && this.isBigEnough(group.extent());
        }
        return false;
    }

    private boolean isBigEnough(float extent) {
        return extent * this.pixelsPerBlock >= MIN_PART_PIXELS;
    }

    /** Draws a part: its own model file if it has one, else the {@code $<name>} group of the body. */
    void draw(VehicleParts.DrawnPart part) {
        IModelCustom partModel = this.models.part(part);
        if (partModel != null) {
            this.sink.draw(partModel);
        } else if (this.models.body() instanceof BakedModelBase body) {
            BakedPart group = body.findPrefixedPart(part.modelName);
            if (group != null) {
                this.sink.draw(group);
            }
        }
    }

    /** Draws a part if it is visible. */
    void drawIfVisible(VehicleParts.DrawnPart part) {
        if (this.isVisible(part)) {
            this.draw(part);
        }
    }

    /** Draws a part turned by {@code angle} about its own axis ({@code rot}) through its pivot ({@code pos}). */
    void drawRotated(VehicleParts.DrawnPart part, float angle) {
        if (angle == 0.0F) {
            this.draw(part);
            return;
        }
        this.sink.pushPose();
        this.rotateAbout(part, angle);
        this.draw(part);
        this.sink.popPose();
    }

    void rotateAbout(VehicleParts.DrawnPart part, float angle) {
        this.sink.rotateAround(angle, (float) part.rot.x, (float) part.rot.y, (float) part.rot.z,
                part.pos.x, part.pos.y, part.pos.z);
    }

    /** Draws a part that either slides along {@code pos} by {@code fraction} or turns by {@code angle}. */
    void drawSlidingOrRotated(VehicleParts.DrawnPart part, boolean slide, float fraction, float angle) {
        if (!slide) {
            this.drawRotated(part, angle);
            return;
        }
        this.sink.pushPose();
        this.sink.translate(part.pos.x * fraction, part.pos.y * fraction, part.pos.z * fraction);
        this.draw(part);
        this.sink.popPose();
    }

    /**
     * A propeller: the blade group spun by {@code spin} about the blade axis, then drawn
     * {@code count} times, each copy turned a further {@code step} degrees about the same axis.
     */
    void drawBladeRing(VehicleParts.DrawnPart blade, int count, float step, float spin) {
        if (!this.isVisible(blade)) {
            return;
        }
        this.sink.pushPose();
        this.rotateAbout(blade, spin);
        for (int i = 0; i < count; i++) {
            this.rotateAbout(blade, step);
            this.draw(blade);
        }
        this.sink.popPose();
    }

    /** The airframe: the {@code $body} group, or the whole model if it has none. */
    void drawBody() {
        IModelCustom model = this.models.body();
        if (model == null) {
            return;
        }
        BakedPart body = model instanceof BakedModelBase baked ? baked.findPrefixedPart("body") : null;
        if (body != null) {
            this.sink.draw(body);
        } else {
            this.sink.draw(model);
        }
    }

    @Nullable
    BakedModelBase bakedBody() {
        return this.models.body() instanceof BakedModelBase body ? body : null;
    }
}
