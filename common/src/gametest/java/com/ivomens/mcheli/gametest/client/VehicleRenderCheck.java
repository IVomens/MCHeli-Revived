package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.client.model.BakedModelBase;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.VehicleModelRegistry;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic vehicle render check, active with -Dmcheli.renderCheck=true: draws every vehicle of
 * the content pack through its entity renderer into an off-screen target, in fixed poses, light and
 * time of day, and writes one PNG per vehicle and view to {@code renderchecks/}. Two builds whose
 * images are byte-identical draw vehicles identically; the ordinary dev scene cannot show that,
 * because its sky, particles and animation timing differ from run to run.
 * <p>
 * Each vehicle is drawn four times: close up, filling the frame, then at middle and long range, where
 * the level of detail switches to the reduced meshes and drops small parts, and close up again with the
 * hull turned and tilted and the crew aiming off to the side, which turns the turrets and guns.
 */
public final class VehicleRenderCheck {
    private static final boolean ENABLED = Boolean.getBoolean("mcheli.renderCheck");
    private static final int SIZE = 256;
    private static final float FOV = 50.0F;
    /** Draws of a view before its image is taken; see {@link #draw}. */
    private static final int DRAWS_PER_IMAGE = 3;
    private static final int START_TICK = 100;
    private static final int LOAD_TIMEOUT_TICKS = 1200;
    private static final float[][] VIEWS = {
            // camera yaw, pitch, distance in model radii; vehicle yaw, pitch, roll; aim yaw, pitch
            {35.0F, 20.0F, 1.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {200.0F, -15.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {-140.0F, 10.0F, 40.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {35.0F, 20.0F, 1.7F, 25.0F, -8.0F, 12.0F, 70.0F, -15.0F},
    };

    private static int clientTicks;
    private static List<AircraftEntity> vehicles;

    private VehicleRenderCheck() {
    }

    static void onServerTick(MinecraftServer server) {
        if (!ENABLED) return;
        // fixed time of day and weather, so the lightmap is the same in every run
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        server.overworld().setDayTime(6000);
        server.overworld().setWeatherParameters(6000, 0, false, false);
    }

    static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.level == null || mc.player == null) return;
        clientTicks++;
        if (clientTicks == START_TICK) {
            vehicles = createVehicles(mc);
            draw(mc, false);
        } else if (clientTicks > START_TICK && vehicles != null) {
            boolean loaded = vehicles.stream().allMatch(v ->
                    VehicleModelRegistry.of(v.spec()).body() instanceof BakedModelBase);
            if (loaded || clientTicks > START_TICK + LOAD_TIMEOUT_TICKS) {
                draw(mc, true);
                // the level of detail scales with the window height, so a different window draws differently
                ModLogger.get().info("[rendercheck] drew {} vehicles, all loaded={}, window {}x{}", vehicles.size(),
                        loaded, mc.getWindow().getWidth(), mc.getWindow().getHeight());
                // every vehicle type, near (full detail) and far (parts dropped by the level of detail)
                Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
                RenderEquivalenceCheck.run("rendercheck-near", vehicles, camera, Float.POSITIVE_INFINITY);
                RenderEquivalenceCheck.run("rendercheck-far", vehicles, camera.add(0.0, -60.0, 0.0), 600.0F);
                vehicles = null;
                mc.stop();
            } else {
                draw(mc, false);
            }
        }
    }

    private static List<AircraftEntity> createVehicles(Minecraft mc) {
        List<VehicleSpec> infos = new ArrayList<>();
        infos.addAll(VehicleTypes.registry(VehicleKind.HELICOPTER).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.PLANE).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.SHIP).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.TANK).values());
        infos.addAll(VehicleTypes.registry(VehicleKind.VEHICLE).values());
        infos.sort(Comparator.comparing((VehicleSpec i) -> i.kind().name()).thenComparing(i -> i.name()));
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        List<AircraftEntity> created = new ArrayList<>();
        for (VehicleSpec info : infos) {
            if (VehicleTypes.item(info).orElse(null) instanceof AircraftItem item) {
                AircraftEntity vehicle = item.createAircraft(mc.level, camera.x, camera.y, camera.z, new ItemStack(item));
                if (vehicle != null) {
                    // the server names the texture when it spawns a vehicle; these never reach it
                    vehicle.appearance().setTexture(info.name());
                    created.add(vehicle);
                }
            }
        }
        return created;
    }

    /** Draws every vehicle; only the final pass, with every model loaded, writes the images. */
    private static void draw(Minecraft mc, boolean save) {
        File dir = new File(mc.gameDirectory, "renderchecks");
        dir.mkdirs();
        RenderTarget target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        try {
            dispatcher.setRenderShadow(false);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            FogRenderer.setupNoFog();
            Lighting.setupLevel();
            for (AircraftEntity vehicle : vehicles) {
                for (int view = 0; view < VIEWS.length; view++) {
                    // the saved image is the third draw of the view: the first two draw the meshes and the
                    // third copies them from the vertex blocks the renderer keeps, so both paths are checked
                    for (int draw = 0; draw < (save ? DRAWS_PER_IMAGE : 1); draw++) {
                        drawView(mc, target, vehicle, VIEWS[view]);
                    }
                    if (save) {
                        save(target, new File(dir, vehicle.spec().kind().name().toLowerCase() + "_"
                                + vehicle.spec().name() + "_" + view + ".png"));
                    }
                }
            }
        } finally {
            dispatcher.setRenderShadow(true);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void drawView(Minecraft mc, RenderTarget target, AircraftEntity vehicle, float[] view) {
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        VehicleSpec info = vehicle.spec();
        float radius = VehicleModelRegistry.of(info).body() instanceof BakedModelBase model
                ? Math.max(1.0F, model.getBoundingRadius()) : 4.0F;
        float distance = radius * view[2];
        // the vehicle stands that far above the game camera, so its level of detail matches the image and
        // its sampled light is open sky in every run
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        vehicle.setPos(camera.x, camera.y + distance, camera.z);
        vehicle.xo = vehicle.xOld = vehicle.getX();
        vehicle.yo = vehicle.yOld = vehicle.getY();
        vehicle.zo = vehicle.zOld = vehicle.getZ();
        vehicle.setYRot(view[3]);
        vehicle.setXRot(view[4]);
        vehicle.rollAngle().set(view[5]);
        vehicle.yRotO = view[3];
        vehicle.xRotO = view[4];
        vehicle.rollAngle().setPrevious(view[5]);
        vehicle.aim().setRiderLook(view[6], view[6], view[7], view[7]);
        if (view[6] != 0.0F) {
            // ground vehicles otherwise reset an unmanned aim to straight ahead
            vehicle.aim().markAimed();
        }
        // near and far planes hug the model, so depth precision does not depend on the distance
        float near = Math.max(0.05F, distance - 2.0F * radius);
        RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(FOV), 1.0F, near,
                distance + 2.0F * radius), VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack pose = new PoseStack();
        pose.translate(0.0F, 0.0F, -distance);
        pose.mulPose(Axis.XP.rotationDegrees(view[1]));
        pose.mulPose(Axis.YP.rotationDegrees(view[0]));
        pose.translate(0.0F, -radius * 0.25F, 0.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        mc.getEntityRenderDispatcher().render(vehicle, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, buffers,
                LightTexture.pack(0, 15));
        buffers.endBatch();
    }

    private static void save(RenderTarget target, File file) {
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            image.writeToFile(file);
        } catch (IOException e) {
            ModLogger.get().info("[rendercheck] could not write {}: {}", file, e.toString());
        }
    }
}
