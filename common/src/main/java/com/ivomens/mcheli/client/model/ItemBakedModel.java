package com.ivomens.mcheli.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wraps an item's normal (sprite) model and switches to the built-in renderer only for the display
 * contexts the {@link IItemModelRenderer} wants (e.g. first/third person), so inventory icons stay
 * flat sprites. The loader-specific subclass hooks the display transform, see {@link #onTransform}.
 */
public class ItemBakedModel implements BakedModel {

    private final BakedModel bakedModel;
    private final IItemModelRenderer renderer;
    private final ItemRenderContext context;
    private final ItemOverrides overrides;

    /** Creates the loader's subclass, which hooks the display transform. */
    @FunctionalInterface
    public interface Factory {
        ItemBakedModel create(BakedModel bakedModel, IItemModelRenderer renderer, ItemRenderContext context);
    }

    public ItemBakedModel(BakedModel bakedModel, IItemModelRenderer renderer, ItemRenderContext context) {
        this.bakedModel = bakedModel;
        this.renderer = renderer;
        this.context = context;
        this.overrides = new ContentItemOverrides(bakedModel, context);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return this.bakedModel.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.bakedModel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.bakedModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.bakedModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.renderer.shouldRender(this.context.stack(), this.context.displayContext())
                || this.bakedModel.isCustomRenderer();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.bakedModel.getParticleIcon();
    }

    @Override
    public @NotNull ItemTransforms getTransforms() {
        return this.bakedModel.getTransforms();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return this.overrides;
    }

    /** The wrapped sprite model. */
    protected BakedModel getWrappedModel() {
        return this.bakedModel;
    }

    protected IItemModelRenderer getRenderer() {
        return this.renderer;
    }

    /** What the client is drawing, shared by every wrapped item model. */
    protected ItemRenderContext getRenderContext() {
        return this.context;
    }

    /**
     * Remembers the display context about to be drawn, which decides {@link #isCustomRenderer()}. The
     * loader's model transform hook calls this before transforming.
     */
    protected void onTransform(ItemDisplayContext transformType) {
        this.context.transforming(transformType);
    }
}
