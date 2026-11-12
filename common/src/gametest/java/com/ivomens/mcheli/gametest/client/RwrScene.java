package com.ivomens.mcheli.gametest.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.RiddenVehicle;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.config.HudSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.vehicle.AircraftFeatures;
import com.ivomens.mcheli.content.def.vehicle.RWRType;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The missile warning in the dev scene, after the inspector: the boarded AH-64 is given a digital RWR, and an
 * AIM-120 (a radar missile) and an AIM-9 (a heat seeker) hang in the air homing on it, so the modern HUD and
 * the legacy HUD's RWR scope are photographed naming their seekers and cueing flares and chaff.
 */
final class RwrScene {

    static final int FIRST_TICK = InspectorScene.LAST_TICK;
    static final int LAST_TICK = FIRST_TICK + 70;

    /** Where the missiles hang, from the vehicle: one ahead and to the left, one behind and to the right. */
    private static final Vec3 RADAR_MISSILE_OFFSET = new Vec3(30, 12, 30);
    private static final Vec3 HEAT_MISSILE_OFFSET = new Vec3(-25, 8, -20);

    /** The missiles held in the air, on the server thread only. */
    private static final List<Hanging> MISSILES = new ArrayList<>();

    private RwrScene() {
    }

    static void onClientTick(Minecraft mc, int tick, AircraftEntity boarded) {
        if (boarded == null) {
            return;
        }
        switch (tick - FIRST_TICK) {
            case 0 -> start(mc, boarded);
            case 20, 24, 28 -> capture(mc, "devscene_rwr_modern_" + (tick - FIRST_TICK) + ".png");
            case 30 -> McheliConfig.update(settings -> settings.with(HudSettings.STYLE, "legacy"));
            case 40, 44 -> capture(mc, "devscene_rwr_legacy_" + (tick - FIRST_TICK) + ".png");
            case 46 -> McheliConfig.update(settings -> settings.with(HudSettings.STYLE, "modern"));
            case 50 -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(RwrScene::stop);
                }
            }
            default -> {
            }
        }
    }

    /** Keeps the missiles where they hang, homing on the vehicle. */
    static void onServerTick() {
        for (Hanging missile : MISSILES) {
            missile.keep();
        }
    }

    private static void start(Minecraft mc, AircraftEntity boarded) {
        mc.setScreen(null);
        mc.options.hideGui = false;
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        // the scene's vehicle is the server's; the client's copy is the one the player rides
        AircraftEntity ridden = RiddenVehicle.of(mc.player);
        if (ridden != null) {
            ridden.setSpec(withDigitalRwr(ridden.spec()));
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        int id = boarded.getId();
        server.execute(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof AircraftEntity vehicle) {
                    vehicle.setSpec(withDigitalRwr(vehicle.spec()));
                    MISSILES.add(new Hanging(level, "aim120", vehicle, RADAR_MISSILE_OFFSET));
                    MISSILES.add(new Hanging(level, "aim9", vehicle, HEAT_MISSILE_OFFSET));
                    ModLogger.get().info("[rwr] missiles homing on {}", vehicle);
                }
            }
        });
    }

    private static void stop() {
        MISSILES.forEach(Hanging::discard);
        MISSILES.clear();
    }

    private static VehicleSpec withDigitalRwr(VehicleSpec info) {
        AircraftFeatures f = info.file().features();
        AircraftFeatures features = new AircraftFeatures(f.gunnerMode(), f.inventorySize(), f.nightVision(),
                f.entityRadar(), f.canReverse(), f.concurrentGunner(), f.ejectionSeat(), f.throttleUpDown(),
                f.throttleUpDownOnEntity(), f.parachuting(), f.flare(), f.engineShutdownHp(), f.aps(),
                f.explosionDamageFactor(), f.radar(), new AircraftFeatures.Rwr(true, RWRType.DIGITAL, "AH64"), f.ecm());
        VehicleFile file = info.file().withFeatures(features);
        ContentEntry<VehicleFile> entry = VehicleTypes.entry(info).orElseThrow();
        return VehicleSpec.of(new ContentEntry<>(entry.type(), entry.location(), entry.filePath(), entry.format(),
                entry.author(), file));
    }

    private static void capture(Minecraft mc, String file) {
        AircraftEntity ridden = RiddenVehicle.of(mc.player);
        if (ridden != null) {
            ModLogger.get().info("[rwr] {} sees {}", file,
                    com.ivomens.mcheli.vehicle.sensor.client.IncomingMissileReader.read(ridden));
        }
        for (Hanging missile : MISSILES) {
            ModLogger.get().info("[rwr] missile {} target {} removed {}", missile.missile,
                    missile.missile == null ? null : missile.missile.target(),
                    missile.missile == null || missile.missile.isRemoved());
        }
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {
        });
    }

    /** A missile of a weapon held at an offset from its target. */
    private static final class Hanging {

        private final ServerLevel level;
        private final String weapon;
        private final Entity target;
        private final Vec3 offset;
        private ProjectileEntity missile;
        private Entity launcher;

        Hanging(ServerLevel level, String weapon, Entity target, Vec3 offset) {
            this.level = level;
            this.weapon = weapon;
            this.target = target;
            this.offset = offset;
        }

        /** An unseen stand the missile counts as fired from, so it stays in range and is not removed. */
        private Entity launcher(Vec3 at) {
            if (this.launcher == null || this.launcher.isRemoved()) {
                ArmorStand stand = new ArmorStand(this.level, at.x, at.y - 3, at.z);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                this.level.addFreshEntity(stand);
                this.launcher = stand;
            }
            return this.launcher;
        }

        void keep() {
            Vec3 at = this.target.position().add(this.offset);
            if (this.missile == null || this.missile.isRemoved()) {
                this.missile = ProjectileEntity.create(ProjectileKind.AA_MISSILE, this.level, at,
                        this.offset.reverse().normalize(), 0.0F, 0.0F, 0.0);
                this.missile.setWeapon(this.weapon);
                this.missile.setShooter(launcher(at), null);
                this.level.addFreshEntity(this.missile);
                this.missile.setTarget(this.target);
            }
            this.missile.setPos(at.x, at.y, at.z);
            this.missile.setDeltaMovement(Vec3.ZERO);
        }

        void discard() {
            if (this.missile != null) {
                this.missile.discard();
            }
            if (this.launcher != null) {
                this.launcher.discard();
            }
        }
    }
}
