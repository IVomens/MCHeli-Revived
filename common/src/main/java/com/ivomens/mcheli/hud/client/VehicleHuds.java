package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.content.def.vehicle.Seat;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.hud.HudManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The HUD each seat of a vehicle shows, resolved once per vehicle definition. A seat that names no HUD, or one
 * that is not loaded, shows nothing; seats past those of the file show their kind's HUD (null when that is not
 * loaded either), and a one-seat vehicle has a second HUD for gunner mode.
 */
public final class VehicleHuds {

    /** The HUD shown while the viewer steers a TV missile. */
    public static final String TV_MISSILE = "tv_missile";

    private static final Map<ContentId, Resolved> RESOLVED = new ConcurrentHashMap<>();

    private record Resolved(VehicleSpec vehicle, List<Hud> seats) {
    }

    private VehicleHuds() {
    }

    /** The HUD of each seat, by seat; an entry is null where the kind's HUD is not loaded. */
    public static List<Hud> of(VehicleSpec vehicle) {
        Resolved resolved = RESOLVED.get(vehicle.id());
        if (resolved == null || resolved.vehicle() != vehicle) {
            resolved = new Resolved(vehicle, resolve(vehicle.file()));
            RESOLVED.put(vehicle.id(), resolved);
        }
        return resolved.seats();
    }

    private static List<Hud> resolve(VehicleFile file) {
        List<Hud> huds = new ArrayList<>();
        for (Seat seat : file.crew().seats()) {
            huds.add(shown(seat.hud() == null ? "none" : seat.hud()));
        }
        int seatCount = VehicleRules.riderSeats(file).size();
        for (int seat = huds.size(); seat < seatCount; seat++) {
            huds.add(kindDefault(file, seat));
        }
        if (seatCount == 1 && huds.size() == 1) {
            String gunnerHud = file.crew().gunnerHud();
            huds.add(gunnerHud != null ? shown(gunnerHud) : kindDefault(file, 1));
        }
        return Collections.unmodifiableList(huds);
    }

    private static Hud shown(String name) {
        return HudManager.get(name).orElse(Hud.NoDisp);
    }

    private static Hud kindDefault(VehicleFile file, int seat) {
        return HudManager.get(VehicleRules.defaultHud(file, seat)).orElse(null);
    }
}
