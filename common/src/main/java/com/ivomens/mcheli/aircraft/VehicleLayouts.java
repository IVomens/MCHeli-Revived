package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.aircraft.VehicleParts.CameraPosition;
import com.ivomens.mcheli.aircraft.VehicleParts.CrawlerTrack;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.Rack;
import com.ivomens.mcheli.content.def.vehicle.Seat;
import com.ivomens.mcheli.content.def.vehicle.SeatExclusions;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleRules;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the run-time layout of a vehicle from its file, once when it loads: the crew, the armament, the
 * drawn parts and the hit boxes. A few values are still derived differently for legacy TXT files, as the old
 * parsers did.
 */
public final class VehicleLayouts {

    private VehicleLayouts() {
    }

    public static CrewLayout crew(VehicleFile file) {
        return new CrewLayout(seats(file), VehicleRules.riderSeats(file).size(), file.crew().racks().size(),
                exclusions(file), cameraPositions(file),
                PartConversions.map(file.crew().rideRacks(), rack -> new VehicleParts.RideRack(rack.name(),
                        rack.rack())),
                PartConversions.map(VehicleRules.repellingHooks(file),
                        hook -> new VehicleParts.RepellingHook(hook.position(), hook.interval())),
                new MobDropOption(file.features().parachuting().position(),
                        file.features().parachuting().interval()));
    }

    public static Armament armament(VehicleFile file) {
        var flare = file.features().flare();
        return new Armament(PartConversions.weaponSets(file), new VehicleParts.Flare(flare.position(),
                flare.types().stream().mapToInt(Integer::intValue).toArray()),
                flare.chaff() ? new VehicleParts.Chaff() : null);
    }

    public static DrawnParts drawnParts(VehicleFile file, boolean legacyTxt) {
        var parts = file.components();
        return new DrawnParts(PartConversions.hatches(file),
                PartConversions.map(parts.lightHatches(), PartConversions::hatch),
                PartConversions.map(parts.cameras(), PartConversions::camera),
                PartConversions.map(parts.canopies(), PartConversions::canopy),
                PartConversions.map(parts.landingGear(), gear -> PartConversions.landingGear(gear, legacyTxt)),
                PartConversions.map(parts.throttles(), PartConversions::throttle),
                PartConversions.map(parts.rotatingParts(), PartConversions::rotating),
                crawlerTracks(file, legacyTxt),
                PartConversions.map(parts.trackRollers(), PartConversions::trackRoller),
                PartConversions.map(parts.wheels(), PartConversions::wheel),
                PartConversions.map(parts.steeringWheels(), PartConversions::wheel),
                PartConversions.map(parts.weapons(), PartConversions::weapon),
                PartConversions.weaponBays(file),
                PartConversions.map(parts.searchLights(), PartConversions::searchLight),
                PartConversions.map(file.render().splashParticles(), splash -> new VehicleParts.ParticleSplash(
                        splash.count(), splash.acceleration(), splash.size(), splash.position(), splash.age(),
                        splash.motion(), splash.gravity())));
    }

    /** Each kind's own parts; hatches and wings share a control, so a plane with both has no wings. */
    public static KindParts kindParts(VehicleFile file, boolean legacyTxt) {
        var parts = file.components();
        return new KindParts(
                PartConversions.map(parts.heliRotors(), VehicleParts.HeliRotor::new),
                PartConversions.map(parts.propellers(), VehicleParts.Propeller::new),
                VehicleRules.hatchesAndWingsClash(file) ? List.of()
                        : PartConversions.map(parts.wings(), VehicleParts.Wing::new),
                PartConversions.map(parts.nozzles(), nozzle -> PartConversions.placed(nozzle.position(),
                        nozzle.rotation(), nozzle.name())),
                PartConversions.map(parts.exhaustFlames(), VehicleParts.ExhaustFlame::new),
                PartConversions.map(parts.turretParts(), part -> new VehicleParts.VPart(part, legacyTxt)));
    }

