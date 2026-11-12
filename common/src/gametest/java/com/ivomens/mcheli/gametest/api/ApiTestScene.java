package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.event.McheliEvent;
import com.ivomens.mcheli.api.event.Subscription;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the API event game tests share: a stone floor, a vehicle spawned through its item, and listeners that
 * are all removed when the test ends. A listener cannot fail a test itself (the bus logs what it throws), so
 * listeners write down what they saw and the test checks it.
 */
final class ApiTestScene {

    private final GameTestHelper h;
    private final List<Subscription> subscriptions = new ArrayList<>();

    ApiTestScene(GameTestHelper h) {
        this.h = h;
    }

    /** Stone at y = 0 over x and z from 0 to 15. */
    ApiTestScene floor() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                this.h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        return this;
    }

    AircraftItem item(VehicleKind kind, String id) {
        if (VehicleTypes.get(kind, id).flatMap(VehicleTypes::item).orElse(null) instanceof AircraftItem item) {
            return item;
        }
        throw new GameTestAssertException(id + " has no item");
    }

    /** Creates the vehicle at a position relative to the test and adds it to the level. */
    AircraftEntity spawn(VehicleKind kind, String id, Vec3 relative) {
        AircraftItem item = item(kind, id);
        Vec3 pos = this.h.absoluteVec(relative);
        AircraftEntity vehicle = item.createAircraft(this.h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        if (vehicle == null) {
            throw new GameTestAssertException("cannot create " + id);
        }
        this.h.getLevel().addFreshEntity(vehicle);
        return vehicle;
    }

    <E extends McheliEvent> void listen(Class<E> type, Consumer<? super E> listener) {
        this.subscriptions.add(McheliApi.get().events().register(type, listener));
    }

    void unsubscribeAll() {
        this.subscriptions.forEach(Subscription::unsubscribe);
        this.subscriptions.clear();
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
