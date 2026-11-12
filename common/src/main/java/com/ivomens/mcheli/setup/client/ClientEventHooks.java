package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.client.render.LegacyGL;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.client.LockOnMarkerRenderer;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.client.camera.CameraAngles;
import com.ivomens.mcheli.aircraft.client.CcipRenderer;
import com.ivomens.mcheli.combat.weapon.client.LaserRenderer;
import com.ivomens.mcheli.config.DebugLog;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import com.ivomens.mcheli.fx.client.legacy.WindSources;
import com.ivomens.mcheli.inspector.client.InspectorKeys;
import com.ivomens.mcheli.livery.client.LiveryClient;
import com.ivomens.mcheli.livery.client.LiveryKeys;
import com.ivomens.mcheli.multiplay.TargetMarkerObject;
import com.ivomens.mcheli.multiplay.client.TargetMarkerOverlay;
import com.ivomens.mcheli.setup.CommonEventHooks;
import com.ivomens.mcheli.sound.client.ClientSoundRegistry;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import com.ivomens.mcheli.vehicle.client.render.VehicleRiderRendering;
import com.ivomens.mcheli.wingman.client.WingmanClientReg;
import com.ivomens.mcheli.world.uavstation.client.UavCameraFeed;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Client render, input and tick hooks. The loader's client event glue calls these methods; each one
 * notes the event and phase it stands for. Handlers of the same event run in the order they are
 * called here.
 */
public final class ClientEventHooks {

    /** Mouse wheel scroll units per notch. */
    private static final double SCROLL_UNITS_PER_NOTCH = 120.0;
    /** Entities whose rider transform was pushed in Pre and must be popped in Post. */
    private static final Set<Entity> PUSHED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final CcipRenderer CCIP = new CcipRenderer();

    private ClientEventHooks() {
    }

    /** Key mappings to add to the controls screen. */
    public static List<KeyMapping> keyMappings() {
        List<KeyMapping> keys = new ArrayList<>();
        keys.add(LiveryKeys.OPEN_SCREEN);
        keys.add(InspectorKeys.OPEN);
        keys.addAll(WingmanClientReg.keyMappings());
        return keys;
    }

    // Ticks and frames

    public static void onClientTickStart() {
        ClientSession session = McheliClient.session();
        session.start(Minecraft.getInstance(), McheliConfig.keys());
        session.ticks().onTickStart();
    }

