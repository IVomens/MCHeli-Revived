package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.inspector.SnapshotBuilder.SectionWriter;
import com.ivomens.mcheli.vehicle.component.SeatsComponent;
import com.ivomens.mcheli.vehicle.component.WeaponComponent;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Locale;

/** The weapons and crew tabs of a snapshot: one section per weapon, one per seat. */
final class CrewSections {

    private CrewSections() {
    }

    static void weapons(SnapshotBuilder builder, AircraftEntity v) {
        WeaponComponent weapons = v.weapons();
        WeaponGroup[] all = weapons.all();
        for (int id = 0; id < all.length; id++) {
            WeaponGroup group = all[id];
            String kind = group.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            SectionWriter out = builder.section(InspectorTab.WEAPONS, "#" + id + " " + group.firstMount().name(),
                    kind + ", " + group.mountCount() + (group.mountCount() == 1 ? " mount" : " mounts"));
            out.put("weapon id", group.firstMount().name());
            out.put("display name", group.displayName());
            out.put("ammo", group.ammo() + " / " + group.magazineSize());
            out.put("reserve", group.reserveAmmo() + " (max " + group.maxAmmo() + ")");
            out.put("heat", group.heat());
            out.put("cooldown", group.cooldown());
            out.put("reload ticks", group.reloadTicks());
            out.put("current mount", (group.currentIndex() + 1) + " of " + group.mountCount());
            WeaponMount mount = group.currentMount();
            out.put("mode", mount == null ? 0 : mount.currentMode());
            out.put("used by", name(weapons.playerUsing(id)));
            lock(out, mount == null ? null : mount.seeker());
        }
    }

    private static void lock(SectionWriter out, @Nullable LockOnSeeker seeker) {
        if (seeker == null) {
            out.put("lock", "no seeker");
            return;
        }
        Entity target = seeker.lockingTarget();
        String state = seeker.isLockComplete() ? "locked" : target != null ? "locking" : "searching";
        out.put("lock", state + " " + seeker.lockCount() + " / " + seeker.lockTicks());
        out.put("lock target", name(target));
        out.put("seeker range", seeker.range());
    }

    static void seats(SnapshotBuilder builder, AircraftEntity v) {
        SeatsComponent seats = v.seats();
        for (int seatId = 0; seatId <= seats.count(); seatId++) {
            SeatInfo info = seats.info(seatId);
            String role = seatId == 0 ? "pilot" : info != null && info.gunner ? "gunner" : "passenger";
            SectionWriter out = builder.section(InspectorTab.CREW, "Seat " + seatId, role);
            out.put("rider", name(seats.riderOf(seatId)));
            out.put("weapon", weaponOf(v, seatId));
            if (seatId > 0) {
                out.put("seat entity", seats.seat(seatId - 1) == null ? "-" : "#" + seats.seat(seatId - 1).getId());
            }
            if (info != null) {
                out.put("position", VehicleSnapshots.vector(info.pos));
                out.put("pitch limits", VehicleSnapshots.number(info.minPitch) + " to "
                        + VehicleSnapshots.number(info.maxPitch));
                out.put("switches to gunner", info.switchgunner);
            }
        }
        out(builder, v);
    }

    /** Who rides the vehicle in total, as a last crew section. */
    private static void out(SnapshotBuilder builder, AircraftEntity v) {
        SectionWriter out = builder.section(InspectorTab.CREW, "Aboard", "");
        out.put("passengers", v.seats().passengers().size());
        out.put("gunners allowed", v.seats().gunnersAllowed());
        out.put("seats created", v.seats().areCreated());
    }

    private static String weaponOf(AircraftEntity v, int seatId) {
        int id = v.weapons().idForSeat(seatId);
        return id < 0 || id >= v.weapons().count() ? "-" : "#" + id + " " + v.weapons().get(id).firstMount().name();
    }

    static String name(@Nullable Entity entity) {
        return entity == null ? "-" : entity.getName().getString();
    }
}
