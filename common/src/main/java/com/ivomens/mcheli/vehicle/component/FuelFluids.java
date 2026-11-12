package com.ivomens.mcheli.vehicle.component;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.Locale;

/** Fuel names as the vehicle configs write them ("water", "mch_fuel", "othermod:oil"). */
public final class FuelFluids {

    public static final String DEFAULT_FUEL_TYPE = "mch_fuel";

    private FuelFluids() {
    }

    /** Registry name of a fluid in the short config form. */
    public static String nameOf(@Nullable Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        String namespace = key.getNamespace();
        return "minecraft".equals(namespace) || "mcheli".equals(namespace) ? key.getPath() : key.toString();
    }

    /** Resolves a config fluid name; names without a namespace are looked up in mcheli, then minecraft. */
    @Nullable
    public static Fluid byName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            return lookup(ResourceLocation.tryParse(normalized));
        }
        Fluid fluid = lookup(ResourceLocation.tryBuild("mcheli", normalized));
        return fluid != null ? fluid : lookup(ResourceLocation.tryBuild("minecraft", normalized));
    }

    @Nullable
    private static Fluid lookup(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid == Fluids.EMPTY ? null : fluid;
    }
}
