package com.ivomens.mcheli.neoforge.client;

import com.ivomens.mcheli.client.camera.CameraAngles;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * Client game-bus listeners; each forwards to {@link ClientEventHooks} or {@link CameraManager}.
 * Registered on the physical client only, after the common listeners.
 */
public final class NeoForgeClientEvents {

    /** Vanilla HUD layers hidden while riding (the hotbar stays visible). */
    private static final Set<ResourceLocation> HIDDEN_LAYERS = Set.of(
            VanillaGuiLayers.BOSS_OVERLAY,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.CAMERA_OVERLAYS,
            VanillaGuiLayers.AIR_LEVEL);

    private static final CameraAngles ANGLES = new CameraAngles();

    private NeoForgeClientEvents() {
    }

    static void register(IEventBus bus) {
        bus.addListener((ClientTickEvent.Pre e) -> ClientEventHooks.onClientTickStart());
        bus.addListener((ClientTickEvent.Post e) -> ClientEventHooks.onClientTickEnd());
        bus.addListener((RenderFrameEvent.Pre e) -> ClientEventHooks.onRenderFrameStart(e.getPartialTick()));
        bus.addListener((RenderFrameEvent.Post e) -> ClientEventHooks.onRenderFrameEnd(e.getPartialTick()));
        bus.addListener((PlayerTickEvent.Pre e) -> ClientEventHooks.onPlayerTickStart(e.getEntity()));
        bus.addListener(NeoForgeClientEvents::onPlayerTurn);
        bus.addListener(NeoForgeClientEvents::onRenderLevelStage);
        bus.addListener(EventPriority.LOWEST, (RenderGuiEvent.Pre e) -> ClientEventHooks.onRenderGuiStart());
        bus.addListener(EventPriority.HIGHEST, (RenderGuiEvent.Post e) -> ClientEventHooks.onRenderGuiEnd());
        bus.addListener((RenderGuiEvent.Post e) -> ClientEventHooks.onRenderGuiOverlay(e.getGuiGraphics()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEventHooks.onLoggingOut());
        bus.addListener((RecipesUpdatedEvent e) -> ClientEventHooks.onRecipesReceived(e.getRecipeManager()));
        bus.addListener((EntityJoinLevelEvent e) -> ClientEventHooks.onEntityJoinLevel(e.getEntity(), e.getLevel()));

        bus.addListener(NeoForgeClientEvents::onRenderNameTag);
        bus.addListener(NeoForgeClientEvents::onMouseScroll);
        bus.addListener(NeoForgeClientEvents::onInteractionKey);
        bus.addListener(NeoForgeClientEvents::onRenderHand);
        // The render events are generic, so they are registered by class.
        bus.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class, NeoForgeClientEvents::onRenderLivingPre);
        bus.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Post.class, NeoForgeClientEvents::onRenderLivingPost);
        bus.addListener(NeoForgeClientEvents::onRenderPlayerPre);

        bus.addListener(NeoForgeClientEvents::onCameraDistance);
        bus.addListener(NeoForgeClientEvents::onCameraAngles);
        bus.addListener(NeoForgeClientEvents::onComputeFov);
        bus.addListener(NeoForgeClientEvents::onRenderGuiLayer);
    }

    private static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (ClientEventHooks.isControllingPlayerTurn()) {
            // Makes vanilla's 0.6 * s + 0.2 sensitivity zero, so only MCHeli turns the player.
            event.setMouseSensitivity(-1.0 / 3.0);
            event.setCinematicCameraEnabled(false);
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ClientEventHooks.onSkyRendered(event.getProjectionMatrix(), event.getModelViewMatrix(), event.getCamera());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ClientEventHooks.onParticlesRendered(event.getCamera(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getPoseStack());
        }
    }

    private static void onRenderNameTag(RenderNameTagEvent event) {
        if (ClientEventHooks.shouldHideNameTag(event.getEntity())) {
            event.setCanRender(TriState.FALSE);
        }
    }

    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ClientEventHooks.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && ClientEventHooks.onAttackKey()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (ClientEventHooks.shouldHideHand()) {
            event.setCanceled(true);
        }
    }

    private static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (ClientEventHooks.onRenderLivingStart(event.getEntity(), event.getRenderer(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick())) {
            event.setCanceled(true);
        }
    }

    private static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        ClientEventHooks.onRenderLivingEnd(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPartialTick());
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (ClientEventHooks.shouldHidePlayer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        event.setDistance(ClientEventHooks.adjustCameraDistance(event.getDistance()));
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ANGLES.set(event.getYaw(), event.getPitch(), event.getRoll());
        ClientEventHooks.adjustCameraAngles(ANGLES);
        event.setYaw(ANGLES.yaw());
        event.setPitch(ANGLES.pitch());
        event.setRoll(ANGLES.roll());
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        event.setFOV(ClientEventHooks.adjustFov(event.getFOV(), event.getCamera()));
    }

    private static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (HIDDEN_LAYERS.contains(event.getName()) && ClientEventHooks.hidesVanillaHud()) {
            event.setCanceled(true);
        }
    }
}
