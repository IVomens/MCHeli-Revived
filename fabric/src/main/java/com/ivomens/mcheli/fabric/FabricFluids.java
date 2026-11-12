package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.Services;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The aircraft fuel fluid {@code mcheli:mch_fuel} (still/flowing fluid, block and bucket), registered
 * with the same ids and properties as on NeoForge. NeoForge's fluid type also gives entities inside the
 * fuel water-like physics (swimming, drowning, boats); vanilla only does that for fluids tagged as water
 * or lava, so on Fabric the fuel behaves as a plain fluid block for entities.
 */
public final class FabricFluids {

    public static final String MCH_FUEL_NAME = "mch_fuel";
    public static final int FUEL_COLOR = 0xCC8A5A2B;
    /** The viscosity NeoForge's fluid type gives the fuel; both loaders put water at 1000. */
    private static final int FUEL_VISCOSITY = 1200;

    public static final Supplier<FuelFluid.Source> MCH_FUEL = Services.REGISTRY.register(Registries.FLUID,
            MCH_FUEL_NAME, FuelFluid.Source::new);
    public static final Supplier<FuelFluid.Flowing> MCH_FUEL_FLOWING = Services.REGISTRY.register(Registries.FLUID,
            MCH_FUEL_NAME + "_flowing", FuelFluid.Flowing::new);

    public static final Supplier<LiquidBlock> MCH_FUEL_BLOCK = Services.REGISTRY.register(Registries.BLOCK, MCH_FUEL_NAME,
            () -> new LiquidBlock(MCH_FUEL.get(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .replaceable().noCollission().strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()));

    public static final Supplier<BucketItem> MCH_FUEL_BUCKET = Services.REGISTRY.register(Registries.ITEM, MCH_FUEL_NAME + "_bucket",
            () -> new BucketItem(MCH_FUEL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private FabricFluids() {
    }

    /** Loading the class queues the fluid, its block and its bucket for registration. */
    static void init() {
    }

    /** Called once the registries are filled: the name and viscosity other mods' fluid displays show. */
    static void registerAttributes() {
        FluidVariantAttributeHandler attributes = new FluidVariantAttributeHandler() {
            @Override
            public Component getName(FluidVariant variant) {
                return Component.translatable("fluid.mcheli.mch_fuel");
            }

            @Override
            public int getViscosity(FluidVariant variant, @Nullable Level world) {
                return FUEL_VISCOSITY;
            }
        };
        FluidVariantAttributes.register(MCH_FUEL.get(), attributes);
        FluidVariantAttributes.register(MCH_FUEL_FLOWING.get(), attributes);
    }
}
