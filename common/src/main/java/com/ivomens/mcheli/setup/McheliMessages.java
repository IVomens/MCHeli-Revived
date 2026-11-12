package com.ivomens.mcheli.setup;

import com.ivomens.mcheli.aircraft.net.AircraftMessages;
import com.ivomens.mcheli.aircraft.net.GuiMessages;
import com.ivomens.mcheli.bench.BenchmarkCommand;
import com.ivomens.mcheli.block.net.DraftingTableMessages;
import com.ivomens.mcheli.combat.weapon.net.CombatMessages;
import com.ivomens.mcheli.equipment.gltd.net.GltdMessages;
import com.ivomens.mcheli.equipment.launcher.net.LightWeaponMessages;
import com.ivomens.mcheli.equipment.rangefinder.net.RangeFinderMessages;
import com.ivomens.mcheli.inspector.InspectorCommand;
import com.ivomens.mcheli.livery.net.LiveryMessages;
import com.ivomens.mcheli.multiplay.net.MultiplayMessages;
import com.ivomens.mcheli.networking.EntitySpawnDataMessage;
import com.ivomens.mcheli.networking.MessageRegistry;
import com.ivomens.mcheli.vehicledata.VehicleDataMessage;
import com.ivomens.mcheli.wingman.net.WingmanMessages;
import com.ivomens.mcheli.world.uavstation.net.UavMessages;

/** Every feature's network messages. Ids are unique, so the order does not matter. */
public final class McheliMessages {

    private static final MessageRegistry REGISTRY = create();

    private McheliMessages() {
    }

    /** Every message type, with the server handlers; the client adds its handlers on start. */
    public static MessageRegistry registry() {
        return REGISTRY;
    }

    /** A new registry of every message type, with the server handlers. */
    public static MessageRegistry create() {
        MessageRegistry registry = new MessageRegistry();
        LiveryMessages.register(registry);
        WingmanMessages.register(registry);
        MultiplayMessages.register(registry);
        CombatMessages.register(registry);
        AircraftMessages.register(registry);
        LightWeaponMessages.register(registry);
        GltdMessages.register(registry);
        RangeFinderMessages.register(registry);
        UavMessages.register(registry);
        GuiMessages.register(registry);
        DraftingTableMessages.register(registry);
        BenchmarkCommand.registerMessages(registry);
        InspectorCommand.registerMessages(registry);
        registry.clientbound(EntitySpawnDataMessage.TYPE);
        registry.clientbound(VehicleDataMessage.TYPE);
        return registry;
    }
}
