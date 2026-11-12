package com.ivomens.mcheli.vehicle;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.net.ControlInputBuilder;

/**
 * Pilot free-look toggle shared by the vehicle client tick handlers.
 * <p>
 * The toggle is applied to the client copy of the free-look state right away (the server value follows
 * through the normal control packet), because the client writes that bit locally when the pilot boards and
 * its copy may differ from the server's. The key can also always turn free look off, even on vehicles that
 * start in free look or report {@code canSwitchFreeLook() == false}; otherwise the pilot could get stuck
 * without mouse steering.
 */
public final class PilotFreeLook {

    private PilotFreeLook() {
    }

    /**
     * Call after {@code commonPlayerControl}.
     *
     * @param freeLookKeyPressed whether the free-look key was pressed this tick
     * @return true when a packet must be sent for the toggle
     */
    public static boolean handle(AircraftEntity ac, boolean isPilot, boolean freeLookKeyPressed,
                                 ControlInputBuilder pc) {
        if (!isPilot || ac == null) {
            return false;
        }

        boolean send = false;
        if (pc.switchFreeLook() == 0 && freeLookKeyPressed && ac.modes().isFreeLook() && !ac.modes().canSwitchFreeLook() &&
                !ac.ropes().isRepelling()) {
            pc.setSwitchFreeLook(2);
            send = true;
        }

        if (pc.switchFreeLook() > 0) {
            ac.modes().predictFreeLook(pc.switchFreeLook() == 1);
        }

        return send;
    }
}
