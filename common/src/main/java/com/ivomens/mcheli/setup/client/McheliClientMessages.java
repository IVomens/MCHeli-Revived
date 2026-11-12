package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.client.AircraftClientMessages;
import com.ivomens.mcheli.aircraft.client.VehicleCombatClientMessages;
import com.ivomens.mcheli.bench.StartClientBenchmarkMessage;
import com.ivomens.mcheli.combat.weapon.client.CombatClientMessages;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import com.ivomens.mcheli.inspector.net.OpenInspectorMessage;
import com.ivomens.mcheli.livery.client.LiveryClientMessages;
import com.ivomens.mcheli.multiplay.client.MultiplayClientMessages;
import com.ivomens.mcheli.networking.EntitySpawnDataMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.networking.client.EntitySpawnDataHandler;
import com.ivomens.mcheli.radar.client.RadarClientMessages;
import com.ivomens.mcheli.vehicledata.VehicleDataMessage;
import com.ivomens.mcheli.vehicledata.client.VehicleDataHandler;
import com.ivomens.mcheli.wingman.client.WingmanClientMessages;
import net.minecraft.client.Minecraft;

/** The client handlers of every feature's clientbound messages. Called from client init only. */
public final class McheliClientMessages {

    private McheliClientMessages() {
    }

    public static void register(MessageRegistry registry) {
        ClientSession session = McheliClient.session();
        LiveryClientMessages.register(registry, session.livery());
        WingmanClientMessages.register(registry);
        MultiplayClientMessages.register(registry, session::showTitle, session.vehicleControls().camera(),
                session.operatorReports(), session.teamMarkers());
        CombatClientMessages.register(registry, session.lockWarningTone(), session.effectSpawner());
        VehicleCombatClientMessages.register(registry, session::showHitMarker);
        RadarClientMessages.register(registry, session.radarContacts());
        AircraftClientMessages.register(registry);
        registry.onClient(StartClientBenchmarkMessage.TYPE, message -> session.benchmark().start(message.quick()));
        registry.onClient(InspectorReplyMessage.TYPE, session.inspector()::onReply);
        registry.onClient(OpenInspectorMessage.TYPE,
                message -> session.inspector().open(Minecraft.getInstance(), message.entityId()));
        registry.onClient(EntitySpawnDataMessage.TYPE, new EntitySpawnDataHandler());
        registry.onClient(VehicleDataMessage.TYPE, new VehicleDataHandler());
    }
}
