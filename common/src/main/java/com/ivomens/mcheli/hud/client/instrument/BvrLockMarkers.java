package com.ivomens.mcheli.hud.client.instrument;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.net.BvrLockMessage;
import com.ivomens.mcheli.combat.weapon.net.RadarContactKind;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.radar.EntityInfo;
import com.ivomens.mcheli.radar.client.EntityInfoClientTracker;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Beyond-visual-range lock markers for weapons with BVR enabled. Every tracked aircraft (and chaff
 * for radar missiles) inside a 45 degree cone gets a box; the one closest to the crosshair is
 * locked and reported to the server. Incoming air-to-air missiles are marked as well.
 */
public final class BvrLockMarkers implements Instrument {

    private final EntityInfoClientTracker contacts;
    private final ScreenProjection projection;

    /**
     * @param contacts   the radar contacts the server streams to this client
     * @param projection where the markers go on screen
     */
    public BvrLockMarkers(EntityInfoClientTracker contacts, ScreenProjection projection) {
        this.contacts = contacts;
        this.projection = projection;
    }

    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/bvr_lock_box.png");
    private static final ResourceLocation MISSILE = ResourceLocation.fromNamespaceAndPath(Ids.MOD_ID,
            "textures/msl.png");

    private static final int BOX_SIZE = 24;
    private static final double MAX_ANGLE_DEG = 45.0;
    private static final int LOCK_PACKET_INTERVAL_TICKS = 10;
    private static final double MISSILE_MIN_RANGE = 100.0;
    private static final double MISSILE_MAX_RANGE = 1000.0;
    /** Markers further than this from the centre are pulled in to this radius. */
    private static final double EDGE_PULL_RADIUS = 200.0;
    private static final int LOCKED_COLOR = 0xFF0000;
    private static final int TRACKED_COLOR = 0x00FF00;

    private final double[] projected = new double[3];

    @Override
    public void draw(HudCanvas canvas, float partialTicks, Player player, AircraftEntity ac) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;

