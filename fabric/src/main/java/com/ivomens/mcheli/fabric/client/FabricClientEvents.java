package com.ivomens.mcheli.fabric.client;

import com.ivomens.mcheli.fabric.client.event.McheliClientEvents;
import com.ivomens.mcheli.fabric.event.McheliEvents;
import com.ivomens.mcheli.setup.client.ClientEventHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

/**
 * Client game events, each forwarded to {@link ClientEventHooks}: the counterpart of
 * {@code NeoForgeClientEvents}. Registered on the physical client only, after the common events.
 * <p>
 * The rest of NeoForge's client events have no Fabric API counterpart at the same point and are
 * handled by the client mixins: the level render stages ({@code LevelRendererMixin}), the GUI layers
 * ({@code GuiMixin}), entity, player, hand and name tag rendering, the mouse wheel and player turn
 * ({@code MouseHandlerMixin}), the camera angles, distance and field of view ({@code CameraMixin},
 * {@code GameRendererMixin}) and the received recipes ({@code ClientPacketListenerMixin}).
 */
final class FabricClientEvents {

    private FabricClientEvents() {
    }

    static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientEventHooks.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEventHooks.onClientTickEnd());
        McheliClientEvents.FRAME_START.register(ClientEventHooks::onRenderFrameStart);
        McheliClientEvents.FRAME_END.register(ClientEventHooks::onRenderFrameEnd);
        McheliEvents.PLAYER_TICK_START.register(ClientEventHooks::onPlayerTickStart);
        McheliEvents.ENTITY_JOIN.register(ClientEventHooks::onEntityJoinLevel);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEventHooks.onLoggingOut());
        // Runs before the attack and the block breaking that follows a click; true cancels both and the swing.
        ClientPreAttackCallback.EVENT.register((client, player, clicks) -> ClientEventHooks.onAttackKey());
    }
}
