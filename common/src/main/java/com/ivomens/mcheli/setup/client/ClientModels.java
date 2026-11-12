package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.client.model.IModelCustom;
import com.ivomens.mcheli.client.model.ModelLoader;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.projectile.client.ProjectileModels;
import com.ivomens.mcheli.combat.projectile.client.BulletModel;
import com.ivomens.mcheli.combat.weapon.Cartridge;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.weapon.CasingSpec;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.equipment.gltd.client.GltdRenderer;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import com.ivomens.mcheli.vehicle.client.VehicleModelLoader;
import com.ivomens.mcheli.world.uavstation.client.UavStationRenderer;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the client's models: those of the built-in items, blocks and entities, the bullets named by
 * the weapons, the throwables and, unless they load on demand, the vehicles.
 */
public final class ClientModels {

    /** The item, bullet and throwable models are parsed again whenever the models are registered. */
    private static final ModelLoader MODELS = ModelLoader.REPARSING;

    private ClientModels() {
    }

    /** Loads the models of the built-in items, bullets, throwables and vehicles; once, before baking. */
    public static void registerModels() {
        if (OnDemandModels.isEnabled()) {
            registerModelsOnDemand();
            return;
        }
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(ClientModels::loadMiscModels),
                    CompletableFuture.runAsync(ClientModels::loadAllBulletModels),
                    CompletableFuture.runAsync(ClientModels::loadThrowableModels),
                    CompletableFuture.runAsync(() -> loadModels(VehicleTypes.registry(VehicleKind.HELICOPTER))),
                    CompletableFuture.runAsync(() -> loadModels(VehicleTypes.registry(VehicleKind.PLANE))),
                    CompletableFuture.runAsync(() -> loadModels(VehicleTypes.registry(VehicleKind.SHIP))),
                    CompletableFuture.runAsync(() -> loadModels(VehicleTypes.registry(VehicleKind.TANK))),
                    CompletableFuture.runAsync(() -> loadModels(VehicleTypes.registry(VehicleKind.VEHICLE)))
            ).join();
        } catch (Exception ex) {
            ModLogger.error("Error during model loading", ex);
        }
    }

    private static void loadModels(ContentRegistry<?, VehicleSpec> registry) {
        registry.values().parallelStream().forEach(info -> VehicleModelLoader.load(info, false));
    }

    /** Loads everything except vehicles now; vehicle models are loaded when first rendered. */
    private static void registerModelsOnDemand() {
        loadMiscModels();
        loadAllBulletModels();
        loadThrowableModels();

        OnDemandModels.installAll();
        ModLogger.info("On-demand model loading enabled, vehicle models load when first rendered.");
    }

    /** Models of the built-in items, blocks and entities that are not defined by content files. */
    private static void loadMiscModels() {
        MODELS.load("box");
        MODELS.load("a-10");
        MODELS.load("gltd");
        MODELS.load("chain");
        MODELS.load("container");
        MODELS.load("parachute1");
        MODELS.load("parachute2");
        MODELS.load("wrench");
        MODELS.load("rangefinder");
        MODELS.load("lweapons", "fim92");
        MODELS.load("lweapons", "fgm148");
        MODELS.load("blocks", "drafting_table");
        for (String name : UavStationRenderer.MODELS) {
            MODELS.load(name);
        }
    }

    private static void loadThrowableModels() {
        // the renderer finds them in the model cache
        for (ThrowableDefinition info : Throwables.registry().values()) {
            MODELS.load("throwable", info.name());
        }
    }

    private static void loadAllBulletModels() {
        for (ProjectileKind kind : ProjectileKind.values()) {
            ProjectileModels.setDefault(kind, loadBulletModel(ProjectileModels.defaultModelName(kind)));
        }
        loadWeaponBulletModels();
    }

    /** Bullet, bomblet and cartridge models named by the weapon definitions. */
    private static void loadWeaponBulletModels() {
        for (WeaponSpec weapon : Weapons.registry().values()) {
            WeaponDefinition.Render render = weapon.render();
            if (!render.bulletModel().isEmpty()) {
                ProjectileModels.setBulletModel(weapon.name(), loadBulletModel(render.bulletModel()));
            }
            if (!render.bombletModel().isEmpty()) {
                ProjectileModels.setBombletModel(weapon.name(), loadBulletModel(render.bombletModel()));
            }
            CasingSpec casing = render.spentCasings();
            if (casing != null && !casing.name().isEmpty()) {
                // the client throws out no casing whose model is missing (see SpentCaseEjector)
                MODELS.load("bullets", casing.name());
            }
        }
    }

    private static BulletModel loadBulletModel(String name) {
        IModelCustom model = MODELS.load("bullets", name);
        return model != null ? new BulletModel(name, model) : null;
    }
}
