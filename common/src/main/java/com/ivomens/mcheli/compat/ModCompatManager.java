package com.ivomens.mcheli.compat;

import com.ivomens.mcheli.platform.Services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional-mod detection. HBM's Nuclear Tech has no 1.21 version; its id stays so weapon definitions
 * that ask for it keep loading and simply see the mod as absent.
 */
public final class ModCompatManager {

    public static final String MODID_HBM = "hbm";

    private static final Map<String, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();

    private ModCompatManager() {}

    public static boolean isLoaded(String modId) {
        return LOADED_CACHE.computeIfAbsent(modId, id -> Services.PLATFORM.isModLoaded(id));
    }
}
