package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.wingman.net.AutopilotVisualMessage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Players riding an aircraft the autopilot flies. Their client shows the autopilot heading and
 * throttle and keeps applying the last values it got, so a rider whose aircraft is no longer flown by
 * the autopilot (mission done or aborted, wingman released) gets a clear.
 */
final class AutopilotRiders {

    /** Riders whose client shows the autopilot values, by aircraft entity id. */
    private final Int2ObjectMap<ServerPlayer> riders = new Int2ObjectOpenHashMap<>();
    /** Aircraft still flown by the autopilot in the current tick. */
    private final IntSet activeThisTick = new IntOpenHashSet();

    void beginTick() {
        activeThisTick.clear();
    }

    /** The autopilot still flies this aircraft this tick, without sending its rider new values. */
    void markActive(int aircraftId) {
        activeThisTick.add(aircraftId);
    }

    /** The rider was just sent the autopilot values of this aircraft. */
    void track(int aircraftId, ServerPlayer rider) {
        riders.put(aircraftId, rider);
        activeThisTick.add(aircraftId);
    }

    /** Clears the riders in this level whose aircraft the autopilot did not fly this tick. */
    void releaseInactive(ServerLevel level) {
        if (riders.isEmpty()) return;
        ObjectIterator<Int2ObjectMap.Entry<ServerPlayer>> it = riders.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<ServerPlayer> e = it.next();
            ServerPlayer rider = e.getValue();
            if (rider.level() != level || activeThisTick.contains(e.getIntKey())) continue;
            if (!rider.hasDisconnected()) {
                Network.sendToPlayer(rider, AutopilotVisualMessage.clear(e.getIntKey()));
            }
            it.remove();
        }
    }

    void clear() {
        riders.clear();
        activeThisTick.clear();
    }
}
