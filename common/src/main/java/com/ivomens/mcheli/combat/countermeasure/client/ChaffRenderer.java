package com.ivomens.mcheli.combat.countermeasure.client;

import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.entity.client.McheliEntityRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/** Chaff: a small cube, plus the lock-on marker since radar missiles can lock onto it. */
public class ChaffRenderer extends McheliEntityRenderer<ChaffEntity> {

    private static final ResourceLocation TEX = texture("textures/chaff.png");
    private final ModelFlare model = new ModelFlare();
    private final Consumer<Entity> lockOnMarker;

    protected ChaffRenderer(EntityRendererProvider.Context context, ThermalVision thermal,
                            Consumer<Entity> lockOnMarker) {
        super(context, thermal);
        this.lockOnMarker = lockOnMarker;
    }

    /**
     * @param lockOnMarker draws the local player's lock-on marker around an entity, inside the frame posed
     *                     at it
     */
    public static EntityRendererProvider<ChaffEntity> factory(ThermalVision thermal, Consumer<Entity> lockOnMarker) {
        return context -> new ChaffRenderer(context, thermal, lockOnMarker);
    }

    @Override
    public void doRender(@NotNull ChaffEntity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        this.bindTexture(TEX);
        this.model.render(entity, x, y, z, 0.5F);
        this.lockOnMarker.accept(entity);
    }
}
