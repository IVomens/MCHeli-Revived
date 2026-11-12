package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.livery.client.LiveryTemplates;
import com.ivomens.mcheli.setup.client.McheliClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Development scene, only active with -Dmcheli.devScene=true: spawns a row of vehicles in front of
 * the first player, boards one, and takes screenshots so the renderers can be checked headlessly. The
 * loader's game test layer calls the hooks through {@link ClientSceneHooks}.
 */
public final class DevScene {
    private static final boolean ENABLED = Boolean.getBoolean("mcheli.devScene");
    private static int serverTicks;
    private static int clientTicks;
    private static AircraftEntity boarded;

    private DevScene() {
    }

    static void onServerTick(MinecraftServer server) {
        if (!ENABLED) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer p = players.getFirst();
        serverTicks++;
        if (serverTicks == 40) {
            ServerLevel level = p.serverLevel();
            List<VehicleSpec> infos = new ArrayList<>();
            pick(infos, VehicleTypes.registry(VehicleKind.HELICOPTER).values(), "ah-64", "uh-1c", "mi-24");
            pick(infos, VehicleTypes.registry(VehicleKind.PLANE).values(), "f-35a", "a-10", "su-33");
            pick(infos, VehicleTypes.registry(VehicleKind.TANK).values(), "m1a2", "t-90");
            pick(infos, VehicleTypes.registry(VehicleKind.VEHICLE).values(), "mim-23", "oto76mm");
            pick(infos, VehicleTypes.registry(VehicleKind.SHIP).values(), "cb90");
            double x = p.getX() - infos.size() * 6;
            for (VehicleSpec info : infos) {
                if (VehicleTypes.item(info).orElse(null) instanceof AircraftItem item) {
                    AircraftEntity ac = item.createAircraft(level, x, p.getY(), p.getZ() + 18, new ItemStack(item));
                    if (ac != null) {
                        level.addFreshEntity(ac);
                        if (boarded == null && info.name().equals("ah-64")) boarded = ac;
                    }
                }
                x += 12;
            }
            p.teleportTo(p.getX(), p.getY() + 3, p.getZ() - 6);
            p.setYRot(0);
            p.setXRot(10);
        }
        if (serverTicks == 260 && boarded != null) {
            p.teleportTo(boarded.getX() + 2, boarded.getY(), boarded.getZ());
            for (int i = 0; i < 4 && boarded.seats().seatIdOf(p) < 0; i++) {
                boarded.boarding().interact(p, InteractionHand.MAIN_HAND);
            }
        }
        if (serverTicks == 300 && boarded != null && boarded.seats().seatIdOf(p) < 0) {
            boarded.boarding().interact(p, InteractionHand.MAIN_HAND);
        }
        if (serverTicks == 340 && boarded != null) {
            for (int id = 0; id < boarded.weapons().count(); id++) {
                boarded.weapons().select(p, id);
            }
            boarded.weapons().select(p, 0);
        }
        if (serverTicks == 380 && boarded != null) {
            boarded.weapons().fire(p);
        }
        if (serverTicks == 390 && boarded != null) {
            paintBoarded(p);
        }
        RwrScene.onServerTick();
    }

