package com.ivomens.mcheli.vehicle.client.render.flywheel;

import com.ivomens.mcheli.client.model.MeshDetail;
import com.ivomens.mcheli.client.model.PartMesh;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderSnapshot;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Flywheel models of vehicle parts, shared by every vehicle that uses the part: one mesh per part
 * and level of detail, and one model per mesh and texture. Two vehicles of a type therefore draw a
 * part from the same instancer, in one draw call. Parts are held weakly, so the models of a vehicle
 * whose models were reloaded go with them. {@link ModelKeepers} keeps the models in use resident.
 */
final class FlywheelModels {

    private static final Map<PartMesh, PartModels> PARTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ResourceLocation, Material> MATERIALS = new ConcurrentHashMap<>();

    private FlywheelModels() {
    }

    /**
     * The level of detail Flywheel draws a vehicle at, from {@link VehicleRenderSnapshot.Detail#unitsPerPixel()}:
     * the full mesh, or the coarsest reduced level. Each level is a model of its own, so a draw call per
     * part and level in view; the intermediate levels the immediate renderer uses would multiply the
     * draw calls (each costs its state changes on Flywheel's instancing backend) and every new level a
     * moving camera reaches adds a mesh, so an upload of the whole mesh pool. The far level only kicks in
     * where its cells are under a pixel.
     */
    static int detailLevel(float unitsPerPixel) {
        int level = MeshDetail.levelFor(unitsPerPixel);
        return level == farLevel() ? level : MeshDetail.FULL;
    }

    /** The coarsest level, the only reduced one Flywheel draws. */
    static int farLevel() {
        return MeshDetail.levelCount() - 1;
    }

    /** The model of a part at a level of detail with a texture, or null when the part has no faces there. */
    @Nullable
    static Model model(PartMesh part, int detailLevel, ResourceLocation texture) {
        return PARTS.computeIfAbsent(part, key -> new PartModels()).model(part, detailLevel, texture);
    }

    /**
     * Like the immediate renderer's vehicle geometry: textured, translucent in drawing order with
     * depth writes, back faces culled, entity lighting, light and overlay from the instance.
     */
    private static Material material(ResourceLocation texture) {
        return MATERIALS.computeIfAbsent(texture, location -> SimpleMaterial.builder()
                .texture(location)
                .mipmap(false)
                .transparency(Transparency.TRANSLUCENT)
                .cutout(CutoutShaders.ONE_TENTH)
                .backfaceCulling(true)
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .ambientOcclusion(false)
                .build());
    }

    /**
     * The meshes and models of one part, per level of detail. It must not hold the part itself, which
     * is the weak key it is stored under.
     */
    private static final class PartModels {

        private final BakedPartMesh[] meshes = new BakedPartMesh[MeshDetail.levelCount() + 1];
        @SuppressWarnings("unchecked")
        private final Map<ResourceLocation, Model>[] models = new Map[MeshDetail.levelCount() + 1];

        @Nullable
        synchronized Model model(PartMesh part, int detailLevel, ResourceLocation texture) {
            int slot = detailLevel + 1;
            if (this.meshes[slot] == null) {
                this.createLevel(part, slot);
            }
            if (this.meshes[slot].isEmpty()) {
                return null;
            }
            BakedPartMesh mesh = this.meshes[slot];
            return this.models[slot].computeIfAbsent(texture, location -> {
                FlywheelVehicleStats.modelCreated();
                return new SingleMeshModel(mesh, material(location));
            });
        }

        /**
         * Many levels keep the full mesh (small groups, or nothing to gain); those share the mesh and
         * the models of the level they equal, so Flywheel holds each set of vertices once.
         */
        private void createLevel(PartMesh part, int slot) {
            float[] vertices = part.vertices(slot - 1);
            for (int other = 0; other < this.meshes.length; other++) {
                if (this.meshes[other] != null && this.meshes[other].hasVertices(vertices)) {
                    this.meshes[slot] = this.meshes[other];
                    this.models[slot] = this.models[other];
                    return;
                }
            }
            this.meshes[slot] = new BakedPartMesh(vertices);
            FlywheelVehicleStats.meshCreated();
            this.models[slot] = new ConcurrentHashMap<>();
        }
    }
}
