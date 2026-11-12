package com.ivomens.mcheli.vehicle.client.render;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.vehicle.client.VehicleModelRegistry;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.Tint;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot.TrackOffsets;

/**
 * The immediate backend: draws the model into the open {@link LegacyGL} frame, which must be posed at
 * the vehicle's origin, streaming every vertex through the vanilla buffers. It is what the mod has
 * always drawn, works on every GL profile and under shader packs, and is the base for the 26.x
 * rendering API, so it stays complete even where Flywheel draws the models.
 */
public final class ImmediateVehicleRenderer implements VehicleModelRenderer {

    public static final ImmediateVehicleRenderer INSTANCE = new ImmediateVehicleRenderer();
    /** The usual model shade, set before the vehicle's own tint as the original renderer did. */
    private static final float MODEL_SHADE = 0.75F;

    private ImmediateVehicleRenderer() {
    }

    @Override
    public void render(VehicleRenderSnapshot snapshot) {
        LegacyGL.enableLighting();
        LegacyGL.enableCull();
        LegacyGL.setLight(snapshot.light());
        LegacyGL.color(MODEL_SHADE, MODEL_SHADE, MODEL_SHADE, 1.0F);
        LegacyGL.enableBlend();
        Tint tint = snapshot.tint();
        LegacyGL.color(tint.red(), tint.green(), tint.blue(), tint.alpha());
        LegacyGL.pushMatrix();
        try {
            VehicleModelPoser.applyHull(LegacyGLPartSink.INSTANCE, snapshot.hull());
            LegacyGL.bindTexture(snapshot.texture());
            VehicleModelPoser.pose(snapshot, LegacyGLPartSink.INSTANCE);
        } finally {
            LegacyGL.popMatrix();
        }
    }

    /**
     * The animated crawler track links of a definition at rest and in full detail, at the current
     * pose (the drafting table preview).
     */
    public static void renderTracksAtRest(VehicleSpec info) {
        PartDrawer drawer = new PartDrawer(VehicleModelRegistry.of(info), Float.POSITIVE_INFINITY,
                LegacyGLPartSink.INSTANCE);
        CommonPartPoser.poseCrawlerTracks(info, TrackOffsets.AT_REST, drawer);
    }

    /** Sends parts straight into the current {@link LegacyGL} frame. */
    public static class LegacyGLPartSink implements PartSink {

        static final LegacyGLPartSink INSTANCE = new LegacyGLPartSink();

        protected LegacyGLPartSink() {
        }

        @Override
        public void pushPose() {
            LegacyGL.pushMatrix();
        }

        @Override
        public void popPose() {
            LegacyGL.popMatrix();
        }

        @Override
        public void translate(double x, double y, double z) {
            LegacyGL.translate(x, y, z);
        }

        @Override
        public void rotate(float degrees, float axisX, float axisY, float axisZ) {
            LegacyGL.rotate(degrees, axisX, axisY, axisZ);
        }

        @Override
        public void rotateAround(float degrees, float axisX, float axisY, float axisZ, double pivotX, double pivotY,
                                 double pivotZ) {
            LegacyGL.rotateAround(degrees, axisX, axisY, axisZ, pivotX, pivotY, pivotZ);
        }

        @Override
        public void draw(PartMesh mesh) {
            mesh.render();
        }

        @Override
        public void draw(IModelCustom model) {
            model.renderAll();
        }
    }
}
