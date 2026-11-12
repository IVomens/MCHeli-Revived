package com.ivomens.mcheli.vehicle.client.debug;

import com.ivomens.mcheli.client.render.LegacyGL;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** 10x10x10 pixel debug cube. */
public class DebugModel {

    public final ModelPart cube;

    public DebugModel() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -5.0F, -5.0F, 10, 10, 10), PartPose.ZERO);
        this.cube = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("cube");
    }

    /** Draws the cube at {@code scale} times its pixel size. */
    public void render(float scale) {
        LegacyGL.pushMatrix();
        LegacyGL.scale(scale, scale, scale);
        LegacyGL.renderModelPart(this.cube);
        LegacyGL.popMatrix();
    }
}
