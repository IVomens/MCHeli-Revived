package com.ivomens.mcheli.api;

import java.util.ServiceLoader;

/** Finds MCHeli's implementation of the API once, the first time {@link McheliApi#get()} is called. */
final class ApiHolder {

    static final McheliApi INSTANCE = load();

    private ApiHolder() {
    }

    private static McheliApi load() {
        return ServiceLoader.load(McheliApi.class, McheliApi.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + McheliApi.class.getName()
                        + " found; is MCHeli: Revived installed?"));
    }
}