    public static HitBoxes hitBoxes(VehicleFile file, boolean legacyTxt) {
        List<PartBoundingBox> boxes = PartConversions.map(file.boundingBoxes(), PartConversions::hitBox);
        float[] bounds = PartConversions.markerBounds(boxes, legacyTxt);
        return new HitBoxes(boxes, PartConversions.map(file.wheels().hitBoxes(),
                wheel -> new VehicleParts.Wheel(wheel.position(), wheel.scale())), bounds[0], bounds[1], bounds[2],
                bounds[3]);
    }

    /** The file's own texture first, then the extra ones. */
    public static List<String> textures(VehicleFile file) {
        List<String> textures = new ArrayList<>();
        textures.add(file.name());
        textures.addAll(file.render().textures());
        return List.copyOf(textures);
    }

    /** Rider seats first, then racks. */
    private static List<SeatInfo> seats(VehicleFile file) {
        List<SeatInfo> seats = new ArrayList<>();
        for (Seat seat : VehicleRules.riderSeats(file)) {
            CameraPosition camera = seat.camera() == null ? null : cameraPosition(seat.camera());
            seats.add(new SeatInfo(seat.position(), seat.unmountPosition(), seat.gunner(), camera,
                    seat.invertCamera(), seat.switchGunner(), seat.fixedRotation(), seat.fixedYaw(),
                    seat.fixedPitch(), seat.minPitch(), seat.maxPitch(), seat.rotating()));
        }
        for (Rack rack : file.crew().racks()) {
            Vec3 position = rack.position();
            seats.add(new SeatRackInfo(rack.names().toArray(new String[0]), position.x, position.y, position.z,
                    rack.unmountPosition(), cameraPosition(rack.entry()), rack.range(), rack.yaw(), rack.pitch(),
                    rack.rotating()));
        }
        return List.copyOf(seats);
    }

    private static CameraPosition cameraPosition(CameraSpec camera) {
        return new CameraPosition(VehicleRules.raisedCamera(camera), camera.fixedRotation(), camera.yaw(),
                camera.pitch());
    }

    /** The third person camera positions; without any, one at the model's origin. */
    private static List<CameraPosition> cameraPositions(VehicleFile file) {
        List<CameraSpec> positions = file.camera().positions();
        if (positions.isEmpty()) {
            return List.of(cameraPosition(new CameraSpec(Vec3.ZERO, false, 0.0F, 0.0F)));
        }
        return PartConversions.map(positions, VehicleLayouts::cameraPosition);
    }

    /**
     * Each seat's and rack's {@code ExcludeWith} as pairs, seats and racks numbered together from 0, then the
     * {@code ExclusiveSeats} groups.
     */
    private static List<Integer[]> exclusions(VehicleFile file) {
        int seatCount = file.crew().seats().size();
        List<Integer[]> groups = new ArrayList<>();
        List<Seat> seats = file.crew().seats();
        for (int i = 0; i < seats.size(); i++) {
            addPairs(groups, i, seats.get(i).excludeWith(), seatCount);
        }
        List<Rack> racks = file.crew().racks();
        for (int i = 0; i < racks.size(); i++) {
            addPairs(groups, seatCount + i, racks.get(i).excludeWith(), seatCount);
        }
        for (List<Integer> group : file.crew().exclusiveSeats()) {
            groups.add(group.stream().map(seat -> seat - 1).toArray(Integer[]::new));
        }
        return List.copyOf(groups);
    }

    private static void addPairs(List<Integer[]> groups, int owner, SeatExclusions exclusions, int seatCount) {
        exclusions.seats().stream().filter(seat -> seat >= 1)
                .forEach(seat -> groups.add(new Integer[]{owner, seat - 1}));
        exclusions.racks().stream().filter(rack -> rack >= 1)
                .forEach(rack -> groups.add(new Integer[]{owner, seatCount + rack - 1}));
    }

    /** A legacy TXT track with fewer than four points keeps its place as null. */
    private static List<CrawlerTrack> crawlerTracks(VehicleFile file, boolean legacyTxt) {
        List<CrawlerTrack> tracks = new ArrayList<>();
        file.components().crawlerTracks().forEach(track -> tracks.add(TrackLinks.of(track, legacyTxt)));
        return tracks;
    }
}
