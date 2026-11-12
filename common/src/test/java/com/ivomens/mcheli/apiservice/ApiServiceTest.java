package com.ivomens.mcheli.apiservice;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.McheliEvent;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.event.VehicleSpawned;
import com.ivomens.mcheli.event.ApiEventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API's entry point and event bus, without a game: what the game tests cannot single out. */
class ApiServiceTest {

    @Test
    void implementsTheVersionTheBuildDeclares() {
        assertEquals(System.getProperty("mcheli.apiVersion"), ApiService.API_VERSION,
                "api_version in gradle.properties and ApiService.API_VERSION differ");
    }

    @Test
    void entryPointFindsTheService() {
        assertInstanceOf(ApiService.class, McheliApi.get());
        assertSame(McheliApi.get(), McheliApi.get());
        assertEquals(ApiService.API_VERSION, McheliApi.get().apiVersion());
    }

    @Test
    void listenersRunInOrderUntilUnsubscribed() {
        ApiEventBus bus = new ApiEventBus();
        List<String> calls = new ArrayList<>();
        Subscription first = bus.register(VehicleSpawned.class, event -> calls.add("first"));
        bus.register(VehicleSpawned.class, event -> {
            throw new IllegalStateException("a broken listener");
        });
        bus.register(VehicleSpawned.class, event -> calls.add("third"));
        assertTrue(bus.hasListeners(VehicleSpawned.class));

        bus.post(VehicleSpawned.class, () -> null);
        assertEquals(List.of("first", "third"), calls);

        first.unsubscribe();
        first.unsubscribe();
        bus.post(VehicleSpawned.class, () -> null);
        assertEquals(List.of("first", "third", "third"), calls);
    }

    @Test
    void onlyEventTypesCanBeListenedTo() {
        ApiEventBus bus = new ApiEventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.register(McheliEvent.class, event -> {
        }));
        assertFalse(bus.hasListeners(VehicleSpawned.class));
    }
}