    /** Uploads a red-tinted copy of the AH-64 texture through the livery manager and applies it. */
    private static void paintBoarded(ServerPlayer p) {
        LiveryManager manager = LiveryManager.of(p.server);
        try (InputStream in = DevScene.class.getResourceAsStream("/assets/mcheli/textures/helicopters/ah-64.png")) {
            if (manager == null || in == null) return;
            BufferedImage image = ImageIO.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int gray = ((argb >> 16 & 0xFF) + (argb >> 8 & 0xFF) + (argb & 0xFF)) / 3;
                    image.setRGB(x, y, argb & 0xFF000000 | Math.min(255, gray + 90) << 16 | gray / 2 << 8 | gray / 2);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            byte[] png = out.toByteArray();
            LiveryManager.UploadStatus status = manager.beginUpload(p, "ah-64", "Red Baron", png.length);
            for (int offset = 0; offset < png.length && status.state() == LiveryManager.UploadStatus.State.RECEIVING;
                 offset += LiveryManager.MAX_CHUNK) {
                status = manager.receiveChunk(p, offset, Arrays.copyOfRange(png, offset, Math.min(png.length, offset + LiveryManager.MAX_CHUNK)));
            }
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] livery upload {} {}", status.state(),
                    status.message());
            if (status.livery() != null) {
                com.ivomens.mcheli.util.ModLogger.get().info("[devscene] apply {}", manager.apply(p, boarded,
                        status.livery().id()));
            }
        } catch (IOException e) {
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] livery failed {}", e.toString());
        }
    }

    static void onClientTick() {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        clientTicks++;
        if (clientTicks == 335 && mc.player.getVehicle() instanceof AircraftEntity acv) {
            mc.player.setYRot(acv.getYRot()); mc.player.yRotO = acv.getYRot();
            mc.player.setXRot(5); mc.player.xRotO = 5;
        }
        if (clientTicks >= 180 && clientTicks < 236 && mc.player.getVehicle() == null) {
            double fx = mc.player.getX(), fz = mc.player.getZ() + 14;
            double fy = mc.player.getY() - 2.6;
            if (clientTicks == 200) com.ivomens.mcheli.fx.client.modern.ModernFx.explosion(fx - 4, fy, fz, 4.0F);
            com.ivomens.mcheli.fx.client.modern.ModernFx.damageSmoke(fx + 6, fy + 1, fz + 4, 1.0F, true);
            if (clientTicks % 2 == 0) com.ivomens.mcheli.fx.client.modern.ModernFx.groundWash(fx + 14, fy, fz, 6,
                    0.75F, 0.68F, 0.5F);
            com.ivomens.mcheli.fx.client.modern.ModernFx.exhaust(fx - 12, fy + 2, fz, 0.0, 0.02, -0.15, 1.0F, 0.85F);
        }
        if (clientTicks == 203 || clientTicks == 212 || clientTicks == 235) {
            Screenshot.grab(mc.gameDirectory, "devscene_fx_" + clientTicks + ".png", mc.getMainRenderTarget(), c -> {});
        }
        if (clientTicks == 345) mc.options.hideGui = true;
        if (clientTicks == 352) { mc.player.setXRot(-60); mc.player.xRotO = -60; }
        if (clientTicks == 356) Screenshot.grab(mc.gameDirectory, "devscene_up.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 358) { mc.player.setXRot(0); mc.player.setYRot(mc.player.getYRot() + 90); mc.player.yRotO = mc.player.getYRot(); }
        if (clientTicks == 362) Screenshot.grab(mc.gameDirectory, "devscene_side.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 364) {
            var cam = mc.gameRenderer.getMainCamera();
            var pos = net.minecraft.core.BlockPos.containing(cam.getPosition());
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] light block={} sky={} state={} fogType={}",
                    mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos), mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos),
                    mc.level.getBlockState(pos), cam.getFluidInCamera());
        }
        if (clientTicks == 371) mc.options.hideGui = false;
        if (clientTicks == 378) Screenshot.grab(mc.gameDirectory, "devscene_modernhud.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 381) mc.options.hideGui = false;
        if (clientTicks == 400) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if (clientTicks == 240 || clientTicks == 330 || clientTicks == 350 || clientTicks == 390 || clientTicks == 420) {
            Screenshot.grab(mc.gameDirectory, "devscene_" + clientTicks + ".png", mc.getMainRenderTarget(), c -> {});
            var cam = mc.gameRenderer.getMainCamera();
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] t={} camEntity={} camPos={} effect={} vehicle={}", clientTicks,
                    mc.getCameraEntity(), cam.getPosition(), mc.gameRenderer.currentEffect() == null ? null : mc.gameRenderer.currentEffect().getName(),
                    mc.player.getVehicle());
        }
        if (clientTicks == 424 && mc.player.getVehicle() != null) {
            AircraftEntity ac = RiddenVehicle.of(mc.player);
            LiveryTemplates.export(ac).whenComplete((dir, err) ->
                    com.ivomens.mcheli.util.ModLogger.get().info("[devscene] template {} {}", dir, err));
        }
        if (clientTicks == 428) McheliClient.openLiveryScreen(mc);
        if (clientTicks == 434) Screenshot.grab(mc.gameDirectory, "devscene_livery_browse.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 435 && mc.screen != null) {
            mc.screen.onFilesDrop(List.of(mc.gameDirectory.toPath().resolve("mcheli_liveries/templates/ah-64_texture.png")));
        }
        if (clientTicks == 439 && mc.screen != null) {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Upload") && b.active) {
                    b.onPress();
                }
            }
        }
        if (clientTicks == 438) Screenshot.grab(mc.gameDirectory, "devscene_livery_upload.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 440) { mc.options.guiScale().set(1); mc.resizeDisplay(); }
        if (clientTicks == 446) Screenshot.grab(mc.gameDirectory, "devscene_livery_scale1.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks == 448) { mc.options.guiScale().set(0); mc.resizeDisplay(); mc.setScreen(null); }
        if (clientTicks == 452) {
            AircraftEntity ac = RiddenVehicle.of(mc.player);
            String id = ac == null ? "" : ac.appearance().liveryId();
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] livery id={} ready={}", id,
                    com.ivomens.mcheli.livery.client.LiveryTextures.isReady(id));
        }
        if (clientTicks == 456) Screenshot.grab(mc.gameDirectory, "devscene_livery_painted.png", mc.getMainRenderTarget(), c -> {});
        if (clientTicks >= WingmanScreensScene.FIRST_TICK) {
            WingmanScreensScene.onClientTick(mc, clientTicks, boarded);
        }
        if (clientTicks >= ScreensScene.FIRST_TICK) {
            ScreensScene.onClientTick(mc, clientTicks, boarded);
        }
        if (clientTicks >= InspectorScene.FIRST_TICK) {
            InspectorScene.onClientTick(mc, clientTicks, boarded);
        }
        if (clientTicks >= RwrScene.FIRST_TICK) {
            RwrScene.onClientTick(mc, clientTicks, boarded);
        }
        if (clientTicks == RwrScene.LAST_TICK) {
            mc.stop();
        }
    }

    static void onRenderLiving(LivingEntity entity) {
        if (!ENABLED || clientTicks < 340 || clientTicks > 342) return;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        com.ivomens.mcheli.util.ModLogger.get().info("[devscene] renderLiving {} dist={} detached={}", entity,
                Math.sqrt(entity.distanceToSqr(cam.getPosition())), cam.isDetached());
    }

    static void onEntitiesRendered(Camera camera) {
        if (!ENABLED || clientTicks != 341) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.getVehicle() instanceof AircraftEntity ac) {
            var d = mc.getCameraEntity();
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] pilot={} always={} gunner={} dummyPos={} camPos=({}, {}, {}) cfgReplace={}",
                    ac.isPilot(mc.player), ac.cameras().pilotUsesOwnEyes(), ac.modes().usesGunnerView(mc.player), d.position(),
                    ac.cameras().camera().posX(), ac.cameras().camera().posY(), ac.cameras().camera().posZ(), McheliConfig.render().replaceRenderViewEntity());
        }
        if (mc.player.getVehicle() instanceof AircraftEntity ac2) {
            com.ivomens.mcheli.util.ModLogger.get().info("[devscene] camRot y={} x={} acRot y={} p={} r={} acPos={}",
                    camera.getYRot(), camera.getXRot(), ac2.getYRot(), ac2.getXRot(), ac2.roll(), ac2.position());
        }
        com.ivomens.mcheli.util.ModLogger.get().info("[devscene] renderCam entity={} pos={} detached={} boarded={}",
                camera.getEntity(), camera.getPosition(), camera.isDetached(), boarded == null ? null : boarded.position());
        for (var ent : mc.level.entitiesForRendering()) {
            if (ent.distanceToSqr(camera.getPosition()) < 64) {
                com.ivomens.mcheli.util.ModLogger.get().info("[devscene] near {} renderer={}", ent,
                        mc.getEntityRenderDispatcher().getRenderer(ent).getClass().getSimpleName());
            }
        }
    }

    private static void pick(List<VehicleSpec> out, List<? extends VehicleSpec> all, String... names) {
        for (String n : names) {
            for (VehicleSpec i : all) {
                if (i.name().equals(n)) out.add(i);
            }
        }
    }
}
