package com.ivomens.mcheli.api.client;

import java.util.ServiceLoader;

/** Finds MCHeli's implementation of the client API once, the first time {@link McheliClientApi#get()} is called. */
final class ClientApiHolder {

    static final McheliClientApi INSTANCE = load();

    private ClientApiHolder() {
    }

    private static McheliClientApi load() {
        return ServiceLoader.load(McheliClientApi.class, McheliClientApi.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of "
                        + McheliClientApi.class.getName() + " found; is MCHeli: Revived installed?"));
    }
}
