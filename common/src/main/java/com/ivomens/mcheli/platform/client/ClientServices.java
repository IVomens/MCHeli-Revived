package com.ivomens.mcheli.platform.client;

import com.ivomens.mcheli.platform.Services;

/**
 * Client-side counterpart of {@link Services}, kept apart so a dedicated server never loads the
 * client implementations.
 */
public final class ClientServices {

    public static final ClientPlatformHelper CLIENT = Services.load(ClientPlatformHelper.class);

    private ClientServices() {
    }
}
