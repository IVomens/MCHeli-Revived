package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.def.vehicle.RWRType;
import com.ivomens.mcheli.radar.EntityInfo;
import com.ivomens.mcheli.radar.client.EntityInfoClientTracker;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Top-down artillery scope for vehicles with a runtime radar: tracked entities around the vehicle,
 * oriented along the gun, plus a cross at the predicted impact point that turns red when a target is
 * within {@link #DEAD_ON_RADIUS} blocks of it.
 */
public final class MortarRadar implements Instrument {

    private final EntityInfoClientTracker contacts;

    /** @param contacts the radar contacts the server streams to this client */
    public MortarRadar(EntityInfoClientTracker contacts) {
        this.contacts = contacts;
    }

    private static final ResourceLocation RADAR = texture("mortar_radar");
    private static final ResourceLocation CROSS = texture("mortar_cross");
    private static final ResourceLocation TARGET = texture("mortar_target");

    /** Layout sizes are given for a 520 pixel high screen and scaled to the actual height. */
    private static final double DESIGN_SCREEN_HEIGHT = 520.0;
    private static final int SCOPE_SIZE = 250;
    private static final int SCOPE_CENTER_X = 150;
    private static final int SCOPE_CENTER_Y = 280;
    private static final int CROSS_SIZE = 21;
    private static final int TARGET_SIZE = 2;
    private static final double MIN_DISTANCE = 20.0;
    private static final double DEFAULT_MAX_DISTANCE = 300.0;
    private static final double DEAD_ON_RADIUS = 6.0;
    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final HudCanvas.Tint DEAD_ON_TINT = new HudCanvas.Tint(1.0F, 0.0F, 0.0F, 1.0F);

    @Override
    public void draw(HudCanvas canvas, float partialTicks, Player player, AircraftEntity ac) {
        if (!ac.radar().isOn()) return;
        double scale = canvas.height() / DESIGN_SCREEN_HEIGHT;

        double maxDistance = DEFAULT_MAX_DISTANCE;
        WeaponGroup weaponSet = ac.weapons().current(player);
        if (weaponSet != null) {
            WeaponSpec info = weaponSet.spec();
            if (info != null && info.camera().mortarRadarMaxDist() > 0) maxDistance = info.camera().mortarRadarMaxDist();
            // Refreshes the impact point for this tick.
            ac.weapons().impact().getLandInDistance(player);
        }

        float yawRad = (float) Math.toRadians(gunYaw(ac, weaponSet, partialTicks));
        Scope scope = new Scope(Instrument.interpolate(ac.getX(), ac.xo, partialTicks),
                Instrument.interpolate(ac.getZ(), ac.zo, partialTicks), Mth.sin(-yawRad), Mth.cos(yawRad),
                SCOPE_CENTER_X * scale, SCOPE_CENTER_Y * scale, SCOPE_SIZE * scale / 2.0, maxDistance);
        drawIcon(canvas, RADAR, scope.centerX, scope.centerY, scope.radius, HudCanvas.Tint.WHITE);

        Vec3 impact = ac.weapons().impact().impactPos();
        Vec3 prevImpact = ac.weapons().impact().prevImpactPos();
        if (impact == null || prevImpact == null) {
            drawContacts(canvas, ac, scope, scale, null, partialTicks);
            return;
        }
        Vec3 impactPoint = new Vec3(Instrument.interpolate(impact.x, prevImpact.x, partialTicks), 0.0,
                Instrument.interpolate(impact.z, prevImpact.z, partialTicks));
        boolean deadOn = drawContacts(canvas, ac, scope, scale, impactPoint, partialTicks);

        double impactDx = impactPoint.x - scope.vehicleX;
        double impactDz = impactPoint.z - scope.vehicleZ;
        double impactDistance = Math.sqrt(impactDx * impactDx + impactDz * impactDz);
        if (impactDistance < MIN_DISTANCE) return;
        drawIcon(canvas, CROSS, scope.markerX(impactDx, impactDz, impactDistance),
                scope.markerY(impactDx, impactDz, impactDistance), CROSS_SIZE * scale / 2.0,
                deadOn ? DEAD_ON_TINT : HudCanvas.Tint.WHITE);
    }

    /**
     * A dot and a label for every tracked entity around the vehicle.
     *
     * @return whether one of them is within {@link #DEAD_ON_RADIUS} of the impact point
     */
    private boolean drawContacts(HudCanvas canvas, AircraftEntity ac, Scope scope, double scale,
                                        Vec3 impact, float partialTicks) {
        boolean deadOn = false;
        for (EntityInfo entity : this.contacts.getAllTrackedEntities()) {
            if (!isValidEntity(entity, ac)) continue;

            double x = Instrument.interpolate(entity.x(), entity.prevX(), partialTicks);
            double z = Instrument.interpolate(entity.z(), entity.prevZ(), partialTicks);
            double dx = x - scope.vehicleX;
            double dz = z - scope.vehicleZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (impact != null) {
                double impactDx = x - impact.x;
                double impactDz = z - impact.z;
                deadOn |= impactDx * impactDx + impactDz * impactDz < DEAD_ON_RADIUS * DEAD_ON_RADIUS;
            }

            double markerX = scope.markerX(dx, dz, distance);
            double markerY = scope.markerY(dx, dz, distance);
            drawIcon(canvas, TARGET, markerX, markerY, TARGET_SIZE * scale / 2.0, HudCanvas.Tint.WHITE);
            String text = radarLabel(entity, ac) + "[" + (int) distance + "]";
            canvas.text(text, (int) (markerX - canvas.textWidth(text) / 2.0), (int) (markerY + 10.0), LABEL_COLOR,
                    true);
        }
        return deadOn;
    }

    /**
     * Where the scope sits and which way it faces.
     *
     * @param lookX the gun's look direction along x
     * @param lookZ the gun's look direction along z
     */
    private record Scope(double vehicleX, double vehicleZ, double lookX, double lookZ, double centerX,
                         double centerY, double radius, double maxDistance) {

        double markerX(double dx, double dz, double distance) {
            return this.centerX + this.radius * rangeFraction(distance, this.maxDistance)
                    * Math.sin(-bearing(this.lookX, this.lookZ, dx, dz));
        }

        double markerY(double dx, double dz, double distance) {
            return this.centerY - this.radius * rangeFraction(distance, this.maxDistance)
                    * Math.cos(bearing(this.lookX, this.lookZ, dx, dz));
        }
    }

    /** Vehicle yaw plus the turret yaw of the current weapon, interpolated. */
    private static float gunYaw(AircraftEntity ac, WeaponGroup weaponSet, float partialTicks) {
        float yaw = ac.yRotO + (ac.getYRot() - ac.yRotO) * partialTicks;
        if (weaponSet != null && weaponSet.currentMount() != null) {
            yaw += weaponSet.turret().prevYaw() + (weaponSet.yaw() - weaponSet.turret().prevYaw()) * partialTicks;
            yaw += weaponSet.currentMount().fixedYaw();
        }
        return yaw;
    }

    /** Signed horizontal angle in radians from the look direction to the offset (dx, dz). */
    private static double bearing(double lookX, double lookZ, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        double nx = length > 1.0E-4 ? dx / length : 0.0;
        double nz = length > 1.0E-4 ? dz / length : 0.0;
        double angle = Math.acos(Mth.clamp(lookX * nx + lookZ * nz, -1.0, 1.0));
        return lookX * nz - lookZ * nx > 0 ? -angle : angle;
    }

    private static double rangeFraction(double distance, double maxDistance) {
        return Mth.clamp((distance - MIN_DISTANCE) / (maxDistance - MIN_DISTANCE), 0.0, 1.0);
    }

    private static boolean isValidEntity(EntityInfo entity, AircraftEntity ac) {
        if (entity.kind().isDecoy()) {
            return false;
        }
        double dx = entity.x() - ac.getX();
        double dz = entity.z() - ac.getZ();
        return dx * dx + dz * dz >= MIN_DISTANCE * MIN_DISTANCE;
    }

    /** Digital RWR sets identify vehicles; everything else shows as unknown. */
    private static String radarLabel(EntityInfo entity, AircraftEntity ac) {
        if (ac.spec().file().features().rwr().type() == RWRType.DIGITAL && entity.kind().isVehicle()) {
            return ac.radar().labelFor(entity.entityId());
        }
        return "?";
    }

    private static void drawIcon(HudCanvas canvas, ResourceLocation texture, double x, double y, double half,
                                 HudCanvas.Tint tint) {
        canvas.texture(texture, x - half, y - half, half * 2, half * 2, tint);
    }

    private static ResourceLocation texture(String name) {
        return Ids.of("textures/gui/" + name + ".png");
    }
}
