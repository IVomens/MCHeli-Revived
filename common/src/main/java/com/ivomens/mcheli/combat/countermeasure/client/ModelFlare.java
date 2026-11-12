package com.ivomens.mcheli.combat.countermeasure.client;

import com.ivomens.mcheli.client.render.LegacyGL;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.Entity;

/** 4x4x4 pixel cube used for flares and chaff (4x4 texture). */
public class ModelFlare {

    public final ModelPart model;

    public ModelFlare() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("flare",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4, 4, 4), PartPose.ZERO);
        this.model = LayerDefinition.create(mesh, 4, 4).bakeRoot().getChild("flare");
    }

    /**
     * Draws the cube at {@code x, y, z}, turned with the entity and tilted 45 degrees so it reads
     * as a diamond, at {@code scale} times its pixel size.
     */
    public void render(Entity entity, double x, double y, double z, float scale) {
        LegacyGL.pushMatrix();
        LegacyGL.enableCull();
        LegacyGL.translate(x, y, z);
        LegacyGL.rotate(-entity.getYRot(), 0.0F, 1.0F, 0.0F);
        LegacyGL.rotate(entity.getXRot(), 1.0F, 0.0F, 0.0F);
        LegacyGL.rotate(45.0F, 0.0F, 0.0F, 1.0F);
        LegacyGL.scale(scale, scale, scale);
        LegacyGL.renderModelPart(this.model);
        LegacyGL.popMatrix();
    }
}
