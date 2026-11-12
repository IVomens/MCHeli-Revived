package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.RegistryHelper;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.state.RegistryHolder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** One {@link DeferredRegister} per registry, attached to the mod bus as soon as the bus is known. */
public class NeoForgeRegistryHelper implements RegistryHelper {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    /** The mod bus FML passes to the mod constructor; deferred registers attach to it. */
    @RegistryHolder
    private static IEventBus modBus;

    /** Called by the mod constructor before common init; registers created later attach right away. */
    static synchronized void attach(IEventBus bus) {
        modBus = bus;
        REGISTERS.values().forEach(register -> register.register(bus));
    }

    @Override
    public synchronized <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name,
                                                              Supplier<T> factory) {
        return registerFor(registry).register(name, factory);
    }

    @Override
    public CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon,
                                             CreativeModeTab.DisplayItemsGenerator items) {
        return CreativeModeTab.builder()
                .title(title)
                .icon(icon)
                .displayItems(items)
                .withTabFactory(builder -> new LiveIconCreativeTab(builder, icon))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <R> DeferredRegister<R> registerFor(ResourceKey<? extends Registry<R>> registry) {
        DeferredRegister<R> register = (DeferredRegister<R>) REGISTERS.get(registry);
        if (register == null) {
            register = DeferredRegister.create(registry, Ids.MOD_ID);
            if (modBus != null) {
                register.register(modBus);
            }
            REGISTERS.put(registry, register);
        }
        return register;
    }
}
