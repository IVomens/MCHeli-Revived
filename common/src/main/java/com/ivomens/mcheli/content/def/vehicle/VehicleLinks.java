package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Mount;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The link step of a vehicle file: checks the weapons and HUDs it names against those loaded. Weapons and
 * HUDs load before vehicles, so every name a vehicle can use is known by then.
 */
public final class VehicleLinks {

    /** The HUD name of a seat that shows none. */
    private static final String NO_HUD = "none";

    private VehicleLinks() {
    }

    /**
     * A name nothing answers to.
     *
     * @param path where the file gives it, such as {@code Weapons[2]}
     */
    public record Unresolved(String path, String name, String kind) {

        public String describe() {
            return this.path + " names " + this.kind + " '" + this.name + "', which is not loaded";
        }
    }

    /**
     * The file with the mounts of weapons that are not loaded left out, and every name that was not found.
     *
     * @param file      the file as the game uses it
     * @param weapons   unknown weapons; their mounts are not in {@code file}
     * @param huds      unknown HUDs; the seats show none
     */
    public record Linked(VehicleFile file, List<Unresolved> weapons, List<Unresolved> huds) {
    }

    /**
     * @param weaponLoaded whether a weapon of that name is loaded
     * @param hudLoaded    whether a HUD of that name is loaded, or null where HUDs are not loaded (a server)
     */
    public static Linked link(VehicleFile file, Predicate<String> weaponLoaded, Predicate<String> hudLoaded) {
        List<Mount> kept = new ArrayList<>();
        List<Unresolved> weapons = new ArrayList<>();
        List<Mount> mounts = file.weapons();
        for (int i = 0; i < mounts.size(); i++) {
            String weapon = mounts.get(i).weapon().name();
            if (weaponLoaded.test(weapon)) {
                kept.add(mounts.get(i));
            } else {
                weapons.add(new Unresolved("Weapons[" + (i + 1) + "]", weapon, "weapon"));
            }
        }
        List<Unresolved> huds = new ArrayList<>();
        if (hudLoaded != null) {
            List<Seat> seats = file.crew().seats();
            for (int i = 0; i < seats.size(); i++) {
                String hud = seats.get(i).hud();
                if (hud != null && !hud.equals(NO_HUD) && !hudLoaded.test(hud)) {
                    huds.add(new Unresolved("Seats[" + (i + 1) + "].Hud", hud, "HUD"));
                }
            }
        }
        VehicleFile linked = weapons.isEmpty() ? file : file.withWeapons(kept);
        return new Linked(linked, List.copyOf(weapons), List.copyOf(huds));
    }
}
