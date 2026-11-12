package com.ivomens.mcheli.platform;

import java.util.ServiceLoader;

/**
 * Finds the loader-specific implementation of a platform interface. Each loader lists its
 * implementations in {@code META-INF/services}. The client implementations of common interfaces
 * (see {@link #sided}) are listed in the shared resources.
 */
public final class Services {

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final RegistryHelper REGISTRY = load(RegistryHelper.class);
    public static final NetworkHelper NETWORK = load(NetworkHelper.class);
    public static final MenuHelper MENUS = load(MenuHelper.class);
    public static final ChunkTickets CHUNK_TICKETS = load(ChunkTickets.class);
    public static final GameplayHooks GAMEPLAY = load(GameplayHooks.class);
    public static final PackHelper PACKS = load(PackHelper.class);
    public static final FluidHelper FLUID = load(FluidHelper.class);
    public static final EnergyHelper ENERGY = load(EnergyHelper.class);
    public static final EntityDataHelper ENTITY_DATA = load(EntityDataHelper.class);
    public static final Permissions PERMISSIONS = load(Permissions.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName() + " found"));
    }

    /**
     * The client's implementation of a common interface on a physical client, and {@code server} on a
     * dedicated server, which so never loads a client class. Common code reaches client behaviour
     * only this way.
     */
    public static <T> T sided(Class<T> type, T server) {
        return PLATFORM.isClient() ? load(type) : server;
    }
}
