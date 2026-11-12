package com.ivomens.mcheli.wingman.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.wingman.net.AutopilotVisualMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the local player's aircraft visually on the autopilot's heading and throttle while it flies
 * autonomously.
 *
 * <p>The aircraft also ticks on the client, where it takes its yaw from the local player's view and its
 * throttle from the (unpressed) throttle keys, so the server's corrections alone would never show. The
 * server sends the autopilot values every tick in {@link AutopilotVisualMessage}; after the client's
 * entity update they are re-applied here, the yaw with smoothing. The player's own view is left alone,
 * so they can still look around.
 */
public class ClientAutopilotHandler {

    /** Fraction of the remaining yaw difference closed per tick; a 90 degree turn catches up in about 6 ticks. */
    private static final float SMOOTH_ALPHA = 0.35f;
    private static final float MAX_VISUAL_YAW_RATE = 6.0f;

    /** Entity id to the autopilot's target yaw and throttle, as last sent by the server. */
    private final Map<Integer, Float> headings = new HashMap<>();
    private final Map<Integer, Float> throttles = new HashMap<>();

    /** Last yaw applied, the start point of the smoothing; null until the first update. */
    private Float lastAppliedYaw = null;
    private int lastEntityId = -1;

    public void onAutopilotVisual(AutopilotVisualMessage message) {
        setOrClear(headings, message.entityId(), message.targetYaw());
        setOrClear(throttles, message.entityId(), message.targetThrottle());
    }

    /** Drops every override; entity ids from the old connection mean nothing on the next one. */
    public void clearOverrides() {
        headings.clear();
        throttles.clear();
    }

    public void onClientTick() {
        if (headings.isEmpty() && throttles.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AircraftEntity aircraft)) {
            clearOverrides();
            lastAppliedYaw = null;
            return;
        }

        // Values for other aircraft are stale (the player switched vehicles); drop them.
        int id = aircraft.getId();
        Float heading = retainOnly(headings, id);
        if (heading != null) applyHeading(aircraft, heading);
        Float throttle = retainOnly(throttles, id);
        if (throttle != null) aircraft.engine().setThrottle(throttle);
    }

    private static void setOrClear(Map<Integer, Float> overrides, int entityId, float value) {
        if (Float.isNaN(value)) {
            overrides.remove(entityId);
        } else {
            overrides.put(entityId, value);
        }
    }

    private static Float retainOnly(Map<Integer, Float> values, int entityId) {
        values.keySet().removeIf(key -> key != entityId);
        return values.get(entityId);
    }

    private void applyHeading(AircraftEntity aircraft, float targetYaw) {
        if (lastAppliedYaw == null || lastEntityId != aircraft.getId()) {
            lastAppliedYaw = aircraft.getYRot();
            lastEntityId = aircraft.getId();
        }
        float step = Mth.wrapDegrees(targetYaw - lastAppliedYaw) * SMOOTH_ALPHA;
        step = Mth.clamp(step, -MAX_VISUAL_YAW_RATE, MAX_VISUAL_YAW_RATE);

        float previousYaw = lastAppliedYaw;
        float newYaw = previousYaw + step;
        lastAppliedYaw = newYaw;
        // Setting the previous rotation too keeps render interpolation smooth.
        aircraft.yRotO = previousYaw;
        aircraft.setYRot(newYaw);
    }
}