        WeaponGroup weaponSet = ac.weapons().current(player);
        if (weaponSet == null || weaponSet.currentMount() == null) return;
        WeaponSpec weapon = weaponSet.currentMount().spec();
        if (weapon == null || !weapon.missile().enableBvr()) return;

        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        Vec3 camera = this.projection.cameraPosition();
        for (EntityInfo entity : this.contacts.getAllTrackedEntities()) {
            Mark mark = markInRange(entity, player, weapon);
            if (mark == Mark.NONE) continue;

            double x = Instrument.interpolate(entity.x(), entity.prevX(), partialTicks);
            double y = Instrument.interpolate(entity.y(), entity.prevY(), partialTicks) + 1;
            double z = Instrument.interpolate(entity.z(), entity.prevZ(), partialTicks);
            if (!isInViewCone(x - camera.x, y - camera.y, z - camera.z, look)) continue;
            if (!this.projection.project(x, y, z, this.projected)) continue;
            if (this.projected[0] <= 0 || this.projected[1] <= 0) continue;
            drawMarker(canvas, mc, ac, entity, mark, Math.sqrt(player.distanceToSqr(x, y, z)));
        }
    }

    /** The marker of one entity at its projected position, locked when it sits on the crosshair. */
    private void drawMarker(HudCanvas canvas, Minecraft mc, AircraftEntity ac, EntityInfo entity, Mark mark,
                            double distance) {
        int screenHeight = canvas.height();
        double halfWidth = canvas.width() / 2.0;
        double halfHeight = screenHeight / 2.0;
        double screenX = this.projected[0];
        double screenY = this.projected[1];
        double offsetX = screenX - halfWidth;
        double offsetY = screenY - halfHeight;
        double centerDistSq = offsetX * offsetX + offsetY * offsetY;
        boolean locked = centerDistSq < square(0.038 * screenHeight);
        float alpha = markerAlpha(centerDistSq, screenHeight);
        if (locked) {
            sendLock(mc, entity);
        } else if (centerDistSq > square(0.384 * screenHeight)) {
            double ratio = EDGE_PULL_RADIUS / Math.sqrt(centerDistSq);
            screenX = halfWidth + offsetX * ratio;
            screenY = halfHeight + offsetY * ratio;
        }

        int textX = (int) (screenX - 20);
        int textY = (int) (screenY + 12);
        if (mark == Mark.INCOMING_MISSILE) {
            if (distance < MISSILE_MAX_RANGE && alpha > 0.4) {
                drawBox(canvas, MISSILE, screenX, screenY, true, alpha);
                canvas.text(String.format("[MSL %.1fm]", distance), textX, textY, LOCKED_COLOR, false);
            }
        } else {
            drawBox(canvas, FRAME, screenX, screenY, locked, alpha);
            if (alpha >= 0.6f) {
                String label = String.format("[%s %.1fm]", ac.radar().labelFor(entity.entityId()), distance);
                canvas.text(label, textX, textY, locked ? LOCKED_COLOR : TRACKED_COLOR, false);
            }
        }
    }

    private static boolean isInViewCone(double dx, double dy, double dz, Vec3 look) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) return false;
        double cos = Math.clamp((dx * look.x + dy * look.y + dz * look.z) / length, -1.0, 1.0);
        return Math.toDegrees(Math.acos(cos)) <= MAX_ANGLE_DEG;
    }

    /** Markers fade out the further they are from the crosshair. */
    private static float markerAlpha(double centerDistSq, int screenHeight) {
        if (centerDistSq < square(0.076 * screenHeight)) return 1.0f;
        if (centerDistSq < square(0.152 * screenHeight)) return 0.8f;
        if (centerDistSq < square(0.228 * screenHeight)) return 0.6f;
        if (centerDistSq < square(0.288 * screenHeight)) return 0.4f;
        if (centerDistSq > square(0.384 * screenHeight)) return 0.2f;
        return 0.1f;
    }

    private static void sendLock(Minecraft mc, EntityInfo entity) {
        if (mc.level != null && mc.level.getGameTime() % LOCK_PACKET_INTERVAL_TICKS == 0) {
            Network.sendToServer(new BvrLockMessage(entity.entityId()));
        }
    }

    private static double square(double value) {
        return value * value;
    }

    /** How a contact is marked. */
    enum Mark {
        /** A box that can be locked, labelled with the radar's name for it. */
        TARGET,
        /** The missile symbol: an air-to-air missile in flight, a threat to the player. */
        INCOMING_MISSILE,
        NONE
    }

    /**
     * How a contact of the kind is marked: aircraft are targets, chaff is one only for radar-guided
     * missiles (it decoys them), and air-to-air missiles are marked as incoming, as in McHeli where the
     * display marked every {@code MCH_EntityAAMissile}. Ground vehicles and ships are not air targets.
     *
     * @param radarMissile whether the selected weapon is a radar-guided missile
     */
    static Mark markOf(RadarContactKind kind, boolean radarMissile) {
        return switch (kind) {
            case HELICOPTER, PLANE -> Mark.TARGET;
            case CHAFF -> radarMissile ? Mark.TARGET : Mark.NONE;
            case AIR_TO_AIR_MISSILE -> Mark.INCOMING_MISSILE;
            case SHIP, TANK, GROUND_VEHICLE, OTHER_MISSILE, FLARE, OTHER -> Mark.NONE;
        };
    }

    /** The contact's mark, or {@link Mark#NONE} when it is too close to the player to be marked. */
    private static Mark markInRange(EntityInfo entity, Player player, WeaponSpec weapon) {
        double distSq = entity.getDistanceSqToEntity(player);
        double minRangeSq = weapon.missile().minRangeBvr() * weapon.missile().minRangeBvr();
        Mark mark = markOf(entity.kind(), weapon.missile().radar().radarMissile());
        boolean inRange = switch (mark) {
            case TARGET -> distSq > minRangeSq;
            case INCOMING_MISSILE -> distSq > MISSILE_MIN_RANGE * MISSILE_MIN_RANGE;
            case NONE -> false;
        };
        return inRange ? mark : Mark.NONE;
    }

    private static void drawBox(HudCanvas canvas, ResourceLocation texture, double x, double y, boolean locked,
                                float alpha) {
        double half = BOX_SIZE / 2.0;
        canvas.texture(texture, x - half, y - half, BOX_SIZE, BOX_SIZE,
                new HudCanvas.Tint(locked ? 1.0F : 0.0F, locked ? 0.0F : 1.0F, 0.0F, alpha));
    }
}