    public static void onClientTickEnd() {
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null) {
            ticks.onTickEnd();
        }
        McheliClient.session().livery().tick();
        WingmanClientReg.onClientTick();
        LaserRenderer.onClientTick();
        McheliClient.session().modelSweeper().onClientTick();
        McheliClient.session().radarContacts().onClientTick();
        McheliClient.session().benchmark().onClientTick();
        McheliClient.session().inspector().onClientTick();
        McheliClient.session().explosionDetail().onClientTick(Minecraft.getInstance());
    }

    public static void onRenderFrameStart(DeltaTracker deltaTracker) {
        McheliClient.session().uavFeed().onRenderFrameStart(deltaTracker);
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null) {
            ticks.onFrameStart(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
        McheliClient.session().benchmark().onFrameStart();
    }

    public static void onRenderFrameEnd(DeltaTracker deltaTracker) {
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null) {
            ticks.onFrameEnd();
        }
    }

    /** Start of a player tick on the client, after {@link CommonEventHooks#onPlayerTickStart}. */
    public static void onPlayerTickStart(Player player) {
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null && player.level().isClientSide()) {
            ticks.onPlayerTickStart(player);
        }
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && isClientPlayerMountedInAircraft(mc)) {
            clearVanillaAttackInput(mc);
        }
    }

    public static void onLoggingOut() {
        McheliClient.session().benchmark().onLoggingOut();
        McheliClient.session().inspector().reset();
        WingmanClientReg.onLoggingOut();
        McheliClient.session().livery().reset();
        WindSources.reset();
        LegacyGL.releaseCachedMeshes();
    }

    /** The client received the server's recipes. */
    public static void onRecipesReceived(RecipeManager recipeManager) {
        ContentRecipes.resolve(recipeManager);
    }

    /** After {@link CommonEventHooks#onEntityJoinLevel}, for entities joining a client level. */
    public static void onEntityJoinLevel(Entity entity, Level level) {
        Minecraft mc = Minecraft.getInstance();
        if (level.isClientSide() && entity == mc.player) {
            DebugLog.log(true, "ClientEventHooks.entityJoinWorldEvent : " + entity);
            McheliClient.session().rangeFinder().resetMode(mc.hasSingleplayerServer());
            McheliClient.session().teamMarkers().clearMarkPoint();
        }
    }

    // Level rendering

    public static void onSkyRendered(Matrix4f projection, Matrix4f modelView, Camera camera) {
        McheliClient.session().screenProjection().capture(projection, modelView, camera);
    }

    public static void onParticlesRendered(Camera camera, float partialTicks, @Nullable PoseStack poseStack) {
        LaserRenderer.renderAfterParticles(camera, partialTicks, poseStack);
        CCIP.renderAfterParticles(camera, poseStack);
    }

    // GUI

    /** Before the vanilla GUI renders; runs after every other handler. */
    public static void onRenderGuiStart() {
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null) {
            ticks.onRenderGuiStart();
        }
    }

    /** After the vanilla GUI rendered; runs before every other handler. */
    public static void onRenderGuiEnd() {
        ClientTicks ticks = McheliClient.session().ticks();
        if (ticks != null) {
            ticks.onRenderGuiEnd();
        }
    }

    /** After the vanilla GUI rendered, at normal priority. */
    public static void onRenderGuiOverlay(GuiGraphics graphics) {
        WingmanClientReg.onRenderGuiPost(graphics);
        McheliClient.session().benchmark().onRenderGui(graphics);
        McheliClient.session().inspector().onRenderGui(graphics);
    }

    // Camera

    /** The third person camera distance to use instead of vanilla's. */
    public static float adjustCameraDistance(float distance) {
        return McheliClient.session().vehicleControls().camera().adjustDistance(distance);
    }

    /** Adjusts the camera angles about to be applied, in place. */
    public static void adjustCameraAngles(CameraAngles angles) {
        McheliClient.session().vehicleControls().camera().adjustAngles(angles);
    }

    /**
     * The field of view to use instead of {@code fov}: the UAV feed's fixed one while it renders, zoomed
     * through a raised range finder or a vehicle camera.
     */
    public static double adjustFov(double fov, Camera camera) {
        ClientSession session = McheliClient.session();
        if (session.uavFeed().isRendering()) {
            return UavCameraFeed.FEED_FOV;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && session.rangeFinder().isUsingScope(player)) {
            return session.vehicleControls().camera().zoomed(fov);
        }
        return session.vehicleControls().camera().adjustFov(fov, camera);
    }

    /** Whether the vanilla HUD elements that clash with the vehicle HUD are hidden. */
    public static boolean hidesVanillaHud() {
        return McheliClient.session().vehicleControls().camera().hidesVanillaHud();
    }

    /** True while MCHeli turns the player itself, so vanilla mouse turning must be switched off. */
    public static boolean isControllingPlayerTurn() {
        ClientTicks ticks = McheliClient.session().ticks();
        return ticks != null && ticks.isControllingPlayerTurn();
    }

    /** Re-applies the sound fallbacks after every resource reload. */
    public static ResourceManagerReloadListener soundReloadListener() {
        return resourceManager -> ClientSoundRegistry.INSTANCE.commitChanges(Minecraft.getInstance().getSoundManager());
    }

    // Input

    /** @return true to swallow the scroll */
    public static boolean onMouseScroll(double scrollDeltaY) {
        return McheliClient.session().vehicleControls().weaponWheel()
                .offer((int) Math.round(scrollDeltaY * SCROLL_UNITS_PER_NOTCH));
    }

    /**
     * The attack key was pressed. While mounted in an aircraft MCHeli handles it, so the vanilla
     * attack and arm swing must be cancelled.
     *
     * @return true to cancel the attack and its swing
     */
    public static boolean onAttackKey() {
        Minecraft mc = Minecraft.getInstance();
        if (isClientPlayerMountedInAircraft(mc)) {
            clearVanillaAttackInput(mc);
            return true;
        }
        return false;
    }

    private static boolean isClientPlayerMountedInAircraft(Minecraft mc) {
        if (mc.player == null) return false;
        Entity vehicle = mc.player.getVehicle();
        return vehicle instanceof AircraftEntity || vehicle instanceof SeatEntity;
    }

    private static void clearVanillaAttackInput(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    // Entity rendering

    /** @return true to hide the entity's name tag */
    public static boolean shouldHideNameTag(Entity entity) {
        if (McheliConfig.render().disableRenderLivingSpecials()) {
            AircraftEntity ac = RiddenVehicle.of(Minecraft.getInstance().player);
            return ac != null && ac.seats().isAboard(entity);
        }
        return false;
    }

    /** @return true to skip rendering the first person hand */
    public static boolean shouldHideHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        if (McheliClient.session().uavFeed().isRendering() || McheliClient.session().view().isHeldItemHidden()) {
            return true;
        }
        Entity riding = mc.player.getVehicle();
        return riding instanceof SeatEntity || riding instanceof AircraftEntity || riding instanceof IUavStation;
    }

    /** @return true to skip rendering the player (riders of aircraft with {@code hideEntity}) */
    public static boolean shouldHidePlayer(Player player) {
        Entity riding = player.getVehicle();
        if (riding instanceof GroundVehicleEntity
                || riding instanceof SeatEntity seat && seat.getParent() instanceof GroundVehicleEntity) {
            return false;
        }
        return riding instanceof AircraftEntity ac && ac.spec() != null
                && ac.spec().file().render().hideRiders();
    }

    /**
     * Before a living entity renders: hides riders, re-renders entities lit by a searchlight and
     * moves riders with their seat.
     *
     * @return true to cancel this render
     */
    public static boolean onRenderLivingStart(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, PoseStack poseStack,
                                              MultiBufferSource buffers, int packedLight, float partialTicks) {
        if (VehicleRiderRendering.shouldHideRider(entity)) {
            return true;
        }

        if (McheliClient.session().searchLightRelight().relight(entity, renderer, poseStack, buffers, packedLight,
                partialTicks)) {
            return true;
        }

        if (VehicleRiderRendering.pushRiderTransform(entity, poseStack, partialTicks)) {
            PUSHED.add(entity);
        }
        return false;
    }

    /** After a living entity rendered (not called when the start hook cancelled the render). */
    public static void onRenderLivingEnd(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffers,
                                         float partialTicks) {
        if (PUSHED.remove(entity)) {
            poseStack.popPose();
        }
        LockOnMarkerRenderer.render(entity, poseStack, buffers, partialTicks);

        // Marker positions are relative to the camera, as seen from inside the entity renderer.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTicks, entity.xo, entity.getX()) - camera.x;
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY()) - camera.y;
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ()) - camera.z;
        if (TargetMarkerOverlay.isEnableEntityMarker()) {
            TargetMarkerObject target = entity instanceof TargetMarkerObject marker ? marker : TargetMarkerObject.fromEntity(entity);
            McheliClient.session().teamMarkers().queue(2, target, x, y + entity.getBbHeight() + 0.5, z);
        }
        McheliClient.session().markForLauncherSight(entity, x, y + entity.getBbHeight() / 2.0F, z);
    }
}
