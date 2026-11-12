package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.config.FlightSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.RenderSettings;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The client settings the benchmark changes, as the player had them. The benchmark draws at a fixed
 * render distance with no frame cap and no vsync, in first person, and keeps running when the window
 * loses focus; {@code VehicleRenderer} and {@code FixedStepAttitude} change per step. None of it is
 * written to disk, and {@link #restore} puts all of it back; should the game die mid-run after
 * something else saved them, {@link BenchmarkRecovery} puts them back on the next start.
 */
record ClientSettings(int renderDistance, int framerateLimit, boolean vsync, boolean pauseOnLostFocus,
                      CameraType camera, boolean hideGui, McheliSettings config) {

    static final int RENDER_DISTANCE = 12;
    /** The slider's top value, which Minecraft treats as no limit. */
    private static final int UNLIMITED_FPS = 260;

    static ClientSettings capture(Minecraft mc) {
        Options o = mc.options;
        return new ClientSettings(o.renderDistance().get(), o.framerateLimit().get(), o.enableVsync().get(),
                o.pauseOnLostFocus, o.getCameraType(), o.hideGui, McheliConfig.get());
    }

    static void applyBenchmark(Minecraft mc) {
        Options o = mc.options;
        o.renderDistance().set(RENDER_DISTANCE);
        o.framerateLimit().set(UNLIMITED_FPS);
        o.enableVsync().set(false);
        o.pauseOnLostFocus = false;
        o.hideGui = false;
        o.setCameraType(CameraType.FIRST_PERSON);
    }

    /** Sets the two mod settings a step compares. */
    static void applyStep(BenchmarkPlan.Step step) {
        McheliConfig.update(settings -> settings
                .with(RenderSettings.VEHICLE_RENDERER, step.renderer())
                .with(FlightSettings.FIXED_STEP_ATTITUDE, step.fixedStep()));
    }

    void restore(Minecraft mc) {
        Options o = mc.options;
        o.renderDistance().set(this.renderDistance);
        o.framerateLimit().set(this.framerateLimit);
        o.enableVsync().set(this.vsync);
        o.pauseOnLostFocus = this.pauseOnLostFocus;
        o.hideGui = this.hideGui;
        o.setCameraType(this.camera);
        McheliConfig.set(this.config);
    }
}
