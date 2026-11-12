package com.ivomens.mcheli.inspector.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.inspector.DefinitionSections;
import com.ivomens.mcheli.inspector.InspectorSnapshot;
import com.ivomens.mcheli.inspector.InspectorTargets;
import com.ivomens.mcheli.inspector.VehicleSnapshots;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.inspector.net.InspectorRequestHandler;
import com.ivomens.mcheli.inspector.net.InspectorRequestMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * The vehicle inspector of one client: which vehicle it follows, the client's snapshot of it (taken again
 * every tick while the screen or the overlay shows it), the definition (taken once per vehicle), and the
 * server's snapshot, which an operator's client asks for every {@link InspectorRequestHandler#MIN_INTERVAL_TICKS}
 * ticks while the inspector is open. Built by the client session; nothing here outlives a world.
 */
public final class InspectorClient {

    private int target = -1;
    private boolean overlay;
    @Nullable
    private InspectorSnapshot client;
    @Nullable
    private InspectorSnapshot definition;
    @Nullable
    private InspectorReplyMessage server;
    private long serverArrivedTick;
    private long ticks;
    private long lastRequest = Long.MIN_VALUE / 2;

    /** Once per client tick: the key, then a fresh snapshot and a server request while open. */
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        this.ticks++;
        while (InspectorKeys.OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                openOnTarget(mc);
            }
        }
        if (isShown(mc)) {
            refresh(mc);
        }
    }

    private void refresh(Minecraft mc) {
        AircraftEntity vehicle = vehicle(mc);
        if (vehicle == null) {
            this.overlay = false;
            return;
        }
        this.client = VehicleSnapshots.capture(vehicle, DefinitionSections.Detail.NONE);
        if (this.definition == null || this.definition.entityId() != vehicle.getId()
                || !this.definition.typeId().equals(this.client.typeId())) {
            this.definition = VehicleSnapshots.definition(vehicle);
        }
        if (mayAskServer(mc) && this.ticks - this.lastRequest >= InspectorRequestHandler.MIN_INTERVAL_TICKS) {
            this.lastRequest = this.ticks;
            Network.sendToServer(new InspectorRequestMessage(vehicle.getId()));
        }
    }

    /** Opens the inspector on the vehicle the player is aboard or looks at, or says there is none. */
    public void openOnTarget(Minecraft mc) {
        AircraftEntity vehicle = InspectorTargets.find(mc.player);
        if (vehicle == null) {
            mc.player.displayClientMessage(Component.translatable("mcheli.inspector.no_vehicle"), true);
            return;
        }
        open(mc, vehicle.getId());
    }

    /** Opens the inspector screen on the vehicle with this network id. */
    public void open(Minecraft mc, int entityId) {
        if (this.target != entityId) {
            this.target = entityId;
            this.client = null;
            this.definition = null;
            this.server = null;
        }
        this.overlay = false;
        this.lastRequest = Long.MIN_VALUE / 2;
        refresh(mc);
        mc.setScreen(new InspectorScreen(this));
    }

    /** The server answered a request. */
    public void onReply(InspectorReplyMessage reply) {
        if (reply.status() != InspectorReplyMessage.Status.OK || reply.snapshot().entityId() == this.target) {
            this.server = reply;
            this.serverArrivedTick = this.ticks;
        }
    }

    /** After the vanilla GUI: the overlay, while it is on. */
    public void onRenderGui(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (this.overlay && this.client != null && mc.screen == null && !mc.options.hideGui) {
            InspectorOverlay.draw(graphics, this);
        }
    }

    /** Leaving a world forgets everything. */
    public void reset() {
        this.target = -1;
        this.overlay = false;
        this.client = null;
        this.definition = null;
        this.server = null;
    }

    public boolean overlay() {
        return this.overlay;
    }

    /** Shows or hides the overlay; the screen closes itself when it turns the overlay on. */
    public void setOverlay(boolean shown) {
        this.overlay = shown;
    }

    @Nullable
    public InspectorSnapshot clientSnapshot() {
        return this.client;
    }

    @Nullable
    public InspectorSnapshot definition() {
        return this.definition;
    }

    /** The server's last answer about the current vehicle, or null before one arrived. */
    @Nullable
    public InspectorReplyMessage server() {
        return this.server;
    }

    /** Ticks since the server's last answer arrived. */
    public long serverAge() {
        return this.ticks - this.serverArrivedTick;
    }

    /** Whether this player may see the server's view; the server checks again. */
    public boolean mayAskServer(Minecraft mc) {
        return mc.player != null && mc.player.hasPermissions(InspectorRequestHandler.OPERATOR);
    }

    @Nullable
    AircraftEntity vehicle(Minecraft mc) {
        return mc.level == null || this.target < 0 ? null : InspectorTargets.vehicleOf(mc.level.getEntity(this.target));
    }

    private boolean isShown(Minecraft mc) {
        return this.target >= 0 && (this.overlay || mc.screen instanceof InspectorScreen);
    }
}
