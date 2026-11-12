package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The aircraft fuel fluid {@code mcheli:mch_fuel} (still/flowing fluid, block and bucket). Aircraft
 * accept it through their fluid-handler capability; fuel names resolve to {@code mcheli:<name>} first.
 */
public final class NeoForgeFluids {

    public static final String MCH_FUEL_NAME = "mch_fuel";
    public static final int FUEL_COLOR = 0xCC8A5A2B;

    public static final Supplier<FluidType> MCH_FUEL_TYPE = Services.REGISTRY.register(NeoForgeRegistries.Keys.FLUID_TYPES,
            MCH_FUEL_NAME, () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.mcheli.mch_fuel")
                    .density(800)
                    .viscosity(1200)
                    .canExtinguish(false)
                    .supportsBoating(true)));

    public static final Supplier<BaseFlowingFluid.Source> MCH_FUEL = Services.REGISTRY.register(Registries.FLUID,
            MCH_FUEL_NAME, () -> new BaseFlowingFluid.Source(properties()));
    public static final Supplier<BaseFlowingFluid.Flowing> MCH_FUEL_FLOWING = Services.REGISTRY.register(Registries.FLUID,
            MCH_FUEL_NAME + "_flowing", () -> new BaseFlowingFluid.Flowing(properties()));

    public static final Supplier<LiquidBlock> MCH_FUEL_BLOCK = Services.REGISTRY.register(Registries.BLOCK, MCH_FUEL_NAME,
            () -> new LiquidBlock(MCH_FUEL.get(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .replaceable().noCollission().strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()));

    public static final Supplier<BucketItem> MCH_FUEL_BUCKET = Services.REGISTRY.register(Registries.ITEM, MCH_FUEL_NAME + "_bucket",
            () -> new BucketItem(MCH_FUEL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private NeoForgeFluids() {}

    /** Loading the class registers the fluid, its block and its bucket. */
    static void init() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(MCH_FUEL_TYPE, MCH_FUEL, MCH_FUEL_FLOWING)
                .bucket(MCH_FUEL_BUCKET).block(MCH_FUEL_BLOCK).slopeFindDistance(3).levelDecreasePerBlock(2);
    }
}
