package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.equipment.rangefinder.RangeFinderClient;

/** {@link RangeFinderClient} on a physical client: the session's range finder scope. */
public final class RangeFinderClientService implements RangeFinderClient {

    @Override
    public void lowerScope() {
        McheliClient.session().rangeFinder().lower();
    }
}
