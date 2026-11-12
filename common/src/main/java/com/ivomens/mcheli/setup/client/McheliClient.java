package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.client.model.ItemRenderContext;
import com.ivomens.mcheli.combat.projectile.behaviour.ProjectileVisualHooks;
import com.ivomens.mcheli.combat.projectile.client.AddonVisuals;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.livery.client.LiveryKeys;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.networking.MessageType;
import com.ivomens.mcheli.setup.McheliMessages;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.input.JoystickSource;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The client half of the mod's entry point. The loader's client glue calls {@link #init()} while the
 * mod is constructed and {@link #onClientSetup()} once the client has been set up.
 *
 * <p>It holds the client's one {@link ClientSession}. The loader reaches the client through static hooks
 * (events, mixins), so the session is the one static root of the client's state; everything below it is
 * handed its collaborators.
 */
public final class McheliClient {

    private static final ClientSession SESSION = new ClientSession();

    private McheliClient() {
    }

    /** The state of this client. Only the client's setup code (hooks, message wiring) asks for it. */
    static ClientSession session() {
        return SESSION;
    }

    /**
     * What the client is drawing as an item: for the item model wrappers, which the loader creates while it
     * bakes the models, and for the built-in item renderer.
     */
    public static ItemRenderContext itemRenderContext() {
        return SESSION.itemRenderContext();
    }

    /** Where this client's joysticks come from, for the joystick screen. */
    public static JoystickSource joystickSource() {
        return SESSION.vehicleControls().pilot().joystick().source();
    }

    /** The key settings changed (the config screen saved them): rebinds every key. */
    public static void rebindKeys(KeySettings bindings) {
        SESSION.updateKeybind(bindings);
    }

    /** Opens the livery screen for the vehicle the player is in, as the livery key does. */
    public static void openLiveryScreen(Minecraft mc) {
        LiveryKeys.openScreen(mc, SESSION.livery());
    }

    /** Opens the vehicle inspector on the vehicle the player is aboard or looks at, as its key does. */
    public static void openInspector(Minecraft mc) {
        SESSION.inspector().openOnTarget(mc);
    }

    /** Shows or hides the vehicle inspector's overlay, as its screen's button does. */
    public static void showInspectorOverlay(boolean shown) {
        SESSION.inspector().setOverlay(shown);
    }

    /** Adds the client handlers of the clientbound messages and the client side of addon weapon behaviours. */
    public static void init() {
        ProjectileVisualHooks.install(new AddonVisuals());
        MessageRegistry messages = McheliMessages.registry();
        McheliClientMessages.register(messages);
        List<MessageType<?>> unhandled = messages.clientboundWithoutHandler();
        if (!unhandled.isEmpty()) {
            throw new IllegalStateException("Clientbound messages without a client handler: " + unhandled);
        }
    }

    /** Once the client has been set up, on the main thread. */
    public static void onClientSetup() {
        SESSION.start(Minecraft.getInstance(), McheliConfig.keys());
        readClientModList();
    }

    private static void readClientModList() {
        try {
            Minecraft mc = Minecraft.getInstance();
            SESSION.operatorReports().readModList(mc.getUser().getProfileId().toString(), mc.getUser().getName());
        } catch (Exception e) {
            ModLogger.error("Could not read the client mod list", e);
        }
    }
}
